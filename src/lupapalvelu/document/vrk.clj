(ns lupapalvelu.document.vrk
  (:use [lupapalvelu.clojure15])
  (:require [sade.util :refer [safe-int]]))

(defn validate
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
