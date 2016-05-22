(ns lupapalvelu.suti
  (:require [monger.operators :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.operations :as op]
            [lupapalvelu.user :as usr]))

(defn admin-org [admin]
  (-> admin
      usr/authority-admins-organization-id
      org/get-organization))

(defn update-operations [organization operations]
  (org/update-organization (:id organization)
                           {$set {:suti.operations (if (seq? operations)
                                                     operations
                                                     [])}}))
