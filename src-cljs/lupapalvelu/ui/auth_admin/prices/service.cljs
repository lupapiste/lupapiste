(ns lupapalvelu.ui.auth-admin.prices.service
  (:require [lupapalvelu.ui.common :as common]
            ;;[lupapalvelu.ui.invoices.state :as state]
            ;;[sade.shared-util :as util]
            ))

(enable-console-print!)

(defn fetch-price-catalogues []
  (common/query :user-organization-price-catalogues
                (fn [data]
                  (println "user org price catalogues response: " data)
                  ;; (reset! state/invoices (sort-by
                  ;;                         drafts-last-then-time-created
                  ;;                         >
                  ;;                         (:invoices data)))
                  )))
