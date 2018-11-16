(ns lupapalvelu.ui.auth-admin.prices.service
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.auth-admin.prices.state :as state]))

(defn fetch-price-catalogues []
  (common/query :organization-price-catalogues
                (fn [data]
                  (let [catalogues (:price-catalogues data)]
                    (reset! state/catalogues catalogues))
                  ;;TODO sort catalogues here by relevance (?)
                  )
                :organization-id @state/org-id))
