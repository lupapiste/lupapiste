(ns lupapalvelu.tiedonohjaus
  (:require [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.core.memoize :as memo]
            [clojure.string :as s]
            [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as o]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.http :as http]
            [sade.util :as util]
            [taoensso.timbre :refer [trace debug debugf info infof warn warnf error errorf]])
  (:import [java.util GregorianCalendar Date]
           [java.io StringWriter StringReader]
           [javax.xml.bind JAXB JAXBContext]
           [javax.xml.datatype DatatypeFactory]
           [org.joda.time DateTime]
           [lupapalvelu.tiedonohjaus CaseFile RestrictionType PublicityClassType PersonalDataType
                                     ProtectionLevelType SecurityClassType AccessRightType ActionType
                                     RecordType AgentType ActionEvent Custom ClassificationScheme]))

(defn- build-url [& path-parts]
  (apply str (env/value :toj :host) path-parts))

(defn get-from-toj-api [organization-id coerce? & path-parts]
  (when (:permanent-archive-enabled (o/get-organization organization-id))
    (let [url (apply str (env/value :toj :host) "/tiedonohjaus/api/org/" organization-id "/asiat" (when path-parts "/")
                     path-parts)
          {:keys [^long status body]} (http/get url (cond-> {:throw-exceptions false}
                                                      coerce? (assoc :as :json)))]
      (case status
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

(defn- get-metadata-for-process-from-toj [organization tos-function]
  (if (and organization tos-function)
    (or (get-from-toj-api organization :coerce tos-function) {})
    {}))

(def metadata-for-process
  (memo/ttl get-metadata-for-process-from-toj
            :ttl/threshold 20000))

(defn- paatospvm-plus-years [verdicts years]
  (some-> (vif/latest-published-verdict-date verdicts)
          long
          c/from-long
          ^DateTime (t/plus (t/years years))
          (.toDate)))

(defn- submitted-plus-years [submitted years]
  (-> (c/from-long (long submitted))
      ^DateTime (t/plus (t/years years))
      (.toDate)))

(defn foreman-app? [primaryOperation] (= :tyonjohtajan-nimeaminen-v2 (-> primaryOperation :name keyword)))

(defn- retention-end-date [{{:keys [arkistointi pituus]} :sailytysaika} verdicts primaryOperation submitted]
  (if (and (foreman-app? primaryOperation) (= (keyword "m\u00E4\u00E4r\u00E4ajan") (keyword arkistointi)))
    (submitted-plus-years submitted pituus)
    (when (and (= (keyword "m\u00E4\u00E4r\u00E4ajan") (keyword arkistointi)) (seq verdicts))
      (paatospvm-plus-years verdicts pituus))))

(defn- security-end-date [{:keys [salassapitoaika julkisuusluokka]} verdicts]
  (when (and (#{:osittain-salassapidettava :salainen} (keyword julkisuusluokka)) salassapitoaika (seq verdicts))
    (paatospvm-plus-years verdicts salassapitoaika)))

(defn update-end-dates
  ([metadata verdicts] (update-end-dates metadata verdicts nil nil))
  ([metadata verdicts primaryOperation submitted]
   (let [retention-end (retention-end-date metadata verdicts primaryOperation submitted)
         security-end (security-end-date metadata verdicts)]
     (cond-> (-> (util/dissoc-in metadata [:sailytysaika :retention-period-end])
                 (dissoc :security-period-end))
       retention-end (assoc-in [:sailytysaika :retention-period-end] retention-end)
       security-end (assoc :security-period-end security-end)))))

(defn document-with-updated-metadata [{:keys [metadata] :as document} organization tos-function application & [type]]
  (if (#{:arkistoidaan :arkistoitu :ei-arkistoida-virheellinen} (keyword (:tila metadata)))
    ;; Do not update metadata for documents that are already archived (or generated review documents that have been
    ;; marked as faulty by the authority)
    document
    (let [document-type (or type (:type document))
          existing-tila (:tila metadata)
          existing-nakyvyys (:nakyvyys metadata)
          new-metadata (metadata-for-document organization tos-function document-type)
          {:keys [verdicts pate-verdicts]} application
          processed-metadata (cond-> new-metadata
                               existing-tila (assoc :tila (keyword existing-tila))
                               true (update-end-dates (concat verdicts pate-verdicts))
                               (and (not (:nakyvyys new-metadata)) existing-nakyvyys) (assoc :nakyvyys existing-nakyvyys))]
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
  (let [documents (get-documents-from-application application)
        attachments (get-attachments-from-application application)
        statement-reqs (get-statement-requests-from-application application)
        neighbors-reqs (get-neighbour-requests application)
        review-reqs (get-review-requests-from-application application)
        reviews-held (get-held-reviews-from-application application)
        tos-fn-changes (tos-function-changes-from-history history lang)
        handler-changes (handler-changes-from-history history organization lang)
        all-docs (sort-by :ts (concat handler-changes tos-fn-changes documents attachments statement-reqs neighbors-reqs
                                      review-reqs reviews-held))
        state-changes (filter :state history)]
    (doall
      (map (fn [[{:keys [state ts user]} next]]
             (let [api-response (toimenpide-for-state organization (:tosFunction application) state)
                   action-name (cond
                                 (:name api-response) (:name api-response)
                                 (= state "complementNeeded") (i18n/localize lang "caseFile.complementNeeded")
                                 (= state "canceled") (i18n/localize lang "caseFile.canceled")
                                 :else (i18n/localize lang "caseFile.stateNotSet"))
                   ; History entries of legacy applications might not have all the timestamps
                   next-ts (or (:ts next) 0)]
               {:action    action-name
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
                               [])
                             )}))
           (partition 2 1 nil state-changes)))))

(defn- document-metadata-final-state [{:keys [tila] :as metadata} verdicts]
  (if (#{:arkistoitu :arkistoidaan} (keyword tila))
    ;; Must not alter already archived documents
    metadata
    (-> (assoc metadata :tila :valmis)
        (update-end-dates verdicts))))

(defn mark-attachment-final! [{:keys [attachments] :as application} now attachment-or-id]
  (let [{:keys [id metadata]} (if (map? attachment-or-id)
                                attachment-or-id
                                (first (filter #(= (:id %) attachment-or-id) attachments)))]
    (when (seq metadata)
      (let [new-metadata (document-metadata-final-state metadata (vif/all-verdicts application))]
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
      (let [verdicts (vif/all-verdicts application)
            new-metadata (document-metadata-final-state metadata verdicts)
            new-process-metadata (update-end-dates processMetadata verdicts)]
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
      (= (keyword "m\u00E4\u00E4r\u00E4ajan") kw-a) pituus)))

(defn- comp-sa [sailytysaika]
  (dissoc sailytysaika :perustelu))

(defn calculate-process-metadata [original-process-metadata application-metadata attachments]
  (let [metadatas (conj (map :metadata attachments) application-metadata)
        {max-retention :sailytysaika} (last (sort-by retention-key metadatas))]
    (if (and max-retention (not= (comp-sa (:sailytysaika original-process-metadata)) (comp-sa max-retention)))
      (assoc original-process-metadata :sailytysaika max-retention)
      original-process-metadata)))

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
    (let [orgId (:organization application)
          updated-attachments (pmap #(document-with-updated-metadata % orgId function-code application)
                                    (:attachments application))
          {updated-metadata :metadata} (document-with-updated-metadata application orgId function-code application
                                                                       "hakemus")
          process-metadata (calculate-process-metadata (metadata-for-process orgId function-code) updated-metadata
                                                       updated-attachments)]
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
    :salainen (PublicityClassType/fromValue "Salassa pidett\u00e4v\u00e4")
    :osittain-salassapidettava (PublicityClassType/fromValue "Osittain salassapidett\u00e4v\u00e4")))

(defn- personal-data-type [{:keys [henkilotiedot]}]
  (case (keyword henkilotiedot)
    :ei-sisalla (PersonalDataType/fromValue "ei sis\u00e4ll\u00e4 henkil\u00f6tietoja")
    :sisaltaa (PersonalDataType/fromValue "sis\u00e4lt\u00e4\u00e4 henkil\u00f6tietoja")
    :sisaltaa-arkaluonteisia (PersonalDataType/fromValue "sis\u00e4lt\u00e4\u00e4 arkaluontoisia henkil\u00f6tietoja")))

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
    (when-not (s/blank? user)
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

(defn ^String xml-case-file [{:keys [id processMetadata tosFunction organization authority] :as application} lang]
  (let [case-file (generate-case-file-data application lang)
        case-file-object (CaseFile.)]
    (doto case-file-object
      (.setNativeId id)
      (.setRestriction (build-restriction-type application lang))
      (.setTitle (str "K\u00e4sittelyprosessi: " id))
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

    (with-open [sw (StringWriter.)]
      (let [marshaller (-> (JAXBContext/newInstance ^"[Ljava.lang.Class;" (into-array [CaseFile]))
                           (.createMarshaller))]
        (.marshal marshaller case-file-object sw)
        (.toString sw)))))
