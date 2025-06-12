(ns lupapalvelu.ui.location.bim-viewer.viewer-subs
  "Re-frame subscriptions and events for the BIM viewer"
  (:require [clojure.set :as set]
            [goog.object :as googo]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.common.job :as job]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.location.bim-viewer.viewer-config :as bim-viewer-config]
            [re-frame.core :as rf]
            [sade.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Subscriptions

(rf/reg-sub ::plugins :-> ::plugins) ; holds xeokit-sdk plugin instances

(rf/reg-sub ::values :-> ::values) ; holds UI component state

(rf/reg-sub ::measuring-mode?
  :<- [::values]
  :-> :measuring-mode?)

(rf/reg-sub ::picking-mode?
  :<- [::values]
  :-> :picking-mode?)

(rf/reg-sub ::fullscreen?
  :<- [::values]
  :-> :fullscreen?)

(rf/reg-sub ::hovered-entity-id
  :<- [::values]
  :-> :hovered-entity-id)

(rf/reg-sub ::viewer
  :<- [::plugins]
  :-> :viewer)

(rf/reg-sub ::scene
  :<- [::viewer]
  :-> #(some-> % .-scene))

(rf/reg-sub ::meta-scene
  :<- [::viewer]
  :-> #(some-> % .-metaScene))

(rf/reg-sub ::models
  :<- [::plugins]
  :-> :models)

(rf/reg-sub ::tree-view
  :<- [::plugins]
  :-> :tree-view)

(rf/reg-sub ::measurements-control
  :<- [::plugins]
  :-> :measurements-control)

(rf/reg-sub ::tree-view-open?
  :<- [::values]
  :-> :tree-view-open?)

(rf/reg-sub ::selection-details-open?
  :<- [::values]
  :-> :selection-details-open?)

(rf/reg-sub ::selected-entity-id
  :<- [::values]
  :-> :selected-entity-id)

(rf/reg-sub ::selected-entity
  :<- [::selected-entity-id]
  :<- [::meta-scene]
  (fn [[entity-id meta-scene]]
    (some-> meta-scene .-metaObjects (googo/get entity-id))))

(rf/reg-sub ::selected-tree-hierarchy
  :<- [::values]
  (fn [values]
    (or (:selected-tree-hierarchy values)
        bim-viewer-config/default-hierarchy)))

(rf/reg-sub ::xkt-status
  :<- [::values]
  :-> :xkt-status)

(rf/reg-sub ::file-size-error
  :<- [::xkt-status]
  (fn [{:keys [text max-size]} _]
    (when (and (= text "ifc-file-too-big")
               (int? max-size))
      max-size)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events

(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ load-model-fn {:keys [ifc-version-ids default-values] :as ifc-info} plugins]]
    {:fx [[:dispatch (if ifc-version-ids
                       [::load-xkt-models ifc-version-ids]
                       [::fetch-xkt-model-info])]]
     :db (-> db
             (assoc ::plugins plugins)
             (update ::values merge default-values)
             (assoc-in [::values :load-model-fn] load-model-fn)
             (update-in [::values :ifc-info] merge ifc-info))}))

(rf/reg-event-fx ::clear-init
  (fn [{:keys [db]} _]
    {:fx [[:fn! {:func   #(some-> % .destroy) ; destroying viewer destroys connected plugins as well
                 :params [(-> db ::plugins :viewer)]}]]
     :db (dissoc db ::plugins ::values)}))

(rf/reg-event-db ::model-loaded
  (fn [db [_ model]]
    (update-in db [::plugins :models] conj model)))

(rf/reg-event-db ::set-mode?
  (fn [db [_ mode-key value]]
    (cond-> db
      true                                          (assoc-in [::values mode-key] value)
      (and (not value) (= mode-key :picking-mode?)) (util/dissoc-in [::values :selected-entity-id]))))

(rf/reg-event-db ::set-hovered-entity-id
  (fn [db [_ entity-id]]
    (if (nil? entity-id)
      (util/dissoc-in db [::values :hovered-entity-id])
      (assoc-in db [::values :hovered-entity-id] entity-id))))

(rf/reg-event-db ::set-selected-entity-id
  (fn [db [_ entity-id]]
    (assoc-in db [::values :selected-entity-id] entity-id)))

(defn- set-fullscreen
  [{:keys [db]} [_ enable?]]
  {:db (update db ::values assoc :fullscreen? enable? :tree-view-open? enable?)})

(defn- run-fullscreen-exit-callback
  [{:keys [db]} _]
  (when-let [exit-fn (some-> db ::values :fullscreen-exit-fn)]
    {:fx [[:fn! {:func   exit-fn
                 :params []}]]}))

(rf/reg-event-fx ::toggle-fullscreen
  (fn [{:keys [db] :as cofx}]
    (if (get-in db [::values :fullscreen?])
      (nutil/merge-every-fn cofx [[set-fullscreen false]
                                  [run-fullscreen-exit-callback]
                                  [hub/unsubscribe-fx ::fullscreen-quit-sub]])
      (nutil/merge-every-fn cofx [[set-fullscreen true]
                                  ;; ESC causes `keyup` -> `dialog-close` hub events,
                                  ;; see `resources/private/common/app.js`
                                  [hub/subscribe-fx
                                   "dialog-close"
                                   (fn [] (>evt [::toggle-fullscreen]))
                                   ::fullscreen-quit-sub]]))))

(rf/reg-event-db ::toggle-tree-view
  (fn [db _]
    (update-in db [::values :tree-view-open?] not)))

(rf/reg-event-db ::toggle-selection-details
  (fn [db _]
    (update-in db [::values :selection-details-open?] not)))

(rf/reg-event-db ::set-selected-tree-hierarchy
  (fn [db [_ value]]
    (assoc-in db [::values :selected-tree-hierarchy] value)))

(rf/reg-event-fx ::fetch-xkt-model-info
  (fn [{:keys [db]} _]
    (let [params (-> (get-in db [::values :ifc-info])
                     (select-keys [:application-id :ifc-version-id :ifc-object-key])
                     (set/rename-keys {:application-id :id}))]
      (when (every? identity ((juxt :ifc-version-id :ifc-object-key :id) params))
        {:action/query {:name    :xkt-file-info
                        :params  params
                        :success [::fetch-xkt-model-info.success]
                        :error   [::fetch-xkt-model-info.error]}}))))

(rf/reg-event-db ::fetch-xkt-model-info.error
  (fn [db [_ resp]]
    (assoc-in db [::values :xkt-status] (select-keys resp [:text :max-size]))))

(rf/reg-event-fx ::load-xkt-model
  (fn [{:keys [db]} [_ ifc-version-id ifc-object-key]]
    (let [prev-ifc-object-key (get-in db [::values :ifc-info :ifc-object-key])]
      (when-not (= prev-ifc-object-key ifc-object-key)
        {:db (-> db
                 (assoc-in [::values :ifc-info :ifc-version-id] ifc-version-id)
                 (assoc-in [::values :ifc-info :ifc-object-key] ifc-object-key))
         :fx [[:dispatch [::fetch-xkt-model-info ifc-version-id ifc-object-key]]]}))))

(defn- model-loading-fx
  "Returns a list of :fx operations for clearing the old loaded models out and loading the new ones instead.
  This causes an error \"[xeokit plugin TreeViewPlugin]: Failed to build storeys hierarchy\" when deleting a model.
  The bug seems to be in the xeokit SDK TreeViewPlugin and xeokit's own viewer at xeo.vision
  seems to suffer from the same error. The feature, however, works fine."
  [{{old-models :models} ::plugins {:keys [load-model-fn ifc-info]} ::values} ifc-version-ids]
  (->> ifc-version-ids
       ;; Adds new models
       (map (fn [ifc-version-id]
              [:fn! {:func   load-model-fn
                     :params [{:application-id (:application-id ifc-info)
                               :ifc-version-id ifc-version-id}]}]))
       ;; Destroy previously loaded model(s)
       (into [(when (seq old-models)
                [:fn! {:func   #(doseq [model %]
                                  (.destroy model))
                       :params [old-models]}])])))

(rf/reg-event-fx ::load-xkt-models
  ;; Bypasses ::fetch-xkt-model-info and loads the models directly since unviewable models
  ;; should never get to this stage (and just results in error if for some reason they are)
  (fn [{:keys [db]} [_ ifc-version-ids]]
    {:db (assoc-in db [::values :xkt-status] "available")
     :fx (model-loading-fx db ifc-version-ids)}))

(defn- job-polling-callback-fn
  "If a job is found on loading the page (or one has been started by the user) that is currently generating
  the XKT file, poll the job until it is done and then reload the XKT file as the model for the BIM viewer"
  [_ updates]
  (when (-> updates :xkt-generation :status (= "done"))
    (>evt [::fetch-xkt-model-info])))

(rf/reg-event-fx ::fetch-xkt-model-info.success
  (fn [{:keys [db]} [_ {:keys [status job-id]}]]
    {:db (assoc-in db [::values :xkt-status] status)
     :fx (case status
           "available"  (model-loading-fx db [(-> db ::values :ifc-info :ifc-version-id)])
           "generating" [[:dispatch [::job/start-polling job-id job-polling-callback-fn]]]
           [])}))

(rf/reg-event-fx ::regenerate-xkt
  ;; Original XKT generation during IFC file upload failed; start a new job generating it
  (fn [{:keys [db]} _]
    {:db             (assoc-in db [::values :xkt-status] "generating")
     :action/command {:name    :generate-xkt-file
                      :params  (-> (get-in db [::values :ifc-info])
                                   (select-keys [:application-id :operation-id :ifc-object-key])
                                   (set/rename-keys {:application-id :id}))
                      :success [::regenerate-xkt.success]
                      :error   [::regenerate-xkt.error]}}))

(rf/reg-event-fx ::regenerate-xkt.success
  (fn [_ [_ {{job-id :id} :job}]]
    {:dispatch [::job/start-polling job-id job-polling-callback-fn]}))

(rf/reg-event-db ::regenerate-xkt.error
  (fn [db _]
    (assoc-in db [::values :xkt-status] "failed")))
