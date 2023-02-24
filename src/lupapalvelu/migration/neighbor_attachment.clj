(ns lupapalvelu.migration.neighbor-attachment
  (:require [monger.operators :refer :all]
            [sade.util :as util]))

(defn no-comments-fix
  "Returns an update map for fixing neighbor attachment types from `naapurin_huomautus` to
  `naapurin_kuuleminen` or nil if no fix is needed."
  [{:keys [neighbors attachments]}]
  (when-let [neighbor-ids (some->> neighbors
                                   (keep (fn [{:keys [id status]}]
                                           ;; in theory, sheriff could have rewound the
                                           ;; status by adding a new state
                                           (when (-> status last :state (= "response-given-ok"))
                                             id)))
                                   seq
                                   set)]
    (some->> attachments
             (map-indexed (fn [i {:keys [source type]}]
                            (when (and (neighbor-ids (:id source))
                                       (= (:type source) "neighbors")
                                       (= (:type-id type) "naapurin_huomautus"))
                              [(util/kw-path :attachments i :type.type-id) :naapurin_kuuleminen])))
             (into {})
             not-empty
             (hash-map $set))))
