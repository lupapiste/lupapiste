(ns lupapalvelu.application-utils
  (:require [sade.strings :as ss]
            [lupapalvelu.organization :as org]))

(defn with-application-kind [{:keys [permitSubtype infoRequest] :as app}]
  (assoc app :kind (cond
                     (not (ss/blank? permitSubtype)) (str "permitSubtype." permitSubtype)
                     infoRequest "applications.inforequest"
                     :else       "applications.application")))

(defn with-organization-name [{:keys [organization] :as app}]
  (assoc app :organizationName (org/with-organization organization org/get-organization-name)))

(defn location->object [application]
  (let [[x y] (:location application)]
    (assoc application :location {:x x :y y})))
