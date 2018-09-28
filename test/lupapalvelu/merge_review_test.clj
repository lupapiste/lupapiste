 (ns lupapalvelu.merge-review-test
   (:require [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             [clojure.data :refer [diff]]
             [lupapalvelu.review :refer :all]
             [lupapalvelu.tasks :as tasks]
             [sade.util :as util]))


(testable-privates lupapalvelu.review
                   merge-review-tasks matching-task
                   remove-repeating-background-ids)

(def rakennustieto-fixture [{:KatselmuksenRakennus {:kiinttun "54300601900001",
                                                    :rakennusnro "001",
                                                    :jarjestysnumero "1",
                                                    :aanestysalue "010",
                                                    :muuTunnustieto {:MuuTunnus {:sovellus "LP-543-2016-94999",
                                                                                 :tunnus "LP-543-2016-94999"}},
                                                    :valtakunnallinenNumero "103571943D",
                                                    :rakennuksenSelite "Omakotitalo"}}] )

(def reviews-fixture
  (map #(tasks/katselmus->task {:state :sent :created (+ (sade.core/now) (rand-int 100))}
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

(fact "merging the review vector with itself leaves everything unchanged"
  (let [[unchanged added-or-updated _] (merge-review-tasks reviews-fixture reviews-fixture)]
    (count unchanged) => (count reviews-fixture)
    (diff unchanged reviews-fixture) => [nil nil reviews-fixture]
    added-or-updated => empty?))

(fact "review is updated on top of empty review"
  (let [original-vec (into [] reviews-fixture)
        mutated (-> original-vec
                    (assoc-in [2 :data :katselmus :pitoPvm :value] "13.05.2016")
                    (assoc-in [2 :created] (sade.core/now)))
        original-ts (get-in original-vec [2 :created])
        [unchanged added-or-updated _] (merge-review-tasks mutated reviews-fixture)]
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
            [_ added-or-updated _] (merge-review-tasks mutated reviews-fixture)
            errors (doall (map #(tasks/task-doc-validation (-> % :schema-info :name) %) added-or-updated))]
        added-or-updated => (just (nth mutated 2))
        (count errors) => 1
        (-> errors flatten first) => (contains {:result [:warn "illegal-value:select"]})))

(fact "more recent review from XML does not overwrite the review in mongo"
  (let [mutated (-> (into [] reviews-fixture)
                    (assoc-in [1 :data :katselmus :lasnaolijat :value] "Lasse Lasna"))
        [unchanged added-or-updated _] (merge-review-tasks mutated reviews-fixture)]
    (count unchanged) => (count reviews-fixture)
    (diff unchanged reviews-fixture) => [nil nil reviews-fixture]
    added-or-updated => empty?))

(fact "more recent review from XML can overwrite if flag is passed to merge-review-tasks"
      (let [mutated (-> (into [] reviews-fixture)
                        (assoc-in [1 :data :katselmus :lasnaolijat :value] "Lasse Lasna"))
            [unchanged added-or-updated new-faulty] (merge-review-tasks mutated reviews-fixture true)]
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
        => {:data {:muuTunnus {:value "ID"}}})))))

(fact "Remove repeating background ids"
  (let [reviews [{:pitaja "One"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "foo"}}]}
                 {:pitaja "Two"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "bar"}}]}
                 {:pitaja "Three"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "hello"}}]}
                 {:pitaja "Four"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "foo"}}]}
                 {:pitaja "Five"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "foo"}}]}
                 {:pitaja "Six"
                  :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
                 {:pitaja "Seven"}
                 {:pitaja "Eight"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "bar"}}]}
                 {:pitaja "Nine"
                  :muuTunnustieto [{:MuuTunnus {:tunnus "world"}}]}]]
    (remove-repeating-background-ids reviews)
    => [{:pitaja "One"}
        {:pitaja "Two"}
        {:pitaja "Three"
         :muuTunnustieto [{:MuuTunnus {:tunnus "hello"}}]}
        {:pitaja "Four"}
        {:pitaja "Five"}
        {:pitaja "Six"
         :muuTunnustieto [{:MuuTunnus {:tunnus ""}}]}
        {:pitaja "Seven"}
        {:pitaja "Eight"}
        {:pitaja "Nine"
         :muuTunnustieto [{:MuuTunnus {:tunnus "world"}}]}]))
