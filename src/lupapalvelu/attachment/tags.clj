(ns lupapalvelu.attachment.tags
  (:require [sade.util :refer [fn->> find-first distinct-by]]
            [lupapalvelu.states :as states]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.operations :as op]
            [lupapalvelu.document.schemas :as schemas]
            [sade.util :as util]
            [sade.strings :as ss]))

(def attachment-groups [:parties :building-site :reports :technical-reports :operation])
(def general-group-tag :general)
(def all-group-tags (cons general-group-tag attachment-groups))

(defn- enrich-op-with-accordion-fields [documents {op-id :id :as op}]
  (if-let [op-doc (util/find-first #(= (get-in % [:schema-info :op :id]) op-id) documents)]
    (let [identifier (schemas/resolve-identifier op-doc)
          value (if (ss/blank? identifier)
                  (when-let [acc-values (schemas/resolve-accordion-field-values op-doc)]
                    (ss/join " - " acc-values))
                  identifier)]
      (cond-> op value (assoc :accordionFields value)))
    op))

(defmulti groups-for-attachment-group-type (fn [application group-type] group-type))

(defmethod groups-for-attachment-group-type :default [_ group-type]
  [{:groupType group-type}])

(defmethod groups-for-attachment-group-type :operation [{primary-op :primaryOperation secondary-ops :secondaryOperations documents :documents} _]
  (->> (cons primary-op secondary-ops)
       (remove empty?)
       (map (partial enrich-op-with-accordion-fields documents))
       (map (partial merge {:groupType :operation}))))

(defn attachment-groups-for-application [application]
  (if (false? (op/get-primary-operation-metadata application :attachment-op-selector))
    []
    (mapcat (partial groups-for-attachment-group-type application) attachment-groups)))

(defn- tag-by-applicationState [{app-state :applicationState}]
  (if (states/post-verdict-states (keyword app-state))
    :postVerdict
    :preVerdict))

(defn- tag-by-notNeeded [{not-needed :notNeeded}]
  (if not-needed
    :notNeeded
    :needed))

(defn- tag-by-forPrinting [{verdict-attachment :forPrinting}]
  (if verdict-attachment
    :verdictAttachment
    :nonVerdictAttachment))

(defn- tag-by-file-status [{{:keys [fileId onkaloFileId]} :latestVersion}]
  (when (or fileId onkaloFileId)
    :hasFile))

(def op-id-prefix "op-id-")

(defn op-id->tag [op-id]
  (when op-id
    (str op-id-prefix op-id)))

(defn tag-by-group-type [{group-type :groupType op :op}]
  (or (some-> group-type keyword ((set (remove #{:operation} all-group-tags))))
      (when (and (sequential? op) (> (count op) 1)) :multioperation)
      (when (not-empty op) :operation)
      general-group-tag))

(def application-group-types [:general :reports :technical-reports :parties])

(def application-group-type-tag :application)

(defn tag-by-application-group-types
  "tag attachments that have application related group types"
  [attachment]
  (when ((set application-group-types) (tag-by-group-type attachment))
    application-group-type-tag))

(defn- tag-by-operation [{:keys [op]}]
  (when (= (count op) 1)
    (->> (get-in op [0 :id])
         op-id->tag)))

(defn tag-by-type [{op :op :as attachment}]
  (or (att-type/tag-by-type attachment)
      (when (not-empty op) att-type/other-type-group)))

(defn- tag-by-ram [{ram :ramLink}]
  (when ram :ram))

(defn attachment-tags
  "Returns tags for a single attachment for filtering and grouping attachments of an application"
  [attachment]
  (->> ((juxt tag-by-applicationState
              tag-by-group-type
              tag-by-application-group-types
              tag-by-operation
              tag-by-notNeeded
              tag-by-forPrinting
              tag-by-type
              tag-by-file-status
              tag-by-ram)
        attachment)
       (remove nil?)))

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
            {:tag :postVerdict :default true})
          (when (some tag-by-ram attachments)
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

(defn- for-printing-filters [{attachments :attachments}]
  (let [existing-verdict-attachment-tags (-> (map tag-by-forPrinting attachments) set)]
    (when (< 1 (count existing-verdict-attachment-tags))
      [{:tag :verdictAttachment    :default false}
       {:tag :nonVerdictAttachment :default false}])))

(defn- targeting-assignments [assignments attachments]
  (or (not-empty (assignment/targeting-assignments assignments attachments))
      [{:trigger "not-targeted"}]))

(defn- sort-filters [filters]
  (sort (fn [a b]
          (if-let [user-created (first (filter #(= (:tag %) (assignment/assignment-tag "user-created")) [a b]))]
            (if (= user-created a) -1 1)
            (if-let [not-targeted (first (filter #(= (:tag %) (assignment/assignment-tag "not-targeted")) [a b]))]
              (if (= not-targeted a) -1 1)
              (compare (:description a) (:description b)))))
        filters))

(defn- assignment-trigger-filters [{:keys [attachments]} assignments]
  (->> attachments
       (mapcat (partial targeting-assignments (remove assignment/completed? assignments)))
       (map #(select-keys % [:trigger :description]))
       (distinct-by :trigger)
       (map (fn [{:keys [trigger description]}]
              (merge {:tag (assignment/assignment-tag trigger)
                      :default false}
                     (when (and description
                                (not= "user-created" trigger))
                       {:description description}))))
       sort-filters))

(defn attachments-filters
  "Get all possible filters with default values for attachments based on attachment data."
  [application & [assignments]]
  (->> (conj ((juxt application-state-filters group-and-type-filters not-needed-filters for-printing-filters) application)
             (when assignments
               (assignment-trigger-filters application assignments)))
       (remove nil?)
       (filter (fn->> count (< 1)))))
