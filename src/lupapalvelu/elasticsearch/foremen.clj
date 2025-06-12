(ns lupapalvelu.elasticsearch.foremen
  (:require [clojure.set :as set]
            [lupapalvelu.application-search :as search]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.elasticsearch.api :as es-api]
            [lupapalvelu.elasticsearch.applications :as es-applications]
            [lupapalvelu.elasticsearch.util :as es-util]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongo-watcher.client :as mw-client]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(def tj-document-schema (schemas/get-schema {:name "tyonjohtaja-v3"}))

(def vastattavat-tyotehtavat-schema (schemas/get-subschema tj-document-schema :vastattavatTyotehtavat))

(def shared-fields
  "Fields that are copied from mongo document to elastic document as is"
  #{:id
    :auth.company-role
    :auth.id
    :auth.role
    :address
    :created
    :handlers.firstName
    :handlers.lastName
    :handlers.roleId
    :handlers.userId
    :location
    :location-wgs84
    :kuntalupatunnus
    :municipality
    :organization
    :propertyId
    :tags})

(def shared-fields-on-document
  "Fields that are copied from mongo document to elastic document as is"
  #{:id
    :created
    :type
    :state
    :started})

(def derived-fields-sources
  "Fields that act as source data for some field in elastic document"
  (set/union es-applications/kuntalupatunnus-source-fields
             #{:documents.tj-document
               :documents.schema-info.name
               :documents.data.henkilotiedot.etunimi.value
               :documents.data.henkilotiedot.sukunimi.value
               :documents.data.kuntaRoolikoodi.value}
             (->> vastattavat-tyotehtavat-schema
                  :body
                  (map :name)
                  (map #(util/kw-path "documents.data.vastattavatTyotehtavat" % "value"))
                  set)))

(def follow-removes
  "Fields that are watched for element removes.

   When element is removed from an array change stream return the array field
   as changed, not the actual removed element fields. For example, when an
   attachment is removed, the changed fields is #{:attachments}"
  #{:documents
    :auth
    :handlers
    :pate-verdicts
    :verdicts})

(def source-fields
  "Fields that should trigger elastic document update when changed. Can also be
   used as projection in mongo query when querying source data for es-document"
  (set/union derived-fields-sources
             shared-fields
             (set (map #(util/kw-path :documents %)
                       shared-fields-on-document))))

(def followed-fields
  (set/union source-fields follow-removes))

(sc/defschema ESForeman
  "Elasticsearch document schema for foreman-v3"
  (st/merge
    {:application-id sc/Str
     :id             sc/Str
     :address        sc/Str
     :organization   sc/Str
     :municipality   sc/Str
     :propertyId     sc/Str}
    (st/optional-keys
      {:address-codes               sc/Str
       :auth                        [{:id                             sc/Str
                                      :role                           sc/Str
                                      (sc/optional-key :company-role) sc/Str}]
       :created                     sc/Int
       :modified                    sc/Int
       :submitted                   sc/Int
       :state                       sc/Str ; tj document state
       :text-search                 sc/Str
       :_attachment_indicator_reset sc/Int
       :kuntalupatunnus             sc/Str
       :creator                     {:firstName sc/Str
                                     :lastName  sc/Str}
       :location                    {:x sc/Num :y sc/Num} ;; Location is already "enriched" by app-utils/location->object
       :location-wgs84              [(sc/one sc/Num "X") (sc/one sc/Num "Y")]
       :handlers                    [{:userId    sc/Str
                                      :roleId    sc/Str
                                      :firstName sc/Str
                                      :lastName  sc/Str}]
       :handler-sortfield           sc/Str
       :foreman                     sc/Str ; työnjohtaja
       :foremanRole                 sc/Str ; kuntaRoolikoodi
       :started                     sc/Int ; vastuun alkaminen
       :tags                        [sc/Str]
       :tyotehtavat                 {(sc/optional-key :i18nkeys)     [sc/Str]
                                     (sc/optional-key :muuMikaValue) sc/Str}
       :type                        sc/Str ; notification or application
       })))

(defn build-text-search-content [application es-foreman-doc]
  (some->>
    (select-keys es-foreman-doc [:foreman])
    (merge (select-keys application [:id :_applicantIndex :_creatorIndex]))
    vals
    (map str)
    (ss/trim-join " ")))

