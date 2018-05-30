(ns lupapalvelu.ui.pate.state
  (:refer-clojure :exclude [select-keys])
  (:require [lupapalvelu.ui.common :as common]
            [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def current-template        (state-cursor :current-template))
(def current-view            (state-cursor :current-view))
(def current-category        (state-cursor :current-category))
(def template-list           (state-cursor :template-list))
(def categories              (state-cursor :categories))
(def references              (state-cursor :references))
(def settings                (rum/cursor-in references [:settings]))
(def settings-info           (state-cursor :settings-info))
(def reviews                 (rum/cursor-in references [:reviews]))
(def plans                   (rum/cursor-in references [:plans]))
(def phrases                 (state-cursor :phrases))
(def application-id          (state-cursor :application-id))
(def current-verdict         (state-cursor :current-verdict))
(def current-verdict-id      (rum/cursor-in current-verdict [:info :id]))
(def verdict-list            (state-cursor :verdict-list))
(def replacement-verdict     (state-cursor :replacement-verdict))
(def allowed-verdict-actions (state-cursor :allowed-verdict-actions))

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


;; Convenience wrappers for the verdicts category allowed
;; actions. Since the actions are only used for ClojureScript we can
;; bypass the JavaScript auth models.

(defn refresh-verdict-auths
  ([app-id callback]
   ;; Not in service due to circular reqs.
   (common/query :allowed-actions-for-category
                 (fn [response]
                   (reset! allowed-verdict-actions (:actionsById response))
                   (when callback
                     (callback)))
                 :id app-id
                 :category :pate-verdicts))
  ([app-id] (refresh-verdict-auths app-id nil)))

(defn verdict-auth? [verdict-id action]
  (get-in @allowed-verdict-actions
          [(keyword verdict-id) (keyword action) :ok]))
