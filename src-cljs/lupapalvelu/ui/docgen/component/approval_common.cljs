(ns lupapalvelu.ui.docgen.component.approval-common
  "Common utilities for document or group approval"
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.docgen.component.reject-note :as reject-note]
            [lupapalvelu.ui.docgen.feature.approvals :as approvals]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.command :as command]
            [re-frame.core :as rf]
            [sade.util :as util]
            [lupapalvelu.ui.docgen.path :as path]))

(rf/reg-sub ::may-reject?
  (fn may-reject-inputfn [[_ doc-id path override-auth-source]]
    {:pre [doc-id]}
    [(rf/subscribe [::auth/auth? doc-id :reject-doc override-auth-source])
     (rf/subscribe [::approvals/rejected? doc-id path])])
  (fn [[ok? rejected?]]
    (and ok?
         (not rejected?))))

(rf/reg-sub ::may-approve?
  (fn may-approve-inputfn [[_ doc-id path override-auth-source]]
    {:pre [doc-id]}
    [(rf/subscribe [::auth/auth? doc-id :approve-doc override-auth-source])
     (rf/subscribe [::auth/doc-auth-reason doc-id :approve-doc])
     (rf/subscribe [::approvals/approved? doc-id path])])
  (fn [[ok? reason approved?]]
    (and (not approved?)
         ;; reason as last item, so this can return the reason code as return value
         (or ok? reason))))

(defn may-reject?
  "Can the target document or group be rejected?

  Options:
  * `doc-id` -- Document id
  * `approvable?` -- The `approvable`-flag from the schema: Can the target document or group be rejected?
  * `path` -- (Optional) If given, should point to the group to reject.
  * `override-auth-source` -- (Optional) Alternative auth model source."
  [{:keys [doc-id path override-auth-source approvable?]}]
  (and approvable? ;; from the schema
       (<sub [::may-reject? doc-id path override-auth-source])))

(defn may-approve?
  "Can the target document or group be approved?

  Options:
  * `doc-id` -- Document id
  * `approvable?` -- The `approvable`-flag from the schema: Can the target document or group be approved?
  * `path` -- (Optional) If given, should point to the group to approve.
  * `override-auth-source` -- (Optional) Alternative auth model source."
  [{:keys [doc-id path override-auth-source approvable?]}]
  (and approvable? ;; from the schema
       (<sub [::may-approve? doc-id path override-auth-source])))

(rf/reg-event-fx ::update-approval.success
  (fn [cofx [_ resp {:keys [app-id doc-id path approved?]}]]
    (nutil/merge-every-fn cofx
                          ;; Response structure is identical for both doc and group approval
                          [[approvals/update-approvals-fx resp (util/mmap doc-id)]
                           ;; Don't refresh auth-model if an individual group is approved/rejected
                           (when-not (seq path)
                             [auth/refresh-doc-auth-fx (util/mmap app-id doc-id)])
                           ;; Open (or close) the reject note editor when rejecting/approving
                           [reject-note/set-editor-toggle {:doc-id   doc-id
                                                           :path     path
                                                           :enabled? (not approved?)}]])))

(defn on-approve
  [{:keys [app-id doc-id path]}]
  (fn []
    (>evt [::command/update-doc-approval
           (util/assoc-when {:app-id     app-id
                             :doc-id     doc-id
                             :approved?  true
                             :on-success [::update-approval.success
                                          (util/mmap {:approved? true} app-id doc-id path)]}
                            :path (some-> path path/path->str))])))

(defn on-reject
  [{:keys [app-id doc-id path]}]
  (fn []
    (>evt [::command/update-doc-approval
           (util/assoc-when {:app-id     app-id
                             :doc-id     doc-id
                             :approved?  false
                             :on-success [::update-approval.success
                                          (util/mmap {:approved? false} app-id doc-id path)]}
                            :path       (some-> path path/path->str))])))
