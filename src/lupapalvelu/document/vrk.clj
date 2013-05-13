(ns lupapalvelu.document.vrk
  (:use [lupapalvelu.clojure15])
  (:require [sade.util :refer [safe-int]]))

;;
;; da lib
;;

(def validators (atom {}))

(defmacro defvalidator [validator-name doc-string bindings & body]
  `(swap! validators assoc (keyword ~validator-name)
     {:doc ~doc-string
      :fn (fn [~@bindings] ~@body)}))

(defn validate
  "Runs all validators, returning list of validation results."
  [document]
  (->>
    validators
    deref
    vals
    (map :fn)
    (map #(apply % [document]))
    (filter (comp not nil?))))

;;
;; validators
;;

(defvalidator "vrk:BR106"
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

(defvalidator "vrk:CR342"
  "S\u00e4hk\u00f6 polttoaineena vaatii s\u00e4hk\u00f6liittym\u00e4n"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :lammitys :lammitystapa :value (= "suorasahk\u00f6"))
      (some-> data :lammitys :lammonlahde :value (not= "s\u00e4hk\u00f6")))
    [{:path[:lammitys :lammitystapa]
      :result [:warn "vrk:CR342"]}
     {:path[:lammitys :lammonlahde]
      :result [:warn "vrk:CR342"]}]))

#_(defvalidator "vrk:ktark-tilavuus-max"
  "Maksimi tilavuus per k\u00e4ytt\u00f6tarkoitus"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :lammitys :lammitystapa :value (= "suorasahk\u00f6"))
      (some-> data :lammitys :lammonlahde :value (not= "s\u00e4hk\u00f6")))
    [{:path[:lammitys :lammitystapa]
      :result [:warn "vrk:CR342"]}
     {:path[:lammitys :lammonlahde]
      :result [:warn "vrk:CR342"]}]))

#_  (let [m {:011 10000
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
           :999 50000}]
