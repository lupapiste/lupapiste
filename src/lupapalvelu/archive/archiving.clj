(ns lupapalvelu.archive.archiving
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as amf]
            [lupapalvelu.archive.archiving-util :refer [mark-application-archived-if-done]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.util :as att-util]
            [lupapalvelu.building-attributes :as ba]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.json :as json]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.pdf.libreoffice-template-history :as history]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapiste-commons.schema-utils :as su]
            [lupapiste-commons.threads :as threads]
            [monger.operators :refer :all]
            [plumbing.map :refer [merge-with-key]]
            [ring.util.codec :as codec]
            [sade.date :as date]
            [sade.env :as env]
            [sade.files :as files]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [info error warn]])
  (:import [java.io InputStream]
           [java.lang AutoCloseable]
           [java.util Date]))

(defonce upload-threadpool (threads/threadpool 10 "archive-upload-worker"))
(defonce post-archiving-pool (threads/threadpool 1 "mark-archived-worker"))

(def archival-states #{:arkistoidaan :arkistoitu})

(defn- upload-file [id ^AutoCloseable is-or-file content-type metadata]
  {:pre [(every? some? [id is-or-file content-type metadata])]}
  (try
    (let [host (env/value :arkisto :host)
          app-id (env/value :arkisto :app-id)
          app-key (env/value :arkisto :app-key)
          encoded-id (codec/url-encode id)
          url (str host "/documents/" encoded-id)
          result (http/put url {:basic-auth [app-id app-key]
                                :throw-exceptions false
                                :quiet true
                                :multipart  [{:name      "metadata"
                                              :mime-type "application/json"
                                              :encoding  "UTF-8"
                                              :content   (json/encode metadata)}
                                             {:name      "file"
                                              :content   is-or-file
                                              :mime-type content-type}]})]
      result)
    (finally
      (when (instance? InputStream is-or-file)
        (try
          (.close is-or-file)
          (catch Exception _))))))

(defn mark-first-time-archival [application now]
  (action/update-application
    (action/application->command application)
    {:archived.initial nil
     :archived.application nil
     :archived.completed nil}
    {$set {:archived.initial now}}))

(defn- remove-mongo-files
  "Remove actual attachment files from MongoDB after archiving. Only for ARK / archiving projects now."
  [app-id attachment-id]
  (when-let [application (domain/get-application-no-access-checking app-id)]
    (when (= permit/ARK (:permitType application))
      (when-let [attachment (first (filter #(= attachment-id (:id %)) (:attachments application)))]
        (att/delete-archived-attachments-files-from-mongo! application attachment)))))

(defn- set-attachment-state [next-state application now id]
  (let [fresh-application  (domain/get-application-no-access-checking (:id application))
        attachment         (util/find-by-id id (:attachments fresh-application))
        last-version-index (dec (count (:versions attachment)))]
    (action/update-application
      (action/application->command fresh-application)
      {:attachments {$elemMatch {:id id :metadata.tila {$ne :arkistoitu}}}}
      {$set (merge {:modified                    now
                    :attachments.$.modified      now
                    :attachments.$.metadata.tila next-state}
                   (when (= :arkistoitu next-state)
                     {:attachments.$.latestVersion.onkaloFileId                          id
                      (str "attachments.$.versions." last-version-index ".onkaloFileId") id}))})
    (when (= :arkistoitu next-state)
      (remove-mongo-files (:id application) id))))

(defn- set-application-state [next-state application now _]
  (action/update-application
    (action/application->command application)
    {:metadata.tila {$ne :arkistoitu}}
    {$set {:modified now
           :metadata.tila next-state}}))

(defn- set-process-state [next-state application now _]
  (action/update-application
    (action/application->command application)
    {:processMetadata.tila {$ne :arkistoitu}}
    {$set {:modified now
           :processMetadata.tila next-state}}))

(defn- do-post-archival-ops
  "Does the post-archiving stuff in a separate single threaded pool to prevent race-condition with Mongo"
  [id application now user]
  (threads/submit
    post-archiving-pool
    (info "State for attachment id" id "from application" (:id application) "updated to arkistoitu.")
    (mark-first-time-archival application now)
    (mark-application-archived-if-done application now user)
    (info "Post archiving ops complete for attachment id" id "from application" (:id application))))

(defn- upload-and-set-state
  "Does the actual archiving in a different thread pool"
  [id is-or-file-fn content-type metadata-fn {app-id :id :as application} now state-update-fn user]
  {:pre [(every? some? [id is-or-file-fn content-type metadata-fn app-id now state-update-fn user])]}
  (info "Trying to archive attachment id" id "from application" app-id)
  (do (state-update-fn :arkistoidaan application now id)
      (threads/submit
        upload-threadpool
        (try
          (let [metadata (metadata-fn)
                {:keys [status body]} (upload-file id
                                                   (is-or-file-fn)
                                                   content-type
                                                   ;; tila is archival state, not app state
                                                   (assoc metadata :tila :arkistoitu))]
            (cond
              (= 200 status)
              (do
                (info "Archived attachment id" id "from application" app-id)
                (state-update-fn :arkistoitu application now id)
                (do-post-archival-ops id application now user))

              (and (= status 409) (string/includes? body "already exists"))
              (do
                (warn "Onkalo response indicates that" id "is already in archive. Updating state to match.")
                (state-update-fn :arkistoitu application now id)
                (do-post-archival-ops id application now user))

              :else
              (do
                (error "Failed to archive attachment id" id "from application" app-id "status:" status "message:" body)
                (state-update-fn :valmis application now id))))
          (catch Throwable t
            (error t "Failed to archive attachment id" id "from application" app-id)
            (state-update-fn :valmis application now id))))))

(defn- find-op [{:keys [primaryOperation secondaryOperations]} op-ids]
  (->>
    (cond->>
      (concat [primaryOperation] secondaryOperations)
      (seq op-ids) (filter (comp (set op-ids) :id)))
    (map :name)
    (distinct)))

(defn valid-ya-state? [application]
  (and (= "YA" (:permitType application))
       (contains? states/ya-post-verdict-states (keyword (:state application)))))

(defn archiving-project? [application]
  (= :ARK (keyword (:permitType application))))

(defn- get-usages [{:keys [documents]} op-ids]
  (let [op-docs (remove #(nil? (get-in % [:schema-info :op :id])) documents)
        id-to-usage (into {} (map (fn [d] {(get-in d [:schema-info :op :id])
                                           (get-in d [:data :kaytto :kayttotarkoitus :value])}) op-docs))]
    (->> (if (seq op-ids)
           (map id-to-usage op-ids)
           (vals id-to-usage))
         (remove nil?)
         (distinct))))

(defn- get-building-types [{:keys [documents]} op-ids]
  (let [op-docs (remove #(nil? (get-in % [:schema-info :op :id])) documents)
        id-to-usage (into {} (map (fn [d] {(get-in d [:schema-info :op :id])
                                           (get-in d [:data :kaytto :rakennusluokka :value])}) op-docs))]
    (->> (if (seq op-ids)
           (map id-to-usage op-ids)
           (vals id-to-usage))
         (remove nil?)
         (distinct))))

(defn- make-version-number [{{{:keys [major minor]} :version} :latestVersion}]
  (str major "." minor))

(defn- make-attachment-type [{{:keys [type-group type-id]} :type}]
  (str type-group "." type-id))

(defn- person-name [person-data]
  (ss/trim
    (str (get-in person-data [:henkilotiedot :sukunimi :value])
         \space
         (get-in person-data [:henkilotiedot :etunimi :value]))))

(defn- foremen [application]
  (if (ss/blank? (:foreman application))
    (when-let [foremen (->> (foreman/get-linked-foreman-applications-by-id (:id application))
                            (map foreman/get-foreman-document)
                            (map #(person-name (:data %)))
                            seq)]
      (string/join ", " foremen))
    (:foreman application)))

(defn- tyomaasta-vastaava [application]
  (when-let [{:keys [_selected henkilo
                     yritys]} (:data (domain/get-document-by-subtype application :tyomaasta-vastaava))]
    (if (= (:value _selected) "henkilo")
      (person-name henkilo)
      (some-> yritys :yritysnimi :value ss/trim))))

(defn building-ids-for-op [[building] [document] b-key doc-ks]
  ;; there should only ever be one item in each buildings and documents,
  ;; because the cardinalities are
  ;;     building 0..1-1 operation and
  ;;     document 1-1 operation
  ;; however
  ;;     building 0..1 -(operation)- 1 document    --- that is, not all
  ;;                                                   documents are associated
  ;;                                                   with a building
  ;; also, a building can have multiple *actually* associated operations,
  ;; but in that case only one of them are referenced by the buildings array,
  ;; so the correct behaviour is to get the building id(s) from documents
  (or (get-in document doc-ks)
      (b-key building)))

(defn- building-ids
  "Return the building-ids, preferring the value from documents if it exist,
  otherwise from buildings."
  [buildings documents b-key doc-ks]
  (let [grouped-b (group-by :operationId buildings)
        grouped-d (group-by (comp :id :op :schema-info) documents)
        ops       (->> [grouped-b grouped-d]
                       (mapcat keys)
                       distinct)]
    (->> ops
         (map #(building-ids-for-op (get grouped-b %)
                                    (get grouped-d %)
                                    b-key
                                    doc-ks))
         ;; remove dupes in case multiple docs refer to same buildingId
         distinct
         (remove nil?))))

(defn- location [application buildings loc-key]
  (if (= 1 (count buildings))
    ; Use building coordinates only for attachments related to exactly one buildings
    (or (loc-key (first buildings)) (loc-key application))
    (loc-key application)))

(defn- project-description [{:keys [_projectDescriptionIndex] :as application} documents]
  (let [doc-desc (->> documents
                      (map #(get-in % [:data :kuvaus :value]))
                      (remove nil?)
                      not-empty)]
    (if (and doc-desc (or (= (count doc-desc) 1)
                          (app/previous-permit? application)))
      (first doc-desc)
      _projectDescriptionIndex)))

(defn buildings-with-operation [op-ids buildings]
  (filter #((set op-ids) (:operationId %)) buildings))

(defn documents-with-operation [op-ids documents]
  (filter #((set op-ids) (get-in % [:schema-info :op :id])) documents))

(defmulti metadata-attribute-strictness
  "Strictness for metadata attribute values. Greater number means stricter."
  (fn [key _] key))

(defmethod metadata-attribute-strictness :julkisuusluokka [_ value]
  ({"salainen"                  3
    "osittain-salassapidettava" 2
    "julkinen"                  1}
   value))

(defmethod metadata-attribute-strictness :nakyvyys [_ value]
  ({"viranomainen"              3
    "asiakas-ja-viranomainen"   2
    "julkinen"                  1}
   value))

(defmethod metadata-attribute-strictness :myyntipalvelu [_ value]
  ({false              2
    true               1}
   value))

(defmethod metadata-attribute-strictness :default [_ value]
  1)

(defn- by-strictness [key & vals]
  (let [strictness (partial metadata-attribute-strictness key)
        strictest-first (sort-by strictness > vals)]
    (first strictest-first)))

(defn merge-building-metadata
  "Merge metadata when there are multiple buildings that apply to this attachment with their own competing metadata.
   We are specific about which metadata we support because each attribute needs their own merge implementation."
  [buildings]
  (let [select-supported-fields (fn [metadata] (select-keys metadata [:julkisuusluokka :nakyvyys :myyntipalvelu]))
        metadata-maps (map (comp select-supported-fields :metadata) buildings)]
    (or (apply merge-with-key by-strictness metadata-maps) {})))

(defn- building-specific-data-for-attachment [{:keys [buildings documents] :as application} attachment]
  (let [attachment-op-ids (att-util/get-operation-ids attachment)
        attachment-buildings (buildings-with-operation attachment-op-ids buildings)]
    (merge-building-metadata attachment-buildings)))

(defn- extinct-metadata-for-attachment
  [{:keys [state extincted]}]
  (when (= state "extinct")
    (util/assoc-when
      {:permit-expired true}
      :permit-expired-date (some-> extincted (Date.)))))

(defn- op-specific-data-for-attachment [{:keys [buildings documents] :as application} attachment]
  (let [attachment-op-ids (att-util/get-operation-ids attachment)
        op-filtered-bldgs (if (seq attachment-op-ids)
                            (buildings-with-operation attachment-op-ids buildings)
                            buildings)
        op-filtered-docs (if (seq attachment-op-ids)
                           (documents-with-operation attachment-op-ids documents)
                           documents)]
    {:nationalBuildingIds   (building-ids op-filtered-bldgs op-filtered-docs :nationalId [:data :valtakunnallinenNumero :value])
     :buildingIds           (building-ids op-filtered-bldgs op-filtered-docs :localId [:data :kunnanSisainenPysyvaRakennusnumero :value])
     :operations            (find-op application attachment-op-ids)
     :kayttotarkoitukset    (get-usages application attachment-op-ids)
     :rakennusluokat        (get-building-types application attachment-op-ids)
     :location-etrs-tm35fin (location application op-filtered-bldgs :location)
     :location-wgs84        (location application op-filtered-bldgs :location-wgs84)
     :projectDescription    (project-description application op-filtered-docs)}))

(defn permit-ids-for-archiving [application attachment permitType]
  (let [backend-ids (vif/kuntalupatunnukset application)]
    (if (not= permitType permit/ARK)
      backend-ids
      (conj (remove (fn [id] (= id (:backendId attachment))) backend-ids) (:backendId attachment)))))

(defn get-ark-paatospvm [application attachment]
  (->> {:verdicts (vif/verdicts-by-backend-id application (:backendId attachment))}
       (vif/latest-published-verdict-date)))

(defn get-verdict-ts [{:keys [id permitType] :as application} attachment]
  (if (not= permitType permit/ARK)
    (vif/latest-published-verdict-date application)
    (or (get-ark-paatospvm application attachment)
        (vif/latest-published-verdict-date application))))

(defn with-secrecy-metadata [metadata verdict-ts]
  (let [{:keys [salassapitoaika]
         :as   metadata} (ba/with-secrecy-defaults metadata)
        security-end     (some-> (date/zoned-date-time verdict-ts)
                                 (.plusYears (or (util/->long salassapitoaika) 0))
                                 date/iso-datetime)]
    (if security-end
      (assoc metadata :security-period-end security-end)
      metadata)))

(defn- generate-archive-metadata
  [{:keys [id propertyId _applicantIndex address organization municipality permitType
           tosFunction handlers closed drawings] :as application}
   user
   s2-md-key
   & [attachment]]
  (let [s2-metadata (or (s2-md-key attachment) (s2-md-key application))
        permit-ids (permit-ids-for-archiving application attachment permitType)
        verdict-ts (get-verdict-ts application attachment)
        base-metadata {:type                  (if attachment (make-attachment-type attachment) :hakemus)
                       ; Don't use application ids for archiving projects if there are municipal permit ids
                       :applicationId         (when (or (not= permitType permit/ARK) (empty? permit-ids)) id)
                       :propertyId            propertyId
                       :applicants            _applicantIndex
                       :tosFunction           (->> (tiedonohjaus/available-tos-functions organization)
                                                   (filter #(= tosFunction (:code %)))
                                                   first)
                       :address               address
                       :organization          organization
                       :municipality          municipality
                       :kuntalupatunnukset    permit-ids
                       :lupapvm               (or (vif/lainvoimainen application date/iso-datetime)
                                                  (vif/latest-published-verdict-date application date/iso-datetime))
                       :paatospvm             (date/iso-datetime verdict-ts)
                       :jattopvm              (date/iso-datetime (:submitted application))
                       :paatoksentekija       (vif/verdict-giver application)
                       :tiedostonimi          (get-in attachment [:latestVersion :filename] (str id ".pdf"))
                       :kasittelija           (select-keys (util/find-first :general handlers) [:userId :firstName :lastName])
                       :arkistoija            (select-keys user [:username :firstName :lastName])
                       :kieli                 "fi"
                       :versio                (if attachment (make-version-number attachment) "1.0")
                       :suunnittelijat        (:_designerIndex (amf/designers-index application))
                       :foremen               (foremen application)
                       :contents              (:contents attachment)
                       :size                  (:size attachment)
                       :scale                 (:scale attachment)
                       :tyomaasta-vastaava    (tyomaasta-vastaava application)
                       :closed                (date/iso-datetime closed)
                       :drawing-wgs84         (seq (map :geometry-wgs84 drawings))
                       :ramLink               (:ramLink attachment)
                       ; case-file metadata does not include these, but archival schema requires them
                       :myyntipalvelu         false
                       :nakyvyys              :julkinen}]
    (-> base-metadata
        (merge (op-specific-data-for-attachment application attachment))
        su/remove-blank-keys
        (merge (dissoc s2-metadata :draftTarget)
               (building-specific-data-for-attachment application attachment)
               (extinct-metadata-for-attachment application))
        (with-secrecy-metadata verdict-ts))))

(defn send-to-archive
  "Prepares metadata for selected attachments/documents
   and sends them to Onkalo archive in a separate thread"
  [{:keys [user created] {:keys [attachments id] :as application} :application} attachment-ids document-ids]
  (if (or (vif/latest-published-verdict-date application)
          (foreman/foreman-app? application)
          (valid-ya-state? application)
          (archiving-project? application))
    (let [selected-attachments (filter (fn [{:keys [id latestVersion metadata]}]
                                         (and (attachment-ids id) (:archivable latestVersion) (seq metadata)))
                                       attachments)
          application-archive-id (str id "-application")
          case-file-archive-id (str id "-case-file")
          case-file-xml-id     (str case-file-archive-id "-xml")
          file-ids (map #(get-in % [:latestVersion :fileId]) selected-attachments)
          gridfs-results (->> (storage/download-many application file-ids)
                              (map (fn [{:keys [fileId] :as res}] [fileId res]))
                              (into {}))]

      (when (and (document-ids application-archive-id)
                 (not (archival-states (keyword (get-in application [:metadata :tila])))))
        (let [content-fn #(pdf-export/generate-application-pdfa application :fi)
              metadata-fn #(generate-archive-metadata application user :metadata)]
          (upload-and-set-state application-archive-id
                                content-fn
                                "application/pdf"
                                metadata-fn
                                application
                                created
                                set-application-state
                                user)))
      (when (and (document-ids case-file-archive-id)
                 (not (archival-states (keyword (get-in application [:processMetadata :tila])))))
        (files/with-temp-file libre-file
          (history/write-history-libre-doc application :fi libre-file)
          (let [pdf-is (laundry-client/convert-libre-template-to-pdfa-stream libre-file)
                pdf-fn #(identity pdf-is)
                xml-fn #(-> (tiedonohjaus/xml-case-file application :fi)
                            (.getBytes "UTF-8")
                            io/input-stream)
                metadata-fn #(-> (generate-archive-metadata application user :processMetadata)
                                 (assoc :type :case-file :tiedostonimi (str case-file-archive-id ".pdf")))
                xml-metadata-fn #(assoc (metadata-fn) :tiedostonimi (str case-file-archive-id ".xml"))]
            (upload-and-set-state case-file-archive-id
                                  pdf-fn
                                  "application/pdf"
                                  metadata-fn
                                  application
                                  created
                                  set-process-state
                                  user)
            (upload-and-set-state case-file-xml-id
                                  xml-fn
                                  "text/xml"
                                  xml-metadata-fn
                                  application
                                  created
                                  set-process-state
                                  user))))
      (->> selected-attachments
           (mapv (fn [attachment]
                   (when-not (archival-states (keyword (get-in attachment [:metadata :tila])))
                     (let [file-id (get-in attachment [:latestVersion :fileId])
                          {:keys [content contentType]} (get gridfs-results file-id)
                           metadata-fn #(generate-archive-metadata application user :metadata attachment)]
                       (upload-and-set-state (:id attachment)
                                            content
                                             contentType
                                             metadata-fn
                                             application
                                             created
                                             set-attachment-state
                                             user)))))
           (remove nil?)))
    {:error :error.invalid-metadata-for-archive}))
