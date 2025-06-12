(ns lupapalvelu.batchrun.fetch-verdict
  (:require [lupapalvelu.action :refer [application->command]]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :as log :refer [info infof]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.pate.verdict-date :as verdict-date]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict :as verdict]
            [sade.core :refer :all]
            [sade.util :as util]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]))

(set! *warn-on-reflection* true)

(defn fetch-verdict
  [system batchrun-name batchrun-user {:keys [id permitType organization] :as app} & [foreman-doc-id]]
  {:pre [(map? system)]}
  (log/with-logging-context {:applicationId id, :userId (:id batchrun-user)}
    (infof "Checking verdict in db %s" (mongo/current-db-name))
    (try
      (let [command (assoc (application->command app)
                           :user batchrun-user
                           :created (now)
                           :action "fetch-verdicts"
                           :system system)
            result  (verdict/do-check-for-verdict command foreman-doc-id)]
        (when (-> result :verdicts count pos?)
          (when-not foreman-doc-id
            (verdict-date/update-verdict-date (:id app)))
          (infof "Found %s verdicts" (-> result :verdicts count))
          ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
          (log/log-event :info {:run-by batchrun-name :event "Found new verdict"})
          ;; no application state change when fetching foreman verdicts
          (when-not foreman-doc-id
            (notifications/notify! :application-state-change command)))
        (when (or (nil? result) (fail? result))
          (info "No verdicts found, result: " (if (nil? result) :error.no-app-xml result))
          (log/log-event :warning {:run-by       batchrun-name
                                   :event        "Failed to check verdict"
                                   :failure      (if (nil? result) :error.no-app-xml result)
                                   :organization {:id organization :permit-type permitType}
                                   }))
        true)
      (catch Throwable t
        (log/error t "Error fetching verdicts")
        (log/log-event :error {:run-by            batchrun-name
                               :event             "Unable to get verdict from backend"
                               :exception-message (.getMessage t)
                               :application-id    id
                               :organization      {:id organization :permit-type permitType}})
        ;; ACK the message even if e.g. the connection failed - a new message will be generated the next night anyway
        ;; and usually the issue won't be resolved immediately so that an immediate retry would help
        true))))

(defn fetch-verdict-message [app-id organization]
  {:id           app-id
   :organization organization})

(defn publish-to-queue [system organization id & [foreman-doc-id]]
  (if-let [publisher (components/system->publisher system :batchrun/fetch-verdicts)]
    (publisher
      (util/assoc-when (fetch-verdict-message id organization)
                       :foreman-doc-id foreman-doc-id))
    (log/warn "Ignoring, no publisher for :batchrun/fetch-verdicts found!")))

(sc/defn ^:always-validate fetch-verdict-attachments
  "Fetches the missing verdict attachments for the target applications (as defined by
  `options`)."
  [system
   options :- verdict/MissingOptions]
  {:pre [(map? system)]}
  (let [app-infos   (verdict/applications-with-missing-verdict-attachments options)
        eraajo-user (usr/batchrun-user (map :organization app-infos))]
    (->> (doall
           (map
             (fn [{:keys [id organization permitType]}]
               (log/with-logging-context {:applicationId id
                                          :userId        (:id eraajo-user)}
                 (try+
                   (log/log-event :info {:run-by         "Automatic verdict attachments checking"
                                         :event          "Fetching verdict attachments"
                                         :application-id id})
                   (let [app     (domain/get-application-no-access-checking id [:state :municipality :documents
                                                                                :address :permitType
                                                                                :permitSubtype :organization
                                                                                :primaryOperation :verdicts
                                                                                :attachments])
                         command (assoc (application->command app)
                                        :user eraajo-user
                                        :created (now)
                                        :action :fetch-verdict-attachments
                                        :system system)
                         app-xml (krysp-fetch/get-valid-xml-by-application-id app)
                         result  (verdict/update-verdict-attachments-from-xml! command app-xml)]
                     (when (-> result :updated-verdicts count pos?)
                       ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
                       (log/log-event :info {:run-by           "Automatic verdict attachments checking"
                                             :event            "Found new verdict attachments"
                                             :application-id   id
                                             :updated-verdicts (-> result :updated-verdicts)}))
                     (when (or (nil? result) (fail? result))
                       (log/log-event :warning {:run-by         "Automatic verdict attachment checking"
                                                :event          "Failed to check verdict attachments"
                                                :application-id id
                                                :failure        (if (nil? result) :error.no-app-xml result)
                                                :organization   {:id organization :permit-type permitType}}))

                     ;; Return result for testing purposes
                     result)
                   (catch [:sade.core/type :sade.core/fail :text "error.no-legacy-available"] _
                     (log/log-event :warning {:run-by         "Automatic verdict attachments checking"
                                              :application-id id
                                              :event          "No legacy available"}))
                   (catch [:sade.core/type :sade.core/fail] {:keys [text]}
                     (log/log-event :warning {:run-by         "Automatic verdict attachments checking"
                                              :event          "fail!"
                                              :application-id id
                                              :text           text}))
                   (catch Throwable t
                     (log/log-event :error {:run-by            "Automatic verdict attachments checking"
                                            :event             "Unable to get verdict from backend"
                                            :exception-message (.getMessage t)
                                            :application-id    id})))))
             app-infos))
         (remove #(empty? (:updated-verdicts %)))
         (hash-map :updated-applications)
         (merge options {:applications (map :id app-infos)}))))
