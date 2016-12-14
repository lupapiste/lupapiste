(ns lupapalvelu.attachment.tag-groups
  (:require [clojure.set :refer [intersection union]]
            [sade.strings :as ss]
            [lupapalvelu.attachment.tags :as att-tags]))


(def attachment-tag-group-hierarchy
  "A model for the attachment hierarchy"
  [[:general]
   [:parties]
   [:building-site]
   [:operation
    [:paapiirustus]
    [:rakennesuunnitelma]
    [:kvv_suunnitelma]
    [:iv_suunnitelma]
    [:default]]])

(defn- attachments-operation-ids [attachments]
  (->> (map (comp :id :op) attachments)
       (remove nil?)
       distinct))

(defn- hierarchy-level-tags [hierarchy]
  (->> hierarchy
       (map first)
       (set)))

(defn- tag-set [attachment]
  (-> attachment :tags set))

(defn- tags-for-hierarchy-level [hierarchy attachment]
  (let [tags (intersection (hierarchy-level-tags hierarchy)
                           (tag-set attachment))]
    (if (empty? tags)
      #{:general}
      tags)))

(defn- all-tags-for-hierarchy-level [hierarchy attachments]
  (apply union (map (partial tags-for-hierarchy-level hierarchy) attachments)))

(defn- filter-tag-groups
  "remove the tag groups whose tag is not found in the attachments"
  [attachments hierarchy]
  (filter (comp (all-tags-for-hierarchy-level hierarchy attachments)
                first)
          hierarchy))

(defn- for-operation? [op-id attachment]
  (= op-id (-> attachment :op :id)))

(defn- operation-tag-group [attachments hierarchy op-id]
  (concat [(att-tags/op-id->tag op-id)]
          (filter-tag-groups (filter (partial for-operation? op-id) attachments)
                             hierarchy)))

(defn- add-operation-tag-groups
  "replace the operation hierarchy template with the actual hierarchies"
  [attachments hierarchy]
  (let [[pre operation post] (partition-by #(= (first %) :operation)
                                           hierarchy)
        op-ids               (attachments-operation-ids attachments)]
    (if-let [operation-template (first operation)]
      (concat pre
              (map (partial operation-tag-group attachments (rest operation-template))
                   op-ids)
              post)
      hierarchy)))

(defn- add-tags [attachment]
  (assoc attachment :tags (att-tags/attachment-tags attachment)))

(defn attachment-tag-groups
  "Get hierarchical attachment grouping by attachments tags for a specific application, based on a tag group hierarchy.
  There are one and two level groups in tag hierarchy (operations are two level groups).
  eg. [[:default] [:parties] [:building-site] [opid1 [:paapiirustus] [:iv_suunnitelma] [:default]] [opid2 [:kvv_suunnitelma] [:default]]]"
  ([application]
   (attachment-tag-groups application attachment-tag-group-hierarchy))
  ([{attachments :attachments :as application} hierarchy]
   (let [enriched-attachments (map add-tags attachments)]
     (->> hierarchy
          (filter-tag-groups enriched-attachments)
          (add-operation-tag-groups enriched-attachments)))))
