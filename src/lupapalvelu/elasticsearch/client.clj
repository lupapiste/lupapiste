(ns lupapalvelu.elasticsearch.client
  (:require [clojure.core.async :as async]
            [clojure.set :as set]
            [integrant.core :as ig]
            [lupapalvelu.elasticsearch.api :as es-api]
            [lupapalvelu.elasticsearch.client-schema :as es-schema]
            [lupapalvelu.elasticsearch.init :as es-init]
            [lupapalvelu.elasticsearch.util :as es-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [qbits.spandex :as sp]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.coerce :as stc]
            [schema-tools.core :as st]
            [schema.coerce :as sco])
  (:import [java.io Closeable]
           [org.elasticsearch.client RestClient]))

(set! *warn-on-reflection* true)

(defn- schema-keys [schema]
  (keys (st/required-keys schema)))

(defn- coerce-result-fn [schema]
  (let [coercer      (sco/coercer! schema stc/json-coercion-matcher)
        allowed-keys (schema-keys schema)]
    (fn [es-hit]
      (-> (assoc (:_source es-hit) :id (:_id es-hit))
          (select-keys allowed-keys)
          (coercer)))))

(defn- bulk-operation [rest-client put-data!]
  (let [{:keys [input-ch output-ch]} (sp/bulk-chan rest-client
                                                   {:flush-threshold         200
                                                    :flush-interval          5000
                                                    :max-concurrent-requests 3})
        *errors (future
                  (loop [error-responses []]
                    ;; Collect any errors that may occur in the bulk operations. Successes are ignored.
                    (if-let [[_job response] (async/<!! output-ch)]
                      (if (-> response :body :errors)
                        (->> response
                             :body
                             :items
                             (keep (comp :error :index))
                             (into error-responses)
                             recur)
                        (recur error-responses))
                      error-responses)))]
    (put-data! input-ch)
    ;; Closing the channel will consume any remaining documents in the queue and close the underlying resources.
    (async/close! input-ch)
    (when-let [errors (seq @*errors)]
      (let [msg (format "Errors in bulk operation: total errors %s" (count errors))]
        (log/error msg)
        (run! #(log/warnf "Error in bulk operation: error %s" %) errors)
        (throw (ex-info msg {:errors errors}))))))

(defrecord Elasticsearch [^RestClient rest-client index-alias-map db-name elastic-config]
  es-api/SearchApi
  (put! [_ index id document]
    (try
      (-> (sp/request rest-client (merge {:url    [(-> index-alias-map index :write) :_doc id]
                                          :method :put
                                          :body   (es-util/assoc-common-fields elastic-config index document)}
                                         (when (:testing? elastic-config)
                                           {:query-string {:refresh true}})))
          :body)
      (catch Exception e
        (throw (ex-info (format "Failed to index document to Elasticsearch using put! (index: %s, id: %s)." index id)
                        {:id       id
                         :index    index
                         :document document}
                        e)))))

  (put-bulk! [_ index ids+documents]
    (let [index-name (-> index-alias-map index :write)]
      (bulk-operation rest-client
                      (fn [input-ch]
                        (let [counter (atom 0)]
                          (run! (fn [[id doc]]
                                  ;; Elastic bulk operations consists of two lines: the first line is the action and second is
                                  ;; the document source.
                                  ;; Spandex will convert the Clojure maps to newline delimited JSON for Elastic.
                                  ;; We use the blocking put, so in practice this blocks when the flush-threshold is full,
                                  ;; until the response consumer will read all the responses from the output channel.
                                  (when (= (mod (swap! counter inc) 1000) 0)
                                    (log/infof "put-bulk! processed %s items. Index: %s, Current item id: %s" @counter index id))

                                  (async/>!! input-ch {:index {:_index index-name :_id id}})
                                  (async/>!! input-ch (es-util/assoc-common-fields elastic-config index doc)))
                                ids+documents))))))

  (re-index-by-query! [this index query]
    (log/with-logging-context {:query query}
      (log/infof "Starting batch update to elastic index. Index: %s, db-name: %s, testing? = %s" index db-name (:testing? elastic-config))
      (try
        (let [docs (->> (es-util/elastic-documents index {:query query})
                        (map (juxt :id (partial es-util/assoc-common-fields elastic-config index))))]
          (if (:testing? elastic-config)
            ;; When testing, update needs to be blocking which cannot be done with bulk-channel
            (run! (fn [[id doc]] (es-api/put! this index id doc)) docs)
            (es-api/put-bulk! this index docs)))
        (catch Exception e
          (log/error e "Errors in Elastic bulk update")))
      (log/infof "Finished batch update to elastic index. Index %s, db-name: %s, testing? = %s" index db-name (:testing? elastic-config))))

  (full-index-update! [this index]
    (es-api/re-index-by-query! this index {}))

  (update-document! [_ index id update-body query-params]
    (-> (sp/request rest-client {:url          [(-> index-alias-map index :write) :_update id]
                                 :method       :post
                                 :body         update-body
                                 :query-string query-params})
        :body))

  (search-raw [_ index search-opts]
    (try
      (-> (sp/request rest-client {:url    [(-> index-alias-map index :read) :_search]
                                   :method :get
                                   :body   (update search-opts :size (fnil identity 1000))})
          :body)
      (catch Exception e
        (log/with-logging-context {:index       index
                                   :search-opts search-opts}
          (log/errorf e "Error in Elasticsearch SearchApi method search-raw")
          (throw e)))))

  (search-raw-all [_ index search-opts]
    (let [batch-size 5000
          ch         (sp/scroll-chan rest-client
                                     {:url  [(-> index-alias-map index :read) :_search]
                                      :body (-> search-opts
                                                (assoc :size batch-size)
                                                (update :_source (fnil identity false)))})]
      (loop [results         []
             total           nil
             cumulative-time 0]
        (if-let [{:keys [took] :as batch-result} (:body (async/<!! ch))]
          (let [{:keys [hits total]} (:hits batch-result)]
            (recur (into results
                         ;; To optimize memory usage for very large result sets, only the :_source field is retained.
                         (map #(select-keys % [:_source]))
                         hits)
                   total
                   (long (+ cumulative-time took))))
          {:took cumulative-time
           :hits {:total total
                  :hits  results}}))))

  (search-documents [this index schema search-opts]
    (let [coerce (coerce-result-fn schema)]
      (-> (es-api/search-raw this index (assoc search-opts :_source (schema-keys schema)))
          :hits
          (update :hits #(mapv coerce %))
          (update :total :value))))

  (unique-values-for-field [this index schema field search-opts]
    (let [field-schema (st/get-in schema [field])
          field-schema (if (vector? field-schema) (first field-schema) field-schema)
          coercer      (if field-schema
                         (sco/coercer! field-schema stc/json-coercion-matcher)
                         identity)
          result       (es-api/search-raw this
                                          index
                                          (merge {:_source false
                                                  :size    0
                                                  :aggs    {field {:terms {:field field
                                                                           :size  1000}}}}
                                                 search-opts))]
      (->> result
           :aggregations
           field
           :buckets
           (map (comp coercer :key))
           set)))

  (unique-values-for-nested-field [this index schema main-field nested-field search-opts]
    (let [coercer   (if-let [schema (st/get-in schema [main-field nested-field])]
                      (sco/coercer! schema stc/json-coercion-matcher)
                      identity)
          field-key (str (some-> (namespace main-field) (str "/")) (name main-field) "."
                         (some-> (namespace nested-field) (str "/")) (name nested-field))]
      (->> (es-api/search-raw this
                              index
                              (merge {:_source false
                                      :size    0
                                      :aggs    {main-field
                                                {:nested {:path main-field}
                                                 :aggs   {nested-field {:terms {:field field-key
                                                                                :size  1000}}}}}}
                                     search-opts))
           :aggregations
           main-field
           nested-field
           :buckets
           (map (comp coercer :key))
           set)))

  (count-documents [_ index search-opts]
    (try
      (-> (sp/request rest-client {:url    [(-> index-alias-map index :read) :_count]
                                   :method :get
                                   :body   search-opts})
          :body
          :count)
      (catch Exception e
        (log/errorf e "Failed to get elastic index document count. Index %s." index))))

  (any-matches? [this index query]
    (-> (es-api/search-raw this index {:size            0
                                       :terminate_after 1
                                       :_source         false
                                       :query           query})
        :hits :total :value (> 0)))

  (document-exists? [_ index id]
    (try
      (sp/request rest-client {:url    [(-> index-alias-map index :read) :_doc id]
                               :method :head})
      true
      (catch Exception e
        (if (-> (ex-data e) :status (= 404))
          false
          (throw e)))))

  (get-by-id [_ index id source-fields]
    (-> (sp/request rest-client (merge {:url    [(-> index-alias-map index :read) :_doc id]
                                        :method :get}
                                       (when source-fields
                                         {:query-string {:_source (ss/join "," (map name source-fields))}})))
        :body))

  (get-document [this index id schema]
    (let [coerce (coerce-result-fn schema)]
      (-> (es-api/get-by-id this index id (schema-keys schema))
          coerce)))


  (delete! [_ index id]
    (sp/request rest-client (merge {:url    [(-> index-alias-map index :write) :_doc id]
                                    :method :delete}
                                   (when (:testing? elastic-config)
                                     {:query-string {:refresh true}}))))

  (delete-by-query! [_ index query]
    (sp/request rest-client (util/assoc-when {:url    [(-> index-alias-map index :write) :_delete_by_query]
                                              :method :post
                                              :body   {:query query}}
                                             :query-string (when (:testing? elastic-config) {:refresh true}))))

  (update-index-settings! [_ index settings]
    (when-let [actual-index (if (keyword? index)
                              (-> index-alias-map index :write)
                              index)]
      (log/info "Modifying index settings for" actual-index ":" settings)
      (sp/request rest-client {:url    [actual-index :_settings]
                               :method :put
                               :body   {:index settings}})))

  (get-index-settings [_ index]
    (when-let [actual-index (if (keyword? index)
                              (-> index-alias-map index :write)
                              index)]
      (-> (sp/request rest-client {:url    [actual-index :_settings]
                                   :method :get})
          :body
          (get (-> index-alias-map index :index-name keyword))
          :settings
          :index)))

  (refresh [_ index]
    (sp/request rest-client {:url    [(-> index-alias-map index :write) :_refresh]
                             :method :post}))

  (get-all-document-ids [_ index]
    (let [ch (sp/scroll-chan rest-client
                             {:url  [(-> index-alias-map index :read) :_search]
                              :body {:size    10000
                                     :_source false}})]
      (loop [ids #{}]
        (if-let [batch-ids (some->> (async/<!! ch)
                                    :body
                                    :hits
                                    :hits
                                    (map :_id)
                                    seq)]
          (recur (into ids batch-ids))
          ids))))

  (delete-bulk! [_ index ids]
    (let [index-name (-> index-alias-map index :write)]
      (bulk-operation rest-client
                      (fn [input-ch]
                        (run! (fn [id]
                                (async/>!! input-ch {:delete {:_index index-name :_id id}}))
                              ids)))))

  (delete-outdated-documents! [this index]
    (let [all-es-ids   (es-api/get-all-document-ids this index)
          all-db-ids   (set (es-util/document-ids index))
          obsolete-ids (set/difference all-es-ids all-db-ids)]
      (es-api/delete-bulk! this index obsolete-ids))))

(defmethod ig/assert-key ::client [_ args]
  (es-schema/valid-elastic-config! (dissoc args :conn)))

(defn initialize [{:keys [conn hosts index-prefix indices mongo-watcher-enabled? settings testing?] :as opts}]
  ;; TODO could we avoid this binding?
  (binding [mongo/*connection* conn]
    (let [rest-client     (sp/client {:hosts   (->> hosts
                                                    (sort-by first)
                                                    (map second))
                                      :request {:socket-timeout 60000}})
          index-alias-map (es-util/index-alias-map indices index-prefix)
          elastic-config  (dissoc opts :conn)
          elasticsearch   (->Elasticsearch rest-client index-alias-map (mongo/current-db-name) elastic-config)]

      ;; Index setup is done by the same instance that runs mongo-mongo watcher.
      (when (or mongo-watcher-enabled? testing?)
        (log/infof "Running elasticsearch index setup. Mongo-watcher: %s, testing?: %s" mongo-watcher-enabled? testing?)
        (doseq [index indices]
          (es-init/setup-index elasticsearch index settings index-prefix testing?)))
      elasticsearch)))

(defmethod ig/init-key ::client [k {:keys [enabled? hosts] :as opts}]
  (log/infof "Starting %s..." k)
  (log/with-logging-context {:elasticsearch.enabled enabled?
                             :elasticsearch.hosts   hosts}
    (initialize opts)))

(defmethod ig/halt-key! ::client [k {:keys [^Closeable rest-client]}]
  (log/infof "Stopping %s..." k)
  (assert rest-client)
  (.close rest-client))
