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
      :fn  (fn [~@bindings] ~@body)}))

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
    [{:path    [:rakenne :kantavaRakennusaine]
      :result  [:warn "vrk:BR106"]}
     {:path    [:mitat :kerrosluku]
      :result  [:warn "vrk:BR106"]}]))

(defvalidator "vrk:CR342"
  "S\u00e4hk\u00f6 polttoaineena vaatii s\u00e4hk\u00f6liittym\u00e4n"
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :lammitys :lammitystapa :value (= "suorasahk\u00f6"))
      (some-> data :lammitys :lammonlahde :value (not= "s\u00e4hk\u00f6")))
    [{:path    [:lammitys :lammitystapa]
      :result  [:warn "vrk:CR342"]}
     {:path    [:lammitys :lammonlahde]
      :result  [:warn "vrk:CR342"]}]))
