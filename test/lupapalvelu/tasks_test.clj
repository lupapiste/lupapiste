(ns lupapalvelu.tasks-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.tasks]))

(testable-privates lupapalvelu.tasks verdict->tasks)

(facts "Tyonjohtajat KRYSP yhteiset 2.1.1"
  (let [tasks (flatten (verdict->tasks {:paatokset [{:lupamaaraykset {:vaadittuTyonjohtajatieto ["testi" "test 2"] }}]} nil))
        task (first tasks)]
    (count tasks) => 2
    (get-in task [:schema-info :name]) => "task-vaadittu-tyonjohtaja"
    (:taskname task) => "testi"))
