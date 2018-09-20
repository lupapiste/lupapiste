(ns lupapalvelu.ui.invoices.service
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.invoices.state :as state]
            [sade.shared-util :as util]))

(defn fetch-price-catalogue [app-id]
  (println ">>> fetch-price-catalogue app-id: " app-id)
  (common/query "price-catalogue"
                (fn [result]
                  (println "fetch-price-catalogue response: " result)
                  ;;TODO: check why data under key verdicts and change it
                  (reset! state/price-catalogue (:verdicts result))
                  (println "price-catalogue-after fetch: " @state/price-catalogue)
                  )
                :id app-id))
