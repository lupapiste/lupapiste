(ns lupapalvelu.merge-review-test
  (:require [clojure.data :refer [diff]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.organization :as org]
            [lupapalvelu.review :refer :all]
            [lupapalvelu.tasks :as tasks]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.common-reader :as cr]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]))

(testable-privates lupapalvelu.review
                   compare-pitaja
                   merge-review-tasks matching-task
                   remove-repeating-background-ids
                   lupapiste-review?
                   validate-new-task
                   process-reviews
                   review->task
                   preprocess-tasks
                   attachments-by-id-from-task-candidates)

(def mock-org {:id "org-id"
               :review-officers-list-enabled true
               :reviewOfficers [{:code "Person Five" :name "Person Five" :id "12345" :_atomic-map? true}]})

(def rakennustieto-fixture [{:KatselmuksenRakennus {:kiinttun "54300601900001",
                                                    :rakennusnro "001",
                                                    :jarjestysnumero "1",
                                                    :aanestysalue "010",
                                                    :muuTunnustieto {:MuuTunnus {:sovellus "LP-543-2016-94999",
                                                                                 :tunnus "LP-543-2016-94999"}},
                                                    :valtakunnallinenNumero "103571943D",
                                                    :rakennuksenSelite "Omakotitalo"}}] )

(def reviews-fixture
  (mapv #(tasks/katselmus->task {:state :sent :created (+ (sade.core/now) (rand-int 100))}
                                {:type "background"}
                                {:buildings nil}
                                (assoc % :katselmuksenRakennustieto rakennustieto-fixture))
       [{:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "rakennuksen paikan merkitseminen",
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Paikan merkitseminen",
         :pitoPvm 1462838400000}
        {:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "rakennuksen paikan tarkastaminen",
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Sijaintikatselmus",
         :pitoPvm 1464739200000}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "aloituskokous",
         :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "pohjakatselmus",
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Pohjakatselmus",
         :pitoPvm 1463961600000}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "rakennekatselmus",
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Rakennekatselmus",
         :pitoPvm 1466467200000}
        {:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "Oli kesken."}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Vesi- ja viem\u00e4rilaitteiden katselmus",
         :pitoPvm 1476748800000}
        {:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "Ulkoviem\u00e4rit ok."}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Vesi- ja viem\u00e4rilaitteiden katselmus",
         :pitoPvm 1463011200000}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "osittainen loppukatselmus",
         :huomautukset [{:huomautus {:kuvaus "kiukaan kaide"}}],
         :osittainen "osittainen",
         :tarkastuksenTaiKatselmuksenNimi "K\u00e4ytt\u00f6\u00f6nottokatselmus",
         :pitoPvm 1477526400000}
        {:vaadittuLupaehtonaKytkin true,
         :katselmuksenLaji "loppukatselmus",
         :osittainen "lopullinen",
         :tarkastuksenTaiKatselmuksenNimi "Loppukatselmus"}
        {:vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "Rakennusty\u00f6n aikana on pidett\u00e4v\u00e4 rakennusty\u00f6n tarkastusasiakirjaa sek\u00e4 laadittava rakennuksen k\u00e4ytt\u00f6- ja huolto-ohje"}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta"}
        {:pitaja "E",
         :vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "S\u00e4hk\u00f6tarkastusp\u00f6yt\u00e4kirja esitett\u00e4v\u00e4 rakennusvalvontaviranomaiselle ennen k\u00e4ytt\u00f6\u00f6nottoa"}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta"}
        {:pitaja "T",
         :vaadittuLupaehtonaKytkin false,
         :katselmuksenLaji "muu katselmus",
         :huomautukset [{:huomautus {:kuvaus "p\u00e4ivitetty energiaselvitys"}}],
         :osittainen "pidetty",
         :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta",
         :pitoPvm 1463097600000}]))

(fact "vaadittuLupaehtonaKytkin is respected (previously was always set to true)"
  (get-in reviews-fixture [0 :data :vaadittuLupaehtona :value]) => false
  (get-in reviews-fixture [2 :data :vaadittuLupaehtona :value]) => true)

