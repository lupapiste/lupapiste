(ns lupapalvelu.suti
  (:require [monger.operators :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr]))

(defn admin-org [admin]
  (-> admin
      usr/authority-admins-organization-id
      org/get-organization))

(defn toggle-enable [organization-id flag]
  (org/update-organization organization-id
                           {$set {:suti.enabled flag}}))

(defn toggle-operation [organization operation-id flag]
  (let [already (contains? (-> organization :suti :operations set) operation-id)]
    (when (not= (boolean already) (boolean flag))
      (org/update-organization (:id organization)
                               {(if flag $push $pull) {:suti.operations operation-id}}))))

(defn toggle-section-operation [organization operation-id flag]
  (let [already (contains? (-> organization :section-operations set) operation-id)]
    (when (not= (boolean already) (boolean flag))
      (org/update-organization (:id organization)
                               {(if flag $push $pull) {:section-operations operation-id}}))))

(defn organization-details [{{server :server :as suti} :suti}]
  (-> suti
      (select-keys [:enabled :www])
      (assoc :server (select-keys server [:url :username]))))

(defn set-www [organization www]
  (org/update-organization (:id organization) {$set {:suti.www (ss/trim www)}}))
