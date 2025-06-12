(ns lupapalvelu.migration.archive
  (:require [monger.operators :refer [$set]]
            [sade.util :as util]))

(defn arkistoidaan->valmis-attachment-updates
  "Returns mongo updates for changing every attachment whose `:metadata.tila` is
  `arkistoidaan` to `valmis`. Nil if no updates needed"
  [{:keys [attachments]}]
  (some->> attachments
           (keep-indexed (fn [i att]
                           (when (some-> att :metadata :tila
                                         (util/=as-kw "arkistoidaan"))
                             [(util/kw-path :attachments i :metadata.tila)
                              :valmis])))
           seq
           (into {})
           (hash-map $set)))
