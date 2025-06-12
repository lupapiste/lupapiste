(ns lupapalvelu.merge-review-test
  (:require [clojure.data :refer [diff]]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.review :refer :all]
            [lupapalvelu.tasks :as tasks]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.common-reader :as cr]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc]))

(testable-privates lupapalvelu.review
                   compare-pitaja
                   merge-review-tasks matching-task
                   remove-repeating-background-ids
                   skippable-lupapiste-review?
                   validate-new-task
                   process-reviews
                   review->task
                   preprocess-tasks
                   reviews-preprocessed
                   attachments-by-id-from-task-candidates)

(def mock-org {:id                           "org-id"
               :review-officers-list-enabled true
               :reviewOfficers               [{:code "Person Five" :name "Person Five" :id "12345" :_atomic-map? true}]})

(def rakennustieto-fixture [{:KatselmuksenRakennus {:kiinttun               "54300601900001",
                                                    :rakennusnro            "001",
                                                    :jarjestysnumero        "1",
                                                    :aanestysalue           "010",
                                                    :muuTunnustieto         {:MuuTunnus {:sovellus "LP-543-2016-94999",
                                                                                         :tunnus   "LP-543-2016-94999"}},
                                                    :valtakunnallinenNumero "103571943D",
                                                    :rakennuksenSelite      "Omakotitalo"}}] )

