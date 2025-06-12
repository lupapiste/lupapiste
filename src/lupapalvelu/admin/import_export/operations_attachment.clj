(ns lupapalvelu.admin.import-export.operations-attachment
  (:require [clojure.set :as set]
            [lupapalvelu.admin.import-export.impl :as impl]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.admin.import-export.util :as ie-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer [$addFields $exists $gt $match $objectToArray $project $set $size]]
            [sade.core :refer [fail fail!]]
            [schema.core :as sc]))

(def ^:const target :operations-attachment)

(defmethod impl/-organization-query [:export target]
  [_ _]
  ;; Find all organizations with operations attachments
  (->> (mongo/aggregate :organizations
                        [{$match {:operations-attachments {$exists true}}}
                         {$addFields {:result-count {$size {$objectToArray "$operations-attachments"}}}}
                         {$match {:result-count {$gt 0}}}
                         {$project {:name 1}}])
       (map (juxt :_id :name))
       (into {})))

(defmethod impl/-organization-query [:import target]
  [_ _]
  (ie-util/export-all-organizations!))

(defn- export-item
  "Mongo returns operations attachments with operation names as keywords and
  the attachment type group and id as string values, but we want the opposite"
  [[id attachments]]
  [(name id) (->> attachments
                  (mapv (fn [type-group+type-id] (mapv keyword type-group+type-id))))])

(defn- import-item
  "Opposite of `export-item`"
  [[id attachments]]
  [(keyword id) (->> attachments
                     (mapv (fn [type-group+type-id] (mapv name type-group+type-id))))])

(sc/defn get-items :- model/OperationsAttachmentItems
  {:always-validate true
   :private         true}
  [org-id :- org/OrgId
   item-ids :- (sc/maybe #{sc/Str})]
  (->> (or (mongo/by-id :organizations org-id [:operations-attachments])
           (fail! :error.organization-not-found))
       :operations-attachments
       (filter (if (seq item-ids)
                 (comp item-ids name first)
                 identity))
       (map export-item)
       (into {})))

(defmethod impl/-item-query target
  [_ org-id item-ids]
  (get-items org-id item-ids))

(defn- deep-merge-with
  [f & xs]
  (apply
    (fn m* [& xs]
      (cond
        (every? map? xs)  (apply merge-with m* xs)
        (every? coll? xs) (->> (apply concat xs)
                               distinct
                               (into (empty (first xs))))
        :else             (apply f xs)))
    (filter some? xs)))

(defn deep-merge
  "Like `sade.util/deep-merge`, but distinctly concatenates any coll values"
  [& xs]
  (apply deep-merge-with (fn [_a b] b) xs))

(defn- do-import
  "Merge with existing categories"
  [{:keys [id operations-attachments]} new-operations-attachments replace?]
  (let [new-operations-attachments (->> new-operations-attachments
                                        (map import-item)
                                        (into {}))
        result                     (if replace?
                                     new-operations-attachments
                                     (deep-merge operations-attachments new-operations-attachments))]
    (if (not= result operations-attachments)
      (mongo/update-by-id :organizations id {$set {:operations-attachments result}})
      (log/info "Ignoring import, no changes"))
    true))

(defmethod impl/-import! target
  [_ org-id {operations-attachments :items} replace?]
  (let [{:keys [selected-operations]
         :as   org} (mongo/by-id :organizations org-id [:operations-attachments
                                                        :selected-operations])
        invalid-ops (delay (let [selected (set selected-operations)
                                 import   (set (keys operations-attachments))]
                             (if-not (set/subset? import selected)
                               (do
                                 (log/warnf "Operations not selected: %s" (set/difference import selected))
                                 (set/difference import selected))
                               #{})))]
    (cond
      (nil? org)                (fail :error.organization-not-found)
      (seq (force invalid-ops)) (fail :error.unknown-operations :loc-args (->> (force invalid-ops)
                                                                               (mapv (fn [op] (str op "._group_label")))))
      :else                     {:ok (do-import org operations-attachments replace?)})))
