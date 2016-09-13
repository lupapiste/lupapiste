(ns lupapalvelu.actions-api
  (:require [clojure.set :refer [difference]]
            [sade.env :as env]
            [sade.core :refer :all]
            [sade.util :refer [fn-> fn->>]]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.authorization :as auth]))

;;
;; Default actions
;;

(defquery actions
  {:user-roles #{:admin}
   :description "List of all actions and their meta-data."} [_]
  (ok :actions (action/serializable-actions)))

(defquery allowed-actions
  {:user-roles       #{:anonymous}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{:keys [web data user application]}]
  (ok :actions (->> (action/foreach-action web user application data)
                    (action/validate-actions))))

(defquery allowed-actions-for-category
  {:description      "Returns map of allowed actions for a category (attachments, tasks, etc.)"
   :user-roles       #{:anonymous}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [command]
  (if-let [actions-by-id (action/allowed-actions-for-category command)]
    (ok :actionsById actions-by-id)
    (fail :error.invalid-category)))
