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

(defn- foreach-action [web user application data]
  (map
   #(let [{type :type categories :categories} (action/get-meta %)]
      (assoc
        (action/action % :type type :data data :user user)
        :application application
        :web web
        :categories categories))
    (remove nil? (keys (action/get-actions)))))

(defn- validated [command]
  {(:action command) (action/validate command)})

(defquery actions
  {:user-roles #{:admin}
   :description "List of all actions and their meta-data."} [_]
  (ok :actions (action/serializable-actions)))

(def- validate-actions
  (if (env/dev-mode?)
    (fn->> (map validated) (into {}))
    (fn->> (map validated) (filter (comp :ok first vals)) (into {}))))

(defn- action-has-category-fn [category]
  (fn [action]
    (->> action :categories (some #{category}) boolean)))

(defn- filter-actions-by-category [actions]
  (filter (action-has-category-fn :attachments) actions))

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
  (let [{:keys [attachments]} application]
    (->> (map (partial build-attachment-query-params application) attachments)
         (map (partial foreach-action web user application))
         (map filter-actions-by-category)
         (map validate-actions)
         (zipmap (map :id attachments)))))

(defquery allowed-actions
  {:user-roles       #{:anonymous}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [{:keys [web data user application]}]
  (ok :actions (->> (foreach-action web user application data)
                    (validate-actions))))

(defquery allowed-actions-for-category
  {:description      "Returns map of allowed actions for a category (attachments, tasks, etc.)"
   :user-roles       #{:anonymous}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles}
  [command]
  (if-let [actions-by-id (allowed-actions-for-category command)]
    (ok :actionsById actions-by-id)
    (fail :error.invalid-category)))
