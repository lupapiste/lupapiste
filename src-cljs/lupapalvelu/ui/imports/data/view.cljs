(ns lupapalvelu.ui.imports.data.view
  "Application imports section top level model support. The following keys are under the
  datapath:

  `:visible?` Whether the section is currently active/visible.

  `:editing?` Is the editing mode active?

  `:show-deleted?` Show also the rows marked deleted? The deleted rows can be only shown
  during the editing mode."
  (:require [lupapalvelu.data :as data]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.imports.data.core :as idata]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.util :as util]))

(def datapath (partial data/datapath ::data))
(def set-d    (partial data/set-d ::data))
(def get-d    (partial data/get-d ::data))
(def unset-d  (partial data/unset-d ::data))
(def ctx-d    (partial data/ctx-d ::data))

;; -----------------------------
;; Events
;; -----------------------------

(rf/reg-event-db ::application-imports.success
  (fn [db [_ {:keys [configs rows]}]]
    (-> db
        (idata/set-configs configs)
        (idata/set-rows rows))))

(rf/reg-event-fx ::toggle-view
  (fn [{db :db} [_ visible?]]
    (if visible?
      {:db           (set-d db {:visible? true})
       :action/query {:name    :application-imports
                      :params  {:id (common/application-id)}
                      :success [::application-imports.success]}
       :fx           [[:dispatch [:auth/refresh]]]}
      {:db (-> db unset-d idata/unset-d)})))

(rf/reg-event-fx ::toggle-edit
  (fn [{db :db} [_ edit?]]
    {:db (set-d db :editing? edit?)
     :fx [[:dispatch [::toggle-show-deleted false]]
          [:dispatch [::idata/toggle-edit-every-row edit?]]]}))

(rf/reg-event-db ::toggle-show-deleted
  (fn [db [_ show-deleted?]]
    (set-d db :show-deleted? show-deleted?)))

(rf/reg-event-fx ::add-row.success
  (fn [_ [_ {:keys [row]}]]
    {:fx [[:dispatch [::idata/add-row row]]
          [:dispatch [::idata/toggle-edit (:id row) true]]]}))

(rf/reg-event-fx ::add-row
  (fn [_ [_ cfg-id]]
    {:action/command {:name    :add-application-import-row
                      :params  {:id       (common/application-id)
                                :configId cfg-id}
                      :success ::add-row.success}}))

;; -----------------------------
;; Subs
;; -----------------------------

(rf/reg-sub ::data :-> get-d)

(rf/reg-sub ::editing?
  :<- [::data]
  :-> :editing?)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-sub ::row-config-ids
  :<- [::idata/rows]
  :<- [::idata/config-ids] ;; Needed for ordering
  (fn [[rows cfg-ids]]
    (let [rids (->> (remove :deleted rows)
                    ;; No blank rows. We want to avoid situation, where there are only
                    ;; blank rows when viewing configuration rows.
                    (filter #(some :value (:cells %)))
                    (keep :config-id)
                    set)]
      (->> cfg-ids (keep rids) seq))))

(rf/reg-sub ::last-modified-ts
  :<- [::idata/rows]
  :-> (util/fn->> (remove :deleted)
                  (mapcat :cells)
                  (keep (comp :ts :modified))
                  (apply max)))

(rf/reg-sub ::show-deleted?
  :<- [::data]
  (fn [{:keys [editing? show-deleted?]}]
    (boolean (and editing? show-deleted?))))