(fact "merging the review vector with itself leaves everything unchanged"
  (let [[unchanged added-or-updated _] (merge-review-tasks nil reviews-fixture reviews-fixture)]
    (count unchanged) => (count reviews-fixture)
    (diff unchanged reviews-fixture) => [nil nil reviews-fixture]
    added-or-updated => empty?))

(fact "review is updated on top of empty review"
  (let [original-vec (into [] reviews-fixture)
        mutated (-> original-vec
                    (assoc-in [2 :data :katselmus :pitoPvm :value] "13.05.2016")
                    (assoc-in [2 :created] (sade.core/now)))
        original-ts (get-in original-vec [2 :created])
        [unchanged added-or-updated _] (merge-review-tasks nil mutated reviews-fixture)]
    (fact "created timestamp is not changed, if update occured"
      (-> added-or-updated first :created) => original-ts)
    unchanged => (just (util/drop-nth 2 mutated) :in-any-order)
    added-or-updated => (just (assoc (nth mutated 2) :created original-ts))
    (fact "only change is pitoPvm"
      (butlast (diff (nth original-vec 2) (first added-or-updated))) => [{:data {:katselmus {:pitoPvm {:value nil}}}}
                                                                         {:data {:katselmus {:pitoPvm {:value "13.05.2016"}}}}])))

