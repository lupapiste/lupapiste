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
            [schema.core :as s]
            [sade.env :as env]
            [sade.files :as files]
            [sade.http :as http]
            [sade.strings :as ss]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.action :as action]
            [lupapalvelu.application-meta-fields :as amf]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.foreman :as foreman])
  (:import (java.util.concurrent ThreadFactory Executors)
           (java.io File)))

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

(defonce upload-threadpool (Executors/newFixedThreadPool 1 (thread-factory)))

(defn- upload-file [id is-or-file content-type metadata]
  (let [host (env/value :arkisto :host)
        app-id (env/value :arkisto :app-id)
        app-key (env/value :arkisto :app-key)
        encoded-id (codec/url-encode id)
        url (str host "/documents/" encoded-id)]
    (http/put url {:basic-auth [app-id app-key]
                   :throw-exceptions false
                   :quiet true
                   :multipart  [{:name      "metadata"
                                 :mime-type "application/json"
                                 :encoding  "UTF-8"
                                 :content   (json/generate-string metadata)}
                                {:name      "file"
                                 :content   is-or-file
                                 :mime-type content-type}]})))

(defn- set-attachment-state [next-state application now id]
  (action/update-application
    (action/application->command application)
    {:attachments.id id}
    {$set {:modified now
           :attachments.$.modified now
           :attachments.$.metadata.tila next-state
           :attachments.$.readOnly true}}))

(defn- set-application-state [next-state application now _]
  (action/update-application
    (action/application->command application)
    {$set {:modified now
           :metadata.tila next-state}}))

(defn- set-process-state [next-state application now _]
  (action/update-application
    (action/application->command application)
    {$set {:modified now
           :processMetadata.tila next-state}}))

(defn- mark-application-archived-if-done [{:keys [id] :as application} now]
  ; If these queries return 0 results, we mark the corresponding phase as archived
  (let [pre-verdict-query {:_id id
                           ; Look for pre-verdict attachments that have versions, are not yet archived, but need to be
                           $or  [{:attachments {$elemMatch {:metadata.tila                     {$nin [:arkistoitu]}
                                                            :applicationState                  {$nin [states/post-verdict-states]}
                                                            :versions                          {$gt  []}
                                                            :metadata.sailytysaika.arkistointi {$ne  :ei}}}}
                                 ; Check if the application itself is not yet archived, but needs to be
                                 {:metadata.tila                     {$ne :arkistoitu}
                                  :metadata.sailytysaika.arkistointi {$ne :ei}}]}
        post-verdict-query {:_id id
                            ; Look for any attachments that have versions, are not yet arcvhived, but need to be
                            $or  [{:attachments {$elemMatch {:metadata.tila                     {$nin [:arkistoitu]}
                                                             :versions                          {$gt  []}
                                                             :metadata.sailytysaika.arkistointi {$ne  :ei}}}}
                                  ; Check if the application itself is not yet archived, but needs to be
                                  {:metadata.tila                     {$ne :arkistoitu}
                                   :metadata.sailytysaika.arkistointi {$ne :ei}}
                                  ; Check if the case file is not yet archived, but needs to be
                                  {:processMetadata.tila                     {$ne :arkistoitu}
                                   :processMetadata.sailytysaika.arkistointi {$ne :ei}}
                                  ; Check if the application is not in a final state
                                  {:state {$nin [:closed :extinct :foremanVerdictGiven]}}]}]

    (when (zero? (mongo/count :applications pre-verdict-query))
      (action/update-application
        (action/application->command application)
        {$set {:archived.application now}}))

    (when (zero? (mongo/count :applications post-verdict-query))
      (action/update-application
        (action/application->command application)
        {$set {:archived.completed now}}))))

