(ns lupapalvelu.onkalo-operations
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [debug info warn error]]))

(defn- update-archival-status-change [application attachment-id tila readOnly modified explanation]
  (let [update-result (action/update-application (action/application->command application)
                                                 {:attachments {$elemMatch {:id attachment-id}}}
                                                 {$set {:attachments.$.metadata.tila tila
                                                        :attachments.$.readOnly      readOnly
                                                        :attachments.$.modified      modified}})]
    (if update-result
      (do
        (info "Onkalo originated status change to state" tila "for attachment" attachment-id "in application" (:id application) "with explanation" explanation "was successful")
        (ok))
      (fail :internal-server-error :desc "Could not change attachment archival status"))))

(defn undo-archiving [application-id attachment-id modified explanation]
  (let [application (domain/get-application-no-access-checking application-id)
        attachment (->> application :attachments (filter #(= attachment-id (:id %))) (first))
        attachment-state (-> attachment :metadata :tila)]
    (cond
      (not application) (fail :bad-request :desc (str "Application not found"))
      (= attachment-state "arkistoitu") (update-archival-status-change application attachment-id "valmis" false modified explanation)
      :else (fail :bad-request :desc (str "Cannot perform this operation when attachment in state " attachment-state)))))

(defn redo-archiving [application-id attachment-id modified explanation]
  (let [application (domain/get-application-no-access-checking application-id)
        attachment (->> application :attachments (filter #(= attachment-id (:id %))) (first))
        attachment-state (-> attachment :metadata :tila)]
    (cond
      (not application) (fail :bad-request :desc (str "Application not found"))
      (= attachment-state "valmis") (update-archival-status-change application attachment-id "arkistoitu" true modified explanation)
      :else (fail :bad-request :desc (str "Cannot perform this operation when attachment in state " attachment-state)))))