(fact "illegal review type causes failure"
  (let [mutated (-> (into [] reviews-fixture)
                        (assoc-in [2 :data :katselmus :pitoPvm :value] "2")
                        (assoc-in [2 :data :katselmuksenLaji :value] "13.05.2016"))
            [_ added-or-updated _] (merge-review-tasks nil mutated reviews-fixture)
            errors (doall (map #(tasks/task-doc-validation (-> % :schema-info :name) %) added-or-updated))]
        added-or-updated => (just (nth mutated 2))
        (count errors) => 1
        (-> errors flatten first) => (contains {:result [:warn "illegal-value:select"]})))

(fact "more recent review from XML does not overwrite the review in mongo"
  (let [mutated (-> (into [] reviews-fixture)
                    (assoc-in [1 :data :katselmus :lasnaolijat :value] "Lasse Lasna"))
        [unchanged added-or-updated _] (merge-review-tasks nil mutated reviews-fixture)]
    (count unchanged) => (count reviews-fixture)
    (diff unchanged reviews-fixture) => [nil nil reviews-fixture]
    added-or-updated => empty?))

(fact "more recent review from XML can overwrite if flag is passed to merge-review-tasks"
      (let [mutated (-> (into [] reviews-fixture)
                        (assoc-in [1 :data :katselmus :lasnaolijat :value] "Lasse Lasna"))
            [unchanged added-or-updated new-faulty] (merge-review-tasks nil mutated reviews-fixture true)]
        (count unchanged)        => (dec (count reviews-fixture))
        (count added-or-updated) => 1
        (count new-faulty)       => 1
        (first new-faulty)       => (second reviews-fixture)
        (first added-or-updated) => (second mutated)))

(facts "matching-task"
  (let [mongo-task {:data {:muuTunnus {:value "ID"}
                           :katselmus {}
                           :katselmuksenLaji {:value "muu katselmus"}}
                    :taskname "Paikan merkitseminen"}]

    (fact "matches tasks with the same background id"
      (matching-task mongo-task
                     [{:data {:muuTunnus {:value "DI"}}}
                      {:data {:muuTunnus {:value "ID"}}}
                      {:data {}}])
      => {:data {:muuTunnus {:value "ID"}}}

      (matching-task mongo-task
                     [{:data {:muuTunnus {:value "DI"}}}
                      {:data {}}])
      => nil?)


  (fact "matches tasks with same name and type given that the task given as first argument contains no review data"
    (let [update-task (update mongo-task :data dissoc :muuTunnus)]
      (matching-task mongo-task [update-task])
      => update-task

      ;; :muuTunnus prevails over same name and type
      (matching-task mongo-task [update-task {:data {:muuTunnus {:value "ID"}}}])
      => {:data {:muuTunnus {:value "ID"}}}

      ;; No match, since mongo task contains review data
      (matching-task (assoc-in mongo-task [:data :katselmus :pitaja :value]
                               "Pekka Pitaja")
                     [update-task])
      => nil?))

  (facts "matches task with same name, type and proper related data"
    (let [held-review-task (-> mongo-task
                               (assoc-in [:data :katselmus]
                                         {:tila    {:value "lopullinen"}
                                          :pitoPvm {:value "8.9.2017"}
                                          :pitaja  {:value "Pekka Pitaja"}})
                               (update :data dissoc :muuTunnus))
          held-review-task-modified (-> held-review-task
                                        (assoc-in [:data :katselmus :lasnaolijat]
                                                  {:value "Lasse Lasnaolija"})
                                        (update :data assoc :muuTunnus {:value "ID"}))
          held-review-task-edit-distance (-> held-review-task-modified
                                             (assoc-in [:data :katselmus :pitaja]
                                                       {:value ", Pekka Pitaja"}))]
      (fact "other data modified"
        (matching-task held-review-task
                       [{:data {:muuTunnus {:value "DI"}}}
                        held-review-task-modified
                        {:data {:muuTunnus {:value "DD"}}}])
        => held-review-task-modified)

      (fact ":pitaja has small edit distance"
        (matching-task held-review-task
                       [held-review-task-edit-distance])
        => held-review-task-edit-distance)

      (fact ":pitaja has large edit distance"
            (matching-task held-review-task
                           [(assoc-in held-review-task-modified
                                      [:data :katselmus :pitaja :value]
                                      ", Pekka Piirakka")])
            => nil?)

      (facts "pitoPvm check is format agnostic"
        (doseq [pvm ["08.09.2017" "2017-09-08" "2017-09-08Z" "2017-09-08+12:00"]
                :let [task (assoc-in held-review-task
                                     [:data :katselmus :pitoPvm :value]
                                     pvm)]]
          (fact {:midje/description pvm}
            (matching-task held-review-task [task])
            => task))
        (fact "Different pitoPvm"
          (doseq [pvm ["8.10.2017" "" nil]
                :let [task (assoc-in held-review-task
                                     [:data :katselmus :pitoPvm :value]
                                     pvm)]]
          (fact {:midje/description pvm}
            (matching-task held-review-task [task])
            => nil)))
        (fact "Invalid date"
          (matching-task held-review-task [(assoc-in held-review-task
                                                     [:data :katselmus :pitoPvm :value]
                                                     "bad date")])
          => (throws)))

      (fact ":tila changed"
        (matching-task held-review-task
                       [(assoc-in held-review-task-modified
                                  [:data :katselmus :tila]
                                  {:value "osittainen"})])
        => #_nil?
        ;; NOTE :tila is ignored temporarily
        (assoc-in held-review-task-modified
                  [:data :katselmus :tila]
                  {:value "osittainen"}))

      (fact ":muuTunnus prevails"
        (matching-task mongo-task [held-review-task
                                   {:data {:muuTunnus {:value "ID"}}}])
        => {:data {:muuTunnus {:value "ID"}}})
      (fact "Id mismatch does not prevent match"
        (let [task (assoc-in mongo-task [:data :muuTunnus :value] "new-id")]
          (matching-task mongo-task [task]) => task))))))

(facts "merge-review-tasks vs. background ids"
  (let [m1     {:taskname    "Review"
                :schema-info {:name    "task-katselmus"
                              :subtype "review"}
                :data        {:muuTunnus        {:value "m1"}
                              :katselmuksenLaji {:value "onpahan vaan katselmus"}
                              :katselmus        {:pitoPvm {:value "24.4.2019"}
                                                 :pitaja  {:value "Humppila"}}}}
        m2     {:taskname    "Review"
                :schema-info {:name    "task-katselmus"
                              :subtype "review"}
                :data        {:muuTunnus        {:value "m2"}
                              :katselmuksenLaji {:value "onpahan vaan katselmus"}
                              :katselmus        {:pitoPvm {:value "24.4.2019"}
                                                 :pitaja  {:value "Humppila"}}}}
        mempty {:taskname    "Review"
                :schema-info {:name    "task-katselmus"
                              :subtype "review"}
                :data        {:katselmuksenLaji {:value "onpahan vaan katselmus"}
                              :katselmus        {:pitoPvm {}
                                                 :pitaja  {}}}}
        u1     {:taskname    "Different"
                :schema-info {:name    "task-katselmus"
                              :subtype "review"}
                :data        {:muuTunnus        {:value "m1"}
                              :katselmuksenLaji {:value "Joku muu katselmus"}
                              :katselmus        {:pitoPvm {:value "22.5.2018"}
                                                 :pitaja  {:value "Jokioinen"}}}}
        u2     {:taskname    "Review"
                :schema-info {:name    "task-katselmus"
                              :subtype "review"}
                :data        {:muuTunnus        {:value "u2"}
                              :katselmuksenLaji {:value "onpahan vaan katselmus"}
                              :katselmus        {:pitoPvm {:value "24.4.2019"}
                                                 :pitaja  {:value "Humppila"}}}}]
    (fact "m1 = u1, mempty = u2, m2 unchanged."
      (merge-review-tasks nil [u1 u2] [mempty m2 m1])
      => [[m1 m2]
          [u2]
          []])
    (fact "m1 = u1, m2 = u2"
      (merge-review-tasks nil [u1 u2] [m2 m1])
      => [[m1 m2]
          []
          []])
    (fact "m1 = u1, mempty = u2"
      (merge-review-tasks nil [u1 u2] [m1 mempty])
      => [[m1]
          [u2]
          []])
    (fact "m2 = u2, mempty unchanged, u1 new"
      (merge-review-tasks nil [u1 u2] [m2 mempty])
      => [[m2 mempty]
          [u1]
          []])
    (fact "mempty = u2, m2 unchanged, u1 new"
      (merge-review-tasks nil [u1 u2] [mempty m2])
      => [[m2]
          [u2 u1]
          []])))

(fact "Remove repeating and application id background ids"
  (let [reviews [{:pitaja "One"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "foo"}}]}
                 {:pitaja "Two"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "bar"
                                                :sovellus "Bar"}}]}
                 {:pitaja "Three"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "hello"}}]}
                 {:pitaja "Four"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "foo"
                                                :sovellus "Foobar"}}]}
                 {:pitaja "Five"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "foo"}}]}
                 {:pitaja "Six"
                  :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
                 {:pitaja "Seven"}
                 {:pitaja "Eight"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "bar"}}]}
                 {:pitaja "Nine"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "world"}}]}]]
    (remove-repeating-background-ids  "app-id" reviews)
    => [{:pitaja "One"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Two"
         :muuTunnustieto [{:MuuTunnus {:sovellus "Bar" :tunnus ""}}]}
        {:pitaja "Three"
         :muuTunnustieto [{:MuuTunnus {:tunnus "hello"}}]}
        {:pitaja "Four"
         :muuTunnustieto [{:MuuTunnus {:sovellus "Foobar" :tunnus ""}}]}
        {:pitaja "Five"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Six"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Seven"}
        {:pitaja "Eight"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Nine"
         :muuTunnustieto [{:MuuTunnus {:tunnus "world"}}]}]
    (remove-repeating-background-ids  "hello" reviews)
    => [{:pitaja "One"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Two"
         :muuTunnustieto [{:MuuTunnus {:sovellus "Bar" :tunnus ""}}]}
        {:pitaja "Three"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Four"
         :muuTunnustieto [{:MuuTunnus {:sovellus "Foobar" :tunnus ""}}]}
        {:pitaja "Five"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Six"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Seven"}
        {:pitaja "Eight"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Nine"
         :muuTunnustieto [{:MuuTunnus {:tunnus "world"}}]}]))

