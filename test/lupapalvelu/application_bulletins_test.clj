(ns lupapalvelu.application-bulletins-test
  (:require [lupapalvelu.application-bulletins :refer :all]
            [lupapalvelu.mongo :refer [create-id]]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict-canonical-test :as pate-test]
            [lupapalvelu.publish-dates :as pd]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]))

(def day-before-ryhti-publish-date (date/minus pd/ryhti-2025-publish-date :day))
(def day-after-ryhti-publish-date (date/plus pd/ryhti-2025-publish-date :day))

(def example-app {:documents   [{:schema-info {:type :party} :data {:name "party 1"}}
                                {:schema-info {:type "party"} :data {:name "party 2"}}
                                {:schema-info {:type :location} :data {:name "location" :x 1 :y 2}}
                                {:schema-info {} :data {:name "untyped"}}]
                  :attachments [{:type "test1" :versions [{:id 1} {:id 2}] :latestVersion {:fileId 223 :filename "2.txt"} :source {:type "verdicts"}}
                                {:type "testi2"}
                                nil]
                  :permitType  "R"
                  :state       :submitted
                  :submitted   day-before-ryhti-publish-date})

(facts "Remove party docs"
  (let [docs (:documents example-app)
        expected-docs [{:schema-info {:type :location} :data {:name "location" :x 1 :y 2}}
                       {:schema-info {} :data {:name "untyped"}}]]
    (remove-party-docs-fn docs) => expected-docs))

(fact "Snapshot"
  (let [snapshot (create-bulletin-snapshot example-app)
        updates  (snapshot-updates snapshot {} 123)]
    (fact "attachments with latest versions"
      (:attachments snapshot) => [{:type "test1" :latestVersion {:fileId 223 :filename "2.txt"} :source {:type "verdicts"}}])

    (fact "state"
      (:state snapshot) => :submitted
      (:bulletinState snapshot) => :proclaimed)

    (fact "submitted"
      (:submitted snapshot) => day-before-ryhti-publish-date)

    (fact "process"
      (:bulletinProcess snapshot) => :julkipano)

    (fact "mongo update clause, versions are pushed"
      updates => {$push {:versions snapshot}
                  $set  {:modified 123}}))

  (facts "kuulutukset"
    (let [snapshot (-> example-app
                       (assoc :submitted (date/timestamp day-after-ryhti-publish-date))
                       create-bulletin-snapshot)]
      (fact "process"
        (:bulletinProcess snapshot) => :kuulutus))))


(fact "Bulletin states mapping from app states"
  (bulletin-state :draft) => :proclaimed
  (bulletin-state :submitted) => :proclaimed
  (bulletin-state :sent) => :proclaimed
  (bulletin-state :verdictGiven) => :verdictGiven
  (bulletin-state :final) => :final
  (bulletin-state :appealed) => :verdictGiven
  (fact "default is 'proclaimed'"
    (bulletin-state :foo) => (throws IllegalArgumentException "No matching clause: :foo")))


(def timestamp 1503003635780)
(def verdict-id (create-id))
(def kuntalupatunnus "lupatunnus")
(def anto 1503003635781)
(def julkipano (- anto 100000))
(def signed1 1503003635782)
(def signed2 1503003635783)
(def signer-id1 (create-id))
(def signer-id2 (create-id))
(def lainvoimainen 1503003635784)
(def paatospvm 1503003635785)
(def handler "handler")
(def verdict-text "Decisions were made.")
(def section "1")
(def status 2)
(def code "hyväksytty")
(def signatures
  [{:created signed1
    :user {:id signer-id1
           :firstName "First"
           :lastName "Name"}}
   {:created signed2
    :user {:id signer-id2
           :firstName "Second"
           :lastName "Name"}}])
