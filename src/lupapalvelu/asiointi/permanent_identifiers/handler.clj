(ns lupapalvelu.asiointi.permanent-identifiers.handler
  "Handle assigning permanent IDs to buildings and apartments"
  (:require [lupapalvelu.asiointi.http-util :as http-util]
            [lupapalvelu.asiointi.permanent-identifiers.document :as id-doc]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [malli.core :as m]
            [monger.conversion :as monger-conv]
            [monger.core :as monger]
            [monger.operators :refer [$set]]
            [ring.util.response :as resp]
            [sade.util :as util]))

(def AssignIdentifiersRequest
  (m/schema [:map {:closed true}
             [:buildings
              {:description "List of buildings"}
              id-doc/Buildings]]))

(defn- make-updates
  [matched application-id created]
  ;; NOTE: To make the update a bit more exact, let's use `:arrayFilters` to
  ;; precisely choose by id which documents to update. That way we don't
  ;; rely on the location of individual documents within the `:documents` array.

  ;; NOTE: We only update the documents! There are various references to PRT/PHT
  ;; in review tasks and buildings array etc, which are not covered here!
  {:update  :applications
   :updates [(merge
               {:q {:_id application-id}}
               (->>
                 ;; Process all updates to the same document at the same time
                 (group-by :doc-id matched)
                 ;; Assign a unique index to each document
                 (map-indexed vector)
                 (reduce (fn [acc [idx [doc-id matches]]]
                           (let [doc-idx  (str "doc" idx)
                                 doc-path (format "documents.$[%s].data" doc-idx)]
                             (-> acc
                                 ;; update all relevant fields within the "doc-idx" document (e.g. `doc-0`)
                                 (update-in [:u $set] merge (reduce (fn [acc {:keys [permanent-id-path permanent-id-new]}]
                                                                      (assoc acc
                                                                             (util/kw-path doc-path permanent-id-path :value) permanent-id-new
                                                                             (util/kw-path doc-path permanent-id-path :modified) created))
                                                                    {}
                                                                    matches))
                                 ;; Assign "doc-idx" to match the actual document ID
                                 (update :arrayFilters conj {(util/kw-path doc-idx :id) doc-id}))))
                         {:u            {$set {}}
                          :arrayFilters []})))]})

(defn- make-updates-or-error
  [{:keys [application created buildings]}]
  (let [;; Find all buildings and apartments in the current document(s)
        current-state (id-doc/parse-documents application)
        ;; Try to match the given buildings with the current data
        matched       (id-doc/match-buildings buildings current-state)
        ;; If we found any problems -> abort immediately
        errors        (not-empty (filterv :error matched))]
    (cond
      errors        {:errors errors}
      (seq matched) {:update-command (make-updates matched (:id application) created)})))

(defn- run-update!
  [update-command]
  (boolean (some-> (mongo/get-db)
                   (monger/command update-command)
                   (monger-conv/from-db-object true)
                   :nModified
                   pos?)))

(defn- errors->resp
  [errors]
  (->> errors
       (map (fn [{:keys [error key-name key-value]}]
              {:detail  (case error
                          :overwriting        "Cannot overwrite an existing permanent ID!"
                          :unknown-field      "Assign request references an unknown field!"
                          :incorrect-building "Apartment assigned to the wrong building"
                          "Unknown error")
               :pointer (format "/%s/%s" (name key-name) key-value)}))
       (apply http-util/bad-request)))

(defn assign!
  "Assign new permanent IDs to buildings or apartments"
  {:arglists '([{:keys [application created buildings]}])}
  [options]
  ;; NOTE: We don't really care about the state of the application here. In other words,
  ;; the backend is free to assign IDs regardless of the state (draft, submitted, archived..).
  (let [{:keys [errors update-command]} (make-updates-or-error options)]
    (cond
      errors         (do
                       (log/warn "Invalid request")
                       (errors->resp errors))
      update-command (if (run-update! update-command)
                       (do
                         (log/info "Permanent IDs updated")
                         (resp/status 204))
                       (do
                         (log/info "Update produced no results, ignoring.")
                         (resp/status 204)))
      :else
      (do
        (log/info "Permanent ID request contained no actionable items, ignoring.")
        (resp/status 204)))))
