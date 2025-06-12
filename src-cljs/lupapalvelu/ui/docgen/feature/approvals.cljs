(ns lupapalvelu.ui.docgen.feature.approvals
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

;;; Notes

;; Some notes on how the approval system seems to work

;; Document approval cascades:
;; - Approving a document will also approve all the child groups
;; - Rejecting a document will also reject all the child groups
;; - These cascaded approvals will set the `override` flag on the child
;;   groups but will not include e.g. who approved/rejected them.

;; Group approvals
;; - Approving a group will not alter the approval state of the document
;; - UNLESS
;;    a) all the child groups are approved
;;    b) the document approval is neutral (iow. not explicitly rejected)
;;    => document will be approved
;; - Rejecting a group will clear any approval state on the document
;; - Rejecting all groups will not do anything special
;; - Approving / rejecting a group will store e.g. who performed the approval.

;; Changes:
;; - If a form field is modified after approval/rejection, the approval state is cleared (`changed`).
;; - This applies both to the group and the document.

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [details]}]]
  (let [approvals (-> (:applicationApprovals details)
                      (update-keys name))]
    {:db (assoc db ::approvals approvals)}))

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::approvals)})

(rf/reg-sub ::approvals :-> ::approvals)

(rf/reg-sub ::approval-entries
  :<- [::approvals]
  (fn [approvals [_ doc-id]]
    (-> (get approvals doc-id))))

(defn- document-approval
  [entries]
  (let [{:keys [changed value]
         :as   approval} (:_root entries)
        value            (when-not changed
                           value)
        fields           (if value
                           [:timestamp :user :note]
                           [:note])]
    (merge (select-keys approval fields)
           {:value            value
            :summary-rejected (boolean
                                (or (= "rejected" value)
                                    (and (= "approved" value)
                                         (some (fn [[id {:keys [override changed value]}]]
                                                 (and (not= :_root id)
                                                      (not override)
                                                      (not changed)
                                                      (= "rejected" value)))
                                               entries))))})))

(defn- group-approval
  [entries path]
  (let [path (keyword (path/path->str path))]
    (when-let [{:keys [override changed] :as approval} (get entries path)]
      (let [value  (or override
                       (and (not changed)
                            (:value approval)))
            fields (if (and (not override)
                            (:value approval))
                     #{:timestamp :user :note}
                     #{:note})]
        (merge (select-keys approval fields)
               {:value        value
                :show-status? (boolean (and (not override) value))})))))

;; NOTE: The optional `path` dictates whether we are interested in the document approval
;; or a particular group

(rf/reg-sub ::approval
  (fn approval-inputfn [[_ doc-id _path]]
    {:pre [doc-id]}
    (rf/subscribe [::approval-entries doc-id]))
  (fn [entries [_ _doc-id path]]
    (if (seq path)
      (group-approval entries path)
      (document-approval entries))))

(defn update-approvals-fx
  "Stores the result of the `:approve-doc`/`:reject-doc` commands."
  [{:keys [db]} [_ {:keys [documentApprovals]} {:keys [doc-id]}]]
  {:db (assoc-in db [::approvals doc-id] documentApprovals)})

(rf/reg-sub ::has-value?
  (fn has-value-inputfn [[_ doc-id path]]
    {:pre [doc-id]}
    (rf/subscribe [::approval doc-id path]))
  (fn [{:keys [value note]}]
    (or (some? (ss/blank-as-nil value))
        (some? (ss/blank-as-nil note)))))

(rf/reg-sub ::approved-doc-ids
  :<- [::approvals]
  (fn [approvals]
    (->> approvals
         (filter (fn [[_doc-id entries]]
                   (= "approved" (:value (document-approval entries)))))
         keys
         set)))

(rf/reg-sub ::approved?
  (fn approved-inputfn [[_ doc-id path]]
    {:pre [doc-id]}
    (rf/subscribe [::approval doc-id path]))
  (fn [approval]
    (= "approved" (:value approval))))

(rf/reg-sub ::rejected?
  (fn rejected-inputfn [[_ doc-id path]]
    {:pre [doc-id]}
    (rf/subscribe [::approval doc-id path]))
  (fn [approval]
    (= "rejected" (:value approval))))

(rf/reg-sub ::summary-rejected?
  (fn summary-rejected-inputfn [[_ doc-id path]]
    {:pre [doc-id]}
    (rf/subscribe [::approval doc-id path]))
  :-> (comp boolean :summary-rejected))

(rf/reg-sub ::details
  (fn details-inputfn [[_ doc-id path]]
    {:pre [doc-id]}
    [(rf/subscribe [::approval doc-id path])
     (rf/subscribe [::approved? doc-id path])
     (rf/subscribe [::rejected? doc-id path])])
  (fn [[{:keys [user timestamp]} approved? rejected?]]
    (when (and user timestamp)
      (str (common/loc (if approved?
                         :document.approved
                         :document.rejected))
           " "
           (common/format-timestamp-with-time timestamp)
           ": "
           (:firstName user)
           " "
           (:lastName user)))))
