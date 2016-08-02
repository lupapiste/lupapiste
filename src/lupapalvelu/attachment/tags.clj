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
