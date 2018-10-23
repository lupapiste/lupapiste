(ns lupapalvelu.archive.archiving
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [info error warn]]
            [ring.util.codec :as codec]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [lupapalvelu.json :as json]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.env :as env]
            [sade.files :as files]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.schema-utils :as ssu]
            [sade.strings :as ss]
            [lupapiste-commons.threads :as threads]
            [sade.util :as util]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.attachment.util :as att-util]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.action :as action]
            [lupapalvelu.archive.archiving-util :refer [metadata-query mark-application-archived-if-done]]
            [lupapalvelu.application-meta-fields :as amf]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.domain :as domain]
            [lupapiste-commons.schema-utils :as su]
            [lupapalvelu.states :as states]
            [lupapalvelu.pate.verdict-interface :as verdict]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.storage.file-storage :as storage])
  (:import [java.io InputStream]))

(defonce upload-threadpool (threads/threadpool 10 "archive-upload-worker"))
(defonce post-archiving-pool (threads/threadpool 1 "mark-archived-worker"))

(def archival-states #{:arkistoidaan :arkistoitu})

(defn- upload-file [id is-or-file content-type metadata]
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

(def archived-ts-keys-schema
  {:application                 (sc/maybe ssc/Timestamp)
   :completed                   (sc/maybe ssc/Timestamp)
   (sc/optional-key :initial)   (sc/maybe ssc/Timestamp)})

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
  (let [fresh-application (domain/get-application-no-access-checking (:id application))
        attachment (first (filter #(= id (:id %)) (:attachments fresh-application)))
        last-version-index (dec (count (:versions attachment)))]
    (action/update-application
      (action/application->command fresh-application)
      {:attachments {$elemMatch {:id id :metadata.tila {$ne :arkistoitu}}}}
      {$set {:modified now
             :attachments.$.modified now
             :attachments.$.metadata.tila next-state
             :attachments.$.latestVersion.onkaloFileId id
             (str "attachments.$.versions." last-version-index ".onkaloFileId") id}})
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
        (let [metadata (metadata-fn)
              {:keys [status body]} (upload-file id
                                                 (is-or-file-fn)
                                                 content-type
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
              (state-update-fn :valmis application now id)))))))

(defn- find-op [{:keys [primaryOperation secondaryOperations]} op-ids]
  (->>
    (cond->>
      (concat [primaryOperation] secondaryOperations)
      (seq op-ids) (filter (comp (set op-ids) :id)))
    (map :name)
    (distinct)))

(defn ts->iso-8601-date [ts]
  (when (number? ts)
    (f/unparse (f/with-zone (:date-time-no-ms f/formatters) (t/time-zone-for-id "Europe/Helsinki")) (c/from-long (long ts)))))

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
  (when-let [document (domain/get-document-by-name application "tyomaastaVastaava")]
    (if (empty? (get-in document [:data :henkilo :henkilotiedot :sukunimi :value]))
      (get-in document [:data :yritys :yritysnimi :value])
      (person-name (get-in document [:data :henkilo])))))

(defn- building-ids [buildings documents b-key doc-ks]
  (or (->> (map b-key buildings)
           (remove nil?)
           seq)
      (->> (map #(get-in % doc-ks) documents)
           (remove nil?))))

(defn- location [application buildings loc-key]
  (if (= 1 (count buildings))
    ; Use building coordinates only for attachments related to exactly one buildings
    (or (loc-key (first buildings)) (loc-key application))
    (loc-key application)))

(defn- prev-permit-application? [application]
  (= (:operation-name application) "aiemmalla-luvalla-hakeminen"))

(defn- project-description [{:keys [_projectDescriptionIndex] :as application} documents]
  (let [doc-desc (some #(get-in % [:data :kuvaus :value]) documents)]
    (if (or (and (= 1 (count documents)) doc-desc)
            (prev-permit-application? application))
      doc-desc
      _projectDescriptionIndex)))

(defn- op-specific-data-for-attachment [{:keys [buildings documents] :as application} attachment]
  (let [attachment-op-ids (set (att-util/get-operation-ids attachment))
        op-filtered-bldgs (if (seq attachment-op-ids)
                            (filter #(attachment-op-ids (:operationId %)) buildings)
                            buildings)
        op-filtered-docs (if (seq attachment-op-ids)
                           (filter #(attachment-op-ids (get-in % [:schema-info :op :id])) documents)
                           documents)]
    {:nationalBuildingIds   (building-ids op-filtered-bldgs op-filtered-docs :nationalId [:data :valtakunnallinenNumero :value])
     :buildingIds           (building-ids op-filtered-bldgs op-filtered-docs :localId [:data :kunnanSisainenPysyvaRakennusnumero :value])
     :operations            (find-op application attachment-op-ids)
     :kayttotarkoitukset    (get-usages application attachment-op-ids)
     :location-etrs-tm35fin (location application op-filtered-bldgs :location)
     :location-wgs84        (location application op-filtered-bldgs :location-wgs84)
     :projectDescription    (project-description application op-filtered-docs)}))

(defn permit-ids-for-archiving [application attachment permitType]
  (let [backend-ids (verdict/kuntalupatunnukset application)]
    (if (not= permitType permit/ARK)
      backend-ids
      (conj (remove (fn [id] (= id (:backendId attachment))) backend-ids) (:backendId attachment)))))

(defn get-ark-paatospvm [application attachment]
  (->> {:verdicts (verdict/verdicts-by-backend-id application (:backendId attachment))}
       (verdict/verdict-date)
       ts->iso-8601-date))

(defn- generate-archive-metadata
  [{:keys [id propertyId _applicantIndex address organization municipality permitType
           tosFunction handlers closed drawings] :as application}
   user
   s2-md-key
   & [attachment]]
  (let [s2-metadata (or (s2-md-key attachment) (s2-md-key application))
        permit-ids (permit-ids-for-archiving application attachment permitType)
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
                       :lupapvm               (or (verdict/lainvoimainen application ts->iso-8601-date)
                                                  (verdict/verdict-date application ts->iso-8601-date))
                       :paatospvm             (if (not= permitType permit/ARK)
                                                (verdict/verdict-date application ts->iso-8601-date)
                                                (or (get-ark-paatospvm application attachment)
                                                    (verdict/verdict-date application ts->iso-8601-date)))
                       :jattopvm              (ts->iso-8601-date (:submitted application))
                       :paatoksentekija       (verdict/handler application)
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
                       :closed                (ts->iso-8601-date closed)
                       :drawing-wgs84         (seq (map :geometry-wgs84 drawings))
                       :ramLink               (:ramLink attachment)
                       ; case-file metadata does not include these, but archival schema requires them
                       :myyntipalvelu         false
                       :nakyvyys              :julkinen}]
    (-> base-metadata
        (merge (op-specific-data-for-attachment application attachment))
        su/remove-blank-keys
        (merge s2-metadata))))

(defn send-to-archive
  "Prepares metadata for selected attachments/documents
   and sends them to Onkalo archive in a separate thread"
  [{:keys [user created] {:keys [attachments id] :as application} :application} attachment-ids document-ids]
  (if (or (verdict/verdict-date application)
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
          (let [pdf-is (libre/generate-casefile-pdfa application :fi libre-file)
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

(defn mark-application-archived [application now archived-ts-key]
  {:pre [(contains? (set (ssu/keys archived-ts-keys-schema)) archived-ts-key)]}
  (action/update-application
    (action/application->command application)
    {$set {(str "archived." (name archived-ts-key)) now}}))
