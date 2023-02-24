(ns lupapalvelu.ui.attachment.assignments
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.attachment.shared :as shared]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.shared-strings :as ss]))

(def accordion-service js/lupapisteApp.services.accordionService)
(def assignment-change-sub "assignmentService::changed-cljs")

(rf/reg-event-fx
  ::complete-assignment
  (fn [_ [_ assignment]]
    {:hub/send {:event "assignmentService::markComplete"
                :data  {:applicationId (common/application-id)
                        :assignmentId  (:id assignment)}}}))

(rf/reg-event-fx
  ::upsert-assignment
  (fn [_ [_ attachment assignment recipient-id description]]
    {:hub/send {:event "assignmentService::saveAssignment"
                :data  {:id           (common/application-id)
                        :assignmentId (:id assignment)
                        :recipientId  recipient-id
                        :targets      [{:group "attachments"
                                        :id    (:id attachment)}]
                        :description  (ss/trim description)}}
     :dispatch [::shared/stop-editing attachment]}))

(defn assignments-changed []
  (>evt [::shared/refresh-assignments]))

(defn creator [{:keys [createdState automatic?]}]
  (let [author (if automatic?
                 (loc :a11y.assignment.automatic)
                 (->> (:user createdState)
                      ((juxt :firstName :lastName))
                      (ss/join-non-blanks " ")))]
    (loc :a11y.author-info
         (js/util.finnishDate (:timestamp createdState))
         author)))

(defn recipient [{:keys [recipient]}]
  (cond
    (:id recipient) (shared/person-name recipient)
    recipient       (loc :not-known)
    :else           (loc :applications.search.recipient.no-one)))

(defn assignment-editor
  ([attachment {:keys [recipient description] :as assignment}]
   (let [select-id (common/unique-id "select")
         input-id  (common/unique-id "input")
         user-id*  (r/atom (:id recipient))
         text*     (r/atom description)
         items     (->> (js->clj (.authorities accordion-service)
                                 :keywordize-keys true)
                        (map (fn [{id :id :as person}]
                               {:value id
                                :text  (shared/person-name person)}))) ]
     (fn [_ _]
       [:div.flex--column.flex--align-start.pad--2.w--30em
        [:label.txt--bold
         {:for select-id}
         (loc :applications.filter.recipient)]
        [components/dropdown
         @user-id*
         {:items      items
          :class      :w--100.gap--b1
          :sort-by    :text
          :choose-loc :applications.search.recipient.no-one
          :callback   (partial reset! user-id*)}]
        [:label.txt--bold
         {:for input-id}
         (loc :triggers.label.description)]
        [components/text-edit
         description
         {:id        input-id
          :callback  (partial reset! text*)
          :lines     5
          :class     :w--100
          :required? true}]
        [:div.flex--align-end.gap--t1.w--100
         [:button.positive.gap--r1
          {:disabled (ss/blank? @text*)
           :on-click #(>evt [::upsert-assignment attachment assignment
                             @user-id* @text*])}
          (loc :save)]
         [:button.secondary
          {:on-click #(>evt [::shared/stop-editing attachment])}
          (loc :cancel)]]])))
  ([attachment]
   [assignment-editor attachment nil]))

(defn assignment-details
  [{:keys [targets automatic?] :as assignment} attachment]
  [:div.flex--column.w--min-10em.gap--r1.gap--v1
   [:div.bg--gray.bd--gray.pad--1 (recipient assignment)]
   [:div.fg--gray.bd--h-gray.pad--1.txt--small (creator assignment)]
   [:div.pad-1.bd--h-gray.bd--b-gray.flex--between.flex--column.flex--g1
    [:div.ws--pre-wrap.pad--1 (:description assignment)]
    (when (> (count targets) 1)
      [:div.pate-note.pad--1
       (loc :a11y.assignment.attachment-targets (count targets))])
    (when (<sub [:auth/application? :update-assignment])
      [:div.flex--between.gap--1
       [:button.btn-small.secondary
        {:on-click #(>evt [::complete-assignment assignment])}
        (loc :application.assignment.complete)]
       (when-not automatic?
         [:button.btn-small.tertiary
          {:on-click #(>evt [::shared/set-editing attachment
                             :edit-assignment assignment])}
          (loc :edit)])])]])

(defn assignments-sub-row [assignments attachment]
  [:div
   (when (seq assignments)
     [:h4.gap--t1 (loc (cond-> "attachment.th-assignment"
                         (> (count assignments) 1) (str "s")))])
   [:div.flex--wrap
    (for [{aid :id :as assi} assignments]
      ^{:key aid} [assignment-details assi attachment])]
   (when (<sub [:auth/application? :create-assignment])
     [components/icon-button {:icon     :lupicon-circle-plus
                              :text-loc :application.assignment.create
                              :class    :gap--v1.primary
                              :on-click #(>evt [::shared/set-editing attachment
                                                :new-assignment])}])])
