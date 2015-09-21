(ns lupapalvelu.migration.raki-conversion-test
  (:require [lupapalvelu.migration.migrations :refer :all]
            [midje.util :refer [testable-privates]]
            [midje.sweet :refer :all]))

(testable-privates lupapalvelu.migration.migrations document-raki-conversion building-raki-conversion task-raki-conversion)

(def documents
  [{:data {:buildingId {:value "001" :source "krysp"}
           :rakennusnro {:value "001" :source "krysp"}
           :manuaalinen_rakennusnro {:value ""}
           :valtakunnallinenNumero {:value ""}
           :muutostyolaji {:value "muut muutosty\u00f6t"}
           :kaytto {:rakentajaTyyppi {:value "ei tiedossa"} :kayttotarkoitus {:value "039 muut asuinkerrostalot"}}
           :lammitys {:lammitystapa {:value "vesikeskus"} :lammonlahde {:value nil} :muu-lammonlahde {:value ""}}
           :perusparannuskytkin {:value false}}}
   {:data {:kuvaus {:value "..."} :poikkeamat {:value ""}}}
   {:data {:kiinteisto {:maaraalaTunnus {:value ""} :tilanNimi {:value "..."}
                        :rekisterointipvm {:value "..."}
                        :maapintaala {:value "0.1"}}
           :kaavatilanne {:value "asemakaava"}}}])

(facts "document-raki-conversion"
  (against-background
    (lupapalvelu.mongo/select-one :hki_tunnusvastaavuudet {:RAKENNUSTUNNUS "091-003-0052-0010 001"}) =>
    {:VTJ_PRT "1030462707" :RAKENNUSTUNNUS "091-003-0052-0010 001" :SIJAINTIKIINTEISTO "091-003-0052-0010" :KUNNAN_PYSYVA_RAKNRO "473"})

  (fact "no building, no change"
    (document-raki-conversion :hki_tunnusvastaavuudet ..id.. ..anything.. (documents 1)) => (documents 1)
    (document-raki-conversion :hki_tunnusvastaavuudet ..id.. ..anything.. (documents 2)) => (documents 2))

  (fact "national building id is generated"
    (document-raki-conversion :hki_tunnusvastaavuudet ..id.. "09100300520010" (documents 0)) =>
    {:data {:buildingId {:value "1030462707" :source "krysp"}
            :muutostyolaji {:value "muut muutosty\u00f6t"}
            :rakennusnro {:value "001" :source "krysp"}
            :kaytto {:rakentajaTyyppi {:value "ei tiedossa"} :kayttotarkoitus {:value "039 muut asuinkerrostalot"}}
            :lammitys {:lammitystapa {:value "vesikeskus"} :lammonlahde {:value nil} :muu-lammonlahde {:value ""}}
            :perusparannuskytkin {:value false}
            :manuaalinen_rakennusnro {:value ""}
            :valtakunnallinenNumero {:value "1030462707"}}}))

(facts "building-raki-conversion"
  (against-background
    (lupapalvelu.mongo/select-one :hki_tunnusvastaavuudet {:RAKENNUSTUNNUS "091-040-0153-0009 016"}) =>
    {:VTJ_PRT "102260741K" :RAKENNUSTUNNUS "091-040-0153-0009 016" :SIJAINTIKIINTEISTO "091-040-0153-0009" :KUNNAN_PYSYVA_RAKNRO "48780"})

  (building-raki-conversion :hki_tunnusvastaavuudet ..id..
    {:localShortId "016"
     :buildingId "016"
     :index "1"
     :created "2015"
     :localId nil
     :usage "712 kauppavarastot"
     :nationalId nil
     :area "6017"
     :propertyId "09104001530009"})
  =>
  {:localShortId "016"
   :buildingId "102260741K"
   :index "1"
   :created "2015"
   :localId "48780"
   :usage "712 kauppavarastot"
   :nationalId "102260741K"
   :area "6017"
   :propertyId "09104001530009"})


(def tasks [{:data {:katselmuksenLaji {:value "loppukatselmus"} :vaadittuLupaehtona {:value true}}}
            {:data {:katselmuksenLaji {:value "aloituskokous"} :vaadittuLupaehtona {:value true}
                    :rakennus {:0 {:rakennus {:valtakunnallinenNumero {:value ""}
                                              :rakennusnro {:value "001"}
                                              :kiinttun {:value "09100200400009"}
                                              :jarjestysnumero {:value "1"}}}
                               :1 {}}}}
            {:data {:maarays {:value "..."}}}
            {:data {:maarays {:value "..."}}}
            {:data {:asiointitunnus { :value "LP-091-2015-00050"}}}])

(facts "task-raki-conversion"
  (against-background
    (lupapalvelu.mongo/select-one :hki_tunnusvastaavuudet {:RAKENNUSTUNNUS "091-002-0040-0009 001"}) =>
    {:VTJ_PRT "1030368157" :RAKENNUSTUNNUS "091-002-0040-0009 001" :SIJAINTIKIINTEISTO "091-002-0040-0009" :KUNNAN_PYSYVA_RAKNRO "370"})

  (fact "no building, no change"
    (task-raki-conversion :hki_tunnusvastaavuudet ..id.. ..anything.. (tasks 0)) => (tasks 0)
    (task-raki-conversion :hki_tunnusvastaavuudet ..id.. ..anything.. (tasks 2)) => (tasks 2)
    (task-raki-conversion :hki_tunnusvastaavuudet ..id.. ..anything.. (tasks 3)) => (tasks 3)
    (task-raki-conversion :hki_tunnusvastaavuudet ..id.. ..anything.. (tasks 4)) => (tasks 4))

  (fact "national building id is generated"
    (task-raki-conversion :hki_tunnusvastaavuudet ..id.. "09100200400009" (tasks 1)) =>
    {:data {:katselmuksenLaji {:value "aloituskokous"} :vaadittuLupaehtona {:value true}
            :rakennus {:1 {}
                       :0 {:rakennus {:valtakunnallinenNumero {:value "1030368157"}
                                      :rakennusnro {:value "001"}
                                      :kiinttun {:value "09100200400009"}
                                      :jarjestysnumero {:value "1"}}}}}}))
