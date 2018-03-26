(ns lupapalvelu.onkalo-operations
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.util :as util]
            [taoensso.timbre :as timbre :refer [debug info warn error]]))

(defn- update-archival-status-change [application attachment-id tila read-only modified explanation]
  (let [update-result (action/update-application (action/application->command application)
                                                 {:attachments {$elemMatch {:id attachment-id}}}
                                                 {$set {:attachments.$.metadata.tila (name tila)
                                                        :attachments.$.readOnly      read-only
                                                        :attachments.$.modified      modified}})]
    (if update-result
      (do
        (info "Onkalo originated status change to state" (name tila) "for attachment" attachment-id "in application" (:id application) "with explanation" explanation "was successful")
        (ok))
      (fail :internal-server-error :desc "Could not change attachment archival status"))))

(defn attachment-archiving-operation [application-id attachment-id operation explanation]
  (let [application (domain/get-application-no-access-checking application-id)
        attachment (util/find-by-id attachment-id (:attachments application))
        attachment-state (-> attachment :metadata :tila (keyword))
        tila (if (= operation :undo-archiving) :valmis :arkistoitu)
        modified (sade.core/now)]
    (cond
      (not application) (fail :bad-request :desc (str "Application not found"))
      (= attachment-state tila) (fail :bad-request :desc (str "Cannot perform this operation when attachment in state " attachment-state))
      (= attachment-state :arkistoitu) (update-archival-status-change application attachment-id :valmis false modified explanation)
      (= attachment-state :valmis) (update-archival-status-change application attachment-id :arkistoitu true modified explanation)
      :else (fail :bad-request :desc "Unknown error"))))
