(ns lupapalvelu.actions-api
  (:require [clojure.set :refer [difference]]
            [sade.env :as env]
            [sade.core :refer :all]
            [sade.util :refer [fn->]]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.authorization :as auth]))

;;
;; Default actions
;;

(defn- foreach-action [web user application data]
  (map
    #(let [{type :type} (action/get-meta %)]
      (assoc
        (action/action % :type type :data data :user user)
        :application application
        :web web))
    (remove nil? (keys (action/get-actions)))))

(defn- validated [command]
  {(:action command) (action/validate command)})

(defquery actions
  {:user-roles #{:admin}
   :description "List of all actions and their meta-data."} [_]
  (ok :actions (action/serializable-actions)))

(defn- get-allowed-actions [web user application data]
  (let [results  (->> (foreach-action web user application data)
                      (map validated))
        filtered (if (env/dev-mode?)
                   results
                   (filter (comp :ok first vals) results))]
    (into {} filtered)))

(defmulti allowed-actions-for-category (fn-> :data :category keyword))

(defmethod allowed-actions-for-category :default
  [_]
  nil)

(defn- build-attachment-query-params [{application-id :id} {attachment-id :id latest-version :latestVersion}]
  {:id           application-id
   :attachmentId attachment-id
   :fileId       latest-version})

(defmethod allowed-actions-for-category :attachments
  [{:keys [web user application]}]
  (->> (:attachments application)
       (map (partial build-attachment-query-params application))
       (map (partial get-allowed-actions web user application))
       (zipmap (map :id (:attachments application)))))

(defquery allowed-actions
  {:user-roles       #{:anonymous}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{:keys [web data user application]}]
  (ok :actions (get-allowed-actions web user application data)))

(defquery allowed-actions-for-category
  {:description      "Returns map of allowed actions for a category (attachments, tasks, etc.)"
   :user-roles       #{:anonymous}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [command]
  (if-let [actions-by-id (allowed-actions-for-category command)]
    (ok :actionsById actions-by-id)
    (fail :error.invalid-category)))
