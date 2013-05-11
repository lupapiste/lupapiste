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

  (fact "Puutalossa saa olla korkeintaan 4 kerrosta"
    (-> uusi-rakennus
      (apply-update [:rakenne :kantavaRakennusaine] "puu")
      (apply-update [:mitat :kerrosluku] "3")) => valid?
    (-> uusi-rakennus
      (apply-update [:rakenne :kantavaRakennusaine] "puu")
      (apply-update [:mitat :kerrosluku] "5")) => (invalid-with? [:warn "vrk:BR106"]))

  (fact "S\u00e4hk\u00f6 polttoaineena vaatii s\u00e4hk\u00f6liittym\u00e4n"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:lammitys :lammonlahde] "s\u00e4hk\u00f6")) => valid?
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:lammitys :lammonlahde] "kaasu")) => (invalid-with? [:warn "vrk:CR342"]))

  )

