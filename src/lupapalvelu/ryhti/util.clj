(ns lupapalvelu.ryhti.util
  "Utility functions for RYHTI integration and compatibility")

(defn ryhti-id
  "Utility for making standard format maps to contain UUID fields for RYHTI.
  See also `lupapalvelu.document.schemas/ryhti-id` for the corresponding fn for docgen documents.
  Note that these are generated in this fn, unlike docgen fields which are populated on document creation"
  [& id-keys]
  {:ryhti (reduce (fn [acc id-key]
                    (assoc acc id-key (str (random-uuid))))
                  {}
                  id-keys)})


