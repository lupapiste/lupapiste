(ns lupapalvelu.invoices.integration.generate
  "Common API for various invoice integration formats.
  Require all implementations here to register the defmethods."
  (:require [lupapalvelu.invoices.integration.general.core]
            [lupapalvelu.invoices.integration.generate-impl :as impl]
            [lupapalvelu.invoices.integration.sap-sbo.v1]
            [lupapalvelu.invoices.integration.sap-sbo.v2]
            [lupapalvelu.invoices.integration.ya-sap.core]
            [lupapalvelu.organization :as org]
            [sade.shared-schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]]))

(defschema Context
  {:cfg                             (st/open-schema org/InvoicingConfig)
   :created                         sc/Int
   :invoices                        sc/Any
   (sc/optional-key :lang)          (sc/cond-pre sc/Keyword sc/Str)
   (sc/optional-key :pretty-print?) sc/Bool})

(defschema DeliverableTransferBatch
  {:filename                                     ssc/NonBlankStr
   :content                                      sc/Any
   :content-type                                 ssc/NonBlankStr
   (sc/optional-key :integration-message-action) ssc/NonBlankStr})

(defschema GenerateError
  {:error                      ssc/NonBlankStr
   :field                      [(sc/cond-pre ssc/NonBlankStr sc/Int)]
   (sc/optional-key :value)    sc/Any
   (sc/optional-key :expected) sc/Any
   :loc                        {:location ssc/NonBlankStr
                                :error    ssc/NonBlankStr}})

(defschema GenerateErrors
  {:error [GenerateError]})

(defschema Output
  (sc/conditional
    ;; Some implementations may return errors as data
    :error GenerateErrors
    :else DeliverableTransferBatch))

(sc/defn generate-invoice-transfer-batch :- Output
  "Transform a transfer batch (or rather the invoices within)
  into a deliverable artifact (e.g., an XML string).

  If something goes wrong, returns a map with `:error` or throws,
  depending on the implementation."
  {:arglists        '([{:keys [cfg created invoices lang pretty-print?]}])
   :always-validate true}
  [context :- Context]
  (impl/generate-invoice-transfer-batch context))
