(ns lupapalvelu.elasticsearch.applications
  (:require [clojure.set :as set]
            [lupapalvelu.application-schema :as app-schema]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.elasticsearch.api :as es-api]
            [lupapalvelu.elasticsearch.util :as es-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongo-watcher.client :as mw-client]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def es-operation-fields [:id :name :description :created :extinct])

(def EsOperation (st/select-keys app-schema/Operation es-operation-fields))

(defn operation-fields [kw-path-root]
  (->> es-operation-fields
       (map (partial util/kw-path kw-path-root))
       set))

(def shared-fields
  "Fields that are copied from mongo document to elastic document as is"
  (set/union
    #{:_attachment_indicator_reset
      :_comments-seen-by
      :_statements-seen-by
      :_verdicts-seen-by
      :address
      :applicant
      :archived.application
      :archived.completed
      :attachments.approvals
      :attachments.auth.id
      :attachments.id
      :attachments.latestVersion.created
      :attachments.latestVersion.fileId
      :attachments.latestVersion.originalFileId
      :attachments.latestVersion.stamped
      :attachments.latestVersion.user.id
      :attachments.latestVersion.user.role
      :attachments.metadata.julkisuusluokka
      :attachments.metadata.nakyvyys
      :attachments.source.id
      :attachments.source.type
      :attachments.target.id
      :attachments.target.type
      :attachments.versions.created
      :attachments.versions.fileId
      :attachments.versions.originalFileId
      :attachments.versions.stamped
      :attachments.versions.user.id
      :attachments.versions.user.role
      :auth.company-role
      :auth.id
      :auth.role
      :authorityNotice
      :buildings.nationalId
      :closed
      :comments.created
      :comments.roles
      :comments.target.id
      :comments.target.type
      :comments.text
      :comments.user.id
      :company-notes.companyId
      :company-notes.tags
      :created
      :creator.firstName
      :creator.lastName
      :documents.data.tyoaika-alkaa-ms.value
      :documents.data.tyoaika-paattyy-ms.value
      :facta-imported
      :foreman
      :foremanRole
      :handlers.firstName
      :handlers.lastName
      :handlers.roleId
      :handlers.userId
      :history.comment
      :history.state
      :history.ts
      :id
      :infoRequest
      :kind
      :kuntalupatunnus
      :location
      :location-wgs84
      :modified
      :municipality
      :organization
      :pate-verdicts.id
      :pate-verdicts.modified
      :pate-verdicts.published.published
      :permitSubtype
      :permitType
      :propertyId
      :state
      :statements.given
      :statements.id
      :statements.person.email
      :statements.person.userId
      :statements.state
      :submitted
      :tags
      :urgency
      :verdictDate
      :verdicts.draft
      :verdicts.id
      :verdicts.paatokset.poytakirjat.paatospvm
      :verdicts.timestamp
      :warrantyEnd
      stats-db/processing-projection}
    (operation-fields :primaryOperation)
    (operation-fields :secondaryOperations)))

(def text-search-fields
  "Fields that are combined to field `:text-search` in elastic"
  #{:_applicantIndex
    :_creatorIndex
    :documents.data.henkilo.henkilotiedot.sukunimi.value
    :documents.data.henkilotiedot.sukunimi.value
    :documents.data.yritys.yhteyshenkilo.henkilotiedot.sukunimi.value
    :documents.data.yritys.yritysnimi.value
    :foreman
    :id})

(def kuntalupatunnus-source-fields
  "Fields in application MongoDB document that are required for finding
   out the 'kuntalupatunnus™"
  #{:verdicts.category
    :verdicts.data.kuntalupatunnus
    :verdicts.kuntalupatunnus
    :pate-verdicts.published
    :pate-verdicts.draft
    :pate-verdicts.category
    :pate-verdicts.legacy?
    :pate-verdicts.data.kuntalupatunnus
    :pate-verdicts.kuntalupatunnus})

(def derived-fields-sources
  "Fields that act as source data for some field in elastic document"
  (set/union
    text-search-fields                                      ; text-search for full-text search
    #{:permitSubtype :infoRequest :permitType}              ; for calculating :kind
    #{:documents.id
      :documents.data
      :documents.meta._approved.timestamp
      :documents.meta._indicator_reset.timestamp
      :documents.schema-info.name
      :documents.schema-info.version}                       ; for calculating :indicator-data.document-modifications
    kuntalupatunnus-source-fields                           ; for finding kuntalupatunnus
    ))

