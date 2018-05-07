(ns lupapalvelu.onkalo-operations
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.archiving-util :as archiving-util]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [noir.response :as resp]
            [sade.core :refer :all]
            [sade.util :as util]
            [taoensso.timbre :as timbre :refer [debug info warn error]]))

(defn- update-archival-status-change [application attachment-id archivist tila read-only modified deletion-explanation]
  (let [mongo-base-updates {:attachments.$.metadata.tila tila
                            :attachments.$.readOnly      read-only
                            :attachments.$.modified      modified}
        mongo-updates (if (= tila :valmis) (assoc mongo-base-updates :archived.completed nil) mongo-base-updates)
        update-result (action/update-application (action/application->command application)
                                                 {:attachments {$elemMatch {:id attachment-id}}}
                                                 {$set mongo-updates}
                                                 :return-count? true)]
    (if (= update-result 1)
      (do
        (when (= tila :arkistoitu) (archiving-util/mark-application-archived-if-done application modified (usr/get-user-by-id (:id archivist))))
        (info "Onkalo originated status change to state" tila "for attachment" attachment-id "in application" (:id application) "with explanation" deletion-explanation "was successful")
        (resp/status 200 {}))
      (resp/status 500 "Could not change attachment archival status"))))

(defn attachment-archiving-operation [application-id attachment-id archivist target-state deletion-explanation]
  (let [application (domain/get-application-no-access-checking application-id)
        attachment (util/find-by-id attachment-id (:attachments application))
        attachment-state (-> attachment :metadata :tila (keyword))
        read-only (= target-state :arkistoitu)
        modified (sade.core/now)]
    (cond
      (not application) (resp/status 400 (str "Application " application-id " not found"))
      (= attachment-state target-state) (resp/status 400 (str "Cannot perform this operation when attachment in state " attachment-state))
      (not (#{:valmis :arkistoitu} target-state)) (resp/status 400 (str "Invalid state " attachment-state))
      :else (update-archival-status-change application attachment-id archivist target-state read-only modified deletion-explanation))))
