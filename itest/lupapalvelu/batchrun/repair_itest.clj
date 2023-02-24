(ns lupapalvelu.batchrun.repair-itest
  (:require [clj-uuid :as uuid]
            [hiccup.core :as hiccup]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.batchrun.repair :as repair]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.review :refer [application-vs-kuntagml-bad-review-candidates]]
            [lupapalvelu.tasks :as tasks]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.date :as date]
            [sade.files :as files]
            [sade.util :as util]
            [sade.xml :as xml]))

(testable-privates lupapalvelu.batchrun.repair
                   pack-dates date-changes verdict-dates-updates)

(defn make-xml-review
  ([name kind authority date state id app]
   [:katselmustieto
    [:Katselmus
     (when id
       [:muuTunnustieto
        [:MuuTunnus [:sovellus app] [:tunnus id]]])
     (when date
       [:pitoPvm (date/xml-date date)])
     (when state
       [:osittainen state])
     (when authority
       [:pitaja authority])
     [:katselmuksenLaji kind]
     [:tarkastuksenTaiKatselmuksenNimi name]]])
  ([name kind authority date state]
   (make-xml-review name kind authority date state (str (uuid/v1)) "Testitausta")))

(defn make-mongo-review
  ([{:keys [name kind authority date state id]} options]
   (let [{:keys [schema source]
          :as   options} (-> {:schema  "task-katselmus"
                              :created (date/now)
                              :state   "sent"
                              :source  "background"}
                             (merge options)
                             (update :created date/timestamp))]
     (assoc (tasks/new-task schema
                            name
                            {:katselmuksenLaji (or kind "muu katselmus")
                             :muuTunnus id
                             :katselmus        {:pitaja    authority
                                                :pitoPvm   (date/finnish-date date :zero-pad)
                                                :tila      state}}
                            options
                            {:type source})
            ;; So we can be sure that date-range filtering targets modified.
            :created (date/timestamp "1.1.2020")))))

(defn make-kuntagml [application-id municipality-id & reviews]
  (-> [:Rakennusvalvonta
       [:RakennusvalvontaAsia
        [:luvanTunnisteTiedot
         [:LupaTunnus
          (when municipality-id
            [:kuntalupatunnus municipality-id])
          (when application-id
            [:muuTunnustieto
             [:MuuTunnus [:tunnus application-id] [:sovellus "Lupapiste"]]])]]
        reviews]]
      hiccup/html
      (xml/parse-string "utf8")))

