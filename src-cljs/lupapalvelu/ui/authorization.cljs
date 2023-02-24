(ns lupapalvelu.ui.authorization
  (:require [lupapalvelu.ui.common :as common]))

(defn get-auth-model [category id state]
  (get-in state [:auth-models (keyword category) (keyword id)]))

(defn ok? [auth-model action]
  (if (object? auth-model)
    (.ok auth-model (name action))
    (get-in auth-model [(keyword action) :ok])))

(defn- set-auth-model-data [state category data]
  (swap! state assoc-in [:auth-models (keyword category)] (:actionsById data)))

(defn refresh-auth-models-for-category [state category]
  (common/query :allowed-actions-for-category
                (partial set-auth-model-data state category)
                :id (:applicationId @state)
                :category category)
  nil)

(defn application-auth? [action]
  (ok? js/lupapisteApp.models.applicationAuthModel action))

(defn attachment-auth? [attachment-or-id action]
  (when-let [auth-model (.getAuthModel js/lupapisteApp.services.attachmentsService
                                       (get attachment-or-id :id attachment-or-id))]
    (ok? auth-model action)))

(defn global-auth? [action]
  (ok? js/lupapisteApp.models.globalAuthModel action))
