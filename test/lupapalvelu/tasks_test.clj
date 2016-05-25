(ns lupapalvelu.tasks-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.tasks :refer :all]
            [lupapalvelu.verdict :as verdict]))

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


(facts "Review building info is updated but status info kept"
  (let [buildings [{:description "Talo A, Toinen selite",
                           :localShortId "value-from-buildings",
                           :buildingId "123456001M",
                           :index "1",
                    :operationId "abcdefghijklmnopqr"}]
        task-rakennus {:schema-info
                          {:version 1,
                           :type "task",
                           :name "task-katselmus"},
                          :closed nil,
                          :id "573996952374d10fe642f304",
                          :assignee {:lastName "Sibbo", :firstName "Sonja", :id "777777777777777777000023"},
                          :taskname "Aloituskokous",
                          :data
                          {:muuTunnus {:modified 1463391893160, :value ""},
                           :katselmus
                           {:poikkeamat {:modified 1463391893160, :value ""},
                            :lasnaolijat {:modified 1463391893160, :value ""},
                            :huomautukset
                            {:toteamisHetki {:value nil}, :toteaja {:value ""}, :maaraAika {:value nil}, :kuvaus {:modified 1463391893160, :value ""}},
                            :tiedoksianto {:value false},
                            :pitaja {:modified 1463391893160, :value nil},
                            :pitoPvm {:modified 1463391893160, :value nil},
                            :tila {:modified 1463391893160, :value nil}},
                           :rakennus
                           {:0
                            {:tila {:kayttoonottava {:modified 1463391893160, :value false}, :tila "value-from-task"},
                             :rakennus
                             {:rakennusnro {:modified 1463391893160, :value "to-be-overridden"},
                              :kiinttun {:modified 1463391893160, :value "18601234567890"},
                              :jarjestysnumero {:modified 1463391893160, :value "1"}}}},
                           :vaadittuLupaehtona {:modified 1463391893160, :value true},
                           :katselmuksenLaji {:modified 1463391893160, :value "aloituskokous"}}}]


    (get-in (update-task-buildings buildings task-rakennus) [:data :rakennus :0 :rakennus :rakennusnro :value]) => "value-from-buildings"
    (get-in (update-task-buildings buildings task-rakennus) [:data :rakennus :0 :tila :tila :value]) => "value-from-task"))