(fact "preprocess tasks"
  (let [tasks [{:data     {:muuTunnus        {:value "foo"}
                           :katselmuksenLaji {:value "one"}}
                :taskname "First"}
               {:data     {:muuTunnus        {:value "bar"}
                           :katselmuksenLaji {:value "two"}}
                :taskname "Second"}
               {:data     {:muuTunnus        {:value "foo"}
                           :katselmuksenLaji {:value "three"}}
                :taskname "Third"}]]
    (preprocess-tasks {:id "app-id" :tasks tasks})
    => tasks
    (preprocess-tasks {:id "foo" :tasks tasks})
    => [{:data     {:muuTunnus        {:value ""}
                    :katselmuksenLaji {:value "one"}}
         :taskname "First"}
        {:data     {:muuTunnus        {:value "bar"}
                    :katselmuksenLaji {:value "two"}}
         :taskname "Second"}
        {:data     {:muuTunnus        {:value ""}
                    :katselmuksenLaji {:value "three"}}
         :taskname "Third"}]
    (preprocess-tasks {:id "bar" :tasks tasks})
    => [{:data     {:muuTunnus        {:value "foo"}
                    :katselmuksenLaji {:value "one"}}
         :taskname "First"}
        {:data     {:muuTunnus        {:value ""}
                    :katselmuksenLaji {:value "two"}}
         :taskname "Second"}
        {:data     {:muuTunnus        {:value "foo"}
                    :katselmuksenLaji {:value "three"}}
         :taskname "Third"}]))


