(ns lupapalvelu.ui.docgen.feature.hidden
  "Handle the `:hidden` attribute"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [sade.util :as util]))

(defn schema-hidden?
  [{:keys [doc-id path schema]}]
  (or (true? (:hidden schema))
      (and (util/=as-kw :by-value (:hidden schema))
           (let [value (<sub [::value/current-field-value doc-id path])]
             (or (false? value)
                 (util/emptyish? value))))))