(def test-verdict {:id verdict-id
                   :kuntalupatunnus kuntalupatunnus
                   :draft true
                   :timestamp timestamp
                   :sopimus false
                   :metadata nil
                   :paatokset [{:id "5b7e5772e7d8a1a88e669357"
                                :paivamaarat    {:anto anto
                                                 :julkipano julkipano
                                                 :lainvoimainen lainvoimainen}
                                :poytakirjat    [{:paatoksentekija handler
                                                  :urlHash "5b7e5772e7d8a1a88e669356"
                                                  :status status
                                                  :paatos verdict-text
                                                  :paatospvm paatospvm
                                                  :pykala section
                                                  :paatoskoodi code}]
                                :lupamaaraykset {:autopaikkojaEnintaan      0
                                                 :autopaikkojaKiinteistolla 0
                                                 :autopaikkojaRakennettu    0
                                                 :maaraykset                [{:sisalto "Muu 1"}
                                                                             {:sisalto "Muu 2"}]
                                                 :vaaditutErityissuunnitelmat []
                                                 :vaaditutKatselmukset [{:tarkastuksenTaiKatselmuksenNimi "Tarkastus"}]
                                                 :vaaditutTyonjohtajat "Supervising supervisor"}}]})

(def migrated-signatures
  [{:date    signed1
    :user-id signer-id1
    :name "First Name"}
   {:date    signed2
    :user-id signer-id2
    :name "Second Name"}])

(defn wrap [x]
  (metadata/wrap "Verdict draft Pate migration" timestamp x))

(def pate-test-verdict {:id         verdict-id
                        :modified   timestamp
                        :category   "r"
                        :state      (wrap "draft")
                        :data       {:handler         (wrap handler)
                                     :kuntalupatunnus (wrap kuntalupatunnus)
                                     :verdict-section (wrap section)
                                     :verdict-code    (wrap (str status))
                                     :verdict-text    (wrap verdict-text)
                                     :anto            (wrap anto)
                                     :julkipano       (wrap julkipano)
                                     :lainvoimainen   (wrap lainvoimainen)
                                     :reviews         {"katselmus-id" {:name (wrap "Tarkastus")
                                                                       :type (wrap "muu-tarkastus")}}
                                     :foremen         {"foreman-id" {:role (wrap "Supervising supervisor")}}
                                     :conditions      {"condition-id1" {:name (wrap "Muu 1")}
                                                       "condition-id2" {:name (wrap "Muu 2")}}
                                     :plans           ["plan1" "plan3"]
                                     :plans-included  true ;; For some reason plans are still listed even if this is false
                                     :attachments     [{:type-group "paatoksenteko"
                                                        :type-id    "paatos"
                                                        :amount     1}
                                                       {:type-group "muut"
                                                        :type-id    "paatosote"
                                                        :amount     1}]}
                        :references {:plans          [{:id "plan1"
                                                       :fi "Suunnitelma 1"}
                                                      {:id "plan2"
                                                       :fi "Suunnitelma 2"}
                                                      {:id "plan3"
                                                       :fi "Suunnitelma 3"}]
                                     :verdict-givers [{:code     "86"
                                                       :fi       "Maxwell Smart"
                                                       :sv       "Maxwell Smart"
                                                       :en       "Maxwell Smart"
                                                       :selected false}]}
                        :template   {:giver      "lautakunta"
                                     :inclusions [:foreman-label :conditions-title :foremen-title :kuntalupatunnus :verdict-section :verdict-text :anto :attachments :foremen.role :foremen.remove :verdict-code :conditions.name :conditions.remove :reviews-title :type-label :reviews.name :reviews.type :reviews.remove :add-review :name-label :condition-label :lainvoimainen :handler :add-foreman :upload :add-condition]}
                        :legacy?    true})

(facts "verdict-data-for-bulletin-snapshot"
  (fact "Backing system verdict"
    (verdict-data-for-bulletin-snapshot test-verdict)
    => {:contact "handler"
        :section "1"
        :status  2
        :text    "Decisions were made."})
  (fact "Legacy verdict"
    (verdict-data-for-bulletin-snapshot pate-test-verdict)
    => {:contact "handler"
        :section "1"
        :status  2
        :text    "Decisions were made."})
  (fact "Pate verdict"
    (-> pate-test-verdict
        (dissoc :legacy?)
        (assoc-in [:data :verdict-code] "hyvaksytty")
        verdict-data-for-bulletin-snapshot )
    => {:contact "handler"
        :section "1"
        :code    "hyvaksytty"
        :text    "Decisions were made."})
  (fact "Pate verdict, giver as literal"
    (-> pate-test-verdict
        (dissoc :legacy?)
        (assoc-in [:data :verdict-code] "hyvaksytty")
        (assoc-in [:data :giver] "FirstName LastName")
        verdict-data-for-bulletin-snapshot )
    => {:contact "FirstName LastName"
        :section "1"
        :code    "hyvaksytty"
        :text    "Decisions were made."})
  (fact "Pate verdict, giver as code"
    (-> pate-test-verdict
        (dissoc :legacy?)
        (assoc-in [:data :verdict-code] "hyvaksytty")
        (assoc-in [:data :giver] "86")
        verdict-data-for-bulletin-snapshot )
    => {:contact "Maxwell Smart"
        :section "1"
        :code    "hyvaksytty"
        :text    "Decisions were made."})
  (fact "Pate verdict, giver as code, use code in bulletin"
    (-> pate-test-verdict
        (dissoc :legacy?)
        (assoc-in [:data :verdict-code] "hyvaksytty")
        (assoc-in [:data :giver] "86")
        (assoc-in [:template :bulletin-code-giver] true)
        verdict-data-for-bulletin-snapshot )
    => {:contact "86"
        :section "1"
        :code    "hyvaksytty"
        :text    "Decisions were made."}))

