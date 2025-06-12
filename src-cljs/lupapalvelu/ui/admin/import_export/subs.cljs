(ns lupapalvelu.ui.admin.import-export.subs
  (:require [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ visible?]]
    {:db (assoc db ::data {:visible? visible?})}))

(rf/reg-sub ::data
  :-> ::data)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-event-fx ::mode-selected
  (fn [{:keys [db]} [_ mode]]
    {:db (-> db
             (assoc-in [::data :mode] mode)
             (update ::data select-keys [:mode :visible?]))}))

(rf/reg-event-fx ::organizations.success
  (fn [{:keys [db]} [_ {:keys [organizations]}]]
    {:db (assoc-in db [::data :organizations] organizations)}))

(rf/reg-event-fx ::target-selected
  (fn [{:keys [db]} [_ target]]
    {:db           (-> db
                       (update ::data select-keys [:mode :visible?])
                       (assoc-in [::data :target] target))
     :action/query {:name    :import-export-fetch-organizations
                    :params  {:mode   (get-in db [::data :mode])
                              :target target}
                    :success ::organizations.success
                    :error   ::generic.error}}))

(rf/reg-event-fx ::items.success
  (fn [{:keys [db]} [_ {:keys [items]}]]
    ;; `:action/query` automatically converts to keywords, revert that
    {:db (assoc-in db [::data :items] (update-keys items name))}))

(defn- data->target
  [{:keys [target import-data]}]
  (or target
      (:target import-data)))

(rf/reg-event-fx ::organization-selected
  (fn [{:keys [db]} [_ org-id]]
    (let [mode (-> db ::data :mode)]
      (merge {:db (-> db
                      (update ::data select-keys [:mode :visible? :target :organizations
                                                  :import-filename :import-data])
                      (assoc-in [::data :org-id] org-id))}
             (when (or true (= :export mode))
               {:action/query {:name    :import-export-fetch-items
                               :params  {:target (-> db ::data data->target)
                                         :org-id org-id}
                               :success ::items.success
                               :error   ::generic.error}})))))

(rf/reg-event-fx ::items-selected
  (fn [{:keys [db]} [_ item-ids]]
    {:db (-> db
             (assoc-in [::data :item-ids] item-ids)
             (update ::data dissoc :select-all?))}))

(rf/reg-event-fx ::select-all-clicked
  (fn [{:keys [db]} _]
    (let [selected?    (-> db ::data :select-all? not)
          all-item-ids (->> db ::data :items keys set)]
      {:db (-> db
               (assoc-in [::data :select-all?] selected?)
               (assoc-in [::data :item-ids] (if selected? all-item-ids #{})))})))

(rf/reg-sub ::select-all?
  :<- [::data]
  :-> (comp boolean :select-all?))

(rf/reg-sub ::selected-mode
  :<- [::data]
  :-> :mode)

(rf/reg-sub ::selected-target
  :<- [::data]
  :-> data->target)

(rf/reg-sub ::organizations
  :<- [::data]
  :-> :organizations)

(rf/reg-sub ::selected-organization
  :<- [::data]
  :-> :org-id)

(rf/reg-sub ::items
  :<- [::data]
  :-> :items)

(rf/reg-sub ::selected-items
  :<- [::data]
  :-> :item-ids)

(rf/reg-sub ::items-selected?
  :<- [::selected-items]
  :-> (comp boolean seq))

(rf/reg-sub ::replace?
  :<- [::data]
  :-> (comp boolean :replace?))

(rf/reg-event-fx ::reset-clicked
  (fn [{:keys [db]} [_ value]]
    {:db (assoc-in db [::data :replace?] value)}))

(rf/reg-event-fx ::import-file-chosen
  (fn [{:keys [db]} [_ filename]]
    {:db (-> db
             (update ::data select-keys [:mode :visible?])
             (assoc-in [::data :import-filename] filename))}))

(rf/reg-sub ::import-filename
  :<- [::data]
  :-> :import-filename)

(rf/reg-sub ::import-filename-chosen?
  :<- [::import-filename]
  :-> boolean)

(rf/reg-event-fx ::import-uploaded
  (fn [{:keys [db]} [_ data]]
    (if-let [errors (model/import-checker data)]
      {:db (assoc-in db [::data :import-errors] errors)}
      {:db           (assoc-in db [::data :import-data] data)
       :action/query {:name    :import-export-fetch-organizations
                      :params  {:mode   :import
                                :target (:target data)}
                      :success ::organizations.success
                      :error   ::generic.error}})))

(rf/reg-event-fx ::clear-file
  (fn [{:keys [db]} _]
    {:db (update db ::data select-keys [:mode :visible?])}))

(rf/reg-sub ::import-data
  :<- [::data]
  :-> :import-data)

(rf/reg-sub ::import-errors
  :<- [::data]
  :-> :import-errors)

(rf/reg-sub ::import-errors?
  :<- [::import-errors]
  :-> (comp boolean seq))

(rf/reg-sub ::import-uploaded?
  :<- [::import-data]
  :-> boolean)

(rf/reg-sub ::import-items
  :<- [::import-data]
  :-> :items)

(rf/reg-sub ::import-org
  :<- [::import-data]
  :-> :org-id)

(rf/reg-sub ::src-is-dst?
  :<- [::selected-organization]
  :<- [::import-org]
  (fn [[destination source]]
    (and destination
         source
         (= destination source))))

(rf/reg-sub ::import-env
  :<- [::import-data]
  :-> :env)

(rf/reg-event-fx ::run-import
  (fn [{:keys [db]} _]
    (let [{:keys [org-id import-data import-filename replace?]} (-> db ::data)]
      {:action/command {:name    :import-export-import-items
                        :params  {:org-id   org-id
                                  :document import-data
                                  :filename import-filename
                                  :replace? (boolean replace?)}
                        :success ::import.success
                        :error   ::generic.error}})))

(rf/reg-event-fx ::import.success
  (fn [{:keys [db]} _]
    (let [{:keys [org-id]} (-> db ::data)]
      {:dialog/show  {:ltitle :admin.import-export.import-success-title
                      :ltext  :admin.import-export.import-success-text}
       :action/query {:name    :import-export-fetch-items
                      :params  {:target (-> db ::data data->target)
                                :org-id org-id}
                      :success ::post-import-items.success
                      :error   ::generic.error}})))

(rf/reg-event-fx ::post-import-items.success
  (fn [{:keys [db]} [_ {:keys [items]}]]
    ;; `:action/query` automatically converts to keywords, revert that
    {:db (assoc-in db [::data :post-import-items] (update-keys items name))}))

(rf/reg-sub ::post-import-items
  :<- [::data]
  :-> :post-import-items)

(rf/reg-sub ::import-done?
  :<- [::post-import-items]
  :-> (comp boolean seq))

(rf/reg-event-fx ::generic.error
  (fn [_ [_ {:keys [text text-args loc-args]}]]
    {:dialog/show (cond-> {:ltitle :error.dialog.title}
                    (not (or loc-args text-args)) (assoc :ltext text)
                    text-args                     (assoc :text (str (common/loc text) ": "
                                                                    (ss/join ", " text-args)))
                    ;; `loc-args` implies we want to show an error message and
                    ;; a list of problems / texts
                    loc-args                      (assoc :text (str (common/loc text) ": "
                                                                    (->> loc-args
                                                                         (map common/loc)
                                                                         (ss/join ", ")))))}))
