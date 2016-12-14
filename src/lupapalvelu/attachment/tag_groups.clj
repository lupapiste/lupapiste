(ns lupapalvelu.attachment.tag-groups
  (:require [lupapalvelu.attachment.tags :as att-tags]))


(defn- attachments-group-types [attachments]
  (->> (map att-tags/tag-by-group-type attachments)
       (remove nil?)
       (map keyword)
       distinct))

(defn- attachments-operation-ids [attachments]
  (->> (map (comp :id :op) attachments)
       (remove nil?)
       distinct))

(defn- type-groups-for-operation
  "Returns attachment type based grouping, used inside operation groups."
  [attachments operation-id]
  (->> (filter (comp #{operation-id} :id :op) attachments)
       (map att-tags/tag-by-type)
       distinct))

(defn- operation-grouping
  "Creates subgrouping for operations attachments if needed."
  [type-groups operation-id]
  (if (empty? type-groups)
    [(att-tags/op-id->tag operation-id)]
    (->> (map vector type-groups)
         (cons (att-tags/op-id->tag operation-id)))))

(defmulti tag-grouping-for-group-type (fn [application group-type] group-type))

(defmethod tag-grouping-for-group-type :default [_ group-type]
  [[group-type]])

(defmethod tag-grouping-for-group-type :operation [{attachments :attachments primary-op :primaryOperation secondary-ops :secondaryOperations :as application} _]
  (->> (map :id (cons primary-op secondary-ops)) ; all operation ids sorted
       (filter (set (attachments-operation-ids attachments)))
       (map #(-> (type-groups-for-operation attachments %)
                 (operation-grouping %)))))

(defn attachment-tag-groups
  "Get hierarchical attachment grouping by attachments tags.
  There are one and two level groups in tag hierarchy (operations are two level groups).
  eg. [[:default] [:parties] [:building-site] [opid1 [:paapiirustus] [:iv_suunnitelma] [:default]] [opid2 [:kvv_suunnitelma] [:default]]]"
  [{attachments :attachments :as application}]
  (->> (filter (set (attachments-group-types attachments))
               (cons att-tags/general-group-tag
                     att-tags/attachment-groups)) ; keep sorted
       (mapcat (partial tag-grouping-for-group-type application))))
