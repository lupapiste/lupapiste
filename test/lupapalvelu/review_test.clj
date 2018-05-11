(ns lupapalvelu.review-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.review :as review]))

(fact "Checking that reviews/can-bypass-task-pdfa-generation? works as expected"
  (let [organization {:only-use-inspection-from-backend true
                      :name "Original organization"}
        pks '({:urlHash "Bamboleo"}
              {:urlHash "Livin' la vida loca" :jambalaya "Smoked fish pie"})]
    (fact "When :only-use-inspection-from-backend is true and every element in pks has :urlHash property, it returns true"
      (review/can-bypass-task-pdfa-generation? organization pks) => true)
    (fact "When :only-use-inspection-from-backend is false and every element in pks has :urlHash property, it returns false"
      (review/can-bypass-task-pdfa-generation? (assoc organization :only-use-inspection-from-backend false) pks) => false)
    (fact "When :only-use-inspection-from-backend is true but not every element in pks has :urlHash property, it returns false"
      (review/can-bypass-task-pdfa-generation? organization (conj pks {:kikka "kukka"})) => false)))

