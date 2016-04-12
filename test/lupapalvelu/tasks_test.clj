(ns lupapalvelu.tasks-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.tasks :refer :all]))

(testable-privates lupapalvelu.tasks verdict->tasks)

(facts "Tyonjohtajat KRYSP yhteiset 2.1.1"
  (let [tasks (flatten (verdict->tasks {:paatokset [{:lupamaaraykset {:vaadittuTyonjohtajatieto ["testi" "test 2"] }}]} nil {:buildings []}))
        task (first tasks)]
    (count tasks) => 2
    (get-in task [:schema-info :name]) => "task-vaadittu-tyonjohtaja"
    (:taskname task) => "testi"))

(facts "Rakennus data from buildings"
  (rakennus-data-from-buildings {} nil) => {}
  (rakennus-data-from-buildings {} []) => {}
  (rakennus-data-from-buildings nil nil) => nil

  (rakennus-data-from-buildings {:1 {:foo "faa"} :3 {:foo "fuu"}}
                                [{:index "1" :propertyId "123" :nationalId "3214M"}]) => {:1 {:foo "faa"}
                                                                                          :3 {:foo "fuu"}
                                                                                          :4 {:rakennus {:jarjestysnumero                    "1"
                                                                                                         :kiinttun                           "123"
                                                                                                         :rakennusnro                        nil
                                                                                                         :valtakunnallinenNumero             "3214M"
                                                                                                         :kunnanSisainenPysyvaRakennusnumero nil}
                                                                                              :tila {:tila ""
                                                                                                     :kayttoonottava false} }})
