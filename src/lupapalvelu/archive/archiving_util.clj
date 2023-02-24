(ns lupapalvelu.archive.archiving-util
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [taoensso.timbre :as timbre]))

(defn metadata-query [md-key]
  (let [tila-key (str md-key ".tila")
        arkistointi-key (str md-key ".sailytysaika.arkistointi")]
    {tila-key {$ne :arkistoitu}
     arkistointi-key {$ne :ei
                      $exists true}}))

(def archived-ts-keys-schema
  {;; Kept optional for legacy reasons, so we do not have to do migration to create nil
   ;; values.
   (sc/optional-key :initial) (sc/maybe ssc/Timestamp)
   :application               (sc/maybe ssc/Timestamp)
   :completed                 (sc/maybe ssc/Timestamp)})

(defn mark-application-archived [application now & archived-ts-keys]
  (let [ts-ks (flatten archived-ts-keys)
        _     (assert (seq ts-ks) "No archived timestamp keys")
        m     (->> (zipmap ts-ks (cycle [now]))
                   (sc/validate (st/select-keys archived-ts-keys-schema ts-ks))
                   (util/map-keys (partial util/kw-path :archived)))]
    (action/update-application
      (action/application->command application)
      {$set (cond-> m
              (nil? (some-> application :archived :initial))
              (assoc :archived.initial now))})))

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

    (some->> [(when pre-verdict-docs-archived?
                (timbre/info "Setting pre-verdict archiving completed")
                :application)
              (when post-verdict-docs-archived?
                (timbre/info "Setting post-verdict archiving completed")
                :completed)]
             (remove nil?)
             seq
             (mark-application-archived application now))

    (when (and archiving-project? post-verdict-docs-archived?)
      (timbre/info "All documents archived for archiving project" id)
      (action/update-application
        (action/application->command application user)
        {:archived.application {$ne nil}
         :archived.completed   {$ne nil}
         :permitType           :ARK}
        (app-state/state-transition-update :archived now application (usr/summary user))))))

(defn set-extinct-metadata
  "Sets the apps and attachments' myyntipalvelu and nakyvyys archival metadata
  to the default values  of an extinct app unless it is already archived"
  [application created user]
  (let [command             (action/application->command application user)
        app-metadata        (:metadata application)
        update-app          (partial action/update-application command)
        extinct-metadata    {:nakyvyys "viranomainen"
                             :myyntipalvelu false}
        updated-attachments (->> (:attachments application)
                                 (filter #(-> % :metadata :tila (not= "arkistoitu"))))]
    (when (-> app-metadata :tila (not= "arkistoitu"))
      (update-app {$set {:modified created
                         :metadata (merge app-metadata extinct-metadata)}}))
    ;; Update individually with $elemMatch since mass updates with $ are forbidden
    (doseq [{:keys [id metadata]} updated-attachments]
      (update-app {:attachments {$elemMatch {:id id}}}
                  {$set (merge {:modified               created
                                :attachments.$.metadata (merge metadata extinct-metadata)})}))))
