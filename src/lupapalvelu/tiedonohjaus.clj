(ns lupapalvelu.tiedonohjaus
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman-application-util :refer [foreman-app?]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.logging :refer [error errorf warn with-logging-context]]
            [lupapalvelu.organization :as o]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.user :as usr]
            [lupapalvelu.xml.util :as xml-util]
            [lupapiste-commons.schema-utils :as schema-utils]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [monger.operators :refer :all]
            [sade.core :refer [fail!]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [jakarta.xml.bind JAXB JAXBContext]
           [java.io StringReader]
           [java.util Date GregorianCalendar]
           [javax.xml.datatype DatatypeFactory]
           [lupapalvelu.tiedonohjaus AccessRightType
                                     ActionEvent
                                     ActionType
                                     AgentType
                                     CaseFile
                                     ClassificationScheme
                                     Custom
                                     PersonalDataType
                                     ProtectionLevelType
                                     PublicityClassType
                                     RecordType
                                     RestrictionType
                                     SecurityClassType]))

(set! *warn-on-reflection* true)

(defn get-from-toj-api [organization-id coerce? & path-parts]
  (when (:permanent-archive-enabled (o/get-organization organization-id))
    (let [url (apply str (env/value :toj :host) "/tiedonohjaus/api/org/" organization-id "/asiat" (when path-parts "/")
                     path-parts)
          {:keys [^long status body]} (http/get url (cond-> {:throw-exceptions false}
                                                      coerce? (assoc :as :json)))]
      (case (long status)
        200 body
        502 (error "Error accessing TOJ API:" body)
        nil))))

(defn- get-tos-functions-from-toj [organization-id]
  (or (get-from-toj-api organization-id :coerce) []))

(def available-tos-functions
  (memo/ttl get-tos-functions-from-toj
            :ttl/threshold 20000))

