(ns lupapalvelu.archive.archiving
  (:require [clojure.java.io :as io]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as amf]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.archive.archiving-util :refer [mark-application-archived-if-done]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.util :as att-util]
            [lupapalvelu.building-attributes :as ba]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.domain-attachment :as domain-att]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.json :as json]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.logging :refer [error info warn]]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.pdf.html-template :as html-template]
            [lupapalvelu.pdf.html-templates.foreman-info-template :as foreman-info-template]
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
            [sade.util :as util])
  (:import [java.io InputStream]
           [java.lang AutoCloseable]
           [java.util Date]))

(set! *warn-on-reflection* true)

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

(defn- remove-storage-files
  "Remove actual attachment files from storage (GCS) after archiving. Only for ARK / archiving projects now."
  [app-id attachment-id]
  (when-let [application (domain/get-application-no-access-checking app-id)]
    (when (= permit/ARK (:permitType application))
      (when-let [attachment (first (filter #(= attachment-id (:id %)) (:attachments application)))]
        (att/delete-archived-attachments-files-from-storage! application attachment)))))

(defn- set-linked-file-state [file-id next-state _application _now _id]
  (linked-files/update-metadata-field {:file-id file-id} "tila" next-state))

(defn- set-attachment-state [next-state application now id]
  (let [fresh-application  (domain/get-application-no-access-checking (:id application))
        attachment         (domain-att/get-attachment-by-id-slow fresh-application id)
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
      (remove-storage-files (:id application) id))))

(defn- set-application-state [next-state application now _]
  (action/update-application
    (action/application->command application)
    {:metadata.tila {$ne :arkistoitu}}
    {$set {:modified      now
           :metadata.tila next-state}}))

(defn- set-foreman-list-state [next-state application now _]
  (action/update-application
    (action/application->command application)
    {:foreman-list-metadata.tila {$ne :arkistoitu}}
    {$set {:modified                   now
           :foreman-list-metadata.tila next-state}}))

(defn- set-process-state [next-state application now _]
  (action/update-application
    (action/application->command application)
    {:processMetadata.tila {$ne :arkistoitu}}
    {$set {:modified             now
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
  (state-update-fn :arkistoidaan application now id)
  (threads/submit
    upload-threadpool
    (try
      (let [metadata              (metadata-fn)
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

          (and (= status 409) (ss/includes? body "already exists"))
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
        (state-update-fn :valmis application now id)))))

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

(defn- make-version-number
  ([attachment]
   (let [version (get-in attachment [:latestVersion :version])]
     (str (:major version) "." (:minor version))))
  ([file-or-attachment linked-file?]
   (let [version (if linked-file?
                   (:version (storage/linked-file-latest-version file-or-attachment))
                   (get-in file-or-attachment [:latestVersion :version]))]
     (str (:major version) "." (:minor version)))))

(defn- make-attachment-type [{{:keys [type-group type-id]} :type}]
  (str type-group "." type-id))

(defn- person-name [person-data]
  (ss/trim
    (str (get-in person-data [:henkilotiedot :sukunimi :value])
         \space
         (get-in person-data [:henkilotiedot :etunimi :value]))))

(defn foremen [application]
  (if (ss/blank? (:foreman application))
    (let [new-foremen (when (env/feature? :new-foreman-invite)
                        (foreman/get-foreman-documents-v3 application))
          foremen     (->> (foreman-util/get-linked-foreman-applications-by-id (:id application))
                           (map foreman/get-foreman-document)
                           (concat new-foremen)
                           (map #(person-name (:data %)))
                           seq)]
      (when foremen
        (ss/join ", " foremen)))
    (:foreman application)))

(defn tyomaasta-vastaava [application]
  (when-let [{:keys [_selected henkilo
                     yritys]} (:data (domain/get-document-by-subtype application :tyomaasta-vastaava))]
    (if (= (:value _selected) "henkilo")
      (person-name henkilo)
      (some-> yritys :yritysnimi :value ss/trim))))

(defn- building-ids-for-op
  "The building ids are matched to `document` as follows:

  1. If the documents has the (non-blank) building id, it is used.

  2. If the document operation has a corresponding building, the building's id is
     used (even if it is blank).

  3. If there are no matches _every_ building id is used.

  The blank ids are omitted from the result."
  [document buildings b-key doc-ks]
  (let [op-id   (some-> document :schema-info :op :id)
        doc-bid (ss/blank-as-nil (get-in document doc-ks))
        build   (util/find-by-key :operationId op-id buildings)]
    (remove ss/blank?
            (cond
              doc-bid [doc-bid]
              build   [(ss/blank-as-nil (b-key build))]
              :else   (->> buildings (remove :operationId) (keep b-key))))))

(defn- building-ids
  "Return the building-ids, preferring the value from documents if it exist,
  otherwise from buildings."
  [buildings op-documents b-key doc-ks]
  (->> op-documents
       (mapcat #(building-ids-for-op % buildings b-key doc-ks))
       distinct))

(defn- location [application buildings loc-key]
  (if (= 1 (count buildings))
    ; Use building coordinates only for attachments related to exactly one buildings
    (or (loc-key (first buildings)) (loc-key application))
    (loc-key application)))

(defn project-description [{:keys [_projectDescriptionIndex] :as application} documents]
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
  [{:keys [state ^long extincted]}]
  (when (= state "extinct")
    (util/assoc-when
      {:permit-expired true}
      :permit-expired-date (some-> extincted (Date.)))))

(defn op-specific-data-for-attachment [{:keys [buildings documents] :as application} attachment]
  (let [;; If there is only one operation and one building, those are matched regardless
        ;; of fields. In other words, "every" operation and builiding (one and one) is
        ;; included.
        one-to-one?       (and (= (count buildings) 1)
                               (not (seq (:secondaryOperations application))))
        attachment-op-ids (when-not one-to-one?
                            (seq (att-util/get-operation-ids attachment)))
        op-filtered-bldgs (cond->> buildings
                            attachment-op-ids
                            (buildings-with-operation attachment-op-ids))
        op-filtered-docs  (if attachment-op-ids
                            (documents-with-operation attachment-op-ids documents)
                            (filter (util/fn-> :schema-info :op) documents))]
    {:nationalBuildingIds   (building-ids op-filtered-bldgs op-filtered-docs :nationalId [:data :valtakunnallinenNumero :value])
     :buildingIds           (building-ids op-filtered-bldgs op-filtered-docs :localId [:data :kunnanSisainenPysyvaRakennusnumero :value])
     :operations            (find-op application attachment-op-ids)
     :kayttotarkoitukset    (get-usages application attachment-op-ids)
     :rakennusluokat        (get-building-types application attachment-op-ids)
     :location-etrs-tm35fin (location application op-filtered-bldgs :location)
     :location-wgs84        (location application op-filtered-bldgs :location-wgs84)
     :projectDescription    (project-description application op-filtered-docs)}))

(defn permit-ids-for-archiving [application attachment permitType]
  (let [backend-ids (vif/published-municipality-permit-ids application)]
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
        security-end (-> (or (date/zoned-date-time verdict-ts) (date/today))
                         (.plusYears (or (util/->long salassapitoaika) 0))
                         date/iso-datetime)]
    (if security-end
      (assoc metadata :security-period-end security-end)
      metadata)))

(defn- update-retention-end-date [metadata application]
  (if-let [retention (some-> (:sailytysaika metadata)
                             (assoc :retention-period-end
                                    (tiedonohjaus/retention-end-date metadata
                                                                     application))
                             util/strip-nils
                             not-empty)]
    (assoc metadata :sailytysaika retention)
    (dissoc metadata :sailytysaika)))

(defn tos-type-name [{:keys [type-group type-id]}]
  (keyword (str (name type-group) "." (name type-id))))

(defn generate-archive-metadata
  [{:keys [id propertyId _applicantIndex address organization municipality permitType
           tosFunction handlers closed drawings] :as application}
   user
   s2-md-key
   file-id->id-map
   & [attachment-or-linked-file]]
  (let [s2-metadata (or (s2-md-key attachment-or-linked-file)
                        (s2-md-key application))

        permit-ids (permit-ids-for-archiving application attachment-or-linked-file permitType)
        verdict-ts (get-verdict-ts application attachment-or-linked-file)
        target-type (get-in attachment-or-linked-file [:target-entity :target-type])
        model-type (get-in attachment-or-linked-file [:metadata :model-type])
        parent-file-id (get-in attachment-or-linked-file [:target-entity :parent-file-id])
        type (cond (and attachment-or-linked-file
                        target-type)
                   (tos-type-name (tiedonohjaus/linked-file-target-type->tos-type target-type model-type))

                   attachment-or-linked-file
                   (make-attachment-type attachment-or-linked-file)

                   :else
                   :hakemus)
        base-metadata {:type                  type
                       ;; Don't use application ids for archiving projects if there are municipal permit ids
                       :applicationId         (when (or (not= permitType permit/ARK) (empty? permit-ids)) id)
                       :propertyId            propertyId
                       :applicants            (or _applicantIndex [])
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
                       :tiedostonimi          (if target-type
                                                (-> attachment-or-linked-file
                                                    storage/linked-file-latest-version
                                                    :metadata
                                                    :name)
                                                (get-in attachment-or-linked-file [:latestVersion :filename] (str id ".pdf")))
                       :kasittelija           (select-keys (util/find-first :general handlers) [:userId :firstName :lastName])
                       :arkistoija            (select-keys user [:username :firstName :lastName])
                       :kieli                 "fi"
                       :versio                (if attachment-or-linked-file (make-version-number attachment-or-linked-file target-type) "1.0")
                       :suunnittelijat        (:_designerIndex (amf/designers-index application))
                       :foremen               (foremen application)
                       :contents              (:contents attachment-or-linked-file)
                       :size                  (:size attachment-or-linked-file)
                       :scale                 (:scale attachment-or-linked-file)
                       :tyomaasta-vastaava    (tyomaasta-vastaava application)
                       :closed                (date/iso-datetime closed)
                       :drawing-wgs84         (seq (map :geometry-wgs84 drawings))
                       :ramLink               (:ramLink attachment-or-linked-file)
                       :parent-id             (get file-id->id-map parent-file-id)
                       ;; case-file metadata does not include these, but archival schema requires them
                       :myyntipalvelu         false
                       :nakyvyys              :julkinen}]
    (-> base-metadata
        (merge (op-specific-data-for-attachment application attachment-or-linked-file))
        su/remove-blank-keys
        (merge (dissoc s2-metadata :draftTarget)
               (building-specific-data-for-attachment application attachment-or-linked-file)
               (extinct-metadata-for-attachment application))
        ;; see lupapalvelu.tiedonohjaus/merge-linked-file-metadata-update
        (dissoc :metadata :name :model-type :document-identifier :document-date :ryhti)
        (update-retention-end-date application)
        (with-secrecy-metadata verdict-ts))))

(defn xkt-linked-file [ifc-file-metadata parent-file-id latest-file-version]
  (let [file-name (str (get-in latest-file-version [:metadata :name]) ".xkt")
        latest-file-version-with-xkt-file-name (assoc-in latest-file-version [:metadata :name] file-name)]
    {:target-entity {:target-type "bim-additional-file"
                     :parent-file-id parent-file-id}
     :metadata (assoc ifc-file-metadata :name file-name)
     :versions [latest-file-version-with-xkt-file-name]}))

(defn archive-linked-file [application user created gridfs-results file-id->id-map linked-file]
  (when-not (archival-states (keyword (get-in linked-file [:metadata :tila])))
    (let [latest-version (storage/linked-file-latest-version linked-file)
          file-id (:file-version-id latest-version)
          {:keys [content contentType]} (get gridfs-results file-id)
          metadata-fn #(generate-archive-metadata application user :metadata file-id->id-map linked-file)]
      ;; If we have an IFC file, we should archive XKT as well
      (when (= contentType "application/x-step")
        (let [xkt-file-version-id (str file-id "-xkt-file")
              xkt-content (get gridfs-results xkt-file-version-id)
              ifc-file-id (get-in linked-file [:target-entity :file-id])
              xkt-archive-id (str (:id linked-file) "-xkt-file")
              xkt-file-id (str file-id "-xkt-file")
              xkt-file (xkt-linked-file (:metadata linked-file) ifc-file-id latest-version)
              xkt-metadata-fn #(generate-archive-metadata application user :metadata file-id->id-map xkt-file)]
          (upload-and-set-state xkt-archive-id
                                (:content xkt-content)
                                (:contentType xkt-content)
                                xkt-metadata-fn
                                application
                                created
                                (partial set-linked-file-state xkt-file-id)
                                user)))
      (upload-and-set-state (:id linked-file)
                            content
                            contentType
                            metadata-fn
                            application
                            created
                            (partial set-linked-file-state (get-in linked-file [:target-entity :file-id]))
                            user))))

(defn archive-linked-files [application user created gridfs-results file-id->id-map selected-linked-files]
  (->> selected-linked-files
       (run! (partial archive-linked-file application user created gridfs-results file-id->id-map))))

(defn send-to-archive
  "Prepares metadata for selected attachments/documents
   and sends them to Onkalo archive in a separate thread"
  [{:keys [user created organization system] {:keys [attachments id] :as application} :application} attachment-ids document-ids linked-file-ids]
  {:pre [(map? system)]}
  (if (or (vif/latest-published-verdict-date application)
          (foreman-util/foreman-app? application)
          (valid-ya-state? application)
          (archiving-project? application)
          (:facta-imported application)
          (app-utils/paper-permit? application))
    (let [selected-attachments    (filter (fn [{:keys [id metadata] :as attachment}]
                                            (and (attachment-ids id)
                                                 (att/can-be-archived? attachment (force organization))
                                                 (seq metadata)))
                                          attachments)
          all-linked-files        (linked-files/get-linked-application-files-for {:application-id (:id application)})
          file-id->id-map         (->> all-linked-files
                                       (map (fn [entry]
                                              [(get-in entry [:target-entity :file-id]) (str (:id entry))]))
                                       (into {}))
          selected-linked-files   (filter (fn [{:keys [target-entity metadata]}]
                                            (and (linked-file-ids (:file-id target-entity))
                                                 (seq metadata)))
                                          all-linked-files)
          application-archive-id  (str id "-application")
          case-file-archive-id    (str id "-case-file")
          case-file-xml-id        (str case-file-archive-id "-xml")
          foreman-list-archive-id (str id "-foreman-list")
          file-ids                (map #(get-in % [:latestVersion :fileId]) selected-attachments)
          files                   (storage/download-many application file-ids)
          linked-files            (storage/download-many-linked-files application selected-linked-files)
          gridfs-results          (->> (concat files linked-files)
                                       (map (fn [{:keys [fileId] :as res}]
                                              [fileId res]))
                                       (into {}))]

      (when (and (document-ids application-archive-id)
                 (not (archival-states (keyword (get-in application [:metadata :tila])))))
        (let [content-fn  #(pdf-export/generate-application-pdfa application :fi)
              metadata-fn #(generate-archive-metadata application user :metadata {})]
          (upload-and-set-state application-archive-id
                                content-fn
                                "application/pdf"
                                metadata-fn
                                application
                                created
                                set-application-state
                                user)))

      (when (and (document-ids foreman-list-archive-id)
                 (-> (get-in application [:foreman-list-metadata :tila])
                     keyword
                     archival-states
                     not))
        (let [content-fn  (fn []
                            (let [html->pdf                      (partial html-template/html->pdf system)
                                  {:keys [pdf-file-stream text]} (-> (foreman/get-all-initiated-foreman-documents application)
                                                                     (foreman-info-template/foreman-list-html application :fi)
                                                                     html->pdf)]
                              (if pdf-file-stream
                                (let [tmp (files/temp-file "foreman-list-pdf-a-" ".tmp")]
                                  (io/copy pdf-file-stream tmp)
                                  (files/temp-file-input-stream tmp))
                                (throw (Exception. (str "Failed to convert foreman list to PDF: " text))))))
              metadata-fn (fn []
                            (-> (generate-archive-metadata application user :foreman-list-metadata {})
                                (assoc :type :osapuolet.tyonjohtajaluettelo
                                       :tiedostonimi (str foreman-list-archive-id ".pdf"))))]
          (upload-and-set-state foreman-list-archive-id
                                content-fn
                                "application/pdf"
                                metadata-fn
                                application
                                created
                                set-foreman-list-state
                                user)))

      (when (and (document-ids case-file-archive-id)
                 (not (archival-states (keyword (get-in application [:processMetadata :tila])))))
        (files/with-temp-file libre-file
          (history/write-history-libre-doc application :fi libre-file)
          (let [pdf-is          (laundry-client/convert-libre-template-to-pdfa-stream libre-file)
                pdf-fn          #(identity pdf-is)
                xml-fn          #(-> (tiedonohjaus/xml-case-file application :fi)
                                     (.getBytes "UTF-8")
                                     io/input-stream)
                metadata-fn     #(-> (generate-archive-metadata application user :processMetadata {})
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
      (when (seq selected-linked-files)
        (archive-linked-files application user created gridfs-results file-id->id-map selected-linked-files))
      (->> selected-attachments
           (mapv (fn [attachment]
                   (when-not (archival-states (keyword (get-in attachment [:metadata :tila])))
                     (let [file-id                       (get-in attachment [:latestVersion :fileId])
                           {:keys [content contentType]} (get gridfs-results file-id)
                           metadata-fn                   #(generate-archive-metadata application
                                                                                     user
                                                                                     :metadata
                                                                                     {}
                                                                                     attachment)]
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
