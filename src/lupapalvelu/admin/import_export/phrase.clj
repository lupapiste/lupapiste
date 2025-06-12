(ns lupapalvelu.admin.import-export.phrase
  (:require [clojure.set :as set]
            [lupapalvelu.admin.import-export.impl :as impl]
            [lupapalvelu.admin.import-export.model :as model]
            [lupapalvelu.admin.import-export.util :as ie-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.shared-schemas :as pate-schemas]
            [monger.operators :refer [$each $exists $not $push $set $size]]
            [sade.core :refer [fail fail!]]
            [sade.shared-schemas :as ssc]
            [schema.core :as sc]))

(def ^:const target :phrase)

(defmethod impl/-organization-query [:export target]
  [_ _]
  ;; Find all organizations with phrases
  (->> (mongo/select :organizations {:phrases {$exists true $not {$size 0}}} [:name])
       (map (juxt :id :name))
       (into {})))

(defmethod impl/-organization-query [:import target]
  [_ _]
  ;; Find all organizations. Technically phrases are used with Pate, but
  ;; we might support them in other features too later
  (ie-util/export-all-organizations!))

(sc/defn get-items :- model/PhraseItems
  {:always-validate true
   :private         true}
  [org-id :- org/OrgId
   item-ids :- (sc/maybe #{ssc/ObjectIdStr})]
  (->> (or (mongo/by-id :organizations org-id [:phrases])
           (fail! :error.organization-not-found))
       :phrases
       (filter (if (seq item-ids)
                 (comp item-ids :id)
                 identity))
       (map (juxt :id identity))
       (into {})))

(defmethod impl/-item-query target
  [_ org-id item-ids]
  (get-items org-id item-ids))

(defn- do-import
  "Assign new ids and concatenate with existing phrases"
  [org-id phrases replace?]
  (let [phrases (->> phrases
                     (mapv (fn [[_id phrase]]
                             (assoc phrase :id (mongo/create-id)))))]
    (mongo/update-by-id :organizations org-id (if replace?
                                                {$set {:phrases phrases}}
                                                {$push {:phrases {$each phrases}}}))
    true))

(defmethod impl/-import! target
  [_ org-id {phrases :items} replace?]
  (let [org                (mongo/by-id :organizations org-id [:custom-phrase-categories])
        unknown-categories (delay
                             (let [known-categories (set/union (-> org :custom-phrase-categories keys set)
                                                               pate-schemas/phrase-categories)
                                   given-categories (->> phrases
                                                         vals
                                                         (map (comp keyword :category))
                                                         set)]
                               (set/difference given-categories known-categories)))]
    (cond
      (nil? org)                       (fail :error.organization-not-found)
      (seq (force unknown-categories)) (fail :error.invalid-categories :text-args (force unknown-categories))
      :else                            {:ok (do-import org-id phrases replace?)})))
