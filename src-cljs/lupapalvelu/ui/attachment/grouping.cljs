(ns lupapalvelu.ui.attachment.grouping
  "Functionality for grouping attachments according to their state
  - missing or requiring user action
  - requiring authority action
  - ok
  And grouping attachments according to their type group or type."
  (:require [sade.shared-util :as util]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.attachment.shared :as shared]

            ))

(def post-verdict-states (set (js->clj js/LUPAPISTE.config.postVerdictStates)))

(def attachment-service js/lupapisteApp.services.attachmentsService)

(defn group-loc [group]
  (js/lupapisteApp.services.accordionService.attachmentAccordionName group))

(def type-group-order ["paapiirustus"
                       "rakennesuunnitelma"
                       "kvv_suunnitelma"
                       "iv_suunnitelma"
                       "other"])

(def type-order ["asemapiirros"
                 "pohjapiirustus"
                 "julkisivupiirustus"
                 "leikkauspiirustus"])

(defn type-sort
  "Sorts items according to their type's placement in the given order array."
  [order-array type]
  (let [index (.indexOf order-array type)]
    (if (= index -1)
      1000
      index)))

(defn whole-group-path-in-tags? [path attachment]
  (every? #(-> attachment :tags set (contains? %)) path))

(defn last-approval [{:keys [approvals latestVersion]}]
  (let [file-id-approval (some->> latestVersion :fileId keyword
                                  (get approvals))
        orig-id-approval (some->> latestVersion :originalFileId keyword
                                  (get approvals))
        ;; Default approval does not have a timestamp or user
        default-approval (util/find-first #(= (keys %) [:state])
                                          (vals approvals))]
    (or file-id-approval orig-id-approval default-approval)))

(defn last-approval-state-matching [attachment state]
  (let [approval (last-approval attachment)]
    (when (->> approval
               :state
               (= state))
      approval)))

(defn requires-authority-action? [attachment]
  (boolean (last-approval-state-matching attachment "requires_authority_action")))

(defn requires-user-action? [attachment]
  (boolean (last-approval-state-matching attachment "requires_user_action")))

(defn missing-or-requires-user-action? [{:keys [required notNeeded versions] :as attachment}]
  (boolean (or (and required
                    (not notNeeded)
                    (empty? versions))
               (and (requires-user-action? attachment)
                    (not (requires-authority-action? attachment))))))

(defn group-attachments-by-state [attachments]
  (let [{not-needed true others false}           (group-by (comp boolean :notNeeded) attachments)
        missing-or-requires-user-action-and-rest (group-by missing-or-requires-user-action?
                                                           others)
        requires-authority-action-and-rest       (group-by requires-authority-action?
                                                           (missing-or-requires-user-action-and-rest false))]
    {:incomplete (missing-or-requires-user-action-and-rest true)
     :updated    (requires-authority-action-and-rest true)
     :ok         (requires-authority-action-and-rest false)
     :not-needed not-needed}))

(defn attachments-by-group [attachments path]
  (filter (partial whole-group-path-in-tags? path)
          attachments))

(defn convert-tag-groups
  "Application attachments don't need the main group so we promote them up in the grouping."
  [tag-groups]
  (mapcat (fn [tag-group]
            (if (-> tag-group first (= "application"))
              (->> tag-group rest (map (comp vector first)))
              [tag-group]))
          tag-groups))
