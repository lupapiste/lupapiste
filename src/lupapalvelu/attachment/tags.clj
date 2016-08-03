(ns lupapalvelu.attachment.tags
  (:require [lupapalvelu.states :as states]
            [lupapalvelu.attachment.type :as att-type]))

(def attachment-groups [:parties :building-site :operation])

(defmulti groups-for-attachment-group-type (fn [application group-type] group-type))

(defmethod groups-for-attachment-group-type :default [_ group-type]
  [{:groupType group-type}])

(defmethod groups-for-attachment-group-type :operation [{primary-op :primaryOperation secondary-ops :secondaryOperations} _]
  (->> (cons primary-op secondary-ops)
       (map (partial merge {:groupType :operation}))))

(defn attachment-groups-for-application [application]
  (mapcat (partial groups-for-attachment-group-type application) attachment-groups))

(defn- tag-by-applicationState [{app-state :applicationState :as attachment}]
  (if (states/post-verdict-states app-state)
    :postVerdict
    :preVerdict))

(defn- tag-by-notNeeded [{not-needed :notNeeded :as attachment}]
  (if not-needed
    :notNeeded
    :needed))

(defn attachment-tags
  "Returns tags for a single attachment for filtering and grouping attachments of an application"
  [attachment]
  (->> [(tag-by-applicationState attachment)
        (get-in attachment [:groupType])
        (get-in attachment [:op :id])
        (tag-by-notNeeded attachment)
        (att-type/tag-by-type attachment)]
       (remove nil?)))

(defn- attachments-group-types [attachments]
  (->> (map #(if ((comp :id :op) %) :operation (:groupType %)) attachments)
       (remove nil?)
       (map keyword)
       distinct))

(defn- attachments-operation-ids [attachments]
  (->> (map (comp :id :op) attachments)
       (remove nil?)
       distinct))

(def type-grouping-stresshold 1)

(defn- type-groups-for-operation
  "Returns attachment type based grouping. Type group is created only if amount of attachments
  of same type exceeds type-grouping-stresshold value."
  [attachments operation-id]
  (->> (filter (comp #{operation-id} :id :op) attachments)
       (map att-type/tag-by-type)
       (remove nil?)
       frequencies
       (filter (comp (partial < type-grouping-stresshold) val))
       keys))

(defn- operation-grouping
  "Creates subgrouping for operations attachments if needed."
  [type-groups operation-id]
  (if (empty? type-groups)
    [operation-id]
    [operation-id (conj (vec type-groups) :default)]))

(defmulti tag-grouping-for-group-type (fn [attachments group-type] group-type))

(defmethod tag-grouping-for-group-type :default [_ group-type]
  [[group-type]])

(defmethod tag-grouping-for-group-type :operation [attachments _]
  (->> (attachments-operation-ids attachments)
       (map #(-> (type-groups-for-operation attachments %)
                 (operation-grouping %)))))

(defn attachment-tag-groups
  "Get hierarchical attachment grouping by attachments tags.
  There are one and two level groups in tag hierarchy (operations are two level groups).
  eg. [[:default] [:parties] [:building-site] [opid1 [:paapiirustus :iv_suunnitelma :default]] [opid2 [:kvv_suunnitelma :default]]]"
  [attachments]
  (->> (filter (set (attachments-group-types attachments)) attachment-groups) ;; keep sorted
       (cons :default)
       (mapcat (partial tag-grouping-for-group-type attachments))))

(defn attachments-filters
  "Get all possible filters with default values for attachments based on attachment data."
  [attachments]
  [[{:tag :preVerdict :default false}
    {:tag :postVerdict :default false}]
   (->> (map att-type/tag-by-type attachments)
        (remove nil?)
        distinct
        (map (partial hash-map :default false :tag)))
   [{:tag :needed :default true}
    {:tag :notNeeded :default false}]])
