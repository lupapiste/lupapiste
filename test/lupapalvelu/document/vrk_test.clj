(ns lupapalvelu.document.vrk-test
  (:use [lupapalvelu.document.tools]
        [lupapalvelu.document.schemas]
        [lupapalvelu.document.validators]
        [lupapalvelu.document.model]
        [midje.sweet]
        [sade.util])
  (:require [lupapalvelu.document.validator :as v]
            [clojure.string :as s]))

(defn check-validator
  "Runs generated facts of a single validator."
  [{:keys [code doc schema level paths] validate-fn :fn {:keys [ok fail]} :facts}]
  (when (and ok fail)
    (let [dummy  (dummy-doc schema)
          doc    (s/replace doc #"\s+" " ")
          update (fn [values]
                   (reduce
                     (fn [d i]
                       (apply-update d (get paths i) (get values i)))
                     dummy
                     (range 0 (count paths))))]

      (facts "Embedded validator facts"
        (println "Checking:" doc)
        (doseq [values ok]
          (validate-fn (update values)) => nil?)
        (doseq [values fail]
          (validate-fn (update values)) => (has some (contains {:result [level (name code)]})))))))

(defn check-all-validators []
  (let [validators (->> v/validators deref vals (filter (fn-> :facts nil? not)))]
    (println "Checking" (str (count validators) "/" (count @v/validators)) "awesome validators!")
    (doseq [validator validators]
      (check-validator validator))))

(facts "Embedded validator facts"
  (check-all-validators))

;; TODO: validate just one validator at a time to reduce hassle from side-effects

(def uusi-rakennus
  (->
    "uusiRakennus"
    dummy-doc
    (apply-update [:mitat :tilavuus] "6")))

(comment "old validations"
  (facts "VRK-validations"

    (fact "uusi rakennus is valid"
      uusi-rakennus => valid?)

    (fact "k\u00e4ytt\u00f6tarkoituksen mukainen maksimitilavuus"
      (-> uusi-rakennus
        (apply-update [:kaytto :kayttotarkoitus] "032 luhtitalot")
        (apply-update [:mitat :tilavuus] "100000")) => valid?
      (-> uusi-rakennus
        (apply-update [:kaytto :kayttotarkoitus] "032 luhtitalot")
        (apply-update [:mitat :tilavuus] "100001")) => (invalid-with? [:warn "vrk:CR327"]))

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

    (fact "Kokonaisalan oltava vahintaan kerrosala"
      (-> uusi-rakennus
        (apply-update [:mitat :kerrosala] "4")
        (apply-update [:mitat :kokonaisala] "4")) => valid?
      (-> uusi-rakennus
        (apply-update [:mitat :kerrosala] "6")
        (apply-update [:mitat :kokonaisala] "4")) => (invalid-with? [:warn "vrk:CR326"]))

    (fact "Sahko polttoaineena vaatii varusteeksi sahkon"
      (-> uusi-rakennus
        (apply-update [:lammitus :lammonlahde] "s\u00e4hk\u00f6")
        (apply-update [:varusteet :sahkoKytkin] true)) => (not-invalid-with? [:warn "vrk:CR324"])
      (-> uusi-rakennus
        (apply-update [:lammitus :lammonlahde] "s\u00e4hk\u00f6")
        (apply-update [:varusteet :sahkoKytkin] false)) => (invalid-with? [:warn "vrk:CR324"]))))