(let [cfg  (fn [start end]
             {:start-ts (date/timestamp start)
              :end-ts   (date/timestamp (date/end-of-day end))})
      may5 (make-mongo-review {:name  "May Day Review" :authority "Random Reviewer"
                               :state "lopullinen"     :date      "1.5.2022"}
                              {:created "5.5.2022 9:00"})]
  (facts application-vs-kuntagml-bad-review-candidates
    (fact "Nothing"
      (application-vs-kuntagml-bad-review-candidates (cfg "1.5.2022" "2.5.2022")
                                                     {} [])
      => {:kuntagml [] :application []})
    (fact "One candidate"
      (application-vs-kuntagml-bad-review-candidates (cfg "5.5.2022" "5.5.2022")
                                                     {:tasks [may5]}
                                                     [])
      => (just {:kuntagml    []
                :application (just (contains {:taskname "May Day Review"}))}))
    (fact "No suitable candidates"
      (application-vs-kuntagml-bad-review-candidates
        (cfg "5.5.2022" "5.5.2022")
        {:tasks [(assoc-in may5 [:data :katselmus :tila :modified] 12345)
                 (assoc may5 :state "not sent")
                 (assoc-in may5 [:source :type] "verdict")
                 (assoc-in may5 [:schema-info :name] "task-lupamaarays")
                 (assoc-in may5 [:data :katselmus :tila :modified] nil)
                 (update-in may5 [:data :katselmus :tila] dissoc :modified)]}
        []) => {:kuntagml [] :application []})
    (fact "KuntaGML"
      (application-vs-kuntagml-bad-review-candidates
        {} {}
        (make-kuntagml nil nil
                       (make-xml-review "XML Review" "aloituskokous"
                                        "RR" "10.5.2022" "pidetty")))
      => (just {:application []
                :kuntagml    (just (contains {:taskname "XML Review"}))}))
    (fact "Lupapiste review returned from the backing system"
      (application-vs-kuntagml-bad-review-candidates
        {} {}
        (make-kuntagml nil nil
                       (make-xml-review "XML Review" "aloituskokous"
                                        "RR" "10.5.2022" "pidetty"
                                        "abcdefgh" "Lupapiste")))
      => {:kuntagml [] :application []}))

  (facts "bad-reviews"
    (fact "No mongo reviews"
      (repair/bad-reviews {} {} []) => nil
      (repair/bad-reviews {} {} (make-kuntagml nil nil
                                               (make-xml-review "XML Review" "aloituskokous"
                                                                "RR" "10.5.2022" "pidetty")))
      => nil)
    (fact "Good review by id"
      (repair/bad-reviews (cfg "1.5.2022" "2.5.2022")
                          {:tasks [(make-mongo-review {:name "Good" :id "good-id"}
                                                      {:created "2.5.2022"})]}
                          (make-kuntagml nil nil
                                         (make-xml-review "XML Review" "aloituskokous"
                                                          "RR" "10.5.2022" "lopullinen"
                                                          "good-id" "System")))
      => nil)
    (fact "Blank id is not suitable id"
      (repair/bad-reviews (cfg "1.5.2022" "2.5.2022")
                          {:tasks [(make-mongo-review {:name "Bad" :id " "}
                                                      {:created "2.5.2022"})]}
                          (make-kuntagml nil nil
                                         (make-xml-review "XML Review" "aloituskokous"
                                                          "RR" "10.5.2022" "lopullinen"
                                                          " " "System")))
      => (just (contains {:taskname "Bad"})))
    (fact "Ids differ but matches otherwise"
      (repair/bad-reviews (cfg "1.5.2022" "2.5.2022")
                          {:tasks [(make-mongo-review {:name      "My Review"  :kind "aloituskokous"
                                                       :state     "lopullinen" :date "10.5.2022"
                                                       :authority "RR"
                                                       :id        "id-in-mongo"}
                                                      {:created "2.5.2022"})]}
                          (make-kuntagml nil nil
                                         (make-xml-review "My Review" "aloituskokous"
                                                          "RR" "10.5.2022" "lopullinen"
                                                          "id-in-xml" "System")))
      => nil
      (repair/bad-reviews (cfg "1.5.2022" "2.5.2022")
                          {:tasks [(make-mongo-review {:name      "My Review"  :kind "aloituskokous"
                                                       :state     "lopullinen" :date "10.5.2022"
                                                       :authority {:_atomic-map? true :code "RR"}
                                                       :id        "id-in-mongo"}
                                                      {:created "2.5.2022"})]}
                          (make-kuntagml nil nil
                                         (make-xml-review "My Review" "aloituskokous"
                                                          "RR" "10.5.2022" "lopullinen"
                                                          "id-in-xml" "System")))
      => nil)
    (let [review (make-mongo-review {:name "Otherwise bad"}
                                    {:created "2.5.2022"})]
      (fact "Review cannot be bad if its attachment is archived"
        (repair/bad-reviews (cfg "1.5.2022" "2.5.2022")
                            {:tasks       [review]
                             :attachments [{:target   {:type "task" :id (:id review)}
                                            :metadata {:tila "arkistoitu"}}]}
                            [])
        => nil
        (repair/bad-reviews (cfg "1.5.2022" "2.5.2022")
                            {:tasks       [review]
                             :attachments [{:source   {:type "tasks" :id (:id review)}
                                            :metadata {:tila "arkistoitu"}}]}
                            [])
        => nil))))