(def reviews-fixture
  (mapv #(tasks/katselmus->task {:state :sent :created (+ (now) (rand-int 100))}
                                {:type "background"}
                                {:buildings nil}
                                (assoc % :katselmuksenRakennustieto rakennustieto-fixture))
        [{:vaadittuLupaehtonaKytkin        false,
          :katselmuksenLaji                "rakennuksen paikan merkitseminen",
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Paikan merkitseminen",
          :pitoPvm                         1462838400000}
         {:vaadittuLupaehtonaKytkin        false,
          :katselmuksenLaji                "rakennuksen paikan tarkastaminen",
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Sijaintikatselmus",
          :pitoPvm                         1464739200000}
         {:vaadittuLupaehtonaKytkin        true,
          :katselmuksenLaji                "aloituskokous",
          :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}
         {:vaadittuLupaehtonaKytkin        true,
          :katselmuksenLaji                "pohjakatselmus",
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Pohjakatselmus",
          :pitoPvm                         1463961600000}
         {:vaadittuLupaehtonaKytkin        true,
          :katselmuksenLaji                "rakennekatselmus",
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Rakennekatselmus",
          :pitoPvm                         1466467200000}
         {:vaadittuLupaehtonaKytkin        false,
          :katselmuksenLaji                "muu katselmus",
          :huomautukset                    [{:huomautus {:kuvaus "Oli kesken."}}],
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Vesi- ja viemärilaitteiden katselmus",
          :pitoPvm                         1476748800000}
         {:vaadittuLupaehtonaKytkin        false,
          :katselmuksenLaji                "muu katselmus",
          :huomautukset                    [{:huomautus {:kuvaus "Ulkoviemärit ok."}}],
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Vesi- ja viemärilaitteiden katselmus",
          :pitoPvm                         1463011200000}
         {:vaadittuLupaehtonaKytkin        true,
          :katselmuksenLaji                "osittainen loppukatselmus",
          :huomautukset                    [{:huomautus {:kuvaus "kiukaan kaide"}}],
          :osittainen                      "osittainen",
          :tarkastuksenTaiKatselmuksenNimi "Käyttöönottokatselmus",
          :pitoPvm                         1477526400000}
         {:vaadittuLupaehtonaKytkin        true,
          :katselmuksenLaji                "loppukatselmus",
          :osittainen                      "lopullinen",
          :tarkastuksenTaiKatselmuksenNimi "Loppukatselmus"}
         {:vaadittuLupaehtonaKytkin        false,
          :katselmuksenLaji                "muu katselmus",
          :huomautukset                    [{:huomautus {:kuvaus "Rakennustyön aikana on pidettävä rakennustyön tarkastusasiakirjaa sekä laadittava rakennuksen käyttö- ja huolto-ohje"}}],
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta"}
         {:pitaja                          "E",
          :vaadittuLupaehtonaKytkin        false,
          :katselmuksenLaji                "muu katselmus",
          :huomautukset                    [{:huomautus {:kuvaus "Sähkötarkastuspöytäkirja esitettävä rakennusvalvontaviranomaiselle ennen käyttöönottoa"}}],
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta"}
         {:pitaja                          "T",
          :vaadittuLupaehtonaKytkin        false,
          :katselmuksenLaji                "muu katselmus",
          :huomautukset                    [{:huomautus {:kuvaus "päivitetty energiaselvitys"}}],
          :osittainen                      "pidetty",
          :tarkastuksenTaiKatselmuksenNimi "Lupaehdon valvonta",
          :pitoPvm                         1463097600000}]))

(fact "vaadittuLupaehtonaKytkin is respected (previously was always set to true)"
  (get-in reviews-fixture [0 :data :vaadittuLupaehtona :value]) => false
  (get-in reviews-fixture [2 :data :vaadittuLupaehtona :value]) => true)

(fact "merging the review vector with itself leaves everything unchanged"
  (let [[unchanged added-or-updated _] (merge-review-tasks nil reviews-fixture reviews-fixture)]
    (count unchanged) => (count reviews-fixture)
    (diff unchanged reviews-fixture) => [nil nil reviews-fixture]
    added-or-updated => empty?))

(fact "review is updated on top of empty review"
  (let [original-vec                   (into [] reviews-fixture)
        mutated                        (-> original-vec
                                           (assoc-in [2 :data :katselmus :pitoPvm :value] "13.05.2016")
                                           (assoc-in [2 :created] (now)))
        original-ts                    (get-in original-vec [2 :created])
        [unchanged added-or-updated _] (merge-review-tasks nil mutated reviews-fixture)]
    (fact "created timestamp is not changed, if update occured"
      (-> added-or-updated first :created) => original-ts)
    unchanged => (just (util/drop-nth 2 mutated) :in-any-order)
    added-or-updated => (just (assoc (nth mutated 2) :created original-ts))
    (fact "only change is pitoPvm"
      (butlast (diff (nth original-vec 2) (first added-or-updated))) => [{:data {:katselmus {:pitoPvm {:value nil}}}}
                                                                         {:data {:katselmus {:pitoPvm {:value "13.05.2016"}}}}])))

(fact "illegal review type causes failure"
  (let [mutated                (-> (into [] reviews-fixture)
                                   (assoc-in [2 :data :katselmus :pitoPvm :value] "2")
                                   (assoc-in [2 :data :katselmuksenLaji :value] "13.05.2016"))
        [_ added-or-updated _] (merge-review-tasks nil mutated reviews-fixture)
        errors                 (doall (map #(tasks/task-doc-validation (-> % :schema-info :name) %) added-or-updated))]
    added-or-updated => (just (nth mutated 2))
    (count errors) => 1
    (-> errors flatten first) => (contains {:result [:warn "illegal-value:select"]})))

(fact "more recent review from XML does not overwrite the review in mongo"
  (let [mutated                        (-> (into [] reviews-fixture)
                                           (assoc-in [1 :data :katselmus :lasnaolijat :value] "Lasse Lasna"))
        [unchanged added-or-updated _] (merge-review-tasks nil mutated reviews-fixture)]
    (count unchanged) => (count reviews-fixture)
    (diff unchanged reviews-fixture) => [nil nil reviews-fixture]
    added-or-updated => empty?))

(fact "more recent review from XML can overwrite if flag is passed to merge-review-tasks"
  (let [mutated                                 (-> (into [] reviews-fixture)
                                                    (assoc-in [1 :data :katselmus :lasnaolijat :value] "Lasse Lasna"))
        [unchanged added-or-updated new-faulty] (merge-review-tasks nil mutated reviews-fixture true)]
    (count unchanged)        => (dec (count reviews-fixture))
    (count added-or-updated) => 1
    (count new-faulty)       => 1
    (first new-faulty)       => (second reviews-fixture)
    (first added-or-updated) => (second mutated)))

(fact "review with matching id replaces original task"
  (let [;; Use aloituskokous as test review
        review-idx   2
        orig-id      (get-in reviews-fixture [review-idx :id])
        ;; Assign random identifiers to incoming review
        mutated      (mapv (fn [review] (assoc review :id (mongo/create-id))) reviews-fixture)
        new-review   (let [aloituskokous (nth mutated review-idx)]
                       (util/deep-merge
                         aloituskokous
                         {:data {:katselmus         {:tila {:value "pidetty"}}
                                 ;; Refer to the original mongo id of the task
                                 :muuTunnus         {:value orig-id}
                                 :muuTunnusSovellus {:value "Lupapiste"}}}))
        mutated      (assoc mutated review-idx new-review)
        [unchanged
         added-or-updated
         new-faulty] (merge-review-tasks nil mutated reviews-fixture)]
    (fact "All but one review is unchanged"
      unchanged => (just (util/drop-nth review-idx reviews-fixture)))
    (fact "A single changed review"
      (count added-or-updated) => 1
      (first added-or-updated) => (just new-review)
      (= orig-id (:id new-review)) => falsey)
    (fact "No faulty tasks"
      (count new-faulty) => 0)

    (fact "re-import reviews again"
      (let [;; Again, all incoming reviews get new identifiers
            mutated      (mapv (fn [review] (assoc review :id (mongo/create-id))) mutated)
            [unchanged
             added-or-updated
             new-faulty] (merge-review-tasks nil mutated (concat unchanged added-or-updated))]
        (fact "No changes"
          (count unchanged) => (count mutated)
          (count added-or-updated) => 0
          (count new-faulty) => 0)))))

(facts "matching-task"
  (let [mongo-task {:id       "65effb0617ee4604152db225"
                    :data     {:muuTunnus        {:value "ID"}
                               :katselmus        {}
                               :katselmuksenLaji {:value "muu katselmus"}}
                    :taskname "Paikan merkitseminen"}]

    (fact "matches tasks with the same Lupapiste mongo id"
      (fact "single match"
        (matching-task mongo-task
                       [;; Wrong sovellus
                        {:data {:muuTunnusSovellus {:value "Facta"}
                                :muuTunnus         {:value "65effb0617ee4604152db225"}}}
                        ;; Correct match
                        {:data {:muuTunnusSovellus {:value " lupaPISTE  "}
                                :muuTunnus         {:value "65effb0617ee4604152db225"}}}
                        ;; Wrong id
                        {:data {:muuTunnusSovellus {:value "lupaPISTE"}
                                :muuTunnus         {:value "65effb0617ee4604152db123"}}}
                        ;; Missing id
                        {:data {:muuTunnusSovellus {:value "lupaPISTE"}}}
                        ;; Missing sovellus
                        {:data {:muuTunnus {:value "65effb0617ee4604152db225"}}}
                        {:data {}}])
        => {:data {:muuTunnusSovellus {:value " lupaPISTE  "}
                   :muuTunnus         {:value "65effb0617ee4604152db225"}}})
      (fact "multiple matches -> ambiguous"
        (matching-task mongo-task
                       [{:data {:muuTunnusSovellus {:value "lupaPISTE"}
                                :muuTunnus         {:value "65effb0617ee4604152db225"}}}
                        {:data {:muuTunnusSovellus {:value "LUPApiste"}
                                :muuTunnus         {:value "65effb0617ee4604152db225"}}}])
        => nil?)
      (fact "no matches"
        (matching-task mongo-task
                       [{:data {:muuTunnusSovellus {:value "lupaPISTE"}
                                :muuTunnus         {:value "65effb0617ee4604152db123"}}}])
        => nil?))

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


    (facts "matches tasks with same name and type given that the task given as first argument contains no review data"
      (let [update-task                 (update mongo-task :data dissoc :muuTunnus)
            update-task-upper-case-name (update update-task :taskname ss/upper-case)]
        (fact "matches task with same name and type"
          (matching-task mongo-task [update-task])
          => update-task)

        (fact ":muuTunnus prevails over same name and type"
          (matching-task mongo-task [update-task {:data {:muuTunnus {:value "ID"}}}])
          => {:data {:muuTunnus {:value "ID"}}})

        (fact "no match when mongo task contains review data"
          (matching-task (assoc-in mongo-task [:data :katselmus :pitaja :value]
                                   "Pekka Pitaja")
                         [update-task])
          => nil?)

        (fact "task name comparison is case insensitive"
          (matching-task mongo-task [update-task-upper-case-name])
          => update-task-upper-case-name)

        (fact "task matching prefers exact match for task name"
          (matching-task mongo-task [update-task-upper-case-name update-task])
          => update-task)

        (fact "type comparison is case sensitive"
          (matching-task mongo-task [(update-in update-task [:data :katselmuksenLaji :value] ss/upper-case)])
          => nil?)

        (fact "name of the first arg is compared case insensitively to katselmuksenLaji of the second arg"
          (let [mongo-task   (assoc mongo-task :taskname (-> update-task
                                                             (get-in [:data :katselmuksenLaji :value])
                                                             ss/upper-case))
                no-name-task (dissoc update-task :taskname)]
            (matching-task mongo-task [no-name-task])
            => no-name-task))

        (fact "exact name and type match is preferred to all others"
          (matching-task mongo-task
                         [(dissoc update-task :taskname)
                          update-task-upper-case-name
                          update-task])
          => update-task)

        (fact "name-to-name and type-to-type match is preferred to name-to-type match"
          (matching-task mongo-task
                         [(dissoc update-task :taskname)
                          update-task-upper-case-name])
          => update-task-upper-case-name)

        (fact "empty collection does not cause problems"
          (matching-task mongo-task
                         [])
          => nil)

        (fact "nil collection does not cause problems"
          (matching-task mongo-task
                         nil)
          => nil)))

    (facts "matches task with same name, type and proper related data"
      (let [held-review-task                 (-> mongo-task
                                                 (assoc-in [:data :katselmus]
                                                           {:tila    {:value "lopullinen"}
                                                            :pitoPvm {:value "8.9.2017"}
                                                            :pitaja  {:value "Pekka Pitaja"}})
                                                 (update :data dissoc :muuTunnus))
            held-review-task-modified        (-> held-review-task
                                                 (assoc-in [:data :katselmus :lasnaolijat]
                                                           {:value "Lasse Lasnaolija"})
                                                 (update :data assoc :muuTunnus {:value "ID"}))
            held-review-task-edit-distance   (-> held-review-task-modified
                                                 (assoc-in [:data :katselmus :pitaja]
                                                           {:value ", Pekka Pitaja"}))
            held-review-task-upper-case-name (update held-review-task :taskname ss/upper-case)]
        (fact "other data modified"
          (matching-task held-review-task
                         [{:data {:muuTunnus {:value "DI"}}}
                          held-review-task-modified
                          {:data {:muuTunnus {:value "DD"}}}])
          => held-review-task-modified)

        (fact "task name comparison is case insensitive"
          (matching-task held-review-task [held-review-task-upper-case-name])
          => held-review-task-upper-case-name)

        (fact "task matching prefers exact match for task name"
          (matching-task held-review-task [held-review-task-upper-case-name held-review-task])
          => held-review-task)

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
          (doseq [pvm  ["08.09.2017" "2017-09-08" "2017-09-08Z" "2017-09-08+12:00"]
                  :let [task (assoc-in held-review-task
                                       [:data :katselmus :pitoPvm :value]
                                       pvm)]]
            (fact {:midje/description pvm}
              (matching-task held-review-task [task])
              => task))
          (fact "Different pitoPvm"
            (doseq [pvm  ["8.10.2017" "" nil]
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
  (let [reviews [{:pitaja         "One"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "foo"}}]}
                 {:pitaja         "Two"
                  :muuTunnustieto [{:MuuTunnus {:tunnus   "bar"
                                                :sovellus "Bar"}}]}
                 {:pitaja         "Three"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "hello"}}]}
                 {:pitaja         "Four"
                  :muuTunnustieto [{:MuuTunnus {:tunnus   "foo"
                                                :sovellus "Foobar"}}]}
                 {:pitaja         "Five"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "foo"}}]}
                 {:pitaja         "Six"
                  :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
                 {:pitaja "Seven"}
                 {:pitaja         "Eight"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "bar"}}]}
                 {:pitaja         "Nine"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "world"}}]}]]
    (remove-repeating-background-ids  "app-id" reviews)
    => [{:pitaja         "One"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja         "Two"
         :muuTunnustieto [{:MuuTunnus {:sovellus "Bar" :tunnus ""}}]}
        {:pitaja         "Three"
         :muuTunnustieto [{:MuuTunnus {:tunnus "hello"}}]}
        {:pitaja         "Four"
         :muuTunnustieto [{:MuuTunnus {:sovellus "Foobar" :tunnus ""}}]}
        {:pitaja         "Five"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja         "Six"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Seven"}
        {:pitaja         "Eight"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja         "Nine"
         :muuTunnustieto [{:MuuTunnus {:tunnus "world"}}]}]
    (remove-repeating-background-ids  "hello" reviews)
    => [{:pitaja         "One"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja         "Two"
         :muuTunnustieto [{:MuuTunnus {:sovellus "Bar" :tunnus ""}}]}
        {:pitaja         "Three"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja         "Four"
         :muuTunnustieto [{:MuuTunnus {:sovellus "Foobar" :tunnus ""}}]}
        {:pitaja         "Five"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja         "Six"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Seven"}
        {:pitaja         "Eight"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja         "Nine"
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
       (map (fn [{:keys [backend-id date other-id person note type attachment]}]
              (let [tunnus (or backend-id (str "HienoTunnus_" date))]
                (ss/join-non-blanks
                  "\n"
                  [(format "<rakval:katselmustieto>
            <rakval:Katselmus>
              <rakval:muuTunnustieto>
                <rakval:MuuTunnus>
                  <yht:tunnus>%s</yht:tunnus>
                  <yht:sovellus>%s</yht:sovellus>
                </rakval:MuuTunnus>
              </rakval:muuTunnustieto>"
                           tunnus             other-id)
                   (some->> date  (format "<rakval:pitoPvm>%s</rakval:pitoPvm>"))
                   "<rakval:osittainen>osittainen</rakval:osittainen>"
                   (some->> person (format "<rakval:pitaja>%s</rakval:pitaja>"))
                   (format "<rakval:katselmuksenLaji>%s</rakval:katselmuksenLaji>
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
                           (or type "rakennekatselmus")
                           note
                           (if attachment
                             (format "<rakval:liitetieto>
            <rakval:Liite>
              <yht:kuvaus>Katselmuksen pöytäkirja: Rakennekatselmus</yht:kuvaus>
              <yht:linkkiliitteeseen>%s</yht:linkkiliitteeseen>
              <yht:muokkausHetki>%s</yht:muokkausHetki>
              <yht:tyyppi>katselmuksen_tai_tarkastuksen_poytakirja</yht:tyyppi>
            </rakval:Liite>
          </rakval:liitetieto>"
                                     attachment (or date (date/xml-date (date/now))))
                             ""))]))))
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
                   :attachment "five.pdf"    :type     "bad type"}
                  {:other-id "Noidea" :note "Note Six" :attachment "six.pdf"}]
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

(fact "Merge review with lupapiste mongo ids"
  (with-redefs [org/get-org-from-org-or-id
                (fn [_] {:id                           "org-id"
                         :review-officers-list-enabled false})]
    (let [[id-1]          (repeatedly mongo/create-id)
          app-xml         (-> [{:backend-id id-1
                                :date       "2020-08-09Z"
                                :note       "jep1"
                                :other-id   "Lupapiste"
                                :person     "Felix Leiter"
                                :attachment "localhost/some-file"}]
                              generate-kuntagml
                              str->xml-map)
          orig-tasks      [{:data        {:id               id-1
                                          :katselmus        {}
                                          :katselmuksenLaji {:modified 12345
                                                             :value    "rakennekatselmus"}
                                          :muuTunnus        {:value ""}
                                          :rakennus         {}}
                            :schema-info {:name    "task-katselmus"
                                          :subtype "review"}
                            :taskname    "rakennekatselmus"}]
          user            {:username "Sonja"}
          created-at      12345
          application     {:created (date/timestamp "7.8.2020")
                           :tasks   orig-tasks}
          reviews         (read-reviews-from-xml user created-at application app-xml)
          expected-review {:created     created-at
                           :data        {:katselmuksenLaji   {:modified created-at
                                                              :value    "rakennekatselmus"}
                                         :katselmus          {:huomautukset {:kuvaus        {:modified created-at
                                                                                             :value    "jep1"}
                                                                             :toteaja       {:value ""}}
                                                              :lasnaolijat  {:modified created-at
                                                                             :value    ""}
                                                              :pitaja       {:modified created-at
                                                                             :value    "Felix Leiter"}
                                                              :pitoPvm      {:modified created-at
                                                                             :value    "09.08.2020"}
                                                              :poikkeamat   {:modified created-at
                                                                             :value    ""}
                                                              :tiedoksianto {:modified created-at
                                                                             :value    false}
                                                              :tila         {:modified created-at
                                                                             :value    "osittainen"}}
                                         :muuTunnus          {:modified created-at
                                                              :value    id-1}
                                         :muuTunnusSovellus  {:modified created-at
                                                              :value    "Lupapiste"}
                                         :rakennus           {}
                                         :vaadittuLupaehtona {:modified created-at
                                                              :value    true}}
                           :schema-info {:name    "task-katselmus"
                                         :subtype :review
                                         :type    :task
                                         :version 1}
                           :source      {:type "background"}
                           :state       :sent
                           :taskname    "rakennekatselmus"}]
      (fact "a valid response is generated"
        reviews
        =>
        (just {:added-tasks-with-updated-buildings (just [(contains expected-review)])
               :all-tasks                          (fn [tasks] (= 1 (count tasks)))
               :attachments-by-ids                 (fn [m]
                                                     ;; Map contains both id-1 (= backend-id) and the new id generated for
                                                     ;; the incoming review
                                                     (and (= 2 (count m))
                                                          (contains? m id-1)))
               :new-faulty-tasks                   []
               :ok                                 true
               :review-count                       1
               ;; The original review task is replaced. We still refer to its mongo-id in muuTunnus
               :updates                            (just {"$set" (just {:tasks (just [(contains expected-review)])})})})))))

(fact "compare-pitaja can operate with names, codes and combinations"
  (compare-pitaja "aa" "bbb") => false
  (compare-pitaja nil nil) => true
  (compare-pitaja nil "aa") => false
  (compare-pitaja "aa" nil) => false
  (compare-pitaja "aa" "aa") => true
  (compare-pitaja "aa" {:name "aa" :code "b"}) => true
  (compare-pitaja "aa" {:name "Alfonso Adler" :code "aa"}) => true
  (compare-pitaja {:name "Mr B." :code "aa"} {:name "aa" :code "b"}) => false)

(def three-attachment [{:liite {:kuvaus            "Katselmuksen pöytäkirja: Rakennekatselmus"
                                :linkkiliitteeseen "three.pdf"
                                :muokkausHetki     "2017-10-03Z"
                                :tyyppi            "katselmuksen_tai_tarkastuksen_poytakirja"}}])

(fact "skippable-lupapiste-review?"
  (fact "non-lupapiste reviews -> don't skip"
    (skippable-lupapiste-review? {}) => falsey
    (skippable-lupapiste-review? {:muuTunnustieto []}) => falsey
    (skippable-lupapiste-review? {:muuTunnustieto [{:MuuTunnus {:tunnus   "123"
                                                                :sovellus "Foo"}}]}) => falsey)
  (fact "lupapiste reviews without proper id -> skip"
    (skippable-lupapiste-review? {:muuTunnustieto [{:MuuTunnus {:sovellus "lupapiste"}}]}) => truthy
    (skippable-lupapiste-review? {:muuTunnustieto [{:MuuTunnus {:tunnus   "123"
                                                                :sovellus "Lupapiste"}}]}) => truthy
    (skippable-lupapiste-review? {:muuTunnustieto [{:MuuTunnus {:tunnus   "LP-543-2016-94999"
                                                                :sovellus "lupapiste"}}]}) => truthy
    (skippable-lupapiste-review? {:muuTunnustieto [{:MuuTunnus {:tunnus   "123"
                                                                :sovellus "LuPaPiste"}}]}) => truthy)
  (fact "lupapiste reviews with proper id -> don't skip"
    (skippable-lupapiste-review? {:muuTunnustieto [{:MuuTunnus {:tunnus   "65eeea850f179a166f5d36eb"
                                                                :sovellus "lupaPisTe"}}]}) => falsey))


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
        (map skippable-lupapiste-review? reviews) => [false true false true false])

      (fact "Review without date is omitted"
        (every? :pitoPvm reviews) => true)

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

(def six-attachment (just {:liite (just {:kuvaus            "Katselmuksen pöytäkirja: Rakennekatselmus"
                                         :linkkiliitteeseen "six.pdf"
                                         :muokkausHetki     truthy
                                         :tyyppi            "katselmuksen_tai_tarkastuksen_poytakirja"})}))

(fact "Read reviews from xml: allow dateless"
  (with-redefs [org/get-org-from-org-or-id (fn [_] mock-org)]
    (let [created-ts     (date/timestamp "30.9.2017")
          options        {:date-optional? true}
          reviews        (reviews-preprocessed app-xml options)]

      (fact "bad type is invalid against schema"
        (sc/with-fn-validation ()
          (reviews-preprocessed app-xml options)) => (throws #":katselmuksenLaji \(not"))

      (fact "schema passes with OK data"
        (sc/with-fn-validation
          (-> [{:date "2017-10-01Z" :other-id "Hello" :person "Person One" :note "Note One"}
               {:date       "2017-10-02Z" :other-id "Lupapiste" :person "Person Two" :note "Note Two"
                :attachment "two.pdf"}
               {:date       "2017-10-03Z" :other-id "Hello" :person "Person Three" :note "Note Three"
                :attachment "three.pdf"}]
              generate-kuntagml
              str->xml-map
              (reviews-preprocessed options)))
        => sequential?)

      (fact "Preprocessed reviews"
        (count reviews) => 6
        (map skippable-lupapiste-review? reviews) => [false true false true false false])

      (fact "Review without date is included"
        (remove :pitoPvm reviews)
        => (just (just {:muuTunnustieto                  [{:MuuTunnus {:tunnus   "HienoTunnus_"
                                                                       :sovellus "Noidea"}}]
                        :osittainen                      "osittainen"
                        :katselmuksenLaji                "rakennekatselmus"
                        :vaadittuLupaehtonaKytkin        true
                        :huomautukset                    [{:huomautus {:kuvaus "Note Six"}}]
                        :liitetieto                      (just six-attachment)
                        :tarkastuksenTaiKatselmuksenNimi "rakennekatselmus"})))

      (fact "Process reviews"
        (let [review-tasks (process-reviews {:id "app-id" :organization "org-id"}
                                            app-xml
                                            12345
                                            {}
                                            options)]
          (count review-tasks) => 4
          (fact "Three and Six have attachments"
            (keep :attachments review-tasks) => (just three-attachment (just six-attachment)
                                                      :in-any-order))))

      (fact "Tasks and attachments"
        (let [{tasks        :added-tasks-with-updated-buildings
               att-by-ids   :attachments-by-ids
               updated-ids  :all-tasks
               review-count :review-count} (read-reviews-from-xml {:username "Foo"} 12345
                                                                  {:created created-ts}
                                                                  app-xml options)
              one-id                       (task-id-for-person tasks "Person One")
              three-id                     (task-id-for-person tasks "Person Three")
              five-id                      (task-id-for-person tasks (get-in mock-org [:reviewOfficers 0]))
              six-id                       (task-id-for-person tasks nil)]
          (fact "Review count"
            review-count => 4)
          (fact "Four new tasks"
            updated-ids => (just [one-id three-id five-id six-id] :in-any-order))
          (fact "Three and Six have the correct attachments"
            att-by-ids => (just {three-id                  three-attachment
                                 "HienoTunnus_2017-10-03Z" three-attachment
                                 six-id                    (just six-attachment)
                                 "HienoTunnus_"            (just six-attachment)})))))))

(against-background [(org/get-org-from-org-or-id anything) => mock-org]
                    (facts "attachments-by-id"
                      (let [review-tasks (process-reviews {:id "app-id" :organization "org-id"} app-xml 12345 {})
                            three-id     (task-id-for-person review-tasks "Person Three")]
                        (fact "keyed by IDs"
                          (attachments-by-id-from-task-candidates review-tasks)
                          => {three-id                  three-attachment
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
                          => {three-id three-attachment})))
                    (facts "attachments-by-id: dateless"
                      (let [review-tasks (process-reviews {:id "app-id" :organization "org-id"} app-xml 12345 {}
                                                          {:date-optional? true})
                            three-id     (task-id-for-person review-tasks "Person Three")
                            six-id       (task-id-for-person review-tasks nil)]
                        (fact "keyed by IDs"
                          (attachments-by-id-from-task-candidates review-tasks)
                          => (just {three-id                  three-attachment
                                    "HienoTunnus_2017-10-03Z" three-attachment
                                    six-id                    (just six-attachment)
                                    "HienoTunnus_"            (just six-attachment)})))))
