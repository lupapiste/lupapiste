(ns lupapalvelu.archive.archiving-util
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]))

(defn metadata-query [md-key]
  (let [tila-key (str md-key ".tila")
        arkistointi-key (str md-key ".sailytysaika.arkistointi")]
    {tila-key {$ne :arkistoitu}
     arkistointi-key {$ne :ei
                      $exists true}}))

(defn mark-application-archived-if-done [{:keys [id permitType] :as application} now user]
  ; If these queries return 0 results, we mark the corresponding phase as archived
  (let [attachment-or-app-md (metadata-query "metadata")
        archiving-project? (= (keyword permitType) :ARK)
        pre-verdict-query {:_id id
                           ; Look for pre-verdict attachments that have versions, are not yet archived, but need to be
                           $or  (remove
                                  nil?
                                  [{:archived.application {$ne nil}}
                                   {:tosFunction nil}
                                   {:attachments {$elemMatch (merge {:applicationState {$nin [states/post-verdict-states]}
                                                                     :versions         {$gt []}}
                                                                    attachment-or-app-md)}}
                                   ; Check if the application itself is not yet archived, but needs to be
                                   (when-not archiving-project? attachment-or-app-md)])}
        post-verdict-query {:_id id
                            ; Look for any attachments that have versions, are not yet archived, but need to be
                            $or  (remove
                                   nil?
                                   [{:tosFunction nil}
                                    {:attachments {$elemMatch (merge {:versions {$gt []}}
                                                                     attachment-or-app-md)}}
                                    ; Check if the application itself is not yet archived, but needs to be
                                    (when-not archiving-project? attachment-or-app-md)
                                    ; Check if the case file is not yet archived, but needs to be
                                    (when-not archiving-project? (metadata-query "processMetadata"))
                                    ; Check if the application is not in a final state
                                    {:state {$nin (vec states/archival-final-states)}}])}
        pre-verdict-docs-archived? (zero? (mongo/count :applications pre-verdict-query))
        post-verdict-docs-archived? (zero? (mongo/count :applications post-verdict-query))]

    (when pre-verdict-docs-archived?
      (timbre/info "Setting pre-verdict archiving completed")
      (action/update-application
        (action/application->command application)
        {$set {:archived.application now}}))

    (when post-verdict-docs-archived?
      (timbre/info "Setting post-verdict archiving completed")
      (action/update-application
        (action/application->command application)
        {$set {:archived.completed now}}))

    (when (and archiving-project? post-verdict-docs-archived?)
      (timbre/info "All documents archived for archiving project" id)
      (action/update-application
        (action/application->command application user)
        {:archived.application {$ne nil}
         :archived.completed   {$ne nil}
         :permitType           :ARK}
        (app-state/state-transition-update :archived now application (usr/summary user))))))
