(ns lupapalvelu.document.vrk
  (:use [lupapalvelu.clojure15]
        [lupapalvelu.document.validator])
  (:require [sade.util :refer [safe-int fn=>]]
            [clojure.string :as s]))

;;
;; da lib
;;

(defmacro defvalidator-old [validator-name doc bindings & body]
  `(swap! validators assoc (keyword ~validator-name)
     {:doc ~doc
      :fn (fn [~@bindings] (do ~@body))}))

(defn exists? [x] (-> x s/blank? not))

;;
;; Data
;;

(def kayttotarkoitus->tilavuus {:011 10000
                                :012 15000
                                :511 200000
                                :013 9000
                                :021 10000
                                :022 10000
                                :521 250000
                                :032 100000
                                :039 150000
                                :041 2500
                                :111 150000
                                :112 1500000
                                :119 1000000
                                :121 200000
                                :123 200000
                                :124 10000
                                :129 20000
                                :131 100000
                                :139 40000
                                :141 40000
                                :151 500000
                                :161 1000000
                                :162 1100000
                                :163 500000
                                :164 100000
                                :169 100000
                                :211 600000
                                :213 250000
                                :214 100000
                                :215 120000
                                :219 100000
                                :221 100000
                                :222 50000
                                :223 50000
                                :229 100000
                                :231 20000
                                :239 100000
                                :241 50000
                                :311 300000
                                :312 30000
                                :322 200000
                                :323 200000
                                :324 1500000
                                :331 50000
                                :341 50000
                                :342 50000
                                :349 25000
                                :351 200000
                                :352 200000
                                :353 260000
                                :354 800000
                                :359 300000
                                :369 300000
                                :531 700000
                                :532 200000
                                :541 100000
                                :549 100000
                                :611 1200000
                                :613 700000
                                :691 3000000
                                :692 800000
                                :699 2000000
                                :711 1700000
                                :712 1500000
                                :719 1000000
                                :721 50000
                                :722 250000
                                :723 50000
                                :729 100000
                                :811 50000
                                :819 50000
                                :891 500000
                                :892 200000
                                :893 100000
                                :899 40000
                                :931 4000
                                :941 50000
                                :999 50000})
;;
;; validators
;;

(defn ->kayttotarkoitus [x]
  (some->> x (re-matches #"(\d+) .*") last keyword ))

(defvalidator-old "vrk:CR327"
  "k\u00e4ytt\u00f6tarkoituksen mukainen maksimitilavuus"
  [{{{schema-name :name} :info} :schema data :data}]
  (when (= schema-name "uusiRakennus")
    (let [kayttotarkoitus (some->> data :kaytto :kayttotarkoitus :value ->kayttotarkoitus)
          tilavuus        (some->> data :mitat :tilavuus :value safe-int)
          max-tilavuus    (kayttotarkoitus->tilavuus kayttotarkoitus)]
      (when (and tilavuus max-tilavuus (> tilavuus max-tilavuus))
        [{:path[:kaytto :kayttotarkoitus]
          :result [:warn "vrk:CR327"]}
         {:path[:mitat :tilavuus]
          :result [:warn "vrk:CR327"]}]))))

(defvalidator-old "vrk:BR106"
  "Puutalossa saa olla korkeintaan 4 kerrosta"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :rakenne :kantavaRakennusaine :value (= "puu"))
      (some-> data :mitat :kerrosluku :value safe-int (> 4)))
    [{:path[:rakenne :kantavaRakennusaine]
      :result [:warn "vrk:BR106"]}
     {:path[:mitat :kerrosluku]
      :result [:warn "vrk:BR106"]}]))

(defvalidator-old "vrk:CR343"
  "Jos lammitustapa on 3 (sahkolammitys), on polttoaineen oltava 4 (sahko)"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :lammitys :lammitystapa :value (= "suorasahk\u00f6"))
      (some-> data :lammitys :lammonlahde :value (not= "s\u00e4hk\u00f6")))
    [{:path [:lammitys :lammitystapa]
      :result [:warn "vrk:CR343"]}
     {:path [:lammitys :lammonlahde]
      :result [:warn "vrk:CR343"]}]))

(defvalidator-old "vrk:CR342"
  "Sahko polttoaineena vaatii sahkoliittyman"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :lammitys :lammonlahde :value (= "s\u00e4hk\u00f6"))
      (some-> data :verkostoliittymat :sahkoKytkin :value not))
    [{:path [:lammitys :lammonlahde]
      :result [:warn "vrk:CR342"]}
     {:path [:verkostoliittymat :sahkoKytkin]
      :result [:warn "vrk:CR342"]}]))

(defvalidator-old "vrk:CR341"
  "Sahkolammitus vaatii sahkoliittyman"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :lammitys :lammitystapa :value (= "suorasahk\u00f6"))
      (some-> data :verkostoliittymat :sahkoKytkin :value not))
    [{:path [:lammitys :lammitystapa]
      :result [:warn "vrk:CR341"]}
     {:path [:verkostoliittymat :sahkoKytkin]
      :result [:warn "vrk:CR341"]}]))

(defvalidator-old "vrk:CR336"
  "Jos lammitystapa on 5 (ei kiinteaa lammitystapaa), ei saa olla polttoainetta"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :lammitys :lammitystapa :value (= "eiLammitysta"))
      (some-> data :lammitys :lammonlahde :value exists?))
    [{:path [:lammitys :lammitystapa]
      :result [:warn "vrk:CR336"]}
     {:path [:lammitys :lammonlahde]
      :result [:warn "vrk:CR336"]}]))

(defvalidator-old "vrk:CR335"
  "Jos lammitystapa ei ole 5 (ei kiinteaa lammitystapaa), on polttoaine ilmoitettava"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :lammitys :lammitystapa :value exists?)
      (some-> data :lammitys :lammitystapa :value (not= "eiLammitysta"))
      (some-> data :lammitys :lammonlahde :value exists? not))
    [{:path [:lammitys :lammitystapa]
      :result [:warn "vrk:CR335"]}
     {:path [:lammitys :lammonlahde]
      :result [:warn "vrk:CR335"]}]))

;;
;; new stuff
;;

(defvalidator :vrk:CR326
  {:doc    "Kokonaisalan oltava vahintaan kerrosala"
   :schema "uusiRakennus"
   :fields [kokonaisala [:mitat :kokonaisala safe-int]
            kerrosala   [:mitat :kerrosala safe-int]]}
  (and kokonaisala kerrosala (> kerrosala kokonaisala)))

(defvalidator :vrk:CR324
  {:doc    "Sahko polttoaineena vaatii varusteeksi sahkon"
   :schema "uusiRakennus"
   :fields [polttoaine [:lammitus :lammonlahde]
            sahko      [:varusteet :sahkoKytkin]]}
  (and (= polttoaine "s\u00e4hk\u00f6") (not= sahko true)))

(defvalidator :vrk:CR322
  {:doc    "Uuden rakennuksen kokonaisalan oltava vahintaan huoneistoala"
   :schema "uusiRakennus"
   :fields [kokonaisala [:mitat :kokonaisala safe-int]
            huoneistot  [:huoneistot]]}
  (let [huoneistoala (reduce + (map (fn=> second :huoneistonTyyppi :huoneistoala safe-int) huoneistot))]
    (and kokonaisala huoneistoala (< kokonaisala huoneistoala))))

(defvalidator :vrk:CR320
  {:doc    "Jos kayttotarkoitus on 011 - 022, on kerrosluvun oltava valilla 1 - 4"
   :schema "uusiRakennus"
   :fields [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus]
            kerrosluku      [:mitat :kerrosluku safe-int]]}
  (and (#{:011 :012 :013 :021 :022} kayttotarkoitus) (> kerrosluku 4)))

(defvalidator :vrk:CR328:sahko
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Sahko"
   :schema "uusiRakennus"
   :fields [liittyma [:verkostoliittymat :sahkoKytkin]
            varuste  [:varusteet         :sahkoKytkin]]}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR328:viemari
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Viemari"
   :schema "uusiRakennus"
   :fields [liittyma [:verkostoliittymat :viemariKytkin]
            varuste  [:varusteet         :viemariKytkin]]}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR328:vesijohto
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Vesijohto"
   :schema "uusiRakennus"
   :fields [liittyma [:verkostoliittymat :vesijohtoKytkin]
            varuste  [:varusteet         :vesijohtoKytkin]]}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR312
  {:doc     "Jos rakentamistoimenpide on 691 tai 111, on kerrosluvun oltava 1"
   :schema  "uusiRakennus"
   :fields  [toimenpide [:kaytto :kayttotarkoitus ->kayttotarkoitus]
             kerrosluku [:mitat :kerrosluku safe-int]]}
  (and (#{:691 :111} toimenpide) (not= kerrosluku 1)))
