(ns lupapalvelu.pate.review-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.pate.review :refer :all]
            [lupapalvelu.pate.verdict :refer [pate-verdict->tasks]]
            [lupapalvelu.pate.verdict-canonical-test :as verdict-test]
            [lupapalvelu.tasks :as tasks]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

(def test-pate-verdict verdict-test/verdict)


(fact "pate review->task, example reviews are valid"
  (->> (pate-verdict->tasks test-pate-verdict [] 123)
       (map (partial tasks/task-doc-validation "task-katselmus"))) => (has every? empty?))

(def b1
  (let [valtakunnallinenNumero (ssg/generate ssc/Rakennustunnus)]
    {:localShortId   "012"
     :nationalId     valtakunnallinenNumero
     :buildingId     valtakunnallinenNumero
     :location-wgs84 nil
     :location       nil
     :index          (str 1)
     :description    "Test buildings"}))

(facts "pate review->task data"
  (let [review (first (pate-verdict->tasks test-pate-verdict [b1] 123))]
    review => map?
    (fact "buildings"
      (let [rakennus (tools/unwrapped (get-in review [:data :rakennus :0 :rakennus]))]
        (get-in rakennus [:jarjestysnumero]) => "1"
        (get-in rakennus [:rakennusnro]) => "012"
        (get-in rakennus [:valtakunnallinenNumero]) => (:nationalId b1)))))
