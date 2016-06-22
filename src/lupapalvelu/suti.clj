(ns lupapalvelu.suti
  (:require [monger.operators :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr]))

(defn admin-org [admin]
  (-> admin
      usr/authority-admins-organization-id
      org/get-organization))

(defn add-operation [organization operation-id]
  (when-not (contains? (set (some-> organization :suti :operations)) operation-id)
    (org/update-organization (:id organization)
                             {$push {:suti.operations operation-id}})))

(defn remove-operation [organization operation-id]
  (when (contains? (set (some-> organization :suti :operations)) operation-id)
    (org/update-organization (:id organization)
                             {$pull {:suti.operations operation-id}})))
