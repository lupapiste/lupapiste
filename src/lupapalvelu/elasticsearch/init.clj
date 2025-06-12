(ns lupapalvelu.elasticsearch.init
  (:require [clojure.set :as set]
            [lupapalvelu.elasticsearch.api :as es-api]
            [lupapalvelu.elasticsearch.query :as es-query]
            [lupapalvelu.elasticsearch.util :as es-util]
            [lupapalvelu.logging :as log]))

(defn generate-data-to-new-index
  "Handles indexing data into a brand new index. Updates the write alias so that
   new data is indexed into the new index, while data retrieval operations
   continue to use the old index. Once the new index is ready, updates the read
   alias to point to the new index as well."
  [{:keys [rest-client] :as elasticsearch}
   index-kw
   current-index
   {:keys [read write]}]
  ;; Remove existing write aliases and create write alias to new index:
  (log/infof "Updating write alias to new index: %s" current-index)
  (es-util/remove-aliases rest-client index-kw write)
  (es-util/update-aliases rest-client [{:add {:index current-index :alias write}}])

  ;; Index all documents
  (log/infof "Indexing all documents to index %s" current-index)
  (es-api/update-index-settings! elasticsearch current-index {:refresh_interval -1})
  (es-api/full-index-update! elasticsearch index-kw)
  (es-api/refresh elasticsearch index-kw)
  (es-api/update-index-settings! elasticsearch current-index {:refresh_interval "1s"})

  ;; Update read alias to
  (log/infof "Updating read alias to new index: %s" current-index)
  (es-util/remove-aliases rest-client index-kw read)
  (es-util/update-aliases rest-client [{:add {:index current-index :alias read}}]))

(defn create-new-index
  "Creates a new Elasticsearch index with the given settings and mappings.
  - `elasticsearch`: Map containing the Elasticsearch rest-client, an index-alias-map,
     and boolean testing?.
  - `settings`: Index settings map.
  - `index-kw`: Keyword identifying the index.
  - `current-index`: Name of the new index to create.
  - `index-prefix`: Prefix for the index name. Can be used for test indices.
  - `version`: Version map of an elasticsearch index
  - `testing?`: If true, performs synchronous reindexing; otherwise,
    runs reindexing in a future."
  [{:keys [rest-client] :as elasticsearch}
   settings
   index-kw
   current-index
   index-prefix
   {:keys [major] :as version}
   testing?]
  (let [existing-version                   (->> (range 1 (inc major))
                                                reverse
                                                (filter #(es-util/index-exists? rest-client (es-util/version->index index-kw % index-prefix)))
                                                first)
        {:keys [read write] :as alias-map} (es-util/aliases index-kw index-prefix)]
    (log/infof "Creating index %s" current-index)
    (try
      (es-util/create-index rest-client
                            current-index
                            (-> (es-util/read-mapping-file index-kw version) es-util/merge-common-mapping)
                            (when-not existing-version
                              {read {} write {}})
                            (set/rename-keys settings {:shards   :number_of_shards
                                                       :replicas :number_of_replicas}))
      (if testing?
        (generate-data-to-new-index elasticsearch index-kw current-index alias-map)
        (future (generate-data-to-new-index elasticsearch index-kw current-index alias-map)))

      (catch Exception e
        (log/errorf e  "Something went wrong when creating index %s." current-index)
        (throw e)))))

(defn update-mapping [{:keys [rest-client]} current-index index-kw version]
  (let [mapping-configuration (-> (es-util/read-mapping-file index-kw version) es-util/merge-common-mapping :properties)]
    (try
      (es-util/update-mapping rest-client current-index mapping-configuration)
      (catch Exception e
        (log/errorf e "Failed to update mapping in elasticsearch index: %s. Changes in mapping might require major version bump." current-index)
        (throw e)))))

(defn reindex-if-necessary [elasticsearch current-index index-kw version testing?]
  (let [doc-count (es-api/count-documents elasticsearch index-kw {:query (es-query/version-equals version)})]
    (when (and (int? doc-count) (zero? doc-count))
      (log/info "Re-indexing all documents to index" current-index)
      (if testing?
        (es-api/full-index-update! elasticsearch index-kw)
        (future (es-api/full-index-update! elasticsearch index-kw))))))

(defonce idx-lock (Object.))

(defn setup-index [{:keys [rest-client] :as elasticsearch}
                   {index-kw :name {:keys [major minor] :as version} :version index-settings :settings}
                   settings
                   index-prefix
                   testing?]
  ;; Try to limit simultaneous (expensive) index creation especially in test use to avoid timeouts while waiting for ES
  (locking idx-lock
    (let [current-index (es-util/version->index index-kw major index-prefix)]
      (log/infof "Using index %s, version %s.%s" current-index major minor)
      (if-not (es-util/index-exists? rest-client current-index)
        (create-new-index elasticsearch (merge settings index-settings) index-kw current-index index-prefix version testing?)
        (do
          (update-mapping elasticsearch current-index index-kw version)
          (reindex-if-necessary elasticsearch current-index index-kw version testing?))))))
