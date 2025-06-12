(ns lupapalvelu.admin.import-export.phrase-category
  (:require [lupapalvelu.admin.import-export.impl :as impl]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.admin.import-export.util :as ie-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer [$addFields $exists $gt $match $objectToArray $project $set $size]]
            [sade.core :refer [fail fail!]]
            [sade.shared-schemas :as ssc]
            [schema.core :as sc]))

(def ^:const target :phrase-category)

(defmethod impl/-organization-query [:export target]
  [_ _]
  ;; Find all organizations with phrase categories
  (->> (mongo/aggregate :organizations
                        [{$match {:custom-phrase-categories {$exists true}}}
                         {$addFields {:category-count {$size {$objectToArray "$custom-phrase-categories"}}}}
                         {$match {:category-count {$gt 0}}}
                         {$project {:name 1}}])
       (map (juxt :_id :name))
       (into {})))

(defmethod impl/-organization-query [:import target]
  [_ _]
  ;; Find all organizations. Technically phrases are used with Pate, but
  ;; we might support them in other features too later
  (ie-util/export-all-organizations!))

(sc/defn get-items :- model/PhraseCategoryItems
  {:always-validate true
   :private         true}
  [org-id :- org/OrgId
   item-ids :- (sc/maybe #{ssc/ObjectIdStr})]
  (->> (or (mongo/by-id :organizations org-id [:custom-phrase-categories])
           (fail! :error.organization-not-found))
       :custom-phrase-categories
       (filter (if (seq item-ids)
                 (comp item-ids name first)
                 identity))
       (map (fn [[id values]]
              [(name id) values]))
       (into {})))

(defmethod impl/-item-query target
  [_ org-id item-ids]
  (get-items org-id item-ids))

(defn- do-import
  "Merge with existing categories"
  [{:keys [id custom-phrase-categories]} categories replace?]
  (let [categories     (->> categories
                            ;; Retain the original ID; we might later import phrases which refer
                            ;; to the phrase category id
                            (map (fn [[id category]]
                                   [(keyword id) (dissoc category :id)]))
                            (into {}))
        new-categories (if replace?
                         categories
                         (merge custom-phrase-categories
                                categories))]
    (if (not= custom-phrase-categories new-categories)
      (mongo/update-by-id :organizations id {$set {:custom-phrase-categories new-categories}})
      (log/info "Ignoring import, no changes"))
    true))

(defn- custom-phrase-categories-in-use?
  [org new-ids]
  (->> org
       :phrases
       (map :category)
       (remove new-ids)
       (some #(re-matches ssc/object-id-pattern %))
       boolean))

(defmethod impl/-import! target
  [_ org-id {categories :items} replace?]
  (let [org            (mongo/by-id :organizations org-id)
        new-ids        (->> categories (map first) set)
        ;; Mustn't replace other custom phrase categories if we have phrases using them
        ;; Exception: we are replacing matching categories
        custom-in-use? (delay (and replace? (custom-phrase-categories-in-use? org new-ids)))]
    (cond
      (nil? org)             (fail :error.organization-not-found)
      (force custom-in-use?) (fail :error.import.phrase-category-in-use)
      :else                  {:ok (do-import org categories replace?)})))
