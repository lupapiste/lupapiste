(ns lupapalvelu.elasticsearch.api)

(defprotocol SearchApi
  (put! [this index id document]
    "Inserts or updates the document with the given `id`.
     Removes the key `:id` from `document` map if present.")
  (put-bulk! [this index ids+documents]
    "Bulk inserts or updates the id + document tuples provided in `ids+documents`.
     Blocks until all documents have been inserted into indexing queue (bulk-channel).
     Actual indexing is not thread blocking.
     Much faster than calling `put!` for each document separately.
     Throws if any of the documents cannot be indexed.")
  (update-document! [this index id update-body query-params]
    "Do a partial or scripted update to the document with `id`.
     See https://www.elastic.co/guide/en/elasticsearch/reference/7.5/docs-update.html
     for body and query options.")
  (full-index-update! [this index]
    "Updates all documents in given index. Does not remove obsolete. Requires that
     (es-util/elastic-documents index) for given index is implemented.
     Blocks thread when testing? in elastic client (i.e. 'this') is true.")
  (re-index-by-query! [this index query]
    "Works similarly to full-index-update! but only creates elastic updates for
     mongo documents returned by the provided query")
  (search-raw [this index search-opts]
    "Returns the raw Elasticsearch search response body as is.")
  (search-raw-all [this index search-opts]
    "Works quite similarly to search-raw but uses Elasticsearch's scroll API to fetch ALL the results.
     If :_source is not given in the search-opts it defaults to false. In other words, returned document
     fields must be explicitly defined.")
  (search-documents [this index schema search-opts]
    "Returns results with id added as `:id` and coerced to the Schema given as argument.
     Return value is a map that looks like:
     `{:total 1 :hits [document_map_coerced_to_given_schema ...]}`")
  (unique-values-for-field [this index schema field search-opts]
    "Returns unique values (up to 1000) for the `field` of the `schema`, filtered with `search-opts`.")
  (unique-values-for-nested-field [this index schema main-field nested-field search-opts])
  (count-documents [this index search-opts]
    "Returns the count of documents for the provided search as an integer.")
  (any-matches? [this index query]
    "Returns true if provided query returns any documents, false if not")
  (document-exists? [this index id]
    "Returns true if the document id exists in index, false if not.")
  (get-by-id [this index id source-fields]
    "Returns the raw Elasticsearch result for `id` with
     optionally limited source fields. Throws on not found.")
  (get-document [this index id schema]
    "Returns the document with id added as `:id` and coerced
     to the Schema given as argument. Throws on not found.")
  (delete! [this index id]
    "Deletes the document with `id`.")
  (delete-by-query! [this index query]
    "Delete all documents matching `query`.")
  (delete-bulk! [this index ids]
    "Bulk deletes the documents with the given `ids`
     Blocks until all documents have been deleted.
     Throws if any of the documents cannot be deleted.")
  (update-index-settings! [this index settings]
    "Update settings for the given index.
     If `index` is a keyword, looks up the corresponding actual current index from the index map.
     If `index` is a string, treats it as a literal.")
  (get-index-settings [this index]
    "Retrieves setting for write index")
  (refresh [this index]
    "Force-refreshes the index immediately.")
  (get-all-document-ids [this index]
    "Returns all document IDs from the index. WARNING: This is a relatively slow
     operation when performed on large indexes. Use with caution!")
  (delete-outdated-documents! [this index]
    "Deletes all Elasticsearch documents from given index for which a
     corresponding MongoDB document does not exist. Requires that
     (es-util/elastic-documents index) for given index is implemented."))


(defn es-doc?
  "Documents in elasticsearch index always contain fields :indexed-ts and :doc-version.
   This can be used to determine whether one is dealing with mongo document or elastic document"
  [doc]
  (let [ts (:indexed-ts doc)
        {:keys [major minor]} (:doc-version doc)]
    (and (int? ts)
         (int? major)
         (int? minor))))
