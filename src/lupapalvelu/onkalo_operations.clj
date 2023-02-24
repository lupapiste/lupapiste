(ns lupapalvelu.onkalo-operations
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.archive.archiving-util :as archiving-util]
            [lupapalvelu.domain :as domain]
            [monger.operators :refer :all]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.util :as util]
            [taoensso.timbre :refer [debug info warn error]]))

(defn- update-archival-status-change [application attachment-id tila read-only modified deletion-explanation]
  (let [mongo-base-updates {:attachments.$.metadata.tila tila
                            :attachments.$.readOnly      (if read-only true false)
                            :attachments.$.modified      modified}
        mongo-updates (if (= tila :valmis) (assoc mongo-base-updates :archived.completed nil) mongo-base-updates)
        update-result (action/update-application (action/application->command application)
                                                 {:attachments {$elemMatch {:id attachment-id}}}
                                                 {$set mongo-updates}
                                                 :return-count? true)]
    (if (= update-result 1)
      (do
        (when (= tila :arkistoitu) (archiving-util/mark-application-archived-if-done application modified nil))
        (info "Onkalo originated status change to state" tila "for attachment" attachment-id "in application" (:id application) "with explanation" deletion-explanation "was successful")
        (resp/status 200 {}))
      (do
        ; At least verdicts can be removed from LP even after archiving, so they may no longer be found, but that is ok.
        (warn "Attachment" attachment-id "not found in application" (:id application) "- failed to change state to" tila)
        (resp/status 200 {})))))

(defn attachment-archiving-operation [application-id attachment-id target-state deletion-explanation]
  (let [application (domain/get-application-no-access-checking application-id)
        attachment (util/find-by-id attachment-id (:attachments application))
        attachment-state (-> attachment :metadata :tila (keyword))
        read-only (when (:readOnly attachment)
                    (or (= target-state :arkistoitu) ; Keep current readOnly status when re-archiving
                        (#{:paatoksenteko :katselmukset_ja_tarkastukset} (-> attachment :type :type-group keyword)))) ; Remove read-only from others, but keep for verdicts & tasks
        modified (sade.core/now)]
    (cond
      (not application)
      (do
        (warn "attachment-archiving-operation failed, application" application-id "not found")
        (resp/status 400 (str "Application " application-id " not found")))

      (= attachment-state target-state)
      (do
        (warn "attachment-archiving-operation failed for application"
              application-id ", attachment in state" attachment-state
              "but expected" target-state)
        (resp/status 400 (str "Cannot perform this operation when attachment in state " attachment-state)))

      (not (#{:valmis :arkistoitu} target-state))
      (do
        (warn "attachment-archiving-operation failed for application"
              application-id ", invalid target state" attachment-state)
        (resp/status 400 (str "Invalid state " attachment-state)))

      :else
      (update-archival-status-change application attachment-id target-state read-only modified deletion-explanation))))