(defn- ignore-some-data [verdict]
  (-> verdict
      (util/dissoc-in [:paatokset 0 :poytakirjat 0 :paatospvm]) ; No place for this in Pate, replaced by :anto
      (util/dissoc-in [:paatokset 0 :poytakirjat 0 :urlHash])   ; Not available, assume not needed
      (util/dissoc-in [:paatokset 0 :id])                       ; Not available, assume not needed, use verdict id
      (dissoc :metadata)))                                      ; Not needed

(fact "->backing-system-verdict"
  ;; Legacy
  (let [backing-system-verdict (->backing-system-verdict pate-test-verdict)
        paatos                 (get-in backing-system-verdict [:paatokset 0])]
    (ignore-some-data backing-system-verdict)
    => (ignore-some-data test-verdict)
    (get-in paatos [:poytakirjat 0 :paatospvm])
    => (-> pate-test-verdict :data :anto :_value)
    (get-in paatos [:paivamaarat :anto])
    => (-> pate-test-verdict :data :anto :_value)
    (get-in paatos [:poytakirjat 0 :paatoksentekija])
    => "handler")

  (fact "kuulutus"
    (let [bsv (->backing-system-verdict pate-test/kuulutus-verdict)]
      (fact bsv => {:draft     true
                    :id        "1a156dd40e40adc8ee064463"
                    :paatokset [{:id             "1a156dd40e40adc8ee064463"
                                 :lupamaaraykset {:autopaikkojaEnintaan        2
                                                  :autopaikkojaKiinteistolla   3
                                                  :autopaikkojaRakennettu      5
                                                  :maaraykset                  [{:sisalto "muut lupaehdot - teksti"}
                                                                                {:sisalto "toinen teksti"}]
                                                  :vaaditutErityissuunnitelmat ["Suunnitelmat"
                                                                                "Suunnitelmat2"]
                                                  :vaaditutKatselmukset        [{:tarkastuksenTaiKatselmuksenNimi "Katselmus"}
                                                                                {:tarkastuksenTaiKatselmuksenNimi "Katselmus2"}]
                                                  :vaaditutTyonjohtajat        "Erityisalojen työnjohtaja, Ilmanvaihtotyönjohtaja, Vastaava työnjohtaja, Vesi- ja viemärityönjohtaja"}
                                 :paivamaarat    {:julkipano        1511474400000
                                                  :lainvoimainen    1511733600000
                                                  :viimeinenValitus 1514325600000
                                                  :voimassaHetki    1700690400000}
                                 :poytakirjat    [{:paatoksentekija "Pate Paattaja"
                                                   :paatos          "päätös - teksti"
                                                   :paatoskoodi     "Myönnetty"
                                                   :paatospvm       1511388000000
                                                   :pykala          "99"}]}]
                    :sopimus   false
                    :timestamp 123456})))

  ;; Plans
  (let [plan-verdict          (-> pate-test-verdict
                                  (assoc :legacy? false)
                                  (assoc-in [:data :verdict-code] "myonnetty")
                                  (util/dissoc-in [:data :foremen]))
        verdict-with-plans    (->backing-system-verdict plan-verdict)
        verdict-without-plans (->backing-system-verdict (util/dissoc-in plan-verdict [:data :plans-included]))]

    (get-in verdict-without-plans [:paatokset 0 :lupamaaraykset :vaaditutErityissuunnitelmat])
    => []
    (get-in verdict-with-plans [:paatokset 0 :lupamaaraykset :vaaditutErityissuunnitelmat])
    => ["Suunnitelma 1" "Suunnitelma 3"])

  (fact "Verdict giver"
    (let [v0       (dissoc pate-test-verdict :legacy?)
          v1       (assoc-in v0 [:data :giver] (wrap "decider"))
          v2       (assoc-in v1 [:references :verdict-givers] [{:code "foo" :fi "bar"}
                                                               {:code "decider"
                                                                :fi   "Petsamon Pormestari"}])
          v3       (assoc-in v2 [:template :bulletin-code-giver] true)
          giver-fn #(-> (->backing-system-verdict %)
                        :paatokset first :poytakirjat first
                        :paatoksentekija)]
      (giver-fn v1)  => "decider"
      (giver-fn v2)  => "Petsamon Pormestari"
      (giver-fn v3)  => "decider"))

  ;; YMP
  (let [backing-system-verdict (->backing-system-verdict (assoc pate-test-verdict :category "ymp"))]
    (get-in backing-system-verdict [:paatokset 0 :poytakirjat 0 :status])
    => nil   ;; Since for Pate legacy YMP verdicts the verdict code is interpreted as free text
    (get-in backing-system-verdict [:paatokset 0 :poytakirjat 0 :paatoskoodi])
    => "2")) ;; Since for Pate legacy YMP verdicts the verdict code is interpreted as free text


