(ns lupapalvelu.ui.pate.state
  (:refer-clojure :exclude [select-keys])
  (:require [clojure.string :as s]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.common :as common]
            [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def current-template          (state-cursor :current-template))
(def current-view              (state-cursor :current-view))
(def current-category          (state-cursor :current-category))
(def org-id                    (state-cursor :org-id))
(def template-list             (state-cursor :template-list))
(def categories                (state-cursor :categories))
(def references                (state-cursor :references))
(def settings                  (rum/cursor-in references [:settings]))
(def settings-info             (state-cursor :settings-info))
(def reviews                   (rum/cursor-in references [:reviews]))
(def plans                     (rum/cursor-in references [:plans]))
(def phrases                   (state-cursor :phrases))
(def application-id            (state-cursor :application-id))
(def current-verdict           (state-cursor :current-verdict))
(def current-verdict-id        (rum/cursor-in current-verdict [:info :id]))
(def verdict-tags              (rum/cursor-in current-verdict [:tags]))
(def verdict-attachment-ids    (rum/cursor-in current-verdict [:attachment-ids]) )
(def verdict-list              (state-cursor :verdict-list))
(def replacement-verdict       (state-cursor :replacement-verdict))
(def allowed-verdict-actions   (state-cursor :allowed-verdict-actions))
;; Wait state for verdict publishing. True when waiting.
(def verdict-wait?             (state-cursor :verdict-wait?))
(def custom-phrases-categories (state-cursor :custom-phrases-categories))
(def appeals                   (state-cursor :appeals))

;; ok function of the currently active authModel.
(defonce auth-fn (atom nil))

(defn select-keys [state ks]
  (reduce (fn [acc k]
            (assoc acc k (rum/cursor-in state [k])))
          {}
          ks))

(defn auth? [action]
  (boolean (when-let [auth @auth-fn]
             (auth (name action)))))

(defn refresh-application-auth-model
  "Refreshes application auth model and resets auth-fn accordingly. Also
  resets application-id. Callback function is called, if given."
  ([app-id callback]
   (reset! auth-fn nil)
   (reset! application-id app-id)
   (js/lupapisteApp.models.applicationAuthModel.refreshWithCallback (clj->js {:id app-id})
                                                                    (fn []
                                                                      (reset! auth-fn
                                                                              js/lupapisteApp.models.applicationAuthModel.ok)
                                                                      (when callback (callback)))))
  ([app-id]
   (refresh-application-auth-model app-id nil)))

(defn application-model-updated-mixin
  "Refreshes auth model after the applicaiton model has been updated."
  []
  (rum-util/hubscribe "application-model-updated"
                      {}
                      (fn [state]
                        (refresh-application-auth-model @application-id nil)
                        state)))

;; Convenience wrappers for the verdicts category allowed
;; actions. Since the actions are only used for ClojureScript we can
;; bypass the JavaScript auth models.

(defn verdict-auth? [verdict-id action]
  (get-in @allowed-verdict-actions
          [(keyword verdict-id) (keyword action) :ok]))

(defn react-verdict-auth? [verdict-id action]
  (rum/react (rum/cursor-in allowed-verdict-actions
                            [(keyword verdict-id) (keyword action) :ok])))

(defn- update-allowed-if-needed [verdict-id new-actions]
  (let [ok-keys-fn #(->> %
                         (map (fn [[k v]]
                                (when (:ok v) k)))
                         (remove nil?)
                         set)
        olds (ok-keys-fn (verdict-id @allowed-verdict-actions))
        news (ok-keys-fn new-actions)]
    (when (not= olds news)
      (swap! allowed-verdict-actions
             assoc
             verdict-id new-actions))))

(defn refresh-verdict-auths
  ([app-id {:keys [callback verdict-id]}]
   ;; Not in service due to circular reqs.
   (let [verdict-id (when-not (s/blank? verdict-id) (keyword verdict-id))
         query-fn   (partial common/query :allowed-actions-for-category
                             (fn [{actions :actionsById}]
                               (if verdict-id
                                 (update-allowed-if-needed verdict-id (verdict-id actions))
                                 (reset! allowed-verdict-actions actions))

                               (when (and verdict-id (= verdict-id @current-verdict-id))
                                 (common/reset-if-needed! (rum/cursor-in current-verdict
                                                                         [:_meta :enabled?])
                                                          (verdict-auth? verdict-id
                                                                         :edit-pate-verdict)))
                               (when callback
                                 (callback)))
                             :id app-id
                             :category :pate-verdicts)]
     (if verdict-id
       (query-fn :verdict-id verdict-id)
       (query-fn))))
  ([app-id] (refresh-verdict-auths app-id nil)))