(facts "pack-dates"
  (pack-dates nil) => nil
  (pack-dates {}) => nil
  (pack-dates :foo {:maaraysPvm  (date/now)
                    :paatospvm   nil
                    :anto        " "
                    :aloitettava (date/timestamp "10.5.2022 12.31.44")})
  => {:foo.maaraysPvm  (date/timestamp (date/today))
      :foo.aloitettava (date/timestamp "10.5.2022")}
  (pack-dates :foo
              {:one   {:two  {:maaraysaika (date/timestamp "2.6.2022")
                              :three       {:toteutusHetki (date/timestamp "3.6.2022")}}
                       :four [{:paatospvm (date/timestamp "4.6.2022")}
                              nil
                              {}
                              {:five {:lainvoimainen (date/timestamp "5.6.2022")
                                      :six           [{:bar       10
                                                       :julkipano (date/timestamp "6.6.2022")}]}}]}
               :seven [{:eight []}]})
  => {:foo.one.four.0.paatospvm            (date/timestamp "4.6.2022")
      :foo.one.four.3.five.lainvoimainen   (date/timestamp "5.6.2022")
      :foo.one.four.3.five.six.0.julkipano (date/timestamp "6.6.2022")
      :foo.one.two.maaraysaika             (date/timestamp "2.6.2022")
      :foo.one.two.three.toteutusHetki     (date/timestamp "3.6.2022")})

(facts "date-changes"
  (date-changes :test.0 {} {}) => nil
  (date-changes :test.0 {:paatospvm (date/timestamp "2.5.2022")} {}) => nil
  (date-changes :test.0
                {:paatospvm (date/timestamp "2.5.2022")}
                {:paatospvm (date/timestamp "2.5.2022")}) => nil
  (date-changes :test.0
                {:paatospvm (date/timestamp "2.5.2022")}
                {:paatospvm (date/timestamp "2.5.2022 18:50")}) => nil
  (date-changes :test.0
                {:paatospvm (date/timestamp "2.5.2022")}
                {:paatospvm (date/timestamp "3.5.2022")})
  => {:test.0.paatospvm (date/timestamp "3.5.2022")}
  (date-changes :test.0
                {:paatospvm (date/timestamp "2.5.2022")}
                {:other {:paatospvm (date/timestamp "3.5.2022")}})
  => {:test.0.other.paatospvm (date/timestamp "3.5.2022")}
  (date-changes :foo
                {:one   {:two  {:maaraysaika (date/timestamp "2.6.2022")
                                :three       {:toteutusHetki (date/timestamp "3.6.2022")}}
                         :four [{:paatospvm (date/timestamp "4.6.2022")}
                                nil
                                {}
                                {:five {:lainvoimainen (date/timestamp "5.6.2022")
                                        :six           [{:bar       10
                                                         :julkipano (date/timestamp "6.6.2022")}]}}]}
                 :seven [{:eight []}]}
                {:one   {:two  {:maaraysaika (date/timestamp "2.6.2022")
                                :three       {:toteutusHetki (date/timestamp "3.7.2022")}}
                         :four [{:paatospvm (date/timestamp "4.6.2022")}
                                nil
                                {}
                                {:five {:lainvoimainen (date/timestamp "5.6.2022")
                                        :six           [{:bar       10
                                                         :julkipano (date/timestamp "6.7.2022")}]}}]}
                 :seven [{:eight [{:raukeamis (date/timestamp "7.7.2022")}]}]})
  => {:foo.one.four.3.five.six.0.julkipano (date/timestamp "6.7.2022")
      :foo.one.two.three.toteutusHetki     (date/timestamp "3.7.2022")
      :foo.seven.0.eight.0.raukeamis       (date/timestamp "7.7.2022")})

