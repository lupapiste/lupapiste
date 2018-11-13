(ns lupapalvelu.ui.auth-admin.prices.service
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.auth-admin.prices.state :as state]
            ))

(enable-console-print!)

(defn fetch-price-catalogues []
  (println ">> fetch-price-catalogues current org id: " @state/org-id)
  (common/query :organization-price-catalogues
                (fn [data]
                  (let [catalogues (:price-catalogues data)]
                    (println "user org price catalogues response: " data)
                    (reset! state/catalogues catalogues))
                  ;;TODO sort catalogues here by relevance (?)
                  )
                :org-id @state/org-id))
