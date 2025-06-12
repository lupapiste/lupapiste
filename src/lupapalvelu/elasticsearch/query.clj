(ns lupapalvelu.elasticsearch.query
  (:require [sade.util :as util]))

(defn term
  "Helper function for creating elasticsearch term query. Creates a term or
   terms query for the given field and values. If `values` is a collection with
   a single item, it is unwrapped. Optionally appends `.keyword` to the field
   if `:keyword?` is true."
  [field values & {:keys [keyword?]}]
  (let [values (if (and (coll? values)
                        (= (count values) 1))
                 (first values)
                 values)]
    {(if (coll? values) :terms :term) {(util/kw-path field (when keyword? :keyword)) values}}))

(defn nested-term [path & rules]
  {:pre [(-> rules count even?)]}
  "Helper function for creating elasticsearch term query for nested fields. Accepts
   multiple key/value pairs that all must match a nested object.
   Example: (es-query/nested-term :handlers :userId userId :roleId generalIds)
  "
  {:nested
   {:path  path
    :query {:bool {:must (mapv (fn [[field values]]
                                 (term (util/kw-path path field) values)) (partition 2 rules))}}}})

(defn es-or
  "Helper function for creating elasticsearch or query. Creates a bool query
   with should clauses, requiring at least one to match. Accepts multiple query
   rules."
  [rules]
  {:bool {:minimum_should_match 1
          :should               (vec rules)}})

(defn must [terms]
  {:bool {:must terms}})

(defn must-not [terms]
  {:bool {:must_not terms}})

(defn version-equals
  "Receives a version map and returns an Elasticsearch query that returns
   documents which doc-version equals the defined version.

   Example:
   (version-equals {:major 2 :minor 14})
  "
  [{:keys [major minor]}]
  {:nested
   {:path  :doc-version
    :query {:bool {:must [{:term {:doc-version.major major}}
                          {:term {:doc-version.minor minor}}]}}}})

(defn version-older-than
  "Receives a version map and returns an Elasticsearch query that returns
   documents with version numbers lower than those defined in the version map.

   Example:
   (version-older-than {:major 2 :minor 14})

   Returned query searches for
     - All documents where major version is lower than 2
     - All documents where major version equals 2 and minor version is lower
       than 14
  "
  [{:keys [major minor]}]
  {:nested
   {:path  :doc-version
    :query (es-or [{:range {:doc-version.major {:lt major}}}
                   {:bool {:must [{:term {:doc-version.major major}}
                                  {:range {:doc-version.minor {:lt minor}}}]}}])}})

(defn full-text-search
  "Creates an Elasticsearch match/multi_match query for full-text search on
   the given field(s).

   Accepts optional parameters:
     - `:operator` (\"and\" or \"or\"):     Controls how multiple terms are combined.
     - `:fuzziness` (\"AUTO\", 0, 1, or 2): Sets the fuzziness level for matching.
   Returns a map suitable for use as an Elasticsearch query.

   Example:
   (es-query/full-text-search [:text-search] \"search-phrase\" {:fuzziness 0 :operator :and})
  "
  [fields search-phrase & {:keys [operator fuzziness]}]
  (if (= 1 (count fields))
    {:match {(first fields) (util/assoc-when {:query search-phrase}
                      :operator operator
                      :fuzziness fuzziness)}}
    {:multi_match (util/assoc-when {:query  search-phrase
                                    :fields fields}
                    :operator operator
                    :fuzziness fuzziness)}))
