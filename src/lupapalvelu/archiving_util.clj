(ns lupapalvelu.archiving-util
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [monger.operators :refer :all]))

(defn metadata-query [md-key]
  (let [tila-key (str md-key ".tila")
        arkistointi-key (str md-key ".sailytysaika.arkistointi")]
    {tila-key {$ne :arkistoitu}
     arkistointi-key {$ne :ei
                      $exists true}}))

(defn mark-application-archived-if-done [{:keys [id] :as application} now]
  ; If these queries return 0 results, we mark the corresponding phase as archived
  (let [attachment-or-app-md (metadata-query "metadata")
        pre-verdict-query {:_id id
                           ; Look for pre-verdict attachments that have versions, are not yet archived, but need to be
                           $or  [{:archived.application {$ne nil}}
                                 {:tosFunction nil}
                                 {:attachments {$elemMatch (merge {:applicationState {$nin [states/post-verdict-states]}
                                                                   :versions         {$gt []}}
                                                                  attachment-or-app-md)}}
                                 ; Check if the application itself is not yet archived, but needs to be
                                 attachment-or-app-md]}
        post-verdict-query {:_id id
                            ; Look for any attachments that have versions, are not yet arcvhived, but need to be
                            $or  [{:archived.completed {$ne nil}}
                                  {:tosFunction nil}
                                  {:attachments {$elemMatch (merge {:versions {$gt []}}
                                                                   attachment-or-app-md)}}
                                  ; Check if the application itself is not yet archived, but needs to be
                                  attachment-or-app-md
                                  ; Check if the case file is not yet archived, but needs to be
                                  (metadata-query "processMetadata")
                                  ; Check if the application is not in a final state
                                  {:state {$nin [:closed :extinct :foremanVerdictGiven :acknowledged]}}]}]

    (when (zero? (mongo/count :applications pre-verdict-query))
      (action/update-application
        (action/application->command application)
        {$set {:archived.application now}}))

    (when (zero? (mongo/count :applications post-verdict-query))
      (action/update-application
        (action/application->command application)
        {$set {:archived.completed now}}))))