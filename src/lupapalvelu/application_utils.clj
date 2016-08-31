(ns lupapalvelu.application-utils
  (:require [sade.strings :as ss]))

(defn with-application-kind [{:keys [permitSubtype infoRequest] :as app}]
  (assoc app :kind (cond
                     (not (ss/blank? permitSubtype)) (str "permitSubtype." permitSubtype)
                     infoRequest "applications.inforequest"
                     :else       "applications.application")))

(defn location->object [application]
  (let [[x y] (:location application)]
    (assoc application :location {:x x :y y})))
