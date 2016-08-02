(ns lupapalvelu.attachment.tags
  (:require [lupapalvelu.states :as states]
            [lupapalvelu.attachment.type :as att-type]))

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
  "WIP Get all possible filters with default values for attachments based on attachment data."
  [attachments]
  [[{:tag :preVerdict :default false}
    {:tag :postVerdict :default false}]
   [{:tag :paapiirustus :default false}
    {:tag :iv_suunnitelma :default false}
    {:tag :kvv_suunnitelma :default false}
    {:tag :rakennesuunnitelma :default false}]
   [{:tag :needed :default true}
    {:tag :notNeeded :default false}]])
