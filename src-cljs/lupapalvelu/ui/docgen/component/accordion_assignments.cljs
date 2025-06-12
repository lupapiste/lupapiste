(ns lupapalvelu.ui.docgen.component.accordion-assignments
  "Docgen accordions contain tools for authorities to manage document specific assignments."
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.bubble-dialog :as bd]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.assignments :as assignments]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(rf/reg-sub ::data :-> ::data)

(defn on-unload [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-sub ::editor-data
  :<- [::data]
  (fn [data [_ doc-id assignment-id]]
    (get-in data [:editor-data doc-id assignment-id])))

(rf/reg-sub ::editing?
  (fn [[_ doc-id assignment-id]]
    (rf/subscribe [::editor-data doc-id assignment-id]))
  :-> some?)

(rf/reg-event-fx ::toggle-editing
  (fn [{:keys [db]} [_ doc-id assignment-id]]
    {:pre [(string? doc-id) (string? assignment-id)]}
    {:db (update-in db [::data :editor-data doc-id assignment-id] (fn [m]
                                                                    (if m nil {})))}))

(rf/reg-sub ::current-description
  (fn [[_ doc-id assignment-id]]
    {:pre [(string? doc-id) (string? assignment-id)]}
    [(rf/subscribe [::editor-data doc-id assignment-id])
     (rf/subscribe [::assignments/description doc-id assignment-id])])
  (fn [[editor description]]
    (or (:description editor) description)))

(rf/reg-sub ::current-recipient-id
  (fn [[_ doc-id assignment-id]]
    {:pre [(string? doc-id) (string? assignment-id)]}
    [(rf/subscribe [::editor-data doc-id assignment-id])
     (rf/subscribe [::assignments/recipient doc-id assignment-id])])
  (fn [[editor recipient]]
    (or (:recipient-id editor)
        (:id recipient))))

(rf/reg-event-fx ::description-edited
  (fn [{:keys [db]} [_ doc-id assignment-id description]]
    {:pre [(string? doc-id) (string? assignment-id)]}
    {:db (assoc-in db [::data :editor-data doc-id assignment-id :description] description)}))

(rf/reg-event-fx ::recipient-id-edited
  (fn [{:keys [db]} [_ doc-id assignment-id recipient-id]]
    {:pre [(string? doc-id) (string? assignment-id)]}
    {:db (assoc-in db [::data :editor-data doc-id assignment-id :recipient-id] recipient-id)}))

(rf/reg-event-fx ::mark-complete
  (fn [{:keys [db]} [_ assignment-id]]
    {:pre [(string? assignment-id)]}
    (let [app-id (app/db->application-id db)]
      {:fx [[:hub/send {:event "assignmentService::markComplete"
                        :data  {:applicationId app-id
                                :assignmentId  assignment-id}}]]})))

(rf/reg-sub ::assignment-ok?
  (fn [[_ doc-id assignment-id]]
    {:pre [(string? doc-id) (string? assignment-id)]}
    [(rf/subscribe [::current-description doc-id assignment-id])
     (rf/subscribe [::assignments/target doc-id assignment-id])])
  (fn [[description target]]
    (boolean (and (ss/blank-as-nil description)
                  ;; Check the target, even though it is not strictly necessary.
                  ;; The old implementation supported both create and edit actions,
                  ;; and only the former supports modifying the target.
                  (:group target)
                  (:id target)))))

(rf/reg-event-fx ::save-assignment
  (fn [{:keys [db]} [_ {:keys [doc-id assignment-id target recipient-id description]}]]
    {:pre [(string? doc-id) (string? assignment-id) target (string? description)]}
    (let [app-id (app/db->application-id db)]
      {:fx [[:hub/send {:event "assignmentService::saveAssignment"
                        :data  {:id           app-id
                                :assignmentId assignment-id
                                :recipientId  (or recipient-id "")
                                :targets      [{:group (:group target)
                                                :id    (:id target)}]
                                :description  description}}]]
       :db (update-in db [::data :editor-data doc-id] dissoc assignment-id)})))

;; TODO: move to some util ns?
(defn- full-name [user]
  (->> user
       ((juxt :firstName :lastName))
       (ss/join-non-blanks " ")))

(defn- receiver-name
  [recipient]
  (cond
    (:id recipient) (full-name recipient)
    recipient       (str "<" (common/loc :not-known) ">")
    :else           (common/loc :applications.search.recipient.no-one)))

(defn- render-assignment
  [idx doc-id assignment-id]
  [:<>
   [:i.lupicon-circle-attention]
   [:div.assignment-header
    [:span (common/loc :application.assignment) " " (inc idx)] " "
    [:span.creator (full-name (<sub [::assignments/creator doc-id assignment-id]))]
    [:span.receiver (receiver-name (<sub [::assignments/recipient doc-id assignment-id]))]]
   [:div.assignment-text
    [:span (str \" (<sub [::assignments/description doc-id assignment-id]) \")]]
   [:div.no-wrap-whitespace
    (when (<sub [:auth/global? :update-assignment])
      [components/icon-button
       {:class    :primary
        :icon     :lupicon-pen
        :text-loc :edit
        :on-click (fn [] (>evt [::toggle-editing doc-id assignment-id]))}])
    (when (<sub [:auth/global? :complete-assignment])
      [components/icon-button
       {:class    :primary
        :icon     :lupicon-check
        :text-loc :application.assignment.complete
        :on-click (fn [] (>evt [::mark-complete assignment-id]))}])]])

(defn- empty-option
  []
  {:value ""
   :label (common/loc :applications.search.recipient.no-one)})

(defn- authority-options
  []
  ;; TODO: could be a subscription, perhaps refactor
  ;; `lupapalvelu.ui.attachment.assignments` at the same time?
  (->> (js->clj (.applicationAuthorities ^js js/lupapisteApp.services.handlerService)
                :keywordize-keys true)
       ;; NOTE: we don't do any sorting, we follow the original
       ;; order according to what the `application-authorities` query returned
       ;; to the JS service.
       (map (fn [{id :id :as person}]
              {:value id
               :label (full-name person)}))
       (cons (empty-option))
       (into [])))

(defn- recipient-editor
  [{:keys [doc-id assignment-id]}]
  (let [id "assignment-recipient"]
    [:div.field--vertical
     [:label.lux.field--block
      {:for id}
      (common/loc :application.assignment.recipient.label)]
     ;; TODO: Some municipalities have lots of authorities
     ;;       -> consider using `lupapalvelu.ui.components.reselect`?
     [ui-input/select
      {:id        id
       :class     [:lux :field--block]
       :value     (or (<sub [::current-recipient-id doc-id assignment-id])
                      "")
       :on-change (fn [val] (>evt [::recipient-id-edited doc-id assignment-id val]))}
      (authority-options)]]))

(defn- description-editor
  [{:keys [doc-id assignment-id]}]
  [ui-input/textarea
   {:id          "assignment-description"
    :debounce-ms docgen-common/default-input-debounce-ms
    :max-length  500
    :value       (<sub [::current-description doc-id assignment-id])
    :on-change   (fn [v] (>evt [::description-edited doc-id assignment-id v]))}])

(defn- render-editor
  "See `edit-assignment-editor-template`"
  ;; There's also a `create-assignment-editor-template`
  [{:keys [doc-id assignment-id]}]
  [bd/bubble-dialog
   {:visible?    true
    :button-icon :lupicon-save
    :ok-visible? true
    :ok-enabled? (<sub [::assignment-ok? doc-id assignment-id])
    :ok-fn       (let [;; TODO: would be nicer to get these from app-db, but needs a few helper fns
                       description  (<sub [::current-description doc-id assignment-id])
                       recipient-id (<sub [::current-recipient-id doc-id assignment-id])
                       target       (<sub [::assignments/target doc-id assignment-id])]
                   (fn [] (>evt [::save-assignment (util/mmap doc-id assignment-id target recipient-id description)])))
    :cancel-fn   (fn [] (>evt [::toggle-editing doc-id assignment-id]))}
   [:div.form-grid.form-grid--no-border
    [:div.row
     [:div.col-1
      [recipient-editor (util/mmap doc-id assignment-id)]]]
    [:div.row
     [:div.col-3
      [description-editor (util/mmap doc-id assignment-id)]]]]])

(defn- render-assignment-and-editor
  [idx doc-id assignment-id]
  [:div.assignment-container
   [:div.assignment
    [render-assignment idx doc-id assignment-id]]
   (when (<sub [::editing? doc-id assignment-id])
     [render-editor {:doc-id        doc-id
                     :assignment-id assignment-id}])])

(defn render
  [{:keys [doc-id]}]
  [:div.accordion-assignments
   (->> (<sub [::assignments/document-assignments-ids doc-id])
        (map-indexed (fn [idx assignment-id]
                       ^{:key assignment-id}
                       [render-assignment-and-editor idx doc-id assignment-id]))
        doall)])
