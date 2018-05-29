(ns lupapalvelu.pate.review-test
  (:require [lupapalvelu.document.tools :as tools]
            [lupapalvelu.pate.tasks :as pate-tasks]
            [lupapalvelu.pate.verdict-canonical-test :as verdict-test]
            [lupapalvelu.tasks :as tasks]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]))

(def test-pate-verdict verdict-test/verdict)

(testable-privates lupapalvelu.pate.tasks
                   reviews->tasks legacy-reviews->tasks)

(defn filter-review-tasks [tasks]
  (filter #(= (get-in % [:schema-info :subtype]) :review)
          tasks))

(fact "pate reviews->tasks, example reviews are valid"
  (->> (pate-tasks/pate-verdict->tasks test-pate-verdict 123 [])
       filter-review-tasks
       (map (partial tasks/task-doc-validation "task-katselmus")))=> (has every? empty?))

(def b1
  (let [valtakunnallinenNumero (ssg/generate ssc/Rakennustunnus)]
    {:localShortId   "012"
     :nationalId     valtakunnallinenNumero
     :buildingId     valtakunnallinenNumero
     :location-wgs84 nil
     :location       nil
     :index          (str 1)
     :description    "Test buildings"}))

(facts "pate reviews->tasks data"
  (let [review (first (pate-tasks/pate-verdict->tasks test-pate-verdict 123 [b1]))]
    review => map?
    (fact "buildings"
      (let [rakennus (tools/unwrapped (get-in review [:data :rakennus :0 :rakennus]))]
        (get-in rakennus [:jarjestysnumero]) => "1"
        (get-in rakennus [:rakennusnro]) => "012"
        (get-in rakennus [:valtakunnallinenNumero]) => (:nationalId b1)))))

(fact "Reviews not included"
  (-> (assoc-in test-pate-verdict [:data :reviews-included] false)
      (pate-tasks/pate-verdict->tasks 123 [])
      filter-review-tasks)
  => [])

(fact "No review selected"
  (-> (assoc-in test-pate-verdict [:data :reviews] [])
      (pate-tasks/pate-verdict->tasks 123 [])
      filter-review-tasks)
  => []
  (-> (assoc-in test-pate-verdict [:data :reviews] nil)
      (pate-tasks/pate-verdict->tasks 123 [])
      filter-review-tasks)
  => [])

(fact "Only one review and language is English"
  (-> (assoc-in test-pate-verdict [:data :language] "en")
      (update-in [:data :reviews] (partial drop 1))
      (pate-tasks/pate-verdict->tasks 123 [])
      filter-review-tasks)
  => (just [(contains {:taskname    "Review2"
                       :schema-info (contains {:type    :task
                                               :subtype :review
                                               :name    "task-katselmus"
                                               :order   1
                                               :version 1})
                       :source      {:id   (:id test-pate-verdict)
                                     :type "verdict"}
                       :state       :requires_user_action
                       :data        (contains {:katselmuksenLaji
                                               {:value    "rakennuksen paikan merkitseminen"
                                                :modified 123}})})]))

(facts "YA reviews"
  (let [ya-verdict (-> test-pate-verdict
                       (assoc :category "ya")
                       (assoc-in [:references :reviews 0 :type] "valvonta")
                       (assoc-in [:references :reviews 1 :type] "aloituskatselmus"))
        tasks      (filter-review-tasks (pate-tasks/pate-verdict->tasks ya-verdict
                                                                        123 []))]
    (fact "Generated YA tasks are valid"
      (count tasks) => 2
      (map (partial tasks/task-doc-validation "task-katselmus-ya") tasks)
      => (has every? empty?))

    (fact "Tasks"
      tasks => (just [(contains {:taskname    "Katselmus"
                                 :schema-info (contains {:type    :task
                                                         :subtype :review
                                                         :name    "task-katselmus-ya"
                                                         :order   1
                                                         :version 1})
                                 :source      {:id   (:id ya-verdict)
                                               :type "verdict"}
                                 :state       :requires_user_action
                                 :data        (contains {:katselmuksenLaji
                                                         {:value    "Muu valvontak\u00e4ynti"
                                                          :modified 123}})})
                      (contains {:taskname    "Katselmus2"
                                 :schema-info (contains {:type    :task
                                                         :subtype :review
                                                         :name    "task-katselmus-ya"
                                                         :order   1
                                                         :version 1})
                                 :source      {:id   (:id ya-verdict)
                                               :type "verdict"}
                                 :state       :requires_user_action
                                 :data        (contains {:katselmuksenLaji
                                                         {:value    "Aloituskatselmus"
                                                          :modified 123}})})] ))))

(def legacy-verdict {:id       "legacy-id"
                     :legacy?  true
                     :category "r"
                     :data     {:reviews {:id1 {:name "The initial review"
                                                :type "aloituskokous"}
                                          :id2 {:name "Where is the house at?"
                                                :type "paikan-tarkastaminen"}}}})

(facts "Legacy reviews"
  (let [tasks (pate-tasks/pate-verdict->tasks legacy-verdict 12345 [b1])]
    (fact "Tasks valid"
      (->> tasks
           (map (partial tasks/task-doc-validation "task-katselmus"))
           (filter seq)) => empty?)
    (fact "Tasks"
      tasks => (just [(contains {:taskname    "The initial review"
                                 :schema-info (contains {:type    :task
                                                         :subtype :review
                                                         :name    "task-katselmus"
                                                         :order   1
                                                         :version 1})
                                 :source      {:id   "legacy-id"
                                               :type "verdict"}
                                 :state       :requires_user_action
                                 :data        (contains {:katselmuksenLaji
                                                         {:value    "aloituskokous"
                                                          :modified 12345}})})
                      (contains {:taskname    "Where is the house at?"
                                 :schema-info (contains {:type    :task
                                                         :subtype :review
                                                         :name    "task-katselmus"
                                                         :order   1
                                                         :version 1})
                                 :source      {:id   "legacy-id"
                                               :type "verdict"}
                                 :state       :requires_user_action
                                 :data        (contains {:katselmuksenLaji
                                                         {:value    "rakennuksen paikan tarkastaminen"
                                                          :modified 12345}})})]))
    (fact "Task buildings"
      (tools/unwrapped (get-in (first tasks) [:data :rakennus :0 :rakennus]))
      => (contains {:jarjestysnumero        "1"
                    :rakennusnro            "012"
                    :valtakunnallinenNumero (:nationalId b1)}))

    (fact "No reviews"
      (pate-tasks/pate-verdict->tasks (assoc-in legacy-verdict
                                                [:data :reviews] nil)
                                      12345
                                      [b1])
      => empty?)))