(def example-app-with-verdict (assoc example-app :verdicts [test-verdict]))
(def example-app-with-pate-verdict (assoc example-app :pate-verdicts [pate-test-verdict]))
(def example-app-with-both-verdicts (assoc example-app
                                           :verdicts [test-verdict]
                                           :pate-verdicts [pate-test-verdict]))

(facts "further create-bulletin-snapshot tests"
  (let [verdict-snapshot (create-bulletin-snapshot example-app-with-verdict)
        pate-verdict-snapshot (create-bulletin-snapshot example-app-with-pate-verdict)
        both-snapshot (create-bulletin-snapshot example-app-with-both-verdicts)]
    (fact "apart from :verdicts and :id, the two snapshots are the same"
      (dissoc verdict-snapshot :verdicts :id) => (dissoc pate-verdict-snapshot :verdicts :id))
    (fact "the verdicts differ only within the constraints of ->backing-system-verdict"
      (mapv ignore-some-data (:verdicts verdict-snapshot))
      => (mapv ignore-some-data (:verdicts pate-verdict-snapshot)))
    (fact "if both backing system and Pate verdicts are present, Pate takes precedence"
      ;; Feel free to disagree, just spelling out the existing implementation
      (:verdicts both-snapshot) => [(->backing-system-verdict pate-test-verdict) test-verdict]
      (:verdictData both-snapshot) => (verdict-data-for-bulletin-snapshot pate-test-verdict))))

