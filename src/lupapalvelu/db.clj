(ns lupapalvelu.db
  "Optimized query support for common mongo use cases. The idea is to write the actual
  queries in a way that match the indexes as selectively as possible. Since in practice
  this means quite unintuitive solutions, it is better to provide a higher, use case based
  function interface.

  A typical mongo query uses only one index. Thus, the query should be constructed in a way
  that provides as much information for the index matching as possible. Especially since
  we do not want to use explicit index hinting."
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]))

(defn one-or-many
  "Returns a schema that accepts values that match `schema` or (non-empty) [`schema`]."
  [schema]
  (sc/cond-pre schema [(sc/one schema "At least one") schema]))

(sc/defn ^:always-validate application-ids-by-kuntalupatunnus :- (sc/maybe [ssc/ApplicationId])
  "Applications that have matching `kuntalupatunnus`. Note that permit type is ignored by
  default. `organization-id` can be either a single id or a list of ids. Query can be
  amended with optional `extra` map. `options` are passed as such to
  `mongo/select-options`. Nil if no results."
  ([organization-id :- (one-or-many ssc/NonBlankStr)
    kuntalupatunnus :- ssc/NonBlankStr
    extra options]
   ;; In order to make sure that the most selective index is selected, all the conditions
   ;; should be in the or expression.
   (let [org-ids         (flatten [organization-id])
         kuntalupatunnus (ss/trim kuntalupatunnus)
         m               (-> extra
                             (assoc :organization (case (count org-ids)
                                                    1 (first org-ids)
                                                    {$in org-ids}))
                             ss/trimwalk)
         ;; q binding makes it easier to extract the query for the manual testing.
         q               {$or [;; Backing system (draft or published)
                               (assoc m :verdicts.kuntalupatunnus kuntalupatunnus)
                               ;; Legacy published (we ignore Pate drafts since
                               ;; backend-id is manually edited).
                               (assoc m :pate-verdicts.data.kuntalupatunnus kuntalupatunnus)]}]
     (->> (mongo/select-options :applications q (assoc options :projection [:_id]))
          (map :id)
          seq)))
  ([organization-id kuntalupatunnus]
   (application-ids-by-kuntalupatunnus organization-id kuntalupatunnus nil nil))
  ([organization-id kuntalupatunnus extra]
   (application-ids-by-kuntalupatunnus organization-id kuntalupatunnus extra nil)))

(defn with-sheriff-note
  "Updates update map `m` with `$push` update map for a sheriff note. `text` is the note
  text and `ts` timestamp (default is now). Optional `kvs` are additional key-values for
  note object."
  ([text ts m & kvs]
   {:pre [(ss/not-blank? text)]}
   (->> {:note    text
         :created (date/timestamp  (or ts (date/now)))}
        (merge (apply hash-map kvs))
        ss/trimwalk
        (assoc-in m [$push :_sheriff-notes])))
  ([text m] (with-sheriff-note text nil m))
  ([text] (with-sheriff-note text {})))

(defn with-reporting-override
  "Adds `:reportingDbOverrideTs` $setter for `ts` or the current time to the update map `m`."
  ([ts m]
   (assoc-in m [$set :reportingDbOverrideTs] (date/timestamp  (or ts (date/now)))))
  ([m] (with-reporting-override nil m))
  ([] (with-reporting-override nil nil)))
