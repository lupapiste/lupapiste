(ns lupapalvelu.ui.attachment.shared
  "Shared events, subs and functions."
  (:require [lupapalvelu.ui.authorization :as auth]
            [re-frame.core :as rf]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(def attachment-service js/lupapisteApp.services.attachmentsService)
(def assignment-service js/lupapisteApp.services.assignmentService)

(defn- status-key-path [attachment-or-id status-key]
   [::row-status (:id attachment-or-id attachment-or-id) status-key])

(defn- status-field [db attachment-or-id status-key]
  (get-in db (status-key-path attachment-or-id status-key)))

(defn- set-status-field [db attachment-or-id status-key value]
  (assoc-in db (status-key-path attachment-or-id status-key) value))

(defn all-open-assignments []
  (->> (js->clj (.automaticAssignments assignment-service) :keywordize-keys true)
       (map #(assoc % :automatic? true))
       (concat (js->clj (.assignments assignment-service) :keywordize-keys true))
       (remove (fn [assn]
                 (-> assn :currentState :type (= "completed"))))))

(defn- trim-join [& xs]
  (some->> (map ss/trim xs)
           (ss/join-non-blanks " ")
           ss/blank-as-nil))

(defn person-name
  "Last First, where either one can be missing. Nil if both are missing. If optional
  `first-last?` is true, the name is ordered First Last (default false)."
  ([{:keys [firstName lastName]} first-last?]
   (apply trim-join (cond->> [lastName firstName]
                      first-last? reverse)))
  ([user]
   (person-name user false)))

(defn latest-version-signatures
  "Unique signatories for the latest version of the attachment (or nil)."
  [{:keys [latestVersion signatures]}]
  (when-let [file-id (:fileId latestVersion)]
    (some->> signatures
             (keep #(when (= (:fileId %) file-id)
                      (person-name (:user %) true)))
             distinct
             seq)))

(defn archival-error
  "Permanent archive is enabled but the attachment is not archivable. Result is either the
  error message loc-key or nil."
  [{:keys [latestVersion]}]
  (when (auth/application-auth? :permanent-archive-enabled)
    (let [{:keys [archivable archivabilityError]} latestVersion]
      (when-not archivable
        archivabilityError))))

(defn all-attachment-assignments [db]
  (->> (::assignments db)
       (filter #(some-> % :targets first :group (= "attachments")))
       not-empty))

(defn raw-attachment [attachment-id]
  (js->clj (.rawAttachment attachment-service attachment-id)
           :keywordize-keys true))

(defn- refresh-attachments
  [db]
  (let [current     (js->clj (.attachmentTimestamps attachment-service))
        old         (into {} (map (juxt :id :__touched) (vals (::attachments db))))
        updated-ids (keep (fn [[id ts]]
                            (when-not (= ts (get old id))
                              id))
                          current)]
    (update db ::attachments
            (fn [m]
              (into (select-keys m (keys current))
                    (keep (fn [id]
                            (some->> (raw-attachment id) (vector id) ))
                          updated-ids))))))

(defn attachments [db]
  (some-> db ::attachments vals seq))

(defn attachment [db attachment-or-id]
  (get-in db [::attachments (cond-> attachment-or-id
                              (map? attachment-or-id) :id)]))

;; ---------------------------
;; Events
;; ---------------------------

(rf/reg-event-db
  ::refresh-attachments
  (fn [db]
    (refresh-attachments db)))

(rf/reg-event-db
  ::set-expanded
  (fn [db [_ attachment-or-id expanded?]]
    (set-status-field db attachment-or-id :expanded? expanded?)))

(rf/reg-event-db
  ::set-editing
  (fn [db [_ attachment-or-id & value]]
    (set-status-field db attachment-or-id :editing value)))

(rf/reg-event-db
  ::stop-editing
  (fn [db [_ attachment-or-id]]
    (set-status-field db attachment-or-id :editing nil)))

(rf/reg-event-db ::refresh-assignments
  (fn [db]
    (assoc db ::assignments (all-open-assignments))))

;; ---------------------------
;; Subs
;; ---------------------------

(rf/reg-sub
  ::attachments
  (fn [db _]
    (attachments db)))

(rf/reg-sub
  ::attachments-for-ids
  ;; We need the whole attachments map.
  (fn [{:keys [::attachments]} [_ & ids]]
    (some->> (flatten ids)
             seq
             (keep #(get attachments %)))))

(rf/reg-sub
  ::expanded?
  (fn [db [_ attachment-or-id]]
    (status-field db attachment-or-id :expanded?)))

(rf/reg-sub
  ::editing
  (fn [db [_ attachment-or-id]]
    (status-field db attachment-or-id :editing)))

(rf/reg-sub ::assignments #(::assignments %))

(rf/reg-sub
  ::all-attachment-assignments
  (fn [db _]
    (all-attachment-assignments db)))

(rf/reg-sub
  ::attachment-assignments
  :<- [::all-attachment-assignments]
  (fn [assignments [_ attachment]]
    (some->> assignments
             (filter (fn [{:keys [targets]}]
                       (util/find-by-id (:id attachment) targets)))
             not-empty)))

(rf/reg-sub
  ::latest-version-signatures
  (fn [db [_ attachment-or-id]]
    (latest-version-signatures (attachment db attachment-or-id))))

(rf/reg-sub
  ::archival-error
  (fn [db [_ attachment-or-id]]
    (archival-error (attachment db attachment-or-id))))