(defn tos-function-with-name [tos-function-code organization-id]
  (when (and tos-function-code organization-id)
    (->> (available-tos-functions (name organization-id))
         (filter #(= tos-function-code (:code %)))
         (first))))

(defn linked-file-target-type->tos-type [target-type model-type]
  (case target-type
    "bim-model-file" {:type-group :tietomallit
                      :type-id (case model-type
                                 "architectural" :arkkitehtimalli_BIM
                                 "structural" :rakennemalli_BIM
                                 "technical" :talotekniikkamalli_BIM
                                 "location" :rakennuspaikkamalli_BIM)}
    "bim-inspection-report" {:type-group :tietomallien_liitteet :type-id :tarkastusraportti}
    "bim-additional-file" {:type-group :tietomallien_liitteet :type-id :muu_tietomallin_liite}))

(defn- get-metadata-for-linked-file-from-toj [organization tos-function linked-file]
  (let [target-type (get-in linked-file [:target-entity :target-type])
        model-type (get-in linked-file [:metadata :model-type])
        linked-file-type (linked-file-target-type->tos-type target-type model-type)
        linked-file-id (str (name (:type-group linked-file-type)) "." (name (:type-id linked-file-type)))]
    (get-from-toj-api organization :coerce tos-function "/document/" linked-file-id)))

(defn- get-metadata-for-document-from-toj [organization tos-function document-type]
  (if (and organization tos-function document-type)
    (let [doc-id (if (map? document-type)
                   (str (name (:type-group document-type)) "." (name (:type-id document-type)))
                   document-type)]
      (or (get-from-toj-api organization :coerce tos-function "/document/" doc-id) {}))
    {}))

(def metadata-for-document
  (memo/ttl get-metadata-for-document-from-toj
            :ttl/threshold 20000))

(def metadata-for-linked-file
  (memo/ttl get-metadata-for-linked-file-from-toj
            :ttl/threshold 20000))

(defn- get-metadata-for-process-from-toj [organization tos-function]
  (if (and organization tos-function)
    (or (get-from-toj-api organization :coerce tos-function) {})
    {}))

(def metadata-for-process
  (memo/ttl get-metadata-for-process-from-toj
            :ttl/threshold 20000))

(defn- ->date
  "`Date` instance that corresponds to `zoned-arg` or nil."
  [zoned-arg]
  (some-> (date/zoned-date-time zoned-arg) .toInstant Date/from))

(defn- paatospvm-plus-years [verdicts years]
  (some-> (vif/latest-published-verdict-date verdicts)
          (date/plus :years years)
          ->date))

(defn- timestamp-plus-years [submitted years]
  (some-> (date/zoned-date-time submitted)
          (date/plus :years years)
          ->date))

(defn retention-end-date
  "There is a possibility that the retention-end-date might 'change'.
  If, for example, an application is finished and then some attachments are archived,
  then the application is appealed, moves to finished again, and is then archived again,
  the dates from the first archiving will not match the new ones.
  This is of course also possible with verdicts given a similar chain of events."
  ([{{:keys [arkistointi pituus]} :sailytysaika} verdicts primaryOperation submitted finished]
   (cond
     (util/not=as-kw :määräajan arkistointi)     nil
     (foreman-app? (util/mmap primaryOperation)) (timestamp-plus-years submitted pituus)
     (seq verdicts)                              (paatospvm-plus-years verdicts pituus)
     finished                                    (timestamp-plus-years finished pituus)))
  ([metadata application]
   (retention-end-date metadata
                       (vif/all-verdicts application)
                       (:primaryOperation application)
                       (:submitted application)
                       (:finished application))))

(defn- security-end-date [{:keys [salassapitoaika julkisuusluokka]} verdicts]
  (when (and (#{:osittain-salassapidettava :salainen} (keyword julkisuusluokka)) salassapitoaika (seq verdicts))
    (paatospvm-plus-years verdicts salassapitoaika)))

(defn update-end-dates
  [metadata  {:keys [primaryOperation submitted finished] :as application}]
  (let [verdicts      (vif/all-verdicts application)
        retention-end (retention-end-date metadata verdicts primaryOperation submitted finished)
        security-end  (security-end-date metadata verdicts)]
    (cond-> (-> (util/dissoc-in metadata [:sailytysaika :retention-period-end])
                (dissoc :security-period-end))
      retention-end (assoc-in [:sailytysaika :retention-period-end] retention-end)
      security-end  (assoc :security-period-end security-end))))

;; Unified metadata processing functions
;; ===================================================================================================================

(def editable-metadata-fields
  (->> (remove #(= tms/Tila %) tms/asiakirja-metadata-fields)
       (concat tms/common-metadata-fields)))

(defn revert-unauthorized-modifications [new-metadata old-metadata user-roles]
  (let [disallowed-metadata  (filter (fn [field]
                                       (when-let [role (:require-role field)]
                                         (not (contains? user-roles role))))
                                     editable-metadata-fields)
        disallowed-keys      (map :type disallowed-metadata)
        replacement-metadata (select-keys old-metadata disallowed-keys)]
    (merge new-metadata replacement-metadata)))

(defn sanitize-metadata [m]
  (try
    (tms/sanitize-metadata m)
    (catch Throwable t
      (warn t)
      (fail! "error.invalid.metadata"))))

(defn process-metadata-update
  "Unified metadata processing for documents, process, and linked files.

   Parameters:
   - :old-metadata - Existing metadata map
   - :new-metadata - New metadata to apply
   - :user-roles - Set of user roles for authorization checks
   - :schema (required) - TOS metadata schema
   - :application - Required when :update-dates? is true
   - :update-dates? - Call update-end-dates (default: true)
   - :set-tila? - Set :tila field (default: true)
   - :preserve-keys - Keys to preserve from old metadata"
  [{:keys [old-metadata new-metadata user-roles
           schema application update-dates?
           set-tila? preserve-keys]
    :or   {update-dates? true
           set-tila?     true}}]
  {:pre [schema]}
  (with-logging-context {:applicationId (:id application)}
    (let [coerced-old (schema-utils/coerce-metadata-to-schema schema old-metadata)
          coerced-new (schema-utils/coerce-metadata-to-schema schema new-metadata)
          processed   (cond-> (revert-unauthorized-modifications coerced-new coerced-old user-roles)
                        update-dates?  (update-end-dates application)
                        set-tila?      (assoc :tila (or (:tila coerced-old) :luonnos)))
          sanitized   (sanitize-metadata processed)]
      (cond-> sanitized
        preserve-keys (merge (select-keys coerced-old preserve-keys))))))

(defn merge-linked-file-metadata-update
  "Merge linked file metadata in a way that preserves
  existing :model-type, :model-phase, :name :tila and :nakyvyys
  metadata fields."
  [application metadata new-metadata]
  (let [existing-tila      (:tila metadata)
        existing-nakyvyys  (:nakyvyys metadata)
        ;; This might need some further investigation on how we define these and where
        file-metadata      (select-keys metadata [:model-type :model-phase :name :document-identifier :document-date :ryhti])
        processed-metadata (cond-> new-metadata
                             existing-tila           (assoc :tila (keyword existing-tila))
                             true                    (update-end-dates application)
                             (and (not (:nakyvyys new-metadata))
                                  existing-nakyvyys) (assoc :nakyvyys existing-nakyvyys))]
    (merge file-metadata processed-metadata)))

(defn linked-file-with-updated-metadata
  "Returns linked file metadata after TOS metadata updates have been
  applied. Only the files that have not been archived or marked as
  defective will have updated metadata."
  [{:keys [metadata] :as linked-file} organization tos-function application]
  (if (#{:arkistoidaan :arkistoitu :ei-arkistoida-virheellinen} (keyword (:tila metadata)))
    linked-file
    (if (env/feature? :bim-archiving)
      (let [new-metadata (metadata-for-linked-file organization tos-function linked-file)]
        (assoc linked-file :metadata (merge-linked-file-metadata-update application metadata new-metadata)))
      linked-file)))

(defn document-with-updated-metadata [{:keys [metadata] :as document} organization tos-function application & [type]]
  (if (#{:arkistoidaan :arkistoitu :ei-arkistoida-virheellinen} (keyword (:tila metadata)))
    ;; Do not update metadata for documents that are already archived (or generated review
    ;; documents that have been marked as faulty by the authority)
    document
    (let [document-type      (or type (:type document))
          existing-tila      (:tila metadata)
          existing-nakyvyys  (:nakyvyys metadata)
          new-metadata       (metadata-for-document organization tos-function document-type)
          processed-metadata (cond-> new-metadata
                               existing-tila           (assoc :tila (keyword existing-tila))
                               true                    (update-end-dates application)
                               (and (not (:nakyvyys new-metadata))
                                    existing-nakyvyys) (assoc :nakyvyys existing-nakyvyys))]
      (assoc document :metadata processed-metadata))))

(defn- get-tos-toimenpide-for-application-state-from-toj [organization tos-function state]
  (if (and organization tos-function state)
    (or (get-from-toj-api organization :coerce tos-function "/toimenpide-for-state/" state) {})
    {}))

(def toimenpide-for-state
  (memo/ttl get-tos-toimenpide-for-application-state-from-toj
            :ttl/threshold 20000))

(defn- full-name [{:keys [lastName firstName]}]
  (str lastName " " firstName))

(defn- get-documents-from-application [application]
  [{:type     :hakemus
    :category :document
    :ts       (:created application)
    :user     (or (:applicant application) "")
    :id       (str (:id application) "-application")}])

(defn- get-attachments-from-application [application]
  (reduce (fn [acc attachment]
            (if-let [versions (seq (:versions attachment))]
              (->> versions
                   (map (fn [ver]
                          {:type     (:type attachment)
                           :category :document
                           :version  (:version ver)
                           :ts       (:created ver)
                           :contents (:contents attachment)
                           :user     (full-name (:user ver))
                           :id       (:id attachment)}))
                   (concat acc))
              acc))
          []
          (:attachments application)))

(defn- get-statement-requests-from-application [application]
  (map (fn [stm]
         {:text     (get-in stm [:person :text])
          :category :request-statement
          :ts       (:requested stm)
          :user     (str "" (:name stm))}) (:statements application)))

(defn- get-neighbour-requests-from-application [application state category]
  (->> (:neighbors application)
       (map (fn [req] (when-let [status (util/find-first #(= (name state) (name (:state %))) (:status req))]
                        {:text     (get-in req [:owner :name])
                         :category category
                         :ts       (:created status)
                         :user     (if (#{:response-given-ok :response-given-comments} state)
                                     (full-name (:vetuma status))
                                     (full-name (:user status)))})))
       (remove nil?)))

(defn- get-review-requests-from-application [application]
  (reduce (fn [acc task]
            (if (and (:created task) (contains? #{"task-katselmus" "task-katselmus-backend"}
                                                (name (get-in task [:schema-info :name]))))
              (conj acc {:text     (:taskname task)
                         :category :request-review
                         :ts       (:created task)
                         :user     (full-name (:assignee task))})
              acc)) [] (:tasks application)))


(defn- get-held-reviews-from-application [application]
  (reduce (fn [acc task]
            (if-let [held (get-in task [:data :katselmus :pitoPvm :modified])]
              (conj acc {:text     (:taskname task)
                         :category :review
                         :ts       held
                         :user     (full-name (:assignee task))})
              acc)) [] (:tasks application)))

(defn- tos-function-changes-from-history [history lang]
  (->> (filter :tosFunction history)
       (map (fn [{:keys [tosFunction correction user] :as item}]
              (merge item {:text     (str (:code tosFunction) " " (:name tosFunction)
                                          (when correction
                                            (str ", " (i18n/localize lang "tos.function.fix.reason") ": "correction)))
                           :category (if correction :tos-function-correction :tos-function-change)
                           :user     (full-name user)})))))

(defn- handler-name-and-role [handler handler-roles lang]
  (let [role (filter #(= (:roleId handler) (:id %)) handler-roles)]
    (str (full-name handler) " (" (get-in (first role) [:name (keyword lang)]) ")")))

(defn- handler-with-role [handler user handler-roles lang category]
  {:text     (handler-name-and-role handler handler-roles lang)
   :category category
   :user     (full-name user)})

(defn- handler-remove [handler-id history user handler-roles lang]
  (let [handler (:handler (last (->> (filter :handler history)
                                     (filter #(= handler-id (get-in % [:handler :id])))
                                     (filter #(not (contains? (:handler %) :removed))))))]
    (handler-with-role handler user handler-roles lang :handler-removed)))

(defn- handler-change [handler history handler-roles lang user]
  (let [old-handler (:handler (last (->> (filter :handler history)
                                         (filter #(= (:roleId handler) (get-in % [:handler :roleId])))
                                         (filter #(= (:id handler) (get-in % [:handler :id])))
                                         (filter #(not= (:userId handler) (get-in % [:handler :userId])))
                                         (sort-by :ts))))]
    {:text     (str (full-name old-handler) " -> " (handler-name-and-role handler handler-roles lang))
     :category :handler-change
     :user     (full-name user)}))

(defn- handler-changes-from-history [history organization-id lang]
  (let [handler-roles (:handler-roles (o/get-organization organization-id))]
    (->> (filter :handler history)
         (map (fn [{:keys [handler user] :as item}]
                (merge item (cond
                              (:new-entry handler) (handler-with-role handler user handler-roles lang :handler-added)
                              (:removed handler) (handler-remove (:id handler) history user handler-roles lang)
                              :else (handler-change handler history handler-roles lang user))))))))

(defn- get-neighbour-requests [application]
  (concat
    (get-neighbour-requests-from-application application :open :request-neighbor)
    (get-neighbour-requests-from-application application :mark-done :request-neighbor-done)
    (get-neighbour-requests-from-application application :response-given-ok :request-neighbor-responded)
    (get-neighbour-requests-from-application application :response-given-comments :request-neighbor-responded)))

(defn generate-case-file-data [{:keys [history organization] :as application} lang]
  (let [documents       (get-documents-from-application application)
        attachments     (get-attachments-from-application application)
        statement-reqs  (get-statement-requests-from-application application)
        neighbors-reqs  (get-neighbour-requests application)
        review-reqs     (get-review-requests-from-application application)
        reviews-held    (get-held-reviews-from-application application)
        tos-fn-changes  (tos-function-changes-from-history history lang)
        handler-changes (handler-changes-from-history history organization lang)
        all-docs        (sort-by :ts (concat handler-changes tos-fn-changes documents attachments statement-reqs neighbors-reqs
                                             review-reqs reviews-held))
        state-changes   (filter :state history)]
    (doall
      (map (fn [[{:keys [state ts info-loc user]} next]]
             (let [api-response (toimenpide-for-state organization (:tosFunction application) state)
                   action-name  (cond
                                  (:name api-response)         (:name api-response)
                                  (= state "complementNeeded") (i18n/localize lang "caseFile.complementNeeded")
                                  (= state "canceled")         (i18n/localize lang "caseFile.canceled")
                                  :else                        (i18n/localize lang "caseFile.stateNotSet"))
                                        ; History entries of legacy applications might not have all the timestamps
                   next-ts      (or (:ts next) 0)]
               (util/assoc-when {:action    action-name
                                 :start     ts
                                 :user      (full-name user)
                                 :documents (if ts
                                              (doall
                                                (filter
                                                  (fn [doc]
                                                    (if-let [doc-ts (:ts doc)]
                                                      (and (>= doc-ts ts) (or (nil? next) (< doc-ts next-ts)))
                                                      (error "document excluded from case file due to missing timestamp:" doc)))
                                                  all-docs))
                                              (do
                                                (errorf "not returning documents for state '%s' because the state has no timestamp in the history array"
                                                        state)
                                                []))}
                                :info (some->> info-loc (i18n/localize lang)))))
           (partition 2 1 nil state-changes)))))

(defn- document-metadata-final-state [{:keys [tila] :as metadata} application]
  (if (#{:arkistoitu :arkistoidaan} (keyword tila))
    ;; Must not alter already archived documents
    metadata
    (-> (assoc metadata :tila :valmis)
        (update-end-dates application))))

(defn mark-attachment-final! [{:keys [attachments] :as application} now attachment-or-id]
  (let [{:keys [id metadata]} (if (map? attachment-or-id)
                                attachment-or-id
                                (first (filter #(= (:id %) attachment-or-id) attachments)))]
    (when (seq metadata)
      (let [new-metadata (document-metadata-final-state metadata application)]
        (when-not (= metadata new-metadata)
          (action/update-application
            (action/application->command application)
            {:attachments {$elemMatch {:id id}}}
            {$set {:modified               now
                   :attachments.$.metadata new-metadata}}))))))

(defn mark-app-and-attachments-final! [app-id modified-ts & attachment-ids-to-ignore]
  (let [{:keys [metadata attachments processMetadata] :as application} (domain/get-application-no-access-checking app-id)
        attachments (remove #(contains? (set (remove nil? attachment-ids-to-ignore))
                                        (:id %))
                            attachments)]
    (when (seq metadata)
      (let [new-metadata (document-metadata-final-state metadata application)
            new-process-metadata (update-end-dates processMetadata application)]
        (when-not (and (= metadata new-metadata) (= processMetadata new-process-metadata))
          (action/update-application
            (action/application->command application)
            {$set {:modified        modified-ts
                   :metadata        new-metadata
                   :processMetadata new-process-metadata}}))
        (doseq [attachment attachments]
          (mark-attachment-final! application modified-ts attachment))))))

(defn- retention-key [{{:keys [pituus arkistointi]} :sailytysaika}]
  (let [kw-a (keyword arkistointi)]
    (cond
      (= :ikuisesti kw-a) Integer/MAX_VALUE
      (= :toistaiseksi kw-a) (- Integer/MAX_VALUE 1)
      (= (keyword "määräajan") kw-a) pituus)))

(defn- comp-sa [sailytysaika]
  (dissoc sailytysaika :perustelu))

(defn calculate-process-metadata [original-process-metadata application-metadata attachments]
  (let [metadatas (conj (map :metadata attachments) application-metadata)
        {max-retention :sailytysaika} (last (sort-by retention-key metadatas))]
    (if (and max-retention (not= (comp-sa (:sailytysaika original-process-metadata)) (comp-sa max-retention)))
      (assoc original-process-metadata :sailytysaika max-retention)
      original-process-metadata)))

(defn process-case-file-metadata-update
  "Process case file (process) metadata with process-specific end date and retention calculations."
  [old-metadata new-metadata user-roles application]
  (-> (process-metadata-update {:old-metadata  old-metadata
                                :new-metadata  new-metadata
                                :user-roles    user-roles
                                :schema        tms/MetaDataMap
                                :update-dates? false
                                :set-tila?     false})
      (update-end-dates application)
      (calculate-process-metadata (:metadata application) (:attachments application))))

(defn process-retention-period-updates [{:keys [metadata attachments processMetadata]} modified-ts]
  (let [new-process-md (calculate-process-metadata processMetadata metadata attachments)]
    (when-not (= processMetadata new-process-md)
      {$set {:modified        modified-ts
             :processMetadata new-process-md}})))

(defn update-process-retention-period
  "Update retention period of the process report to match the longest retention time of actionEvents document
   as per SAHKE2 operative system certification requirement 6.3"
  [app-id modified-ts]
  (let [application (domain/get-application-no-access-checking app-id)]
    (when-let [updates (process-retention-period-updates application modified-ts)]
      (action/update-application
        (action/application->command application)
        updates))))

(defn update-metadata-attribute! [{:keys [attachments] :as application} now attachment-id attr value]
  (let [{:keys [id metadata]} (first (filter #(= (:id %) attachment-id) attachments))]
    (when id
      (let [new-metadata (assoc metadata (keyword attr) value)]
        (when-not (= metadata new-metadata)
          (action/update-application
            (action/application->command application)
            {:attachments {$elemMatch {:id id}}}
            {$set {:modified               now
                   :attachments.$.metadata new-metadata}}
            :return-count? true))))))

(defn tos-history-entry [tos-function timestamp user & [correction-reason]]
  {:pre [(map? tos-function)]}
  {:tosFunction tos-function
   :ts          timestamp
   :user        (usr/summary user)
   :correction  correction-reason})

(defn update-tos-metadata [function-code {:keys [application created user] :as command} correction-reason]
  (if-let [tos-function-map (tos-function-with-name function-code (:organization application))]
    (let [orgId                        (:organization application)
          linked-files                 (linked-files/get-linked-application-files-for {:application-id (:id application)})
          updated-linked-files         (map #(linked-file-with-updated-metadata % orgId function-code application)
                                            linked-files)
          updated-attachments          (pmap #(document-with-updated-metadata % orgId function-code application)
                                             (:attachments application))
          {updated-metadata :metadata} (document-with-updated-metadata application orgId function-code application
                                                                       "hakemus")
          process-metadata             (calculate-process-metadata (metadata-for-process orgId function-code) updated-metadata
                                                                   updated-attachments)]
      (doseq [file updated-linked-files]
        (let [file-id        (get-in file [:target-entity :file-id])
              application-id (get-in file [:target-entity :application-id])
              query          {$and [{:target-entity.application-id application-id}
                                    {:target-entity.file-id file-id}]}]
          (linked-files/upsert-linked-file query (dissoc file :id))))
      (action/update-application command
                                 {$set  {:modified        created
                                         :tosFunction     function-code
                                         :metadata        updated-metadata
                                         :processMetadata process-metadata
                                         :attachments     updated-attachments}
                                  $push {:history (tos-history-entry tos-function-map created user correction-reason)}})
      true)
    false))

;;;; Case file XML creation
;;;; ===================================================================================================================

(defn- classification-xml [organization tos-function]
  (if-let [xml-str (get-from-toj-api organization false tos-function "/classification")]
    (with-open [reader (StringReader. xml-str)]
      (JAXB/unmarshal reader ClassificationScheme))
    (error "Could not get classification XML from TOJ API for" organization "/" tos-function)))

(defn- xml-date [ts-or-date]
  (when ts-or-date
    (let [factory (DatatypeFactory/newInstance)
          date (if (number? ts-or-date) (Date. (long ts-or-date)) ts-or-date)
          calendar (doto (GregorianCalendar.)
                     (.setTime date))]
      (.newXMLGregorianCalendar factory calendar))))

(defn- publicity-class-type [{:keys [julkisuusluokka]}]
  (case (keyword julkisuusluokka)
    :julkinen PublicityClassType/JULKINEN
    :salainen (PublicityClassType/fromValue "Salassa pidettävä")
    :osittain-salassapidettava (PublicityClassType/fromValue "Osittain salassapidettävä")))

(defn- personal-data-type [{:keys [henkilotiedot]}]
  (case (keyword henkilotiedot)
    :ei-sisalla (PersonalDataType/fromValue "ei sisällä henkilötietoja")
    :sisaltaa (PersonalDataType/fromValue "sisältää henkilötietoja")
    :sisaltaa-arkaluonteisia (PersonalDataType/fromValue "sisältää arkaluontoisia henkilötietoja")))

(defn- protection-level-type [{:keys [suojaustaso]}]
  (case (keyword suojaustaso)
    :ei-luokiteltu nil
    :suojaustaso4 ProtectionLevelType/IV
    :suojaustaso3 ProtectionLevelType/III
    :suojaustaso2 ProtectionLevelType/II
    :suojaustaso1 ProtectionLevelType/I))

(defn- security-class-type [{:keys [turvallisuusluokka]}]
  (case (keyword turvallisuusluokka)
    :ei-turvallisuusluokkaluokiteltu SecurityClassType/EI_TURVALLISUUSLUOKITELTU
    :turvallisuusluokka4 SecurityClassType/TURVALLISUUSLUOKKA_IV
    :turvallisuusluokka3 SecurityClassType/TURVALLISUUSLUOKKA_III
    :turvallisuusluokka2 SecurityClassType/TURVALLISUUSLUOKKA_II
    :turvallisuusluokka1 SecurityClassType/TURVALLISUUSLUOKKA_I))

(defn- build-restriction-type [{:keys [processMetadata]} lang]
  (let [r-type (doto (RestrictionType.)
                 (.setPublicityClass (publicity-class-type processMetadata))
                 (.setPersonalData (personal-data-type processMetadata)))]
    (when-not (= :julkinen (keyword (:julkisuusluokka processMetadata)))
      (doto r-type
        (.setSecurityPeriod (BigInteger/valueOf (:salassapitoaika processMetadata)))
        (.setSecurityPeriodEnd (xml-date (:security-period-end processMetadata)))
        (.setSecurityReason (:salassapitoperuste processMetadata))
        (.setProtectionLevel (protection-level-type processMetadata))
        (.setSecurityClass (security-class-type processMetadata)))
      (.add (.getAccessRight r-type) (doto (AccessRightType.)
                                       (.setName (i18n/localize lang (:kayttajaryhma processMetadata)))
                                       (.setRole (i18n/localize lang (:kayttajaryhmakuvaus processMetadata))))))
    r-type))

(defn- agent-type [role name]
  (doto (AgentType.)
    (.setRole role)
    (.setName name)))

(defn- action-subevent [{:keys [correction user ts tosFunction text category]} lang]
  (let [title (case category
                :document (i18n/localize lang "caseFile.documentSubmitted")
                :request-statement (i18n/localize lang "caseFile.operation.statement.request")
                :request-neighbor (i18n/localize lang "caseFile.operation.neighbor.request")
                :request-neighbor-done (i18n/localize lang "caseFile.operation.neighbor.request.done")
                :request-neighbor-responded (i18n/localize lang "caseFile.operation.neighbor.responded")
                :request-review (i18n/localize lang "caseFile.operation.review.request")
                :review (i18n/localize lang "caseFile.operation.review")
                :tos-function-change (i18n/localize lang "caseFile.tosFunctionChange")
                :tos-function-correction (i18n/localize lang "caseFile.tosFunctionCorrection")
                :handler-change (i18n/localize lang "caseFile.handlerChange")
                :handler-added (i18n/localize lang "caseFile.handlerAdded")
                :handler-removed (i18n/localize lang "caseFile.handlerRemoved"))
        description (str title ": " text)
        event (ActionEvent.)]
    (when-not (ss/blank? user)
      (.add (.getAgent event) (agent-type "registrar" user)))
    (doto event
      (.setDescription description)
      (.setCreated (xml-date ts))
      (.setFunction (:code tosFunction))
      (.setType (name category))
      (.setCorrectionReason correction))))

(defn- custom-type [lang documents]
  (let [custom-obj (Custom.)]
    (->> documents
         (map #(action-subevent % lang))
         vec
         (.addAll (.getActionEvent custom-obj)))
    custom-obj))

(defn- record-type [{:keys [type id ts version contents user]} lang]
  (let [record-obj (RecordType.)
        type-str (if (map? type) (str (:type-group type) "." (:type-id type)) (name type))
        loc-key (if (map? type) (str "attachmentType." type-str) type-str)]
    (.add (.getCreated record-obj) (xml-date ts))
    (when version
      (.setVersion record-obj (str (:major version) "." (:minor version))))
    (when contents
      (.add (.getDescription record-obj) contents))
    (.add (.getAgent record-obj) (agent-type "registrar" user))
    (doto record-obj
      (.setTitle (i18n/localize lang loc-key))
      (.setType type-str)
      (.setNativeId id))))

(defn- action-type [{:keys [start action documents user]} lang]
  (let [action-obj (ActionType.)]
    (.add (.getAgent action-obj) (agent-type "registrar" user))
    (->> (filter #(= :document (:category %)) documents)
         (map #(record-type % lang))
         vec
         (.addAll (.getRecord action-obj)))
    (when-let [non-records (seq (remove #(= :document (:category %)) documents))]
      (.setCustom action-obj (custom-type lang non-records)))
    (doto action-obj
      (.setCreated (xml-date start))
      (.setTitle action)
      (.setType action))))

(defn- retention-period [{{{:keys [pituus arkistointi]} :sailytysaika} :processMetadata}]
  (-> (cond
        (= :ei (keyword arkistointi)) 0
        (#{:ikuisesti :toistaiseksi} (keyword arkistointi)) 999999
        :else pituus)
      (BigInteger/valueOf)))

(def jaxb-context (JAXBContext/newInstance ^Class/1 (into-array [CaseFile])))

(def schema (xml-util/load-schema "sahke2/onkalo-case-file-schema-2016-06-01.xsd"))

(defn xml-case-file
  ^String [{:keys [id processMetadata tosFunction organization authority] :as application} lang]
  (let [case-file        (generate-case-file-data application lang)
        case-file-object (CaseFile.)]
    (doto case-file-object
      (.setNativeId id)
      (.setRestriction (build-restriction-type application lang))
      (.setTitle (str "Käsittelyprosessi: " id))
      (.setRetentionPeriod (BigInteger/valueOf (retention-period application)))
      (.setRetentionReason (get-in processMetadata [:sailytysaika :perustelu]))
      (.setRetentionPeriodEnd (xml-date (get-in processMetadata [:sailytysaika :retention-period-end])))
      (.setStatus (get processMetadata :tila "valmis"))
      (.setFunction tosFunction)
      (.setClassificationScheme (classification-xml organization tosFunction)))
    (.add (.getCreated case-file-object) (xml-date (:start (first case-file))))
    (.add (.getLanguage case-file-object) (name lang))
    (.add (.getAgent case-file-object) (agent-type "responsible"
                                                   (str (:firstName authority) " " (:lastName authority))))
    (.addAll (.getAction case-file-object) (vec (map #(action-type % lang) case-file)))

    (xml-util/->xml
      (cond-> {:jaxb-context    jaxb-context
               :document        case-file-object
               :schema-prefixes {"http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1" "lp"}}
        ;; TODO: Enable always once we are certain we always generate valid messages
        (env/dev-mode?) (assoc :schema schema)))))
