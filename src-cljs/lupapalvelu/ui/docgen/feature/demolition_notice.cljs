(ns lupapalvelu.ui.docgen.feature.demolition-notice
  (:require [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.notice-utils :as notice-utils]))

(defn on-app-loaded [{:keys [db]} _]
  (let [docs (::docs/documents db)
        demolition-notice-doc (some (fn [[_doc-id doc]]
                                      (when (notice-utils/demolition-notice-document? doc)
                                        doc))
                                    docs)]
    (when demolition-notice-doc
      {:fx [[:hub/send {:event "buildingDemolition"
                        :data {:checked (boolean (-> demolition-notice-doc :data :buildingDemolition :value))
                               :noticeDocExists true}}]]})))
