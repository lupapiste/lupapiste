(ns lupapalvelu.ui.docgen.component.group-approval
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.component.approval-common :as approval-common]
            [lupapalvelu.ui.docgen.component.reject-note :as reject-note]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.approvals :as approvals]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.user :as user]
            [sade.util :as util]))

(defn- approval
  [{:keys [doc-id path]}]
  (let [approved? (<sub [::approvals/approved? doc-id path])
        rejected? (<sub [::approvals/rejected? doc-id path])]
    [:span.form-approval-status.is-status
     {:class (cond-> []
               approved? (conj "approved")
               rejected? (conj "rejected"))}
     (when (or approved? rejected?)
       [:span.like-btn
        (when rejected?
          [:i.lupicon-circle-attention.rejected
           {:aria-hidden true}])
        (when approved?
          [:i.lupicon-circle-check.approved])
        [:span.is-details
         {:class (when rejected?
                   "is-details--rejected")}
         (<sub [::approvals/details doc-id path])]])]))

(defn- remove-button
  [{:keys [doc-id path]}]
  (let [app-id (<sub [::app/application-id])]
    [:button.secondary.is-right
     {:on-click (fn [_] (>evt [::docgen-common/remove-button-clicked (util/mmap doc-id app-id path)]))}
     [:i.lupicon-remove {:aria-hidden true}]
     [:span
      (common/loc :remove)]]))

(defn- reject-button
  [{:keys [doc-id path]}]
  (let [app-id (<sub [::app/application-id])]
    [:button.secondary.is-right
     {:on-click (approval-common/on-reject (util/mmap app-id doc-id path))}
     [:span
      (common/loc :document.reject)]]))

(defn- approve-button
  [{:keys [doc-id path]}]
  (let [app-id (<sub [::app/application-id])]
    [:button.positive.is-right
     {:on-click (approval-common/on-approve (util/mmap app-id doc-id path))}
     [:i.lupicon-check]
     [:span
      (common/loc :document.approve)]]))

(defn render
  "Approval component for an individual group."
  [{:keys [doc-id path approvable? removable? override-auth-source]}]
  (when (user/has-role?)
    (let [show-reject?  (approval-common/may-reject? (util/mmap doc-id path override-auth-source approvable?))
          show-approve? (approval-common/may-approve? (util/mmap doc-id path override-auth-source approvable?))
          has-approval? (<sub [::approvals/has-value? doc-id path])
          content?      (or has-approval?
                            removable?
                            show-reject?
                            show-approve?)
          args          (util/mmap doc-id path)]
      (when content?
        [:div.group-buttons
         (when has-approval?
           [approval args])
         (when removable?
           [remove-button args])
         (when show-reject?
           [reject-button args])
         (when show-approve?
           [approve-button args])
         [:div
          [reject-note/render args]]]))))
