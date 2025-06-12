(ns lupapalvelu.elasticsearch.util
  (:require [lupapalvelu.logging :as log]
            [qbits.spandex :as sp]
            [sade.core :as sade]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn elevate-index-name [index]
  (keyword "elastic.index" (-> index keyword name (ss/replace #"^:+" ""))))

(defn create-index [client idx mappings aliases settings]
  (sp/request client {:url    [idx]
                      :method :put
                      :body   (util/assoc-when {:mappings (assoc mappings :dynamic "strict") ; Deny dynamic creation of new fields
                                                :settings settings}
                                               :aliases aliases)}))

(defn version->index [index-name version prefix]
  (str prefix (name index-name) "-v" version))

;; Aliases

(defn aliases [index-kw prefix]
  {:read  (str prefix (name index-kw) "-read")
   :write (str prefix (name index-kw) "-write")})

(defn aliases-and-index-name [{index-kw :name version :version} index-prefix]
  (-> (aliases index-kw index-prefix)
      (assoc :index-name (version->index index-kw (:major version) index-prefix)
             :version version)))

(defn index-alias-map [indices index-prefix]
  (->> indices
       (reduce (fn [acc index]
                 (assoc acc (:name index) (aliases-and-index-name index index-prefix)))
               {})))

(defn get-aliases [c index]
  (when (keyword? index)
    (let [re (re-pattern (str "^" (name index) "-v\\d+"))]
      (->> (sp/request c {:url          [:_cat :aliases]
                          :method       :get
                          :query-string {:format "json"}})
           :body
           (util/filter-by-key :index #(re-matches re %))
           (mapv #(select-keys % [:alias :index]))))))

(defn update-aliases [c actions]
  (try (sp/request c {:url    [:_aliases]
                      :method :post
                      :body   {:actions actions}})
       (catch Throwable t
         (log/error t "Could not update aliases")
         (throw t))))

(defn remove-aliases [c index-kw alias]
  (->> (get-aliases c index-kw)
       (util/filter-by-key :alias alias)
       (run! (fn [{:keys [alias index]}]
               (try
                 (update-aliases c [{:remove {:index index :alias alias}}])
                 (catch Exception _
                   (log/infof "Elastic index alias cannot be removed index: %s, alias: % " index alias)))))))

;; Mapping

(def common-mapping
  "Mapping that is common for all elasticsearch indices"
  {:doc-version {:type       "nested"
                 :properties {:major {:type "long"}
                              :minor {:type "long"}}}
   :indexed-ts  {:type "date", :format "epoch_millis"}})

(defn merge-common-mapping [mapping]
  (update mapping :properties merge common-mapping))

(defn full-version [elastic-config index]
  {:pre [(map? elastic-config)]}
  (->> elastic-config
       :indices
       (util/filter-by-key :name index)
       first
       :version
       (util/map-values #(if (string? %) (parse-long %) %))))

(defn assoc-common-fields
  "Assoc common fields to es-document"
  [elastic-config index doc]
  (util/assoc-when doc
                   :doc-version (full-version elastic-config index)
                   :indexed-ts (sade/now)))

(defn read-mapping-file
  "Returns an elasticsearch index mapping from a mapping file.
   index-kw: index name keyword from elastic configuration e.g :elastic.index/applications
   version:  index version from elastic configuration e.g. {:major 2 :minor 0}

   (read-mapping-file :elastic.index/applications {:major 2 :minor 0})
   reads the mapping from the file resources/elasticsearch/applications-v1.edn"
  [index-kw {:keys [major]}]
  (let [res-path (str "elasticsearch/" (name index-kw) "-v" major ".edn")]
    (try
      (util/read-edn-resource res-path)
      (catch Exception e
        (throw (Exception. (str "Missing valid Elasticsearch index mappings " res-path) e))))))

(defn get-mapping [c idx]
  (get-in (sp/request c {:url    [idx :_mapping]
                         :method :get})
          [:body
           (keyword idx)
           :mappings
           :properties]))

(defn update-mapping [c idx mapping]
  (sp/request c {:url    [idx :_mapping]
                 :method :put
                 :body   {:properties mapping}}))

(defn- thing-exists? [c path]
  (try
    (sp/request c {:url    path
                   :method :head})
    true
    (catch Exception _
      false)))

(defn index-exists? [c idx]
  (thing-exists? c [idx]))

(defmulti elastic-documents
  "Multimethod invoked for each Elasticsearch index during a full update
   operation. The method should return a clojure.lang.LazySeq to avoid loading
   the entire MongoDB collection into memory at once. For optimal performance,
   use a MongoDB projection to limit the returned fields to only those necessary.
  "
  {:arglists '([index & args])}
  (fn [index & _] index))

(defmethod elastic-documents :default [index & _]
  (log/errorf "No implementation of elastic-documents for parameter: %s" index))

(defmulti document-ids
  "Method for this multi should be for each elasticsearch index. It should return
   Lazy sequence of all the document ids that should exist in the elastisc index. Basically
   the same as `elastic-documents` but just the ids.
  "
  identity)

(defmethod document-ids :default [index]
  (log/errorf "No implementation of document-ids for parameter: %s" index))