(facts "verdict-dates-updates"
  (let [v1 {:kuntalupatunnus "one"
            :paatospvm       (date/timestamp "1.5.2022")
            :notes           [{:lainvoimainen (date/timestamp "2.5.2022")}]
            :timestamp       100}
        v2 {:kuntalupatunnus "two"
            :paatospvm       (date/timestamp "2.5.2022")
            :notes           [{:lainvoimainen (date/timestamp "2.5.2022")}
                              {:julkipano (date/timestamp "3.5.2022")
                               :anto      (date/timestamp "4.5.2022")}]
            :timestamp       200}]
    (verdict-dates-updates [0 1000] {:verdicts [v1]}) => nil
    (provided (lupapalvelu.verdict/get-xml-verdicts anything) => nil)
    (verdict-dates-updates [0 1000] {:verdicts [v1]}) => nil
    (provided (lupapalvelu.verdict/get-xml-verdicts anything) => [v2])
    (verdict-dates-updates [0 1000] {:verdicts [v1]}) => nil
    (provided (lupapalvelu.verdict/get-xml-verdicts anything) => [v1 v2])
    (verdict-dates-updates [0 90] {:verdicts [v1]}) => nil
    (provided (lupapalvelu.verdict/get-xml-verdicts anything) => nil)
    (verdict-dates-updates [0 1000] {:verdicts [(dissoc v1 :timestamp)]}) => nil
    (provided (lupapalvelu.verdict/get-xml-verdicts anything) => nil)
    (verdict-dates-updates [0 1000] {:verdicts [(assoc v1 :draft true)]}) => nil
    (provided (lupapalvelu.verdict/get-xml-verdicts anything) => nil)
    (verdict-dates-updates [0 1000] {:verdicts [v1]})
    => {$set {:verdicts.0.paatospvm              (date/timestamp "5.5.2022")
              :verdicts.0.others.0.toteutusHetki (date/timestamp "6.5.2022")}}
    (provided (lupapalvelu.verdict/get-xml-verdicts anything)
              => [{:kuntalupatunnus "one"
                   :paatospvm       (date/timestamp "5.5.2022 14.00")
                   :others          [{:toteutusHetki (date/timestamp "6.5.2022 14:01:02")}]}])
    (verdict-dates-updates [0 1000] {:verdicts [v2 v1]})
    => {$set {:verdicts.1.paatospvm              (date/timestamp "5.5.2022")
              :verdicts.1.others.0.toteutusHetki (date/timestamp "6.5.2022")}}
    (provided (lupapalvelu.verdict/get-xml-verdicts anything)
              => [{:kuntalupatunnus "one"
                   :paatospvm       (date/timestamp "5.5.2022 14.00")
                   :others          [{:toteutusHetki (date/timestamp "6.5.2022 14:01:02")}]}])
    (verdict-dates-updates [0 1000] {:verdicts [v2]}) => nil
    (provided (lupapalvelu.verdict/get-xml-verdicts anything)
              => [{:kuntalupatunnus "one"
                   :paatospvm       (date/timestamp "5.5.2022 14.00")
                   :others          [{:toteutusHetki (date/timestamp "6.5.2022 14:01:02")}]}])
    (verdict-dates-updates [0 1000] {:verdicts [v2 v1]})
    => {$set {:verdicts.0.notes.1.anto           (date/timestamp "4.6.2022")
              :verdicts.1.paatospvm              (date/timestamp "5.5.2022")
              :verdicts.1.others.0.toteutusHetki (date/timestamp "6.5.2022")}}
    (provided (lupapalvelu.verdict/get-xml-verdicts anything)
              => [{:kuntalupatunnus "one"
                   :paatospvm       (date/timestamp "5.5.2022 14.00")
                   :others          [{:toteutusHetki (date/timestamp "6.5.2022 14:01:02")}]}
                  {:kuntalupatunnus "two"
                   :notes           [{}
                                     {:anto (date/timestamp "4.6.2022")}]}
                  {:kuntalupatunnus "three"
                   :paatospvm       (date/timestamp "5.7.2022 14.00")
                   :others          [{:toteutusHetki (date/timestamp "6.7.2022 14:01:02")}]}])))



(defn task-marked-faulty? [app-id task-id]
  (->> (mongo/by-id :applications app-id)
       :tasks
       (util/find-by-id task-id)
       :state
       (= "faulty_review_task")))

