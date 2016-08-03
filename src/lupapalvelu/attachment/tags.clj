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

(defn attachment-tag-groups
  "WIP Get hierarchical attachment grouping by attachments tags.
  There are one and two level groups in tag hierarchy (operations are two level groups).
  eg. [[:default] [:parties] [:building-site] [opid1 [:paapiirustus :iv_suunnitelma]] [opid2 [:kvv_suunnitelma]]]"
  [attachments]
  (let [groups     (->> (map :groupType attachments)
                        (map keyword)
                        (remove #{:operation})
                        (remove nil?)
                        distinct
                        (map vector))
        operations (->> (map (comp :id :op) attachments)
                        (remove nil?)
                        distinct
                        (map #(vector % att-type/type-groups)))]
    (concat [[:default]] groups operations)))


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