(defn resolve-tj-tyotehtavat [document]
  (let [data               (-> document :data)
        tyotehtavat-fields (->> (pdf-export/filter-fields-by-group-subtype
                                  data
                                  vastattavat-tyotehtavat-schema
                                  (:body vastattavat-tyotehtavat-schema))
                                (filter (comp #{:checkbox} :type))
                                (filter (fn [{:keys [name]}]
                                          (get-in data [:vastattavatTyotehtavat (keyword name) :value]))))
        i18nkeys           (mapv :i18nkey tyotehtavat-fields)]
    (when (not-empty i18nkeys)
      (util/assoc-when {:i18nkeys i18nkeys}
        :muuMikaValue (when (util/find-by-key :name "muuMika" tyotehtavat-fields)
                        (get-in data [:vastattavatTyotehtavat :muuMikaValue :value]))))))

(def foreman-es-document-checker (sc/checker ESForeman))

(sc/defn application->es-foreman-documents :- [ESForeman] [application]
  (let [application      (-> application
                             mongo/with-id
                             search/enrich-row)
        foreman-docs     (foreman/get-foreman-documents-v3 application)
        app-id           (:id application)
        app-context-data (-> application
                             (util/deep-select-keys (conj shared-fields :kuntalupatunnus))
                             (util/assoc-when :address-codes (:codes (es-applications/parse-address (:address application))))
                             (set/rename-keys {:id :application-id}))
        es-documents     (->> foreman-docs
                              (map #(merge app-context-data
                                           (util/assoc-when {} :tyotehtavat (resolve-tj-tyotehtavat %))
                                           (select-keys % shared-fields-on-document)
                                           (let [submitted (some-> (util/find-first (fn [log-item]
                                                                                      (contains? #{"declared" "submitted"} (:value log-item)))
                                                                                    (:changeLog %))
                                                                   :ts)
                                                 modified  (or (some-> (:changeLog %) last :ts) (:created %))]
                                             (util/assoc-when {}
                                               :submitted submitted
                                               :modified modified))
                                           (when-let [foreman-role (some-> % :data :kuntaRoolikoodi :value)]
                                             {:foremanRole foreman-role})
                                           (when-let [foreman (some->> %
                                                                       :data
                                                                       :henkilotiedot
                                                                       ((juxt (comp :value :sukunimi) (comp :value :etunimi)))
                                                                       (ss/trim-join " "))]
                                             {:foreman foreman})))
                              (mapv #(assoc % :text-search (build-text-search-content application %))))]
    (if-let [schema-errors (not-empty (keep foreman-es-document-checker es-documents))]
      (log/with-logging-context
        {:applicationId app-id
         :schema-errors schema-errors}
        (log/warnf "Cannot create foreman elastic documents for application: %s" app-id)
        [])
      es-documents)))

(defmethod es-util/elastic-documents :elastic.index/foremen [_ & {:keys [query]}]
  (->> (mongo/select :applications
                     (or query {:documents {"$elemMatch" {:tj-document true}}})
                     (vec (util/remove-path-collisions source-fields)))
       (mapcat application->es-foreman-documents)))

(defn index-foreman [elastic application]
  (when-let [es-documents (seq (application->es-foreman-documents application))]
    (log/infof "Updating %s foreman documents to :elastic.index/foremen from application id: %s" (count es-documents) (:id application))
    (->> es-documents
         (map (juxt :id identity))
         (es-api/put-bulk! elastic :elastic.index/foremen))))

(defn delete-foreman-es-documents-by-application-id [elastic id]
  (log/infof "Deleting all foreman documents from elastic related to application-id: %s" id)
  (es-api/delete-by-query! elastic :elastic.index/foremen
                           {:term {:application-id id}}))

(defn- delete-orphaned-es-foreman-documents!
  "Removes such foreman documents from elastic that no longer exist in the application"
  [elastic {:keys [id] :as application}]

  (let [existing-doc-ids (->> application
                              foreman/get-foreman-documents-v3
                              (keep :id)
                              seq)]


    (if existing-doc-ids
      (do
        (log/infof "Deleting orphaned foremen from elastic related to application-id: %s. Remaining foreman documents: %s" id existing-doc-ids)
        (es-api/delete-by-query! elastic :elastic.index/foremen
                                 {:bool {:must     [{:term {"application-id" id}}]
                                         :must_not [{:ids {:values existing-doc-ids}}]}}))
      (delete-foreman-es-documents-by-application-id elastic id))))

(defn foreman-elastic-updater [elastic {:keys [id operation-type changed-fields full-document]}]
  (case operation-type
    ;; Application created
    :insert (index-foreman elastic full-document)
    ;; Application updated
    :update (do
              (when (contains? changed-fields :documents)
                ;; changed-fields contains :documents which implies that a document has been
                ;; removed from the application -> remove orphaned in case it was a foreman document
                (delete-orphaned-es-foreman-documents! elastic full-document))
              (when (seq (set/intersection followed-fields changed-fields))
                (index-foreman elastic full-document)))
    ;; Application deleted
    :delete (delete-foreman-es-documents-by-application-id elastic id)))

;; Mongo query to check if an application document has any document in `documents` array with `tj-document: true`
;; Register mongo-watcher event handler to handle Elastic updates when :applications mongo collection change
(defmethod mw-client/event-handler-callback ::handler
  [_]
  foreman-elastic-updater)
