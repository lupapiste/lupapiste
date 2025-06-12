(ns lupapalvelu.smoketest.invoice-smoke-tests
  (:require [lupapalvelu.invoices.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongocheck.core :refer [mongocheck]]
            [schema.core :as sc]))

(mongocheck :invoices
  (comp (sc/checker schemas/Invoice) mongo/with-id))

(mongocheck :invoice-transfer-batches
  (comp (sc/checker schemas/InvoiceTransferBatch) mongo/with-id))
