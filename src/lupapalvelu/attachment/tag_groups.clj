(ns lupapalvelu.attachment.tag-groups
  (:require [clojure.set :refer [intersection union]]
            [sade.util :refer [fn->] :as util]
            [sade.strings :as ss]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.type :as att-type]))

(defn- node
  "(node :foo [:bar :quux]) => [:foo [:bar] [:quux]]"
  [parent children]
  (apply vector parent (map vector children)))

(def attachment-tag-group-hierarchy
  "A model for the attachment hierarchy"
  [[:building-site]
   (node :application
         att-tags/application-group-types)
   (node :operation
         att-type/type-groups)
   (node :multioperation
         att-type/type-groups)])

(defn- get-operation-ids [{op :op :as attachment}]
  (->> (if (sequential? op) (mapv :id op) [(:id op)])
       (remove nil?)))

(defn- tag-set [attachment]
  (-> attachment :tags set))

(defn- attachments-tag-sets-with-tag [tag attachments-tags]
  (-> (filter (fn-> (contains? tag)) attachments-tags)
      not-empty))

(defn- filter-tag-groups
  "remove the tag groups whose tag is not found in the attachments"
  [attachments-tag-sets hierarchy]
  (letfn [(filter-recursively [[parent & children :as group]]
            (when-let [attachments-tag-sets-in-group (attachments-tag-sets-with-tag parent attachments-tag-sets)]
              (->> (filter-tag-groups attachments-tag-sets-in-group children)
                   (cons parent))))]
    (keep filter-recursively hierarchy)))

(defn- partition-by-tag
  "Return [pre tagged post], where pre is all groups before one with
  tag, tagged is the group with tag, and post is all subsequent
  groups. Assume tags are unique."
  [tag hierarchy]
  (let [[pre [tagged & post]] (split-with #(not= (first %) tag) hierarchy)]
    [pre
     tagged
     (or post [])]))

(defn- add-operation-tag-groups
  "replace the operation hierarchy template with the actual hierarchies"
  [attachments hierarchy]
  (let [[pre operation post] (partition-by-tag :operation hierarchy)
        op-ids               (-> (mapcat get-operation-ids attachments) distinct)]
    (if operation
      (concat pre
              (map (fn-> att-tags/op-id->tag (cons (rest operation))) op-ids)
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
          (add-operation-tag-groups enriched-attachments)
          (filter-tag-groups (map tag-set enriched-attachments))))))