(facts "Verdict accessors"
  (fact "for Pate verdict"
    (let [pate-verdict {:data       {:foremen    ["vastaava-tj" "iv-tj"]
                                     :conditions {:5beec543e9a0396ee0e14f1a {:condition "Ehto"}}
                                     :reviews    ["5beec544e9a0396ee0e14f1c"]}
                        :references {:reviews [{:fi   "Katselmus"
                                                :sv   "Katslemus"
                                                :en   "Katselmus"
                                                :type "muu-katselmus"
                                                :id   "5beec544e9a0396ee0e14f1c"}]}}]


      (foremen pate-verdict)    => "Vastaava työnjohtaja, Ilmanvaihtotyönjohtaja"
      (conditions pate-verdict) => [{:sisalto "Ehto"}]
      (reviews pate-verdict)    => [{:tarkastuksenTaiKatselmuksenNimi "Katselmus"}]

      (let [pate-verdict-no-includes (-> pate-verdict
                                         (assoc-in [:data :reviews-included] false)
                                         (assoc-in [:data :foremen-included] false))]
        (fact "reviews not included"
          (reviews pate-verdict-no-includes) => [])
        (fact "forement not included"
          (foremen pate-verdict-no-includes) => ""))))
  (fact "for legacy verdict"
    (let [pate-legacy-verdict {:category "r"
                               :legacy?  true
                               :data     {:foremen    {:5beeca9fe9a0396ee0e14f36 {:role "Työnjohtaja"}
                                                       :5beecaa5e9a0396ee0e14f37 {:role "Vastaava työnjohtaja"}}
                                          :conditions {:5beecaafe9a0396ee0e14f39 {:name "condition 1"}
                                                       :5beecab4e9a0396ee0e14f3a {:name "condition 2"}
                                                       :5beecab9e9a0396ee0e14f3b {:name "condition 3"}}
                                          :reviews    {:5beeca83e9a0396ee0e14f33 {:name "Katselmus 1", :type "loppukatselmus"}
                                                       :5beeca8be9a0396ee0e14f34 {:name "Katselmus 2", :type "osittainen-loppukatselmus"}
                                                       :5beeca91e9a0396ee0e14f35 {:name "Katselmus 3", :type "paikan-tarkastaminen"}}}}]

      (foremen pate-legacy-verdict)    => "Työnjohtaja, Vastaava työnjohtaja"
      (conditions pate-legacy-verdict) => [{:sisalto "condition 1"} {:sisalto "condition 2"} {:sisalto "condition 3"}]
      (reviews pate-legacy-verdict)    => [{:tarkastuksenTaiKatselmuksenNimi "Katselmus 1"}
                                           {:tarkastuksenTaiKatselmuksenNimi "Katselmus 2"}
                                           {:tarkastuksenTaiKatselmuksenNimi "Katselmus 3"}])))


(defn dates [start end]
  {:start (date/timestamp start)
   :end   (date/timestamp end)})

