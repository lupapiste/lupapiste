(ns lupapalvelu.document.vrk-test
  (:use [lupapalvelu.document.tools]
        [lupapalvelu.document.schemas]
        [lupapalvelu.document.validators]
        [lupapalvelu.document.model]
        [midje.sweet]))

(def uusi-rakennus
  (let [schema (schemas "uusiRakennus")
        data   (create-document-data schema dummy-values)]
    {:schema schema
     :data   data}))

(facts "VRK-validations"

  (fact "uusi rakennus is valid"
    uusi-rakennus => valid?)

  (fact "k\u00e4ytt\u00f6tarkoituksen mukainen maksimitilavuus"
    (-> uusi-rakennus
      (apply-update [:kaytto :kayttotarkoitus] "032 luhtitalot")
      (apply-update [:mitat :tilavuus] "100000")) => valid?
    (-> uusi-rakennus
      (apply-update [:kaytto :kayttotarkoitus] "032 luhtitalot")
      (apply-update [:mitat :tilavuus] "100001")) => (invalid-with? [:warn "vrk:ktark-tilavuus-max"]))

  (fact "Puutalossa saa olla korkeintaan 4 kerrosta"
    (-> uusi-rakennus
      (apply-update [:rakenne :kantavaRakennusaine] "puu")
      (apply-update [:mitat :kerrosluku] "3")) => valid?
    (-> uusi-rakennus
      (apply-update [:rakenne :kantavaRakennusaine] "puu")
      (apply-update [:mitat :kerrosluku] "5")) => (invalid-with? [:warn "vrk:BR106"]))

  (fact "Jos lammitustapa on 3 (sahkolammitys), on polttoaineen oltava 4 (sahko)"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:lammitys :lammonlahde] "s\u00e4hk\u00f6")) => valid?
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:lammitys :lammonlahde] "kaasu")) => (invalid-with? [:warn "vrk:CR343"]))

  (fact "Sahko polttoaineena vaatii sahkoliittyman"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammonlahde] "s\u00e4hk\u00f6")
      (apply-update [:verkostoliittymat :sahkoKytkin] true)) => valid?
    (-> uusi-rakennus
      (apply-update [:lammitys :lammonlahde] "s\u00e4hk\u00f6")
      (apply-update [:verkostoliittymat :sahkoKytkin] false)) => (invalid-with? [:warn "vrk:CR342"]))

  (fact "Sahkolammitus vaatii sahkoliittyman"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:verkostoliittymat :sahkoKytkin] true)) => (not-invalid-with? [:warn "vrk:CR341"])
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:verkostoliittymat :sahkoKytkin] false)) => (invalid-with? [:warn "vrk:CR341"]))

  (fact "Jos lammitystapa on 5 (ei kiinteaa lammitystapaa), ei saa olla polttoainetta"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "eiLammitysta")
      (apply-update [:lammitys :lammitystapa])) => valid?
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "eiLammitysta")
      (apply-update [:lammitys :lammonlahde] "kaasu")) => (invalid-with? [:warn "vrk:CR336"]))

)
