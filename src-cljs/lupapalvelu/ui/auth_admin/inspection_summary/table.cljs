(ns lupapalvelu.ui.auth-admin.inspection-summary.table
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.inspection-summary.subs :as subs]
            [lupapalvelu.ui.auth-admin.org-subs :as org-subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.bubble-dialog :as bd]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-sub ::operation-choices
  :<- [::org-subs/selected-operations]
  (fn [operations]
    (->> operations
         :R
         (map (fn [op]
                {:value op
                 :text  (common/loc (str "operations." op))}))
         (sort-by :text)
         (into []))))

(rf/reg-sub ::operations-templates-by-template-id
  :<- [::org-subs/operations-templates]
  (fn [operations-templates]
    (->> operations-templates
         (mapcat (fn [[operation templates]]
                   (map (fn [template]
                          [template (name operation)]) templates)))
         (reduce (fn [m [template operation]]
                   (update m template (fnil conj #{}) operation))
                 {}))))

(rf/reg-sub ::rows
  :<- [::org-subs/templates]
  (fn [templates]
    (->> templates
         (map (fn [{:keys [items] :as template}]
                (-> template
                    (update :modified js/util.finnishDateAndTime)
                    (assoc :template-text (ss/join "\n" items)))))
         (sort-by (juxt :name :id))
         (into []))))

(rf/reg-sub ::rows-by-id
  :<- [::rows]
  (fn [rows]
    (->> rows
         (map (juxt :id identity))
         (into {}))))

(rf/reg-sub ::row-by-id
  :<- [::rows-by-id]
  (fn [rows [_ id]]
    (get rows id {})))

;; Returns raw user input, without trimming/cleanup
(rf/reg-sub ::current-name
  (fn [[_ id]]
    [(rf/subscribe [::row-by-id id])
     (rf/subscribe [::subs/edits id])])
  (fn [[row edits]]
    (or (:name edits) (:name row))))

(rf/reg-sub ::current-template-text
  (fn [[_ id]]
    [(rf/subscribe [::row-by-id id])
     (rf/subscribe [::subs/edits id])])
  (fn [[row edits]]
    (or (:template-text edits) (:template-text row))))

(rf/reg-sub ::original-operations
  :<- [::operations-templates-by-template-id]
  (fn [templates [_ id]]
    (get templates id)))

(rf/reg-sub ::current-operations
  (fn [[_ id]]
    [(rf/subscribe [::original-operations id])
     (rf/subscribe [::subs/edits id])])
  (fn [[operations edits]]
    (or (:operations edits)
        operations)))

(rf/reg-sub ::current-operations-count
  (fn [[_ id]]
    (rf/subscribe [::current-operations id]))
  :-> count)

(rf/reg-sub ::original
  (fn [[_ id]]
    [(rf/subscribe [::row-by-id id])
     (rf/subscribe [::original-operations id])])
  (fn [[row operations]]
    (-> row
        (select-keys [:name :template-text])
        ;; Operations are stored elsewhere in the DB, but
        ;; let's pretend that each template knows the operations
        ;; it's attached to
        (assoc :operations operations))))

;; Returns trimmed/cleaned up values
(rf/reg-sub ::modified
  (fn [[_ id]]
    [(rf/subscribe [::original id])
     (rf/subscribe [::subs/edits id])])
  (fn [[original edits]]
    (-> (merge original (select-keys edits [:name :template-text :operations]))
        (update :name (comp ss/blank-as-nil ss/trim))
        (update :template-text (comp ss/blank-as-nil ss/trim)))))

(rf/reg-sub ::modified?
  (fn [[_ id]]
    [(rf/subscribe [::original id])
     (rf/subscribe [::modified id])])
  (fn [[original modified]]
    (not= original modified)))

(rf/reg-sub ::valid?
  (fn [[_ id]]
    (rf/subscribe [::modified id]))
  (fn [{:keys [name template-text]}]
    (boolean (and name template-text))))

(rf/reg-sub ::can-save?
  (fn [[_ id]]
    [(rf/subscribe [::modified? id])
     (rf/subscribe [::valid? id])])
  (fn [[modified? valid?]]
    (and modified? valid?)))

(rf/reg-sub ::templates?
  :<- [::org-subs/templates]
  :-> (comp boolean seq))

(defn- edit-name
  [row-id]
  (let [input-id "inspection-summary-template-name"]
    [:<>
     [:label.required {:for input-id}
      (common/loc :auth-admin.inspection-summary-templates.name)]
     [:input.required {:type      :text
                       :id        input-id
                       :value     (<sub [::current-name row-id])
                       :disabled  (not (<sub [:auth/global? :modify-inspection-summary-template]))
                       :on-change (fn [e] (>evt [::subs/alter-name (util/tval e)]))}]]))

(defn- edit-template-text
  [row-id]
  (let [input-id "inspection-summary-template-items"]
    [:<>
     [:label.required {:for input-id}
      (common/loc :auth-admin.inspection-summary-templates.items)]
     [:textarea.required {:id          input-id
                          :value       (<sub [::current-template-text row-id])
                          :disabled    (not (<sub [:auth/global? :modify-inspection-summary-template]))
                          :on-change   (fn [e] (>evt [::subs/alter-template-text (util/tval e)]))
                          :placeholder (common/loc :auth-admin.inspection-summary-templates.items.placeholder)}]]))

(defn- edit-operations
  [row-id]
  (let [input-id "inspection-summary-template-operations"]
    [:<>
     [:label {:for input-id}
      (common/loc :auth-admin.inspection-summary-templates.operations-selected)]
     [reselect/reselect
      (<sub [::current-operations row-id])
      {:id        input-id
       :multi?    true
       :disabled? (not (<sub [:auth/global? :modify-inspection-summary-template]))
       :items     (<sub [::operation-choices])
       :callback  (fn [ops]
                    (>evt [::subs/alter-operations ops]))}]]))

(defn- template-bubble
  [row-id]
  [:div.inspection-summary-template-editor-container
   [:div.container-bubble
    [bd/bubble-dialog {:visible?        true
                       :button-icon     "lupicon-check"
                       :ok-visible?     true
                       :ok-enabled?     (<sub [::can-save? row-id])
                       :ok-fn           (fn []
                                          (>evt [::subs/create-or-modify-template row-id]))
                       :cancel-visible? true
                       :cancel-fn       #(>evt [::subs/cancel-editing])
                       :class           "inspection-summary-template-editor"}
     [edit-name row-id]
     [edit-template-text row-id]
     [edit-operations row-id]]]])

(defn- table-row
  [{:keys [id name modified]}]
  (let [editing? (<sub [::subs/editing-id? id])]
    ^{:key id}
    [:tbody.inspection-summary-template
     [:tr.inspection-summary-template-row
      [:td [:a {:on-click (fn []
                            ;; Unlike the old knockout implementation, allow toggling
                            ;; editing state by clicking on the title again
                            (if editing?
                              (>evt [::subs/cancel-editing])
                              (>evt [::subs/start-editing id])))}
            name]]
      [:td (let [op-count (<sub [::current-operations-count id])]
             (when-not (zero? op-count) op-count))]
      [:td modified]
      [:td
       (when (<sub [:auth/global? :delete-inspection-summary-template])
         [:a {:on-click (fn [] (>evt [::subs/delete-template id]))}
          (common/loc :remove)])]]
     (when editing?
       [:tr.inspection-summary-template-row
        [:td {:colSpan 4}
         [template-bubble id]]])]))

(defn- create-new-button
  []
  [:div.stacked--inline
   [:button.btn.btn-primary
    {:on-click (fn [] (>evt [::subs/start-creating]))
     :disabled (or (not (<sub [:auth/global? :create-inspection-summary-template]))
                   (<sub [::subs/creating-new?]))}
    (common/loc :auth-admin.inspection-summary-templates.add)]])

(defn- table
  []
  (into [:table
         [:thead
          [:tr
           [:th (common/loc :auth-admin.inspection-summary-templates.name)]
           [:th (common/loc :auth-admin.inspection-summary-templates.operations-count)]
           [:th (common/loc :auth-admin.inspection-summary-templates.modified)]
           [:th (common/loc :auth-admin.inspection-summary-templates.actions)]]]]
        (->> (<sub [::rows])
             (mapv table-row))))

(defn inspection-summaries
  []
  [:div.content
   (if (not (<sub [::templates?]))
     [:p (common/loc :auth-admin.inspection-summary-templates.none)]
     [table])
   [create-new-button]
   (when (<sub [::subs/creating-new?])
     [template-bubble :inspection-summary/new])])
