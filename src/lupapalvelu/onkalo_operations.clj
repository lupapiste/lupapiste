(ns lupapalvelu.onkalo-operations
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [taoensso.timbre :as timbre :refer [debug info warn error]]))

(defn undo-archiving [application-id attachment-id modified]
  (let [application (domain/get-application-no-access-checking application-id)
        attachment (->> application :attachments (filter #(= attachment-id (:id %))) (first))
        attachment-state (-> attachment :metadata :tila (keyword))]
    (if (= attachment-state :arkistoitu)
      (action/update-application (action/application->command application)
                                 {:attachments {$elemMatch {:id attachment-id}}}
                                 {$set {:attachments.$.metadata.tila :valmis
                                        :attachments.$.readOnly      false
                                        :attachments.$.modified      modified}})
      (fail :bad-request :desc (str "Cannot perform this operation when attachment in state " attachment-state)))))

(defn redo-archiving [application-id attachment-id modified]
  (let [application (domain/get-application-no-access-checking application-id)
        attachment (->> application :attachments (filter #(= attachment-id (:id %))) (first))
        attachment-state (-> attachment :metadata :tila (keyword))]
    (if (= attachment-state :valmis)
      (action/update-application (-> application-id
                                 (domain/get-application-no-access-checking)
                                 action/application->command)
                             {:attachments {$elemMatch {:id attachment-id}}}
                             {$set {:attachments.$.metadata.tila :arkistoitu
                                    :attachments.$.readOnly      true
                                    :attachments.$.modified      modified}})
      (fail :bad-request :desc (str "Cannot perform this operation when attachment in state " attachment-state)))))
