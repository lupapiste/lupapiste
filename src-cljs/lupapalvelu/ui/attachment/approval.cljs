(ns lupapalvelu.ui.attachment.approval
  "Attachment approvals and rejections."
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.attachment.grouping :as att-grouping]
            [lupapalvelu.ui.attachment.shared :as shared]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.shared-strings :as ss]))

(def attachment-service js/lupapisteApp.services.attachmentsService)
(def REJECTED "requires_user_action")
(def APPROVED "ok")

(defn- approved-or-nil [{:keys [state] :as approval}]
  (when (= state APPROVED)
    approval))

(defn- rejected-or-nil  [{:keys [state] :as approval}]
  (when (= state REJECTED)
    approval))

(defn- rejection [attachment]
  (rejected-or-nil (att-grouping/last-approval attachment)))

(defn approve-attachment [attachment]
  (.approveAttachment attachment-service (:id attachment)))

(defn reset-attachment [attachment]
  (.resetAttachment attachment-service (:id attachment)))

(defn approved? [attachment]
  (-> (att-grouping/last-approval attachment) approved-or-nil boolean))

(defn rejected? [attachment]
  (-> (att-grouping/last-approval attachment) rejected-or-nil boolean))

(rf/reg-event-fx
  ::reject
  (fn [_ [_ {id :id :as attachment} note]]
    (.rejectAttachmentNote attachment-service id (ss/trim note))
    (when-not (rejected? attachment)
      (.rejectAttachment attachment-service id))
    {:dispatch [::shared/stop-editing attachment]}))

(defn- approval-note [approval]
  (some-> approval :note ss/trim ss/blank-as-nil))

(defn reject-note [attachment]
  (approval-note (rejection attachment)))

(defn reject-note-editor [{id :id :as attachment}]
  (let [input-id     (str "reject-note-" id)
        reject-note* (r/atom (approval-note (att-grouping/last-approval attachment)))]
    (fn [attachment]
      [:div.flex--column.flex--align-start.pad--2.w--30em
       [:label.txt--bold {:for input-id} (loc "attachment.th-not-ok")]
       [components/text-edit
        @reject-note*
        {:placeholder (loc :a11y.attachment.reject-placeholder)
         :id          input-id
         :callback   (partial reset! reject-note*)
         :lines       5
         :class       :w--100
         :required?   true}]
       [:div.flex--align-end.gap--t1.w--100
        [:button.positive.gap--r1
         {:on-click #(>evt [::reject attachment @reject-note*])}
         (loc :save)]
        [:button.secondary
         {:on-click #(>evt [::shared/stop-editing attachment])}
         (loc :cancel)]]])))

(defn approval-buttons [attachment]
  (when (<sub [:auth/attachment? (:id attachment) :approve-attachment])
    (let [editing (<sub [::shared/editing attachment])
          ok?     (approved? attachment)
          nok?    (rejected? attachment)]
      [components/toggle-group
       (cond-> []
         ok?  (conj :approved)
         nok? (conj :rejected))
       {:items         [{:text-loc :ok :value :approved}
                        {:text-loc :attachment.th-not-ok :value :rejected}]
        :prefix        :attachment-approval-tag
        :class         :gap--05
        :callback      (fn [v]
                         (case v
                           :approved (approve-attachment attachment)
                           :rejected (>evt [::shared/set-editing attachment :reject-note])
                           (reset-attachment attachment)))
        :disabled?     editing
        :pseudo-radio? true}])))

(defn reject-note-details [attachment]
  (let [{:keys [timestamp user
                note]} (rejection attachment)
        author         (->> user
                            ((juxt :firstName :lastName))
                            (ss/join-non-blanks " "))]
    [:div
     [:h4.gap--t1 (loc :attachment.th-not-ok)]
     [:div.flex--column.w--min-20em.gap--r1.gap--v1.bd--gray
      [:div.fg--gray.pad--1.txt--small
       (loc :a11y.author-info
            (js/util.finnishDate timestamp)
            author)]
      [:div.pad-1.bd--h-gray.bd--b-gray.flex--between.flex--column.flex--g1
       [:div.ws--pre-wrap.pad--1.bg--blue (ss/trim note)]
       (when (<sub [:auth/attachment? (:id attachment) :approve-attachment])
         [:div.flex--between.gap--1
          [:button.btn-small.secondary
           {:on-click #(approve-attachment attachment)}
           (loc :ok)]
          [:button.btn-small.tertiary
           {:on-click #(>evt [::shared/set-editing attachment :reject-note])}
           (loc :edit)]])]]]))
