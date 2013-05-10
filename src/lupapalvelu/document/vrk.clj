(ns lupapalvelu.document.vrk
  (:use [lupapalvelu.clojure15])
  (:require [sade.util :refer [safe-int]]))

(def validators (atom {}))

(defmacro defvalidator [validator-name doc-string bindings & body]
  `(swap! validators assoc (keyword ~validator-name)
     {:doc ~doc-string
      :fn  (fn [~@bindings] ~@body)}))

(defvalidator "BR102"
  "puutalossa ei voi olla kuin nelja kerrosta"
  [x]
  x)

(defvalidator "BR106"
  "puutalossa ei voi olla kuin nelja kerrosta"
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

(defn validate
  "Runs all validators"
  [document]
  (->> validators
    deref
    (map :fn)
    (map #(partial apply document))))