(defn attachment-exists? [app-id task-id]
  (->> (mongo/by-id :applications app-id)
       :attachments
       (some (util/fn-> :target :id (= task-id)))
       boolean))

(mount/start #'mongo/connection)
(mongo/with-db "test_repair_batchruns"
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (against-background
      [(lupapalvelu.batchrun/mount-for-batchrun anything) => nil]
      (let [{app-id :id}          (create-and-submit-local-application pena
                                                                       :address "Review Repair Road"
                                                                       :operation "pientalo")
            {review-id :id
             :as       bs-review} (make-mongo-review {:name      "Done Deal"  :kind "loppukatselmus"
                                                      :state     "lopullinen" :date "10.5.2022"
                                                      :authority "Random Reviewer"
                                                      :id        "done-deal-id"}
                                                     {:created "2.5.2022"})]
        (mongo/update-by-id :organizations
                            "753-R"
                            {$set {:krysp.R.url (str (server-address) "/dev/krysp")}})

        (fact "Get verdict"
          (command sonja :check-for-verdict :id app-id) => ok?)
        (fact "Add review task"
          (mongo/update-by-id :applications app-id {$push {:tasks bs-review}}) => nil)
        (files/with-temp-file edn
          (->> {:date-range      ["1.5.2022" "2.5.2022"]
                :application-ids [app-id]}
               repair/config->str
               (spit edn))

          (let [bad-args? (throws #"Bad arguments")]
            (facts "Bad batchrun arguments"
              (batchrun/review-cleanup) => bad-args?
              (batchrun/review-cleanup (str (uuid/v1) ".edn")) => (throws #"File not found")
              (batchrun/review-cleanup (.getAbsolutePath edn) "-bad") => bad-args?
              (batchrun/review-cleanup (.getAbsolutePath edn) "-dry-run" "extra") => bad-args?
              (files/with-temp-file tmp
                (spit tmp "bad data")
                (batchrun/review-cleanup (.getAbsolutePath tmp)) => (throws #"does not match schema"))))

          (fact "Matching review from the backing system"
            (batchrun/review-cleanup (.getAbsolutePath edn)) => nil
            (provided
              (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                anything anything [app-id] :application-id false)
              => (make-kuntagml app-id nil (make-xml-review "Backroom Deal" "loppukatselmus"
                                                            "BB" "1.5.2022" "pidetty"
                                                            "done-deal-id" "Backend")))
            (task-marked-faulty? app-id review-id) => false)

          (fact "Empty messages from the backing system"
            (batchrun/review-cleanup (.getAbsolutePath edn)) => nil
            (provided
              (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                anything anything anything anything false)
              => (xml/parse-string "<sorry>No data, much empty</sorry>" "utf8"))
            (task-marked-faulty? app-id review-id) => false)

          (fact "Faultify candidate, but dry-run"
            (batchrun/review-cleanup (.getAbsolutePath edn) "-dry-run") => nil
            (provided
              (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                anything anything [app-id] :application-id false)
              => (make-kuntagml app-id nil))
            (task-marked-faulty? app-id review-id) => false)

          (fact "Marked faulty"
            (batchrun/review-cleanup (.getAbsolutePath edn)) => nil
            (provided
              (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                anything anything [app-id] :application-id false)
              => (make-kuntagml app-id nil))
            (task-marked-faulty? app-id review-id) => true)

          (let [{task-id :id
                 :as     meet} (->> (mongo/by-id :applications app-id)
                                    :tasks
                                    (util/find-by-key :taskname "Aloituskokous"))]
            (facts "Aloituskokous"
              (fact "Required, not done"
                meet => (contains {:state  "requires_user_action"
                                   :source (contains {:type "verdict"})}))
              (fact "Let's meet!"
                (command sonja :update-task :id app-id
                         :collection "tasks"
                         :doc task-id
                         :updates [["katselmus.tila" "lopullinen"]
                                   ["katselmus.pitoPvm" "08.05.2022"]
                                   ["katselmus.pitaja" "Sinbad Sibbo"]]) => ok?
                (command sonja :review-done :id app-id
                         :taskId task-id
                         :lang "fi") => ok?)
              (fact "Attachment is created"
                (attachment-exists? app-id task-id) => true)
              (fact "Cleanup does not faultify Aloituskokous"
                (batchrun/review-cleanup (.getAbsolutePath edn)) => nil
                (provided
                  (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                    anything anything [app-id] :application-id false)
                  => (make-kuntagml app-id nil))
                (task-marked-faulty? app-id task-id) => false)
              (fact "Modify Aloituskokous: backing system review, timestamp and archived attachment"
                (mongo/update-by-query :applications
                                       {:_id         app-id
                                        :attachments {$elemMatch {:source.id task-id}}}
                                       {$set {:attachments.$.metadata.tila "arkistoitu"}}) => 1
                (mongo/update-by-query :applications
                                       {:_id   app-id
                                        :tasks {$elemMatch {:id task-id}}}
                                       {$set {:tasks.$.source.type                  "background"
                                              :tasks.$.data.katselmus.tila.modified (date/timestamp "2.5.2022")}})
                => 1)
              (fact "Cleanup still does not faultify Aloituskokous"
                (batchrun/review-cleanup (.getAbsolutePath edn)) => nil
                (provided
                  (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                    anything anything [app-id] :application-id false)
                  => (make-kuntagml app-id nil))
                (task-marked-faulty? app-id task-id) => false
                (attachment-exists? app-id task-id) => true)
              (fact "Make the attachment not archived"
                (mongo/update-by-query :applications
                                       {:_id         app-id
                                        :attachments {$elemMatch {:source.id task-id}}}
                                       {$set {:attachments.$.metadata.tila "luonnos"}}) => 1)
              (fact "Now cleanup cleans up"
                (batchrun/review-cleanup (.getAbsolutePath edn)) => nil
                (provided
                  (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                    anything anything [app-id] :application-id false)
                  => (make-kuntagml app-id nil))
                (task-marked-faulty? app-id task-id) => true
                (attachment-exists? app-id task-id) => false)))

          (facts "fix-verdict-dates"
            (let [{:keys [verdicts] :as application} (mongo/by-id :applications app-id)]
              (fact "No sheriff notes"
                (:_sheriffi-notes application) => nil)
              (fact "Old verdict date"
                (date/before? (:verdictDate application) "1.1.2014") => true)
              (fact "Timestamp does not match"
                (batchrun/fix-verdict-dates (.getAbsolutePath edn)) => nil
                (provided
                  (lupapalvelu.verdict/get-xml-verdicts anything) => nil :times 0))
              (fact "Reset verdictDate. Change the first verdict's paatospvm, timestamp and make it draft"
                (mongo/update-by-id :applications
                                    app-id
                                    {$set {:verdictDate          0
                                           :verdicts.0.timestamp (date/timestamp "2.5.2022")
                                           :verdicts.0.paatokset.0.poytakirjat.0.paatospvm
                                           (date/timestamp "1.6.2022")
                                           :verdicts.0.draft     true}})
                (batchrun/fix-verdict-dates (.getAbsolutePath edn)) => nil
                (provided
                  (lupapalvelu.verdict/get-xml-verdicts anything) => nil :times 0))
              (fact "No longer draft, but dry-run"
                (mongo/update-by-id :applications
                                    app-id
                                    {$unset {:verdicts.0.draft true}})
                (batchrun/fix-verdict-dates (.getAbsolutePath edn) "-dry-run") => nil
                (fact "No changes"
                  (let [a (mongo/by-id :applications app-id)]
                    (:_sheriff-notes a) => nil
                    (:verdictDate a) => zero?
                    (-> a :verdicts first :paatokset first :poytakirjat
                        first :paatospvm (date/eq? "1.6.2022")) => true)))
              (fact "Successful update"
                (batchrun/fix-verdict-dates (.getAbsolutePath edn))
                (let [a (mongo/by-id :applications app-id)]
                  (-> a :_sheriff-notes first :note) => "Batchrun: fix-verdict-dates"
                    (:verdictDate a) => (:verdictDate application)
                    (-> a :verdicts first :paatokset) => (-> verdicts first :paatokset)))
              (fact "No matching kuntalupatunnus"
                (mongo/update-by-id :applications
                                    app-id
                                    {$set {:verdicts.0.kuntalupatunnus                   "Unknown"
                                           :verdicts.0.paatokset.0.paivamaarat.anto      (date/timestamp "2.6.2022")
                                           :verdicts.0.paatokset.0.paivamaarat.julkipano (date/timestamp "3.6.2022")}})
                (batchrun/fix-verdict-dates (.getAbsolutePath edn))
                (let [a (mongo/by-id :applications app-id)]
                  (-> a :_sheriff-notes count) => 1
                  (-> a :verdicts first :paatokset first :paivamaarat :anto (date/eq? "2.6.2022")) => true))
              (fact "Success again"
                (mongo/update-by-id :applications
                                    app-id
                                    {$set {:verdicts.0.kuntalupatunnus (-> verdicts
                                                                           first
                                                                           :kuntalupatunnus)}})
                (batchrun/fix-verdict-dates (.getAbsolutePath edn))
                (let [a (mongo/by-id :applications app-id)]
                  (-> a :_sheriff-notes count) => 2
                  (-> a :verdicts first :paatokset first :paivamaarat)
                  => {:anto      (-> verdicts first :paatokset first :paivamaarat :anto)
                      :julkipano (date/timestamp "3.6.2022")})))))

        (files/with-temp-file edn
          (->> {:date-range       ["10.5.2022" "20.5.2022"]
                :organization-ids ["753-R"]}
               repair/config->str
               (spit edn))

          (fact "Add new backing system review (and change organization)"
            (mongo/update-by-id :applications app-id
                                {$push {:tasks (make-mongo-review {:name      "Sightseeing"
                                                                   :kind      "sijaintikatselmus"
                                                                   :state     "lopullinen"
                                                                   :date      "22.5.2022"
                                                                   :authority "Some Surveyor"
                                                                   :id        "sightseeing-id"}
                                                                  {:created "15.5.2022"})}
                                 $set  {:organization "186-R"}})

            => nil)
          (let [task-id (->> (mongo/by-id :applications app-id)
                             :tasks last :id)]
            (fact "Cleanup does not touch the task"
              (batchrun/review-cleanup (.getAbsolutePath edn)) => nil
              (provided
                (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                  anything anything [app-id] :application-id false)
                => nil :times 0)
              (task-marked-faulty? app-id task-id) => false)
            (fact "Verdict dates ditto"
              (mongo/update-by-id :applications app-id
                                  {$set {:verdicts.1.timestamp (date/timestamp "12.5.2022")
                                         :verdicts.1.paatokset.1.poytakirjat.0.paatospvm
                                         (date/timestamp "10.5.2022")}})
              (batchrun/fix-verdict-dates (.getAbsolutePath edn)) => nil
              (provided
                (lupapalvelu.verdict/get-xml-verdicts anything) => nil :times 0))
            (fact "Revert organization change"
              (mongo/update-by-id :applications app-id {$set {:organization "753-R"}}) => nil)
            (fact "Cleanup marks the review faulty"
              (batchrun/review-cleanup (.getAbsolutePath edn)) => nil
              (provided
                (lupapalvelu.backing-system.krysp.reader/rakval-application-xml
                  anything anything [app-id] :application-id false)
                => (make-kuntagml app-id nil))
              (task-marked-faulty? app-id task-id) => true)
            (fact "Verdict dates fixed"
              (batchrun/fix-verdict-dates (.getAbsolutePath edn)) => nil
              (-> (mongo/by-id :applications app-id) :_sheriff-notes count) => 3)))))))