(defn- upload-and-set-state [id is-or-file content-type metadata {app-id :id :as application} now state-update-fn]
  (info "Trying to archive attachment id" id "from application" app-id)
  (if-not (#{:arkistoidaan :arkistoitu} (:tila metadata))
    (do (state-update-fn :arkistoidaan application now id)
        (.submit
          upload-threadpool
          (fn []
            (let [{:keys [status body]} (upload-file id is-or-file content-type (assoc metadata :tila :arkistoitu))]
              (if (= 200 status)
                (do
                  (state-update-fn :arkistoitu application now id)
                  (info "Archived attachment id" id "from application" app-id)
                  (mark-application-archived-if-done application now))
                (do
                  (error "Failed to archive attachment id" id "from application" app-id "status:" status "message:" body)
                  (if (and (= status 409) (string/includes? body "already exists"))
                    (do
                      (info "Response indicates that" id "is already in archive. Updating state.")
                      (state-update-fn :arkistoitu application now id)
                      (mark-application-archived-if-done application now))
                    (state-update-fn :valmis application now id))))))))
    (warn "Tried to archive attachment id" id "from application" app-id "again while it is still marked unfinished")))

(defn- find-op [{:keys [primaryOperation secondaryOperations]} op-id]
  (cond->> (concat [primaryOperation] secondaryOperations)
           op-id (filter #(= op-id (:id %)))
           true (map :name)
           true (distinct)))

(defn- ->iso-8601-date [date]
  (f/unparse (f/with-zone (:date-time-no-ms f/formatters) (t/time-zone-for-id "Europe/Helsinki")) date))

(defn- get-verdict-date [{:keys [verdicts]} type]
  (let [ts (->> verdicts
                (map (fn [{:keys [paatokset]}]
                       (->> (map #(get-in % [:paivamaarat type]) paatokset)
                            (remove nil?)
                            (first))))
                (remove nil?)
                (first))]
    (when (number? ts)
      (->iso-8601-date (c/from-long (long ts))))))

(defn- get-from-verdict-minutes [{:keys [verdicts]} key]
  (->> verdicts
       (map (fn [{:keys [paatokset]}]
              (map (fn [pt] (map key (:poytakirjat pt))) paatokset)))
       (flatten)
       (remove nil?)
       (first)))

(defn- get-paatospvm [{:keys [verdicts]}]
  (let [ts (->> verdicts
                (map (fn [{:keys [paatokset]}]
                       (map (fn [pt] (map :paatospvm (:poytakirjat pt))) paatokset)))
                (flatten)
                (remove nil?)
                (sort)
                (last))]
    (when (number? ts)
      (->iso-8601-date (c/from-long (long ts))))))

(defn- get-usages [{:keys [documents]} op-id]
  (let [op-docs (remove #(nil? (get-in % [:schema-info :op :id])) documents)
        id-to-usage (into {} (map (fn [d] {(get-in d [:schema-info :op :id])
                                           (get-in d [:data :kaytto :kayttotarkoitus :value])}) op-docs))]
    (->> (if op-id
           [(get id-to-usage op-id)]
           (vals id-to-usage))
         (remove nil?)
         (distinct))))

(defn- get-building-ids [bldg-key {:keys [buildings]} op-id]
  ;; Only some building lists contain operation ids at all
  (->> (if-let [filtered-bldgs (and op-id (seq (filter #(= op-id (:operationId %)) buildings)))]
         filtered-bldgs
         buildings)
       (map bldg-key)
       (remove nil?)))

(defn- make-version-number [{{{:keys [major minor]} :version} :latestVersion}]
  (str major "." minor))

(defn- make-attachment-type [{{:keys [type-group type-id]} :type}]
  (str type-group "." type-id))

(defn- foreman-name [document]
  (ss/trim (str (get-in document [:data :henkilotiedot :sukunimi :value]) \space (get-in document [:data :henkilotiedot :etunimi :value]))))

(defn- foremen [application]
  (if (empty? (:foreman application))
    (let [foreman-applications (foreman/get-linked-foreman-applications (:id application))
          foreman-documents (mapv foreman/get-foreman-documents foreman-applications)
          foremen (mapv foreman-name foreman-documents)]
      (apply str (interpose ", " foremen)))
    (:foreman application)))

(defn- generate-archive-metadata
  [{:keys [id propertyId _applicantIndex address organization municipality location location-wgs84] :as application}
   user
   & [attachment]]
  (let [s2-metadata (or (:metadata attachment) (:metadata application))
        base-metadata {:type                  (if attachment (make-attachment-type attachment) :hakemus)
                       :applicationId         id
                       :buildingIds           (get-building-ids :localId application (get-in attachment [:op :id]))
                       :nationalBuildingIds   (get-building-ids :nationalId application (get-in attachment [:op :id]))
                       :propertyId            propertyId
                       :applicants            _applicantIndex
                       :operations            (find-op application (get-in attachment [:op :id]))
                       :tosFunction           (first (filter #(= (:tosFunction application) (:code %)) (tiedonohjaus/available-tos-functions (:organization application))))
                       :address               address
                       :organization          organization
                       :municipality          municipality
                       :location-etrs-tm35fin location
                       :location-wgs84        location-wgs84
                       :kuntalupatunnukset    (remove nil? (map :kuntalupatunnus (:verdicts application)))
                       :lupapvm               (or (get-verdict-date application :lainvoimainen)
                                                  (get-paatospvm application))
                       :paatospvm             (get-paatospvm application)
                       :paatoksentekija       (get-from-verdict-minutes application :paatoksentekija)
                       :tiedostonimi          (get-in attachment [:latestVersion :filename] (str id ".pdf"))
                       :kasittelija           (select-keys (:authority application) [:username :firstName :lastName])
                       :arkistoija            (select-keys user [:username :firstName :lastName])
                       :kayttotarkoitukset    (get-usages application (get-in attachment [:op :id]))
                       :kieli                 "fi"
                       :versio                (if attachment (make-version-number attachment) "1.0")
                       :suunnittelijat        (:_designerIndex (amf/designers-index application))
                       :foremen               (foremen application)}]
    (cond-> base-metadata
            (:contents attachment) (conj {:contents (:contents attachment)})
            (:size attachment) (conj {:size (:size attachment)})
            (:scale attachment) (conj {:scale (:scale attachment)})
            true (merge s2-metadata))))

(defn send-to-archive [{:keys [user created] {:keys [attachments id] :as application} :application} attachment-ids document-ids]
  (if (or (get-paatospvm application) (foreman/foreman-app? application))
    (let [selected-attachments (filter (fn [{:keys [id latestVersion metadata]}]
                                         (and (attachment-ids id) (:archivable latestVersion) (seq metadata)))
                                       attachments)
          application-archive-id (str id "-application")
          case-file-archive-id (str id "-case-file")
          case-file-xml-id     (str case-file-archive-id "-xml")]
      (when (document-ids application-archive-id)
        (let [application-file-stream (pdf-export/generate-application-pdfa application :fi)
              metadata (generate-archive-metadata application user)]
          (upload-and-set-state application-archive-id application-file-stream "application/pdf" metadata application created set-application-state)))
      (when (document-ids case-file-archive-id)
        (let [libre-file (files/temp-file "casefile-to-archive" ".fodt") ; deleted in finally
              xml-tmp-file (files/temp-file "case-file" "xml")] ; deleted in finally
          (try
            (let [case-file-file (libre/generate-casefile-pdfa application :fi libre-file)
                  case-file-xml (tiedonohjaus/xml-case-file application :fi)
                  metadata (-> (generate-archive-metadata application user)
                               (assoc :type :case-file :tiedostonimi (str case-file-archive-id ".pdf")))
                  xml-metadata (assoc metadata :tiedostonimi (str case-file-archive-id ".xml"))]
              (spit xml-tmp-file case-file-xml)
              (upload-and-set-state case-file-archive-id case-file-file "application/pdf" metadata application created set-process-state)
              (upload-and-set-state case-file-xml-id xml-tmp-file "text/xml" xml-metadata application created set-process-state))
            (finally
              (io/delete-file libre-file :silently)
              (io/delete-file xml-tmp-file :silently)))))
      (doseq [attachment selected-attachments]
        (let [{:keys [content content-type]} (att/get-attachment-file! (get-in attachment [:latestVersion :fileId]))
              metadata (generate-archive-metadata application user attachment)]
          (upload-and-set-state (:id attachment) (content) content-type metadata application created set-attachment-state))))
    {:error :error.invalid-metadata-for-archive}))

(defn mark-application-archived [application now archived-ts-key]
  (action/update-application
    (action/application->command application)
    {$set {(str "archived." (name archived-ts-key)) now}}))
