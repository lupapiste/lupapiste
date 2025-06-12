(ns lupapalvelu.admin.import-export.inspection-summary
  (:require [clojure.set :as set]
            [lupapalvelu.admin.import-export.impl :as impl]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.admin.import-export.util :as ie-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer [$each $exists $not $push $set $size]]
            [sade.core :refer [fail fail!]]
            [sade.shared-schemas :as ssc]
            [schema.core :as sc]))

(def ^:const target :inspection-summary)

(defmethod impl/-organization-query [:export target]
  [_ _]
  ;; Find all organizations with inspection summary templates
  (->> (mongo/select :organizations {:inspection-summary.templates {$exists true $not {$size 0}}} [:name])
       (map (juxt :id :name))
       (into {})))

(defmethod impl/-organization-query [:import target]
  [_ _]
  (ie-util/export-all-organizations!))

(sc/defn get-items :- model/InspectionSummaryItems
  {:always-validate true
   :private         true}
  [org-id :- org/OrgId
   item-ids :- (sc/maybe #{ssc/ObjectIdStr})]
  (->> (or (mongo/by-id :organizations org-id [:inspection-summary.templates])
           (fail! :error.organization-not-found))
       :inspection-summary
       :templates
       (filter (if (seq item-ids)
                 (comp item-ids :id)
                 identity))
       (map (juxt :id identity))
       (into {})))

(defmethod impl/-item-query target
  [_ org-id item-ids]
  (get-items org-id item-ids))

(defn- do-import
  [org-id templates replace?]
  (let [templates (->> templates
                       (map (fn [[_id template]] template))
                       ;; Sort by the original ID to effectively sort by the creation time
                       (sort-by :id)
                       (into []))]
    (mongo/update-by-id :organizations org-id (if replace?
                                                {$set {:inspection-summary.templates templates}}
                                                {$push {:inspection-summary.templates {$each templates}}})))
  true)

(defmethod impl/-import! target
  [_ org-id {templates :items} replace?]
  (let [org               (mongo/by-id :organizations org-id [:inspection-summary])
        templates-in-use? (delay
                            ;; Shouldn't replace all the templates if the existing ones are assigned to operations
                            (and replace?
                                 (->> org
                                      :inspection-summary
                                      :operations-templates
                                      (mapcat (fn [[_operation template-ids]]
                                                template-ids))
                                      seq)))
        id-conflicts      (delay
                            ;; (Similar to verdict template import)
                            ;; Try to detect if we accidentally import an existing template
                            ;; Alternatively we could allow this and generate new IDs during import
                            (when-not replace?
                              (let [current-ids (->> org :inspection-summary :templates
                                                     (map :id)
                                                     set)
                                    import-ids  (set (keys templates))]
                                (set/intersection current-ids import-ids))))]
    (cond
      (nil? org)                 (fail :error.organization-not-found)
      (force templates-in-use?)  (fail :error.import.inspection-templates-in-use)
      (seq (force id-conflicts)) (fail :error.import.duplicate-ids :text-args (force id-conflicts))
      :else                      {:ok (do-import org-id templates replace?)})))
