(ns lupapalvelu.pate.review-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.pate.review :refer :all]
            [lupapalvelu.pate.verdict :refer [pate-verdict->tasks]]
            [lupapalvelu.pate.verdict-canonical-test :as verdict-test]
            [lupapalvelu.tasks :as tasks]))

(def test-pate-verdict verdict-test/verdict)


(fact "pate review->task, example reviews are valid"
  (->> (pate-verdict->tasks test-pate-verdict 123)
       (map (partial tasks/task-doc-validation "task-katselmus"))) => (has every? empty?))
