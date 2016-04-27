(ns lupapalvelu.migration.task-katselmus-building-update-test
  (:require  [midje.sweet :refer :all]
             [sade.util :as util]
             [lupapalvelu.migration.migrations :refer [update-katselmus-buildings remove-empty-buildings]]))

(def buildings [{:description "Talo A, Toinen selite",
                 :localShortId "101",
                 :buildingId "123456001M",
                 :index "1",
                 :created "2013",
                 :localId nil,
                 :usage "039 muut asuinkerrostalot",
                 :nationalId "123456001M",
                 :area "2000",
                 :propertyId "18601234567890",
                 :operationId "abcdefghijklmnopqr"}
                {:description nil,
                 :localShortId "102",
                 :buildingId "123456002N",
                 :index "2",
                 :created "2013",
                 :localId nil,
                 :usage "719 muut varastorakennukset",
                 :nationalId "123456002N",
                 :area "20",
                 :propertyId "18601234567891",
                 :operationId nil}
                {:description nil,
                 :localShortId "103",
                 :buildingId "103",
                 :index "3",
                 :created "2013",
                 :localId nil,
                 :usage "",
                 :nationalId nil,
                 :area "22",
                 :propertyId "18601234567892",
                 :operationId nil}])

(def task-katselmus {:data {:katselmus {:tila {:value nil},
                                        :poikkeamat {:value ""},
                                        :lasnaolijat {:value ""},
                                        :huomautukset {:toteamisHetki {:value nil},
                                                       :toteaja {:value ""},
                                                       :maaraAika {:value nil},
                                                       :kuvaus {:value ""}},
                                        :pitaja {:value ""},
                                        :pitoPvm {:value nil}},
                            :rakennus {:2 {:rakennus {:kunnanSisainenPysyvaRakennusnumero {:modified 1460368138321, :value ""},
                                                      :valtakunnallinenNumero {:modified 1460368138321, :value "123456002N"},
                                                      :rakennusnro {:modified 1460368138321, :value "102"},
                                                      :jarjestysnumero {:modified 1460368138321, :value "2"},
                                                      :kiinttun {:modified 1460368138321, :value "18601234567891"}}},
                                       :1 {:tila {:tila {:modified 1460368127480, :value "lopullinen"}},
                                           :rakennus {:kunnanSisainenPysyvaRakennusnumero {:value "", :modified 1460368121329},
                                                      :kiinttun {:modified 1460368121329, :value "18601234567892"},
                                                      :valtakunnallinenNumero {:value "", :modified 1460368121329},
                                                      :rakennusnro {:modified 1460368121329, :value "103"},
                                                      :jarjestysnumero {:modified 1460368121329, :value "3"}}},
                                       :0 {:tila {:kayttoonottava {:value false}, :tila {:modified 1460368143572, :value "osittainen"}},
                                           :rakennus {:kunnanSisainenPysyvaRakennusnumero {:modified 1460368109879, :value ""},
                                                      :kiinttun {:modified 1460368109879, :value "18601234567890"},
                                                      :rakennusnro {:modified 1460368109879, :value "101"},
                                                      :valtakunnallinenNumero {:modified 1460368109879, :value "123456001M"},
                                                      :jarjestysnumero {:modified 1460368109879, :value "1"}}}},
                            :vaadittuLupaehtona {:modified 1459840742117, :value true},
                            :katselmuksenLaji {:modified 1459840742117, :value "aloituskokous"}}})

(def katselmus-missing-rakennus (util/dissoc-in task-katselmus [:data :rakennus :2]))

(fact "no new buildings results in nil"
  (update-katselmus-buildings buildings task-katselmus) => nil)

(facts "updating buildings"
  (let [new-katselmus (update-katselmus-buildings buildings katselmus-missing-rakennus)
        katselmus-data (:data new-katselmus)
        rakennus-data  (:rakennus katselmus-data)]
    (fact "other than :rakennus are untouched"
      (util/dissoc-in task-katselmus [:data :rakennus]) => (util/dissoc-in new-katselmus [:data :rakennus]))

    (fact "rakennus has new index"
      (keys (get-in katselmus-missing-rakennus [:data :rakennus])) => (just [:1 :0] :in-any-order)
      (keys (get-in new-katselmus [:data :rakennus]))              => (just [:2 :1 :0] :in-any-order))

    (fact "index is incremented correctly"
      (-> (update-katselmus-buildings buildings (util/dissoc-in task-katselmus [:data :rakennus :1]))
          (get-in [:data :rakennus])
          keys) => (just [:0 :2 :3] :in-any-order))

    (fact "new rakennus data is correct"
      (:2 rakennus-data) => {:rakennus {:kunnanSisainenPysyvaRakennusnumero {:value nil},
                                        :valtakunnallinenNumero {:value "123456002N"},
                                        :rakennusnro {:value "102"},
                                        :jarjestysnumero {:value "2"},
                                        :kiinttun {:value "18601234567891"}}})))

(def data-with-empty-rakennus {:data {:katselmus {:tila {:value nil},
                                                  :poikkeamat {:value ""},
                                                  :lasnaolijat {:value ""},
                                                  :huomautukset {:toteamisHetki {:value nil},
                                                                 :toteaja {:value ""},
                                                                 :maaraAika {:value nil},
                                                                 :kuvaus {:value ""}},
                                                  :pitaja {:value ""},
                                                  :pitoPvm {:value nil}},
                                      :rakennus {:3 {:rakennus {:kunnanSisainenPysyvaRakennusnumero {:value nil},
                                                                :kiinttun {:value "132456"},
                                                                :rakennusnro {:value "003"},
                                                                :valtakunnallinenNumero {:value "231313321"},
                                                                :jarjestysnumero {:value "3"}}},
                                                 :2 {:rakennus {:kunnanSisainenPysyvaRakennusnumero {:value nil},
                                                                :kiinttun {:value "5431312"},
                                                                :rakennusnro {:value "002"},
                                                                :valtakunnallinenNumero {:value "24321321"},
                                                                :jarjestysnumero {:value "2"}}},
                                                 :1 {:rakennus {:kunnanSisainenPysyvaRakennusnumero {:value nil},
                                                                :kiinttun {:value "313215664"},
                                                                :rakennusnro {:value "001"},
                                                                :valtakunnallinenNumero {:value "3213587951"},
                                                                :jarjestysnumero {:value "1"}}}
                                                 :0 {:rakennus {:jarjestysnumero {:value nil},
                                                                :valtakunnallinenNumero {:value ""},
                                                                :rakennusnro {:value ""},
                                                                :kiinttun {:value ""},
                                                                :kunnanSisainenPysyvaRakennusnumero {:value ""}}},
                                      :vaadittuLupaehtona {:modified 1452747765376, :value true},
                                      :katselmuksenLaji {:modified 1452747765376, :value "loppukatselmus"}}}})

(facts "cleaning up buildings"
  (fact "index 0 is removed, as all it's :rakennus values are empty"
    (-> (remove-empty-buildings data-with-empty-rakennus)
        :data
        :rakennus
        keys) => (just [:3 :2 :1])))