(let [yesterday (-> (date/today) (date/minus :day) date/timestamp)
      tomorrow  (-> (date/today) (date/plus :day) date/timestamp)
      now       (date/timestamp (date/now))]
  (facts "Date checkers"
    (facts "bulletin-date-in-period?"
      (bulletin-date-in-period? :start :end nil) => false
      (bulletin-date-in-period? :start :end {}) => false
      (bulletin-date-in-period? :start :end {:start 123}) => false
      (bulletin-date-in-period? :start :end {:end 123}) => false
      (bulletin-date-in-period? :start :end {:start "bad " :end 123}) => (throws)
      (bulletin-date-in-period? :start :end (dates yesterday tomorrow))
      => true
      (bulletin-date-in-period? :start :end (dates now now)) => true
      (bulletin-date-in-period? :start :end (dates (date/with-time now 20)
                                                   (date/with-time now 10)))
      => true
      (bulletin-date-in-period? :start :end (dates tomorrow tomorrow))
      => false
      (bulletin-date-in-period? :start :end (dates yesterday yesterday))
      => false
      (bulletin-date-in-period? :start :end
                                (dates tomorrow
                                       (date/with-time tomorrow 12)))
      => false
      (bulletin-date-in-period? :start :end
                                (dates yesterday
                                       (date/with-time yesterday 12)))
      => false
      (bulletin-date-in-period? :start :end (dates tomorrow yesterday))
      => false
      (bulletin-date-in-period? :start :end (dates now yesterday)) => false)

    (facts "validate-input-dates"
      (let [fail? {:ok false :text "error.startdate-before-enddate"}]
        (validate-input-dates :start :end {}) => (throws)
        (validate-input-dates :start :end nil) => (throws)
        (validate-input-dates :start :end {:data {:start 123}}) => (throws)
        (validate-input-dates :start :end {:data {:end 123}}) => (throws)
        (validate-input-dates :start :end {:data (dates yesterday tomorrow)})
        => nil
        (validate-input-dates :start :end {:data (dates yesterday yesterday)})
        => nil
        (validate-input-dates :start :end {:data (dates (date/with-time now 20)
                                                        (date/with-time now 10))})
        => nil
        (validate-input-dates :start :end {:data (dates tomorrow yesterday)})
        => fail?
        (validate-input-dates :start :end {:data (dates (date/end-of-day now)
                                                        (date/end-of-day now))})
        => nil)))

  (against-background
    [(lupapalvelu.organization/get-organization "753-R" [:scope])
     => {:scope [{:municipality "753"
                  :permitType   "R"
                  :bulletins    {:appeal false}}
                 {:municipality "753"
                  :permitType   "P"
                  :bulletins    {:appeal true}}
                 {:municipality "123"
                  :permitType   "R"
                  :bulletins    {}}
                 {:municipality "123"
                  :permitType   "YA"
                  :bulletins    {}}]}]
    (facts "bulletin-appeal-enabled?"
     (bulletin-appeal-enabled? nil) => false
     (bulletin-appeal-enabled? {}) => false
     (bulletin-appeal-enabled? {:municipality         "753"
                                :organization         "753-R"
                                :permitType           "YA"
                                :appealPeriodStartsAt yesterday
                                :appealPeriodEndsAt   now})
     => false
     (bulletin-appeal-enabled? {:municipality         "753"
                                :organization         "753-R"
                                :permitType           "A"
                                :appealPeriodStartsAt yesterday
                                :appealPeriodEndsAt   now})
     => false
     (bulletin-appeal-enabled? {:municipality         "753"
                                :organization         "753-R"
                                :permitType           "R"
                                :appealPeriodStartsAt tomorrow
                                :appealPeriodEndsAt   yesterday})
     => false
     (bulletin-appeal-enabled? {:municipality         "753"
                                :organization         "753-R"
                                :permitType           "R"
                                :appealPeriodStartsAt yesterday
                                :appealPeriodEndsAt   tomorrow})
     => false
     (bulletin-appeal-enabled? {:municipality         "753"
                                :organization         "753-R"
                                :permitType           "P"
                                :appealPeriodStartsAt yesterday
                                :appealPeriodEndsAt   tomorrow})
     => true
     (bulletin-appeal-enabled? {:municipality         "123"
                                :organization         "753-R"
                                :permitType           "R"
                                :appealPeriodStartsAt yesterday
                                :appealPeriodEndsAt   tomorrow})
     => true
     (bulletin-appeal-enabled? {:municipality         "404"
                                :organization         "753-R"
                                :permitType           "P"
                                :appealPeriodStartsAt yesterday
                                :appealPeriodEndsAt   tomorrow})
     => false)

    (let [tomorrow-deadline (-> tomorrow date/end-of-day date/timestamp)]
      (env/with-feature-value :bulletin-comment-verdict-given true
        (facts "bulletin-comment-deadline (verdictGiven supported)"
          (doseq [bulletin-version [nil {} {:bulletinState "proclaimed"}
                                    {:bulletinState "verdictGiven"}
                                    {:bulletinState        "proclaimed"
                                     :proclamationStartsAt tomorrow
                                    :proclamationEndsAt   yesterday}
                                    {:bulletinState        "verdictGiven"
                                     :proclamationStartsAt yesterday
                                     :proclamationEndsAt   tomorrow}]]
            (fact {:midje/description bulletin-version}
              (bulletin-comment-deadline bulletin-version) => nil))

         (bulletin-comment-deadline {:bulletinState        "proclaimed"
                                     :proclamationStartsAt yesterday
                                     :proclamationEndsAt   tomorrow})
         => tomorrow-deadline
         (bulletin-comment-deadline {:bulletinState        "verdictGiven"
                                     :appealPeriodStartsAt yesterday
                                     :appealPeriodEndsAt   tomorrow})
         => tomorrow-deadline))
      (env/with-feature-value :bulletin-comment-verdict-given nil
        (facts "bulletin-comment-deadline (verdictGiven NOT supported)"
          (doseq [bulletin-version [nil {} {:bulletinState "proclaimed"}
                                    {:bulletinState "verdictGiven"}
                                    {:bulletinState        "proclaimed"
                                     :proclamationStartsAt tomorrow
                                    :proclamationEndsAt   yesterday}
                                    {:bulletinState        "verdictGiven"
                                     :proclamationStartsAt yesterday
                                     :proclamationEndsAt   tomorrow}]]
            (fact {:midje/description bulletin-version}
              (bulletin-comment-deadline bulletin-version) => nil))

         (bulletin-comment-deadline {:bulletinState        "proclaimed"
                                     :proclamationStartsAt yesterday
                                     :proclamationEndsAt   tomorrow})
         => tomorrow-deadline
         (bulletin-comment-deadline {:bulletinState        "verdictGiven"
                                     :appealPeriodStartsAt yesterday
                                     :appealPeriodEndsAt   tomorrow})
         => nil)))))
