(ns lupapalvelu.attachment.tags
  (:require [sade.util :refer [fn->>]]
            [lupapalvelu.states :as states]
            [lupapalvelu.attachment.type :as att-type]))

(def attachment-groups [:parties :building-site :operation])
(def general-group-tag :general)
(def all-group-tags (cons general-group-tag attachment-groups))

(defmulti groups-for-attachment-group-type (fn [application group-type] group-type))

(defmethod groups-for-attachment-group-type :default [_ group-type]
  [{:groupType group-type}])

(defmethod groups-for-attachment-group-type :operation [{primary-op :primaryOperation secondary-ops :secondaryOperations} _]
  (->> (cons primary-op secondary-ops)
       (map (partial merge {:groupType :operation}))))

(defn attachment-groups-for-application [application]
  (mapcat (partial groups-for-attachment-group-type application) attachment-groups))

(defn- tag-by-applicationState [{ram :ramLink app-state :applicationState :as attachment}]
  (cond
    ram :ram
    (states/post-verdict-states (keyword app-state)) :postVerdict
    :else :preVerdict))

(defn- tag-by-notNeeded [{not-needed :notNeeded :as attachment}]
  (if not-needed
    :notNeeded
    :needed))

(defn- tag-by-file-status [{{file-id :fileId} :latestVersion :as attachment}]
  (when file-id
    :hasFile))

(defn- op-id->tag [op-id]
  (when op-id
    (str "op-id-" op-id)))

(defn- tag-by-group-type [{group-type :groupType {op-id :id} :op}]
  (or (some-> group-type keyword ((set (remove #{:operation} all-group-tags))))
      (when op-id :operation)
      general-group-tag))

(defn- tag-by-operation [{{op-id :id} :op :as attachment}]
  (op-id->tag op-id))

(defn- tag-by-type [{{op-id :id} :op :as attachment}]
  (or (att-type/tag-by-type attachment)
      (when op-id att-type/other-type-group)))

(defn attachment-tags
  "Returns tags for a single attachment for filtering and grouping attachments of an application"
  [attachment]
  (->> ((juxt tag-by-applicationState
              tag-by-group-type
              tag-by-operation
              tag-by-notNeeded
              tag-by-type
              tag-by-file-status)
        attachment)
       (remove nil?)))

(defn- attachments-group-types [attachments]
  (->> (map tag-by-group-type attachments)
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
       (map tag-by-type)
       distinct))

(defn- operation-grouping
  "Creates subgrouping for operations attachments if needed."
  [type-groups operation-id]
  (if (empty? type-groups)
    [(op-id->tag operation-id)]
    (->> (map vector type-groups)
         (cons (op-id->tag operation-id)))))

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
  (->> (filter (set (attachments-group-types attachments)) (cons general-group-tag attachment-groups)) ; keep sorted
       (mapcat (partial tag-grouping-for-group-type application))))

(defn- filter-tag-group-attachments [attachments [tag & _]]
  (filter #((-> % :tags set) tag) attachments))

(defn sort-by-tags [attachments tag-groups]
  (if (not-empty tag-groups)
    (->> (map (partial filter-tag-group-attachments attachments) tag-groups)
         (mapcat #(sort-by-tags %2 (rest %1)) tag-groups))
    attachments))

(defn- application-state-filters [{attachments :attachments state :state}]
  (let [states (-> (map tag-by-applicationState attachments) set)]
    (->> [{:tag :preVerdict  :default (boolean (states/pre-verdict-states (keyword state)))}
          (when (or (states/post-verdict-states (keyword state)) (:postVerdict states))
            {:tag :postVerdict :default (boolean (states/post-verdict-states (keyword state)))})
          (when (:ram states)
            {:tag :ram :default true})]
         (remove nil?))))

(defn- group-and-type-filters [{attachments :attachments}]
  (let [existing-groups-and-types (->> (mapcat (juxt tag-by-type tag-by-group-type) attachments)
                                       (remove #{:operation})
                                       set)]
    (->> (concat all-group-tags att-type/type-groups)
         (filter existing-groups-and-types)
         (map (partial hash-map :default false :tag) ))))

(defn- not-needed-filters [{attachments :attachments}]
  (let [existing-notNeeded-tags (-> (map tag-by-notNeeded attachments) set)]
    (when (every? existing-notNeeded-tags [:needed :notNeeded])
      [{:tag :needed    :default true}
       {:tag :notNeeded :default false}])))

(defn attachments-filters
  "Get all possible filters with default values for attachments based on attachment data."
  [application]
  (->> ((juxt application-state-filters group-and-type-filters not-needed-filters) application)
       (remove nil?)
       (filter (fn->> count (< 1)))))
