(ns lupapalvelu.tasks-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.xml.krysp.reader :as reader]
            [lupapalvelu.tasks :as tasks]))

(facts
  (let [xml (sade.xml/parse (slurp "resources/krysp/sample/verdict.xml"))
        cases (reader/->verdicts xml)
        tasks (tasks/verdicts->tasks {:verdicts cases :auth [{:id "pena's mongo id" :firstName "Pena" :lastName "Panaani" :role "owner"}]} 1312341259733)]
    (clojure.pprint/pprint tasks)
    ))