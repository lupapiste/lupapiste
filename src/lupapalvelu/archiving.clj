(ns lupapalvelu.archiving
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [info error warn]]
            [ring.util.codec :as codec]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cheshire.core :as json]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.env :as env]
            [sade.files :as files]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.schema-utils :as ssu]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.action :as action]
            [lupapalvelu.archiving-util :refer [metadata-query mark-application-archived-if-done]]
            [lupapalvelu.application-meta-fields :as amf]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.domain :as domain]
            [lupapiste-commons.schema-utils :as su]
            [lupapalvelu.states :as states]
            [lupapalvelu.mongo :as mongo])
  (:import [java.util.concurrent ThreadFactory Executors]
           [java.io InputStream]))

(defn thread-factory []
  (let [security-manager (System/getSecurityManager)
        thread-group (if security-manager
                       (.getThreadGroup security-manager)
                       (.getThreadGroup (Thread/currentThread)))]
    (reify
      ThreadFactory
      (newThread [this runnable]
        (doto (Thread. thread-group runnable "archive-upload-worker")
          (.setDaemon true)
          (.setPriority Thread/NORM_PRIORITY))))))

(defonce upload-threadpool (Executors/newFixedThreadPool 3 (thread-factory)))

(def archival-states #{:arkistoidaan :arkistoitu})

(defn- upload-file [id is-or-file content-type metadata]
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
                                            :content   (json/generate-string metadata)}
                                           {:name      "file"
                                            :content   is-or-file
                                            :mime-type content-type}]})]
    (when (instance? InputStream is-or-file)
      (try
        (.close is-or-file)
        (catch Exception _)))
    result))

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

(defn- set-attachment-state [next-state application now id]
  (action/update-application
    (action/application->command application)
    {:attachments {$elemMatch {:id id :metadata.tila {$ne :arkistoitu}}}}
    {$set {:modified now
           :attachments.$.modified now
           :attachments.$.metadata.tila next-state
           :attachments.$.readOnly (contains? archival-states next-state)}}))

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

(defn- upload-and-set-state
  "Does the actual archiving in a different thread pool"
  [id is-or-file-fn content-type metadata-fn {app-id :id :as application} now state-update-fn]
  (info "Trying to archive attachment id" id "from application" app-id)
  (do (state-update-fn :arkistoidaan application now id)
      (.submit
        upload-threadpool
        (fn []
          (let [metadata (metadata-fn)
                {:keys [status body]} (upload-file id
                                                   (is-or-file-fn)
                                                   content-type
                                                   (assoc metadata :tila :arkistoitu))]
            (cond
              (= 200 status)
              (do
                (state-update-fn :arkistoitu application now id)
                (info "Archived attachment id" id "from application" app-id)
                (mark-first-time-archival application now)
                (mark-application-archived-if-done application now))

              (and (= status 409) (string/includes? body "already exists"))
              (do
                (warn "Onkalo response indicates that" id "is already in archive. Updating state to match.")
                (state-update-fn :arkistoitu application now id)
                (mark-first-time-archival application now)
                (mark-application-archived-if-done application now))

              :else
              (do
                (error "Failed to archive attachment id" id "from application" app-id "status:" status "message:" body)
                (state-update-fn :valmis application now id))))))))

(defn- find-op [{:keys [primaryOperation secondaryOperations]} op-ids]
  (cond->> (concat [primaryOperation] secondaryOperations)
           (seq op-ids) (filter (comp (set op-ids) :id))
           true (map :name)
           true (distinct)))

(defn- ->iso-8601-date [date]
  (f/unparse (f/with-zone (:date-time-no-ms f/formatters) (t/time-zone-for-id "Europe/Helsinki")) date))

(defn- ts->iso-8601-date [ts]
  (when (number? ts)
    (->iso-8601-date (c/from-long (long ts)))))

(defn- get-verdict-date [{:keys [verdicts]} type]
  (let [ts (->> verdicts
                (map (fn [{:keys [paatokset]}]
                       (->> (map #(get-in % [:paivamaarat type]) paatokset)
                            (remove nil?)
                            (first))))
                (remove nil?)
                (first))]
    (ts->iso-8601-date ts)))

(defn- get-from-verdict-minutes [{:keys [verdicts]} key]
  (->> verdicts
       (map (fn [{:keys [paatokset]}]
              (map (fn [pt] (map key (:poytakirjat pt))) paatokset)))
       (flatten)
       (remove nil?)
       (first)))

(defn valid-ya-state? [application]
  (and (= "YA" (:permitType application))
       (contains? states/ya-post-verdict-states (keyword (:state application)))))

(defn- get-paatospvm [{:keys [verdicts]}]
  (let [ts (->> verdicts
                (map (fn [{:keys [paatokset]}]
                       (map (fn [pt] (map :paatospvm (:poytakirjat pt))) paatokset)))
                (flatten)
                (remove nil?)
                (sort)
                (last))]
    (ts->iso-8601-date ts)))

(defn- get-usages [{:keys [documents]} op-ids]
  (let [op-docs (remove #(nil? (get-in % [:schema-info :op :id])) documents)
        id-to-usage (into {} (map (fn [d] {(get-in d [:schema-info :op :id])
                                           (get-in d [:data :kaytto :kayttotarkoitus :value])}) op-docs))]
    (->> (if (seq op-ids)
           (map id-to-usage op-ids)
           (vals id-to-usage))
         (remove nil?)
         (distinct))))

(defn- get-building-ids [bldg-key {:keys [buildings]} op-ids]
  ;; Only some building lists contain operation ids at all
  (->> (if-let [filtered-bldgs (seq (filter (comp (set op-ids) :operationId) buildings))]
         filtered-bldgs
         buildings)
       (map bldg-key)
       (remove nil?)))

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
                            (map foreman/get-foreman-documents)
                            (map #(person-name (:data %)))
                            seq)]
      (string/join ", " foremen))
    (:foreman application)))