(facts "validate-changed-tasks"
  (letfn [(validate-dates [created-date pitoPvm]
            (validate-new-task
              {:created (date/timestamp created-date)}
              (tools/unwrapped
                (tasks/katselmus->task
                  {:state :sent :created 123}
                  {:type "background"}
                  nil
                  {:pitoPvm (date/timestamp pitoPvm)}))))]
    (fact "pitoPvm before application created"
      (validate-dates "2.1.2001" "1.1.2001")
      => (contains {:error #"pitoPvm .+ is before application was created"}))
    (fact "created before pitoPvm"
      (validate-dates "1.1.2001" "2.1.2001")
      => nil)))

(defn generate-kuntagml
  [reviews]
  (->> reviews
       (map (fn [{:keys [date other-id person note type attachment]}]
              (format "<rakval:katselmustieto>
            <rakval:Katselmus>
              <rakval:muuTunnustieto>
                <rakval:MuuTunnus>
                  <yht:tunnus>HienoTunnus_%s</yht:tunnus>
                  <yht:sovellus>%s</yht:sovellus>
                </rakval:MuuTunnus>
              </rakval:muuTunnustieto>
              <rakval:pitoPvm>%s</rakval:pitoPvm>
              <rakval:osittainen>osittainen</rakval:osittainen>
              <rakval:pitaja>%s</rakval:pitaja>
              <rakval:katselmuksenLaji>%s</rakval:katselmuksenLaji>
              <rakval:vaadittuLupaehtonaKytkin>true</rakval:vaadittuLupaehtonaKytkin>
              <rakval:huomautukset>
                <rakval:huomautus>
                  <rakval:kuvaus>%s</rakval:kuvaus>
                </rakval:huomautus>
              </rakval:huomautukset>
              %s
              <rakval:tarkastuksenTaiKatselmuksenNimi>rakennekatselmus</rakval:tarkastuksenTaiKatselmuksenNimi>
            </rakval:Katselmus>
          </rakval:katselmustieto>"
                      date other-id date person
                      (or type "rakennekatselmus")
                      note
                      (if attachment
                        (format "<rakval:liitetieto>
            <rakval:Liite>
              <yht:kuvaus>Katselmuksen p\u00f6yt\u00e4kirja: Rakennekatselmus</yht:kuvaus>
              <yht:linkkiliitteeseen>%s</yht:linkkiliitteeseen>
              <yht:muokkausHetki>%s</yht:muokkausHetki>
              <yht:tyyppi>katselmuksen_tai_tarkastuksen_poytakirja</yht:tyyppi>
            </rakval:Liite>
          </rakval:liitetieto>"
                                attachment date)
                        ""))))
       (ss/join "\n")
       (format "<rakval:RakennusvalvontaAsia>%s</rakval:RakennusvalvontaAsia>")))

(defn str->xml-map [xml-str]
  (-> xml-str
      (xml/parse-string "UTF-8")
      (cr/strip-xml-namespaces)))

(defn task-id-for-person [tasks person]
  (:id (util/find-first #(= person (get-in % [:data :katselmus :pitaja :value])) tasks)))

(def app-xml (-> [{:date "2017-10-01Z" :other-id "Hello" :person "Person One" :note "Note One"}
                  {:date       "2017-10-02Z" :other-id "Lupapiste" :person "Person Two" :note "Note Two"
                   :attachment "two.pdf"}
                  {:date       "2017-10-03Z" :other-id "Hello" :person "Person Three" :note "Note Three"
                   :attachment "three.pdf"}
                  {:date       "2017-10-04Z" :other-id "Lupapiste" :person "Person Four" :note "Note Four"
                   :attachment "four.pdf"}
                  {:date       "2017-10-05Z" :other-id "World" :person "Person Five" :note "Note Five"
                   :attachment "five.pdf" :type "bad type"}]
                 generate-kuntagml
                 str->xml-map))

(def bond {:code         "007"
           :name         "James Bond"
           :id           "007"
           :_atomic-map? true})

(defn katselmus [pitaja pvm]
  {:data        {:katselmus        {:pitaja  {:value pitaja}
                                    :pitoPvm {:value pvm}}
                 :katselmuksenLaji {:modified 12345
                                    :value    "rakennekatselmus"}
                 :muuTunnus        {:value ""}
                 :rakennus         {}}
   :schema-info {:name    "task-katselmus"
                 :subtype "review"}
   :taskname    "rakennekatselmus"})

(fact "Merge review officers when some tasks created before mapping"
  (with-redefs [org/get-org-from-org-or-id
                (fn [_] {:id                           "org-id"
                         :review-officers-list-enabled true
                         :reviewOfficers               [bond]})]
    (let [app-xml    (-> [{:date "2020-08-09Z" :other-id "ugh" :person "Felix Leiter" :note "jep1"}
                          {:date "2020-08-10Z" :other-id "ugh" :person "007" :note "jep2"}
                          {:date "2020-08-08Z" :other-id "ugh" :person "007" :note "jep3"}
                          {:date "2020-08-11Z" :other-id "ugh" :person "007" :note "jep4"}]
                         generate-kuntagml
                         str->xml-map)
          orig-tasks [(katselmus "007" "08.08.2020")
                      (katselmus bond "10.08.2020")
                      (katselmus "Felix Leiter" "09.08.2020")
                      ;; name-as-value should also match
                      (katselmus "James Bond" "11.08.2020")]
          reviews    (read-reviews-from-xml
                       {:username "M"}
                       12345
                       {:created (date/timestamp "7.8.2020")
                        :tasks   orig-tasks}
                       app-xml)]
      (fact "mapping works for both code and full identity-map, identity is updated"
        reviews
        =>
        {:added-tasks-with-updated-buildings '()
         :all-tasks                          '(nil nil nil nil)
         :attachments-by-ids                 {}
         :new-faulty-tasks                   '()
         :ok                                 true
         :review-count                       4
         :updates                            {"$set" {:tasks orig-tasks}}}))))

(fact "compare-pitaja can operate with names, codes and combinations"
  (compare-pitaja "aa" "bbb") => false
  (compare-pitaja nil nil) => true
  (compare-pitaja nil "aa") => false
  (compare-pitaja "aa" nil) => false
  (compare-pitaja "aa" "aa") => true
  (compare-pitaja "aa" {:name "aa" :code "b"}) => true
  (compare-pitaja "aa" {:name "Alfonso Adler" :code "aa"}) => true
  (compare-pitaja {:name "Mr B." :code "aa"} {:name "aa" :code "b"}) => false)

(def three-attachment [{:liite {:kuvaus            "Katselmuksen p\u00f6yt\u00e4kirja: Rakennekatselmus"
                                :linkkiliitteeseen "three.pdf"
                                :muokkausHetki     "2017-10-03Z"
                                :tyyppi            "katselmuksen_tai_tarkastuksen_poytakirja"}}])

(fact "Read reviews from xml"
  (with-redefs [org/get-org-from-org-or-id (fn [_] mock-org)]
    (let [created-ts (date/timestamp "30.9.2017")
          reviews    (reviews-preprocessed app-xml)]

      (fact "bad type is invalid against schema"
        (sc/with-fn-validation ()
          (reviews-preprocessed app-xml)) => (throws #":katselmuksenLaji \(not"))

      (fact "schema passes with OK data"
        (sc/with-fn-validation
          (-> [{:date "2017-10-01Z" :other-id "Hello" :person "Person One" :note "Note One"}
               {:date       "2017-10-02Z" :other-id "Lupapiste" :person "Person Two" :note "Note Two"
                :attachment "two.pdf"}
               {:date       "2017-10-03Z" :other-id "Hello" :person "Person Three" :note "Note Three"
                :attachment "three.pdf"}]
              generate-kuntagml
              str->xml-map
              (reviews-preprocessed)))
        => sequential?)

      (fact "Preprocessed reviews"
        (count reviews) => 5
        (map lupapiste-review? reviews) => [false true false true false])

      (fact "No attachment for invalid task"
        (:attachments (review->task 12345 {} (nth reviews 2))) => truthy
        (:attachments (review->task 12345 {} (last reviews))) => nil)

      (fact "Process reviews"
        (let [review-tasks (process-reviews {:id "app-id" :organization "org-id"}
                                            app-xml
                                            12345
                                            {})]
          (count review-tasks) => 3
          (fact "Only Three has attachment (one: no attachment, five: invalid data)"
            (keep :attachments review-tasks) => [three-attachment])))

      (fact "Tasks and attachments"
        (let [{tasks        :added-tasks-with-updated-buildings
               att-by-ids   :attachments-by-ids
               updated-ids  :all-tasks
               review-count :review-count} (read-reviews-from-xml {:username "Foo"} 12345 {:created created-ts} app-xml)

              one-id                       (task-id-for-person tasks "Person One")
              three-id                     (task-id-for-person tasks "Person Three")
              five-id                      (task-id-for-person tasks (get-in mock-org [:reviewOfficers 0]))]
          (fact "Review count"
            review-count => 3)
          (fact "Three new tasks"
            updated-ids => (just [one-id three-id five-id] :in-any-order))
          (fact "Three has the correct attachment, and Five does not (invalid)"
            att-by-ids => {three-id                  three-attachment
                           "HienoTunnus_2017-10-03Z" three-attachment})))

      (let [invalid-data-xml (-> [{:date "2017-10-02Z" :other-id "Hello" :person "Person One" :note "Note One"}
                                        ; this date is in the history, before the application was created
                                  {:date "2010-10-01Z" :other-id "Hello" :person "Person Two" :note "Note Two"}]
                                 generate-kuntagml
                                 str->xml-map)]
        (fact "Failing review read"
          (read-reviews-from-xml {:username "Failer"} 12345 {:created created-ts} invalid-data-xml)
          => (contains {:ok false :text "error.invalid-reviews"}))))))

(against-background [(org/get-org-from-org-or-id anything) => mock-org]
  (facts "attachments-by-id"
         (let [review-tasks (process-reviews {:id "app-id" :organization "org-id"} app-xml 12345 {})
          three-id     (task-id-for-person review-tasks "Person Three")]
      (fact "keyed by IDs"
        (attachments-by-id-from-task-candidates review-tasks) => {three-id                  three-attachment
                                                                  "HienoTunnus_2017-10-03Z" three-attachment})
      (fact "empty muuTunnus doesn't exist"
        (->> review-tasks
             (map #(if (= (:id %) three-id)
                     (assoc-in % [:data :muuTunnus :value] "")
                     %))
             (attachments-by-id-from-task-candidates))
        => {three-id three-attachment})

      (fact "nil muuTunnus doesn't exist"
        (->> review-tasks
             (map #(if (= (:id %) three-id)
                     (assoc-in % [:data :muuTunnus :value] nil)
                     %))
             (attachments-by-id-from-task-candidates))
        => {three-id three-attachment}))))
