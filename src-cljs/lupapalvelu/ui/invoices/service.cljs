(ns lupapalvelu.ui.invoices.service
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.invoices.state :as state]
            [sade.shared-util :as util]))

(defn insert-invoice [app-id data]
  (println ">> insert-invoice app-id: " app-id " data: " data)
  (common/command "insert-invoice"
                  (fn [result]
                    (println "insert-invoice response: " result))
                  :id app-id
                  :invoice data))
