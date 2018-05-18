(ns lupapalvelu.pate.review-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.pate.verdict :refer [pate-verdict->tasks]]
            [lupapalvelu.pate.verdict-canonical-test :as verdict-test]
            [lupapalvelu.tasks :as tasks]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

(def test-pate-verdict verdict-test/verdict)

(defn filter-review-tasks [tasks]
  (filter #(= (get-in % [:schema-info :name]) "task-katselmus")
          tasks))

(fact "pate reviews->tasks, example reviews are valid"
  (->> (pate-verdict->tasks test-pate-verdict [] 123)
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
  (let [review (first (pate-verdict->tasks test-pate-verdict [b1] 123))]
    review => map?
    (fact "buildings"
      (let [rakennus (tools/unwrapped (get-in review [:data :rakennus :0 :rakennus]))]
        (get-in rakennus [:jarjestysnumero]) => "1"
        (get-in rakennus [:rakennusnro]) => "012"
        (get-in rakennus [:valtakunnallinenNumero]) => (:nationalId b1)))))

(fact "Reviews not included"
  (-> (assoc-in test-pate-verdict [:data :reviews-included] false)
      (pate-verdict->tasks [] 123)
      filter-review-tasks)
  => [])

(fact "No review selected"
  (-> (assoc-in test-pate-verdict [:data :reviews] [])
      (pate-verdict->tasks [] 123)
      filter-review-tasks)
  => []
  (-> (assoc-in test-pate-verdict [:data :reviews] nil)
      (pate-verdict->tasks [] 123)
      filter-review-tasks)
  => [])

(fact "Only one review and language is English"
  (-> (assoc-in test-pate-verdict [:data :language] "en")
      (update-in [:data :reviews] (partial drop 1))
      (pate-verdict->tasks [] 123)
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
                                                :modified nil}})})]))