(defn- tyomaasta-vastaava [application]
  (when-let [document (domain/get-document-by-name application "tyomaastaVastaava")]
    (if (empty? (get-in document [:data :henkilo :henkilotiedot :sukunimi :value]))
      (get-in document [:data :yritys :yritysnimi :value])
      (person-name (get-in document [:data :henkilo])))))

(defn- generate-archive-metadata
  [{:keys [id propertyId _applicantIndex address organization municipality location
           location-wgs84 tosFunction verdicts handlers closed drawings] :as application}
   user
   s2-md-key
   & [attachment]]
  (let [s2-metadata (or (s2-md-key attachment) (s2-md-key application))
        base-metadata {:type                  (if attachment (make-attachment-type attachment) :hakemus)
                       :applicationId         id
                       :buildingIds           (get-building-ids :localId application (att/get-operation-ids attachment))
                       :nationalBuildingIds   (get-building-ids :nationalId application (att/get-operation-ids attachment))
                       :propertyId            propertyId
                       :applicants            _applicantIndex
                       :operations            (find-op application (att/get-operation-ids attachment))
                       :tosFunction           (->> (tiedonohjaus/available-tos-functions organization)
                                                   (filter #(= tosFunction (:code %)))
                                                   first)
                       :address               address
                       :organization          organization
                       :municipality          municipality
                       :location-etrs-tm35fin location
                       :location-wgs84        location-wgs84
                       :kuntalupatunnukset    (remove nil? (map :kuntalupatunnus verdicts))
                       :lupapvm               (or (get-verdict-date application :lainvoimainen)
                                                  (get-paatospvm application))
                       :paatospvm             (get-paatospvm application)
                       :paatoksentekija       (get-from-verdict-minutes application :paatoksentekija)
                       :tiedostonimi          (get-in attachment [:latestVersion :filename] (str id ".pdf"))
                       :kasittelija           (select-keys (util/find-first :general handlers) [:userId :firstName :lastName])
                       :arkistoija            (select-keys user [:username :firstName :lastName])
                       :kayttotarkoitukset    (get-usages application (att/get-operation-ids attachment))
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
                       :projectDescription    (:_projectDescriptionIndex application)
                       ; case-file metadata does not include these, but archival schema requires them
                       :myyntipalvelu         false
                       :nakyvyys              :julkinen}]
    (-> base-metadata
        su/remove-blank-keys
        (merge s2-metadata))))

(defn send-to-archive
  "Prepares metadata for selected attachments/documents
   and sends them to Onkalo archive in a separate thread"
  [{:keys [user created] {:keys [attachments id] :as application} :application} attachment-ids document-ids]
  (if (or (get-paatospvm application)
          (foreman/foreman-app? application)
          (valid-ya-state? application))
    (let [selected-attachments (filter (fn [{:keys [id latestVersion metadata]}]
                                         (and (attachment-ids id) (:archivable latestVersion) (seq metadata)))
                                       attachments)
          application-archive-id (str id "-application")
          case-file-archive-id (str id "-case-file")
          case-file-xml-id     (str case-file-archive-id "-xml")
          file-ids (map #(get-in % [:latestVersion :fileId]) selected-attachments)
          gridfs-results (->> (mongo/download-find-many {:_id {$in file-ids}})
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
                                set-application-state)))
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
                                  set-process-state)
            (upload-and-set-state case-file-xml-id
                                  xml-fn
                                  "text/xml"
                                  xml-metadata-fn
                                  application
                                  created
                                  set-process-state))))
      (doseq [attachment selected-attachments
              :when (not (archival-states (keyword (get-in attachment [:metadata :tila]))))]
        (let [file-id (get-in attachment [:latestVersion :fileId])
              {:keys [content contentType]} (get gridfs-results file-id)
              metadata-fn #(generate-archive-metadata application user :metadata attachment)]
          (upload-and-set-state (:id attachment)
                                content
                                contentType
                                metadata-fn
                                application
                                created
                                set-attachment-state))))
    {:error :error.invalid-metadata-for-archive}))

(defn mark-application-archived [application now archived-ts-key]
  {:pre [(contains? (set (ssu/keys archived-ts-keys-schema)) archived-ts-key)]}
  (action/update-application
    (action/application->command application)
    {$set {(str "archived." (name archived-ts-key)) now}}))
