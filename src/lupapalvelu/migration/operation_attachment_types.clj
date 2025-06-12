(ns lupapalvelu.migration.operation-attachment-types
  "Remove unsupported attachment types from an organization."
  (:require [clojure.set :as set]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.operations :as operations]
            [monger.operators :refer :all]
            [sade.util :as util]))

(defn- allowed-typeset [operation]
  (->> (operations/get-operation-metadata operation)
       :permit-type keyword
       (get att-type/attachment-types-by-permit-type)
       (map #(select-keys % [:type-group :type-id]))
       set))

(defn- settings-types-by-operation [{:keys [operations-attachment-settings]}]
  (->> (:operation-nodes operations-attachment-settings)
         (map (fn [[op {:keys [allowed-attachments]}]]
                [op (mapcat (fn [[type-group type-ids]]
                              (map #(hash-map :type-group type-group :type-id (keyword %))
                                   type-ids))
                            (:types allowed-attachments))]))
         (into {})))

(defn- removal-updates [operation-attachments-map update-fn]
  (some->> operation-attachments-map
           (mapcat (fn [[op types]]
                     (some->> (set/difference (set types) (allowed-typeset op))
                              seq
                              (update-fn op))))
           seq
           (apply util/deep-merge)))

(defn- attachment-settings-updates [organization]
  (let [type-map  (settings-types-by-operation organization)
        group-set (fn [op group]
                    (->> (get type-map op)
                         (group-by :type-group)
                         group
                         set))]
    (removal-updates type-map
                     (fn [op rm-types]
                       (map (fn [[group ids]]
                              (let [path (util/kw-path :operations-attachment-settings.operation-nodes
                                                       op
                                                       :allowed-attachments.types
                                                       group)]
                                (if (= (group-set op group) (set ids))
                                  {$unset {path true}}
                                  {$pull {path {$in (set (map :type-id ids))}}})))
                            (group-by :type-group rm-types))))))

(defn- types-by-operation [{:keys [operations-attachments]}]
  (->> operations-attachments
       (map (fn [[op types]]
              [op (map #(hash-map :type-group (keyword (first %))
                                  :type-id (keyword (second %)))
                       types)]))
       (into {})))

(defn- operations-updates [organization]
  (removal-updates (types-by-operation organization)
                   (fn [op rm-types]
                     [{$pull {(util/kw-path :operations-attachments op)
                              {$in (set (map (juxt :type-group :type-id) rm-types))}}}])))

(defn organization-operation-attachment-updates
  "Returns an update map that purges the no longer supported attachment types from
  `operations-attachment-settings` and `operations-attachments` (or nil the `organization`
  is already up-to-date)."
  [organization]
  (util/deep-merge (attachment-settings-updates organization)
                   (operations-updates organization)))
