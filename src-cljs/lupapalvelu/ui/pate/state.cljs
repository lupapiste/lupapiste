(ns lupapalvelu.ui.pate.state
  (:refer-clojure :exclude [select-keys])
  (:require [clojure.string :as s]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [lupapalvelu.pate.schema-helper :as helper]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def current-template          (state-cursor :current-template))
(def current-view              (state-cursor :current-view))
(def current-category          (state-cursor :current-category))
(def phrase-categories         (rum-util/derived-atom [current-category]
                                 (fn [category]
                                   (-> category
                                       (shared-schemas/phrase-categories-by-template-category)))))
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
(def application-state         (state-cursor :application-state))
(def current-verdict           (state-cursor :current-verdict))
(def current-verdict-id        (rum/cursor-in current-verdict [:info :id]))
(def current-verdict-meta      (rum/cursor-in current-verdict [:_meta]))
(def current-verdict-schema    (rum-util/derived-atom [current-verdict]
                                 (fn [{:keys [info]}]
                                   (when-let [category (:category info)]
                                     (let [{:keys [schema-version legacy?]} info]
                                       (if legacy?
                                         (legacy/legacy-verdict-schema category)
                                         (verdict-schemas/verdict-schema category schema-version)))))))
(def open-section-ids          (rum-util/derived-atom [current-verdict-schema current-verdict-meta]
                                 (fn [schema _meta]
                                   (->> (:sections schema)
                                        (remove #(= (:buttons? %) false))
                                        (map :id)
                                        (filter #(get _meta (util/kw-path % :editing?)))))))
(def verdict-tags              (rum/cursor-in current-verdict [:tags]))
(def verdict-attachment-ids    (rum/cursor-in current-verdict [:attachment-ids]))
(def verdict-list              (state-cursor :verdict-list))
(def replacement-verdict       (state-cursor :replacement-verdict))
(def allowed-verdict-actions   (state-cursor :allowed-verdict-actions))
;; Wait state for verdict publishing. True when waiting.
(def verdict-wait?             (state-cursor :verdict-wait?))
(def custom-phrase-categories  (state-cursor :custom-phrase-categories))
(def appeals                   (state-cursor :appeals))
(def verdict-sent?             (state-cursor :verdict-sent))
(def verdict-recipient         (state-cursor :verdict-recipient))
(def current-verdict-state     (rum/cursor-in current-verdict [:info :state]))
(def verdict-bulletins         (state-cursor :verdict-bulletins))
(def current-allowed-actions   (rum-util/derived-atom [allowed-verdict-actions current-verdict-id]
                                 (fn [actions verdict-id]
                                   (get actions (keyword verdict-id)))))

;; ok function of the currently active authModel.
(defonce auth-fn (atom nil))

(def generating-verdict? (rum-util/derived-atom [current-verdict-state]
                           (fn [verdict-state]
                             (= verdict-state helper/publishing-verdict))))

(def generating-proposal? (rum-util/derived-atom [current-verdict-state]
                            (fn [verdict-state]
                              (= verdict-state helper/publishing-proposal))))

(defn select-keys [state ks]
  (reduce (fn [acc k]
            (assoc acc k (rum/cursor-in state [k])))
          {}
          ks))

(defn auth? [action]
  (boolean (when-let [auth @auth-fn]
             (auth (name action)))))

(defn set-meta-enabled [flag]
  (swap! current-verdict-meta assoc :enabled? flag))

(defn close-open-sections []
  (when-let [open-ones (seq @open-section-ids)]
    (swap! current-verdict-meta
           (fn [meta]
             (->> open-ones
                  (map
                    (fn [section-id]
                      [(util/kw-path section-id :editing?)
                       false]))
                  (into {})
                  (merge meta))))))

(defn toggle-sections []
  (let [open-ones @open-section-ids]
    (swap! current-verdict-meta
           (fn [m]
             (->> (or (not-empty open-ones)
                      (map :id (:sections @current-verdict-schema)))
                  (map (fn [id]
                         [(util/kw-path id :editing?)
                          (empty? open-ones)]))
                  (into {})
                  (merge m))))))

(defn reset-application-id [id]
  (reset! application-id id))

(defn refresh-application-auth-model
  "Refreshes application auth model and resets auth-fn accordingly.
   Callback function is called, if given."
  ([app-id callback]
   (reset! auth-fn nil)
   (js/lupapisteApp.models.applicationAuthModel.refreshWithCallback
     (clj->js {:id app-id})
     (fn []
       (reset! auth-fn
               js/lupapisteApp.models.applicationAuthModel.ok)
       (when callback (callback)))))
  ([app-id]
   (refresh-application-auth-model app-id nil)))

;; Convenience wrappers for the verdicts category allowed
;; actions. Since the actions are only used for ClojureScript we can
;; bypass the JavaScript auth models.

(defn verdict-auth? [verdict-id action]
  (boolean (get-in @allowed-verdict-actions
                   [(keyword verdict-id) (keyword action) :ok])))

(defn react-verdict-auth? [verdict-id action]
  (boolean (rum/react (rum/cursor-in allowed-verdict-actions
                                     [(keyword verdict-id) (keyword action) :ok]))))

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
                                 (common/reset-if-needed! (rum/cursor-in current-verdict-meta [:enabled?])
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

(defn application-model-updated-mixin
  "Refreshes auth models after the application model has been updated."
  []
  (rum-util/hubscribe "application-model-updated"
                      {}
                      (fn [state]
                        (refresh-application-auth-model @application-id nil)
                        (refresh-verdict-auths @application-id nil)
                        state)))
