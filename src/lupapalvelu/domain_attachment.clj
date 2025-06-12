(ns lupapalvelu.domain-attachment
  (:require [sade.util :as util]))

(defn add-attachment-map [application]
  (let [*attachments-by-id (delay (->> application
                                       :attachments
                                       (reduce (fn [acc attachment]
                                                 (assoc acc (:id attachment) attachment))
                                               {})))]
    (assoc application :attachments-by-id *attachments-by-id)))

(defn remove-attachment-map
  "Remove the `:attachments-by-id` quick access map set in `add-attachment-map`"
  [application]
  (dissoc application :attachments-by-id))

(defn get-attachment-by-id-slow
  "Find a single attachment by id (linearly).
  For situations where `attachments-by-id` might be stale or missing."
  [{:keys [attachments]} attachment-id]
  (util/find-by-id attachment-id attachments))

(defn get-attachment-by-id
  "Find a single attachment by id.
  Uses faster lookup if `application` has been enriched with `add-attachment-map`.
  Assumes that `:attachments` has not been modified."
  [{:keys [attachments-by-id] :as application} attachment-id]
  (when attachment-id
    (if attachments-by-id
      (get (force attachments-by-id) attachment-id)
      ;; fallback to old slow linear way if necessary
      (get-attachment-by-id-slow application attachment-id))))

(defn get-attachment-by-target
  "Find a single attachment with matching target type and id"
  [{:keys [attachments]} target-type target-id]
  (some
    (fn [attachment]
      (when (and (= target-type (:type (:target attachment)))
                 (= target-id (:id (:target attachment))))
        attachment))
    attachments))
