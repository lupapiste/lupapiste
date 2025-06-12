(ns lupapalvelu.invoices.integration.generate-impl
  (:require [lupapalvelu.invoices.shared.schemas :as schemas]))

(defmulti generate-invoice-transfer-batch
  (fn [{:keys [cfg]}]
    (or (:invoicing-system cfg)
        schemas/default-invoicing-system)))