(def dynamic-key-maps
  "Fields that are automatically converted to vectors of {:key key :value value} maps:

   {:_comments-seen-by {:some-id-1 1741937559767
                        :some-id-2 1741938539595}}
   <->
   {:_comments-seen-by [{:key \"some-id-1\" :value 1741937559767}
                        {:key \"some-id-2\" :value 1741938539595}]}
  "
  #{:_comments-seen-by
    :_statements-seen-by
    :_verdicts-seen-by})

(def followed-top-level-fields
  "Fields that are watched for index update but are not required for mongo
   projection.

   When element is removed from an array change stream return the array field
   as changed, not the actual removed element fields. For example, when an
   attachment is removed, the changed fields is #{:attachments}

   When new object is added to a document its subfields are not shown in the
   change stream documents' changed fields. For example when adding :_stats for
   the first time.
   "
  #{:_stats
    :attachments
    :auth
    :comments
    :handlers
    :pate-verdicts
    :statements
    :verdicts})

(def source-fields
  "Fields that should trigger elastic document update when changed. Can also be
   used as projection in mongo query when querying source data for es-document"
  (set/union shared-fields derived-fields-sources dynamic-key-maps))

(def followed-fields
  (set/union source-fields followed-top-level-fields))

(def EsAttachmentVersion {:created                          sc/Int
                          (sc/optional-key :fileId)         sc/Str
                          (sc/optional-key :originalFileId) sc/Str
                          (sc/optional-key :stamped)        sc/Bool
                          (sc/optional-key :user)           {:id sc/Str :role sc/Str}})

(defn parse-address
  "Parses an address string into two searchable components:
   - `:codes` — digit sequences and short letter sequences (1–2 chars) found in the
     address, joined by spaces. Intended for matching house numbers and abbreviated
     suffixes (e.g. \"12\", \"B\", \"a\").
   - `:words` — words of three or more characters found in the address, joined by
     spaces. Intended for matching street names and place names.

   Returns nil when `address` is blank."
  [address]
  (when-not (ss/blank? address)
    (let [number-codes (re-seq #"\d+" address)
          letter-codes (->> (ss/replace address #"\p{L}{3,}+" " ")
                            (re-seq #"[a-zA-Z]+"))
          words        (re-seq #"\p{L}{3,}+" address)]
      {:codes (ss/join " " (concat number-codes letter-codes))
       :words (ss/join " " words)})))

(sc/defschema ESApplication
  "Elasticsearch document schema for application"
  (st/merge
    {:id            sc/Str
     :address       sc/Str
     :organization  sc/Str
     :kind          sc/Str
     :municipality  sc/Str
     :state         sc/Str
     :permitType    sc/Str
     :propertyId    sc/Str}
    (st/optional-keys
      {:address-codes               sc/Str
       :auth                        [{:id                             sc/Str
                                      :role                           sc/Str
                                      (sc/optional-key :company-role) sc/Str}]
       :buildings                   [{(sc/optional-key :nationalId) sc/Str}]
       :created                     sc/Int
       :modified                    sc/Int
       :submitted                   sc/Int
       :authorityNotice             sc/Str
       :permitSubtype               sc/Str
       :verdictDate                 sc/Int
       :location                    {:x sc/Num :y sc/Num} ;; Location is already "enriched" by app-utils/location->object
       :location-wgs84              [(sc/one sc/Num "X") (sc/one sc/Num "Y")]
       :text-search                 sc/Str
       :_attachment_indicator_reset sc/Int
       :kuntalupatunnus             sc/Str
       :urgency                     sc/Str
       :applicant                   sc/Str
       :creator                     {:firstName sc/Str
                                     :lastName  sc/Str}
       :infoRequest                 sc/Bool
       :facta-imported              sc/Bool
       :tags                        [sc/Str]
       :primaryOperation            EsOperation
       :secondaryOperations         [EsOperation]
       :company-notes               [{:companyId              sc/Str
                                      (sc/optional-key :tags) [sc/Str]}]
       :handlers                    [{:userId    sc/Str
                                      :roleId    sc/Str
                                      :firstName sc/Str
                                      :lastName  sc/Str}]
       :handler-sortfield           sc/Str
       :history                     [{(sc/optional-key :ts)      sc/Int
                                      (sc/optional-key :state)   sc/Str
                                      (sc/optional-key :comment) sc/Str}]
       :archived                    {(sc/optional-key :application) sc/Int
                                     (sc/optional-key :completed)   sc/Int}
       :warrantyEnd                 sc/Int
       :closed                      sc/Int
       :documents                   [{:data {(sc/optional-key :tyoaika-alkaa-ms)   {:value sc/Int}
                                             (sc/optional-key :tyoaika-paattyy-ms) {:value sc/Int}}}]
       :foreman                     sc/Str
       :foremanRole                 sc/Str
       :_comments-seen-by           [{:key sc/Str :value sc/Int}]
       :_statements-seen-by         [{:key sc/Str :value sc/Int}]
       :_verdicts-seen-by           [{:key sc/Str :value sc/Int}]
       :comments                    [{:created                  sc/Int
                                      :roles                    [sc/Str]
                                      (sc/optional-key :target) {(sc/optional-key :type) sc/Str
                                                                 (sc/optional-key :id)   sc/Str}
                                      (sc/optional-key :user)   {:id sc/Str}
                                      (sc/optional-key :text)   sc/Str}]
       :statements                  [{:person                  {:email  sc/Str
                                                                :userId sc/Str}
                                      (sc/optional-key :given) sc/Int
                                      :id                      sc/Str
                                      :state                   sc/Str}]
       :pate-verdicts               [{:id                          sc/Str
                                      (sc/optional-key :modified)  sc/Int
                                      (sc/optional-key :published) {:published sc/Int}}]

       :verdicts                    [{:id                          sc/Str
                                      (sc/optional-key :timestamp) sc/Int
                                      (sc/optional-key :draft)     sc/Bool
                                      (sc/optional-key :paatokset) [{(sc/optional-key :poytakirjat) [{(sc/optional-key :paatospvm) sc/Int}]}]}]
       :attachments                 [(st/merge
                                       {:id sc/Str}
                                       (st/optional-keys {:metadata      {(sc/optional-key :julkisuusluokka) sc/Str
                                                                          (sc/optional-key :nakyvyys)        sc/Str}
                                                          :approvals     [{:key sc/Str :value {:state sc/Str}}]
                                                          :auth          [{:id sc/Str}]
                                                          :latestVersion EsAttachmentVersion
                                                          :versions      [EsAttachmentVersion]
                                                          :source        {(sc/optional-key :id)   sc/Str
                                                                          (sc/optional-key :type) sc/Str}
                                                          :target        {:id sc/Str :type sc/Str}}))]
       :_stats                      {:processing stats-sc/DbProcessing}
       :indicator-data              {:document-modifications sc/Int}})))

(def application-es-document-checker (sc/checker ESApplication))

(defn build-text-search-content [application]
  (some->> (util/deep-select-keys application text-search-fields)
           util/all-values
           (map str)
           (ss/trim-join " ")))

(defn convert-dynamic-key-submap [m submap-path]
  (if (seq (get m submap-path))
    (update m submap-path #(mapv (fn [[key val]] {:key (name key) :value val}) %))
    m))

(defn restore-dynamic-key-submap [m path]
  (if (seq (get m path))
    (update m path #(some->> %
                             (map (fn [{:keys [key value]}]
                                    [(keyword key) value]))
                             (into {})))
    m))

(defn convert-dynamic-key-maps
  "Converts dynamic-key-maps in the application (such as :_comments-seen-by etc.)
   into vectors of {:key key :value value} maps. This is required for Elasticsearch
   compatibility, as it does not support dynamic keys in nested objects."
  [application]
  (-> (reduce #(convert-dynamic-key-submap %1 %2)
              application dynamic-key-maps)
      (update :attachments (fn [attachments]
                             (mapv #(convert-dynamic-key-submap % :approvals) attachments)))))

(defn restore-dynamic-key-maps
  "An inverse function for `convert-dynamic-key-maps`"
  [application]
  (-> (reduce #(restore-dynamic-key-submap %1 %2)
              application dynamic-key-maps)
      (update :attachments (fn [attachments] (mapv #(restore-dynamic-key-submap % :approvals) attachments)))))


(defn select-attachment-approval-fields [application]
  (update application
          :attachments
          (fn [attachments]
            (mapv (fn [attachment]
                    (if (seq (:approvals attachment))
                      (update attachment
                              :approvals
                              (fn [approvals] (update-vals approvals #(select-keys % [:state]))))
                      attachment))
                  attachments))))

(defn build-indicator-data [{:keys [documents]}]
  {:document-modifications (apply + (map model/modifications-since-approvals documents))})

(defn cleanup-filthy-data [application]
  (->
    ;; TODO: These can be removed when LPK-7670 is done!
    (reduce (fn [app field]
              (if (seq (field app))
                (update app field (partial util/filter-map-by-val int?))
                app))
            application
            [:_comments-seen-by :_statements-seen-by :_verdicts-seen-by])

    ;; Remove non-integer timestamps from history items. Should be removed when/if
    ;; history items are no longer needed in elastic
    (update :history #(map (fn [history-item]
                             (if-not (int? (:ts history-item))
                               (dissoc history-item :ts)
                               history-item)) %))))

(sc/defn application->es-document :- ESApplication [application]
  (let [application (-> application
                        mongo/with-id
                        cleanup-filthy-data
                        search/enrich-row)
        app-id      (:id application)
        es-document (-> application

                        ;; Select fields that are copied from mongo document to elastic document
                        (util/deep-select-keys shared-fields)
                        select-attachment-approval-fields

                        ;; Convert dynamic-key-maps
                        convert-dynamic-key-maps

                        ;; Build derived fields
                        (assoc :address-codes (:codes (parse-address (:address application))))
                        (assoc :indicator-data (build-indicator-data application))
                        (assoc :text-search (build-text-search-content application))
                        (assoc :handler-sortfield (->> application :handlers first ((juxt :lastName :firstName)) (ss/trim-join " ")))
                        util/strip-nils)]
    (log/with-logging-context {:id app-id}
      (if-let [schema-errors (application-es-document-checker es-document)]
        (log/warnf "Cannot create application document for elastic, id: %s, errors: %s" app-id schema-errors)
        es-document))))

(defmethod es-util/elastic-documents :elastic.index/applications [_ & {:keys [query]}]
  (->> (mongo/select :applications (or query {}) (vec (util/remove-path-collisions source-fields)))
       (keep application->es-document)))

(defmethod es-util/document-ids :elastic.index/applications [_]
  (map :id (mongo/select :applications {} [:id])))

(defn index-application [elastic application db-event-ts]
  (let [start-ts     (now)
        es-document  (application->es-document application)
        doc-ready-ts (now)
        end-ts       (delay (now))]
    (when es-document
      (es-api/put! elastic :elastic.index/applications (:id application) es-document)
      ;; NOTE: Update latency is only indicative because the timestamps come from different machines. However, a rising
      ;; update latency indicates that events are arriving faster than they can be processed.
      (log/infof "Updated application to :elastic.index/applications. id: %s, Document generation %sms, Elastic put! %sms, Update latency %sms"
                 (:id application) (- doc-ready-ts start-ts) (- @end-ts doc-ready-ts) (- @end-ts db-event-ts)))))

(def change-stream-counter-init-val {:updates 0 :discards 0})
(def change-stream-counter (atom change-stream-counter-init-val))

(defn- update-counter [op]
  (let [[old new] (swap-vals! change-stream-counter update op inc)]
    (when (= 999 (apply + ((juxt :updates :discards) old)))
      (let [{:keys [updates discards]} new]
        ;; Write log and reset change stream counters
        (reset! change-stream-counter change-stream-counter-init-val)
        (log/infof "%.2f%% of change stream update events (%s/%s) triggered elastic update to applications index."
                   (float (-> updates (/ (+ updates discards)) (* 100)))
                   updates
                   (+ updates discards))))))

(defn followed-fields-changed?
  "Returns true if `changed-fields` in change-stream-document contains fields that
   should trigger elastic update"
  [changed-fields]
  (boolean (or (seq (set/intersection followed-fields changed-fields))
               ;; Check if any of the dynamic-key-maps have changed.
               ;; Objects with dynamic keys cannot be followed on the field level
               (seq (set/intersection dynamic-key-maps (->> changed-fields
                                                            (map (comp first util/split-kw-path))
                                                            set))))))

(defn application-elastic-updater [elastic {:keys [id operation-type changed-fields full-document db-event-ts]}]
  (case operation-type
    ;; Document created
    :insert (index-application elastic full-document db-event-ts)
    ;; Document updated
    :update (if (followed-fields-changed? changed-fields)
              (do
                (update-counter :updates)
                (index-application elastic full-document db-event-ts))
              (update-counter :discards))
    ;; Document deleted
    :delete (do (log/infof "Deleting application from elastic. id: %s" id)
                (es-api/delete! elastic :elastic.index/applications id))))

;; Register mongo-watcher event handler to handle Elastic updates when :applications mongo collection change
(defmethod mw-client/event-handler-callback ::handler
  [_]
  application-elastic-updater)
