(ns lupapalvelu.ui.imports.data.core
  "Model support for rows and their cells. Used both from the authority admin and
  application section. The following keys can be found under datapath:

  `:cell-data` Runtime information for each cell. Map hierarchy, where toplevel keys are
  row-ids and lowerlevel keys cell-ids and field keys.

  `:validation-errors` Row-id -> cell-id -> error map hierarchy. The validation errors are
  not persistent. In other words, bad data is never stored into mongo.

  `:editing` Row-id -> boolean map, where the value is true if the row is currently in the
  edit mode.

  `:cell-staging` When a cell is edited, it can also be processed in the backend (for
  example, strings are trimmed). However, the cell editors are not updated with the
  processed values, since that would confuse and irritate the user. Instead, the values
  are stored in `:cell-staging` map hierarchy (similar to `:cell-data`) and later
  'committed' to `:cell-data` when editing is finished.

  `:rows` Collection of rows.

  `:configs` Map of config-id -> configs.

  `:column-order` Map of config-id -> column-id -> index.

  `:sorted-column-order` Similar to `:column-order`. When `:sorted-column-order` for a
  config is found, it overrides the `:column-order`."
  (:require [lupapalvelu.data :as data]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.misc]
            [re-frame-fx.dispatch]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(def datapath (partial data/datapath ::data))
(def set-d    (partial data/set-d ::data))
(def get-d    (partial data/get-d ::data))
(def unset-d  (partial data/unset-d ::data))
(def ctx-d    (partial data/ctx-d ::data))

(defn- set-cell-field [db row-id cell-id k value]
  (set-d db :cell-data row-id cell-id k value))

(defn get-cell-field [ctx row-id cell-id k]
  (ctx-d ctx :cell-data row-id cell-id k))

(defn set-cell-value [db row-id cell-id value]
  (set-cell-field db row-id cell-id :value value))

(defn get-cell-value [ctx row-id cell-id]
  (get-cell-field ctx row-id cell-id :value))

(defn set-cell-modified [db row-id cell-id ts-or-m]
  (set-cell-field db row-id cell-id :modified (:ts ts-or-m ts-or-m)))

(defn set-cell-error [db row-id cell-id error]
  (if error
    (set-cell-field db row-id cell-id :error error)
    (unset-d db :cell-data row-id cell-id :error)))

(defn set-cell-validation-error [db row-id cell-id error]
  (if error
    (set-d db :validation-errors row-id cell-id error)
    (unset-d db :validation-errors row-id cell-id)))

(defn get-cell-error [ctx row-id cell-id]
  (or (ctx-d ctx :validation-errors row-id cell-id)
      (ctx-d ctx :cell-data row-id cell-id :error)))

(defn clear-cell-errors [db row-id cell-id]
  (-> db
      (set-cell-error row-id cell-id nil)
      (set-cell-validation-error row-id cell-id nil)))

(defn set-cell-stage [db row-id cell-id value]
  (set-d db :cell-staging row-id cell-id value))

(defn commit-row-stages [db row-id]
  (reduce (fn [acc [cell-id value]]
            (set-cell-value acc row-id cell-id value))
          (unset-d db :cell-staging row-id)
          (get-d db :cell-staging row-id)))

(defn get-row [ctx row-id & ks]
  (cond-> (util/find-by-id row-id (ctx-d ctx :rows))
    (seq ks) (get-in ks)))

(defn update-row [db row-id f & args]
  (apply update-in db (datapath :rows)
         (partial util/update-by-id row-id f)
         args))

(defn get-title
  "Current language title. `title` is a lang - text map."
  [{:keys [title]}]
  (get title (keyword (js/loc.getCurrentLanguage))))

(defn select-command
  [org-id org-cmd app-cmd]
  (let [app-id (common/hash-application-id)]
    (cond
      app-id {:name   app-cmd
              :params {:id app-id}}
      org-id {:name   org-cmd
              :params {:organizationId org-id}}
      :else  (throw (ex-info "No command" {})))))

(defn toggle-row-edit [db row-id edit?]
  (if edit?
    (set-d db :editing row-id true)
    (-> db
        (unset-d :editing row-id)
        (unset-d :validation-errors row-id)
        (commit-row-stages row-id))))


(defn set-configs [db configs]
  (reduce (fn [acc {:keys [id columns]}]
            (->> columns
                 (mapv (juxt :id :index))
                 (into {})
                 ;; Note that `:sorted-column-order` is not reinitialized.
                 (set-d acc :column-order id)))
          (set-d db :configs (into {} (mapv (juxt (comp keyword :id) identity) configs)))
          configs))

(defn add-row [db {:keys [id cells] :as row}]
  (let [pairs (mapv (partial vector id) cells)]
    (reduce (fn [acc [row-id {:keys [id value modified]}]]
              (cond-> acc
                value    (set-cell-value row-id id value)
                modified (set-cell-modified row-id id modified)))
            (update-in db (datapath :rows) util/conjv row)
            pairs)))

(defn set-rows [db rows]
  (let [db    (-> db
                  (unset-d :cell-data)
                  (unset-d :editing)
                  (unset-d :validation-errors)
                  (unset-d :cell-staging)
                  (unset-d :rows))]
    (reduce add-row db rows)))

;; ---------------------------------
;; Events
;; ---------------------------------

(rf/reg-event-db ::set-rows
  (fn [db [_ rows]]
    (set-rows db rows)))

(rf/reg-event-db ::add-row
  (fn [db [_ row]]
    (add-row db row)))

(rf/reg-event-db ::set-configs
  (fn [db [_ configs]]
    (set-configs db configs)))

(rf/reg-event-db ::update-column-type
  (fn [db [_ cfg-id column-id typedata]]
    (update-in db (datapath :configs cfg-id :columns)
               (fn [columns]
                 (util/update-by-id column-id util/deep-merge columns {:type typedata})))))

(rf/reg-event-db ::set-sorted-column-order
  (fn [db [_ cfg-id column-ids]]
    (set-d db :sorted-column-order cfg-id (zipmap column-ids (range)))))

(rf/reg-event-db ::toggle-edit
  (fn [db [_ row-id edit?]]
    (toggle-row-edit db row-id edit?)))

(rf/reg-event-db ::toggle-edit-every-row
  (fn [db [_ edit?]]
    (reduce (fn [acc {row-id :id}]
              (toggle-row-edit acc row-id edit?))
            db
            (get-d db :rows))))

(rf/reg-event-fx ::edit-import-row.result
  (fn [{db :db} [_ {:keys [ok validation-error value modified] :as res} row-id cell-id]]
    (cond
      ok               {:db (-> db
                                (set-cell-stage row-id cell-id value)
                                (clear-cell-errors row-id cell-id)
                                (set-cell-modified row-id cell-id modified))}
      validation-error {:db (set-cell-validation-error db row-id cell-id
                                                       validation-error)}
      :else            {:indicator/negative res})))

(rf/reg-event-fx ::edit-import-row
  (fn [_ [_ org-id row-id cell-id value]]
    {:action/command (util/deep-merge
                       (select-command org-id
                                       :edit-import-row
                                       :edit-application-import-row)
                       {:params  {:rowId  row-id
                                  :cellId cell-id
                                  :value  value}
                        :success [::edit-import-row.result row-id cell-id]
                        :error   [::edit-import-row.result row-id cell-id]})}))

(rf/reg-event-fx ::edit-cell
  (fn [{db :db} [_ row-id cell-id value]]
    (when-let [row (util/find-by-id row-id (get-d db :rows))]
      {:dispatch-debounce {:id      :edit-cell-value
                           :action  :dispatch
                           :event   [::edit-import-row (:organization row) row-id cell-id value]
                           :timeout 750}})))

(rf/reg-event-db ::mark-row-deleted.success
  (fn [db [_ {:keys [deleted]} row-id]]
    (update-row db row-id assoc :deleted deleted)))

(rf/reg-event-fx ::mark-row-deleted
  (fn [{db :db} [_ row-id deleted?]]
    (let [{:keys [organization deleted]} (get-row db row-id)
          app-id                         (common/hash-application-id)]
      (when-not (= (boolean deleted) deleted?)
        (cond-> {:action/command (util/deep-merge
                                   (select-command organization
                                                   :mark-import-config-row-deleted
                                                   :mark-application-import-row-deleted)
                                   {:params  {:rowId   row-id
                                              :deleted deleted?}
                                    :success [::mark-row-deleted.success row-id]})}
          (and app-id deleted?) (assoc :db (commit-row-stages db row-id))
          (not app-id)          (assoc :fx [[:dispatch [::toggle-edit row-id false]]]))))))

;; ---------------------------------
;; Subs
;; ---------------------------------

(rf/reg-sub ::data :-> get-d)

(rf/reg-sub ::configs
  :<- [::data]
  :-> (util/fn->> :configs vals (sort-by (comp ss/lower-case get-title))))

(rf/reg-sub ::config-ids
  :<- [::configs]
  :-> (util/fn->> (mapv :id)))

(rf/reg-sub ::config-title
  :<- [::configs]
  (fn [cfgs [_ cfg-id]]
    (get-title (util/find-by-id cfg-id cfgs))))

(rf/reg-sub ::rows
  :<- [::data]
  :-> :rows)

(rf/reg-sub ::deleted-rows?
  :<- [::rows]
  :-> (util/fn->> (some :deleted) boolean))

(rf/reg-sub ::config-rows
  :<- [::rows]
  (fn [rows [_ cfg-id & {:keys [include-deleted?]
                         :or   {include-deleted? true}
                         :as   _opts}]]
    (cond->> (util/filter-by-key :config-id cfg-id rows)
      (not include-deleted?) (remove :deleted))))

(rf/reg-sub ::config-row-ids
  (fn [[_ cfg-id & opts]]
    (rf/subscribe (apply conj [::config-rows cfg-id] opts)))
  :-> (util/fn->> (mapv :id) seq))

(rf/reg-sub ::row
  :<- [::data]
  (fn [data [_ row-id]]
    (get-row data row-id)))

(rf/reg-sub ::row-deleted?
  (fn [[_ row-id]]
    (rf/subscribe [::row row-id]))
  :-> (comp boolean :deleted))

(rf/reg-sub ::row-config
  (fn [[_ row-id]]
    [(rf/subscribe [::configs])
     (rf/subscribe [::row row-id])])
  (fn [[cfgs {:keys [config-id]}]]
    (util/find-by-id config-id cfgs)))

(rf/reg-sub ::row-columns
  (fn [[_ row-id]]
    (rf/subscribe [::row-config row-id]))
  :-> (util/fn->> :columns (sort-by :index)))

(rf/reg-sub ::row-cell-ids
  :<- [::data]
  (fn [data [_ row-id]]
    (let [{:keys [config-id cells]} (get-row data row-id)
          order                     (or (ctx-d data :sorted-column-order config-id)
                                        (ctx-d data :column-order config-id))]
      (->> cells
           (sort-by #(get order (:column-id %)))
           (mapv :id)))))

(rf/reg-sub ::cell
  (fn [[_ row-id _]]
    (rf/subscribe [::row row-id]))
  (fn [{:keys [cells]} [_ _ cell-id]]
    (some-> (util/find-by-id cell-id cells))))

(rf/reg-sub ::cell-column
  (fn [[_ row-id cell-id]]
    [(rf/subscribe [::row-columns row-id])
     (rf/subscribe [::cell row-id cell-id])])
  (fn [[columns {:keys [column-id]}]]
    (util/find-by-id column-id columns)))

(rf/reg-sub ::editing?
  :<- [::data]
  (fn [data [_ row-id]]
    (boolean (ctx-d data :editing row-id))))

(rf/reg-sub ::cell-value
  :<- [::data]
  (fn [data [_ row-id cell-id]]
    (get-cell-value data row-id cell-id)))

(rf/reg-sub ::cell-error
  :<- [::data]
  (fn [data [_ row-id cell-id]]
    (get-cell-error data row-id cell-id)))

(rf/reg-sub ::cell-original
  (fn [[_ row-id cell-id]]
    (rf/subscribe [::cell row-id cell-id]))
  (fn [{:keys [original type]}]
    (let [{:keys [value raw]} original]
      (cond
        (and value (= type "date")) (date/finnish-date value)
        value                       value
        :else                       raw))))

(rf/reg-sub ::row-deleted-datestring
  (fn [[_ row-id]]
    (rf/subscribe [::row row-id]))
  :-> #(some-> % :deleted :ts date/finnish-datetime))

(rf/reg-sub ::row-modified-datestring
  :<- [::data]
  (fn [data [_ row-id]]
    (some->> (ctx-d data :cell-data row-id)
             vals
             (keep :modified)
             (apply max)
             date/finnish-datetime)))

(rf/reg-sub ::latest-cell-modified-ts
  :<- [::data]
  (fn [{:keys [cell-data staging]}]
    (some->> [cell-data staging]
             (mapcat (util/fn->> vals (mapcat vals)))
             (keep :modified)
             (apply max))))

(rf/reg-sub ::row-added-infostring
  (fn [[_ row-id]]
    (rf/subscribe [::row row-id]))
  (fn [{:keys [added]}]
    (when added
      (common/loc :imports.row.added
                  (-> added :ts date/finnish-datetime)
                  (:id added)))))
