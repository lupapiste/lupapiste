(ns lupapalvelu.generators.invoices
  (:require [clojure.test.check.generators :as gen]
            [lupapalvelu.invoices.schemas :as inv]
            [lupapalvelu.invoices.shared.schemas :refer [DiscountPercent]]
            [lupapalvelu.organization :as org]
            [sade.schema-generators :as ssg]
            [schema.core :as sc]))

(def common-invoice-leaf-generators
  {org/OrgId           (gen/elements ["837-YA"])
   sc/Num              (gen/fmap ; When converting decimals to Euros, it should have only two decimals (cents)
                         #(/ (Math/round (* % 100)) 100.0)
                         (gen/double* {:min 0 :infinite? false :NaN? false}))
   DiscountPercent (gen/frequency [[2 (gen/return 0)]
                                   [1 gen/s-pos-int]])})

(def invoice-row-generator (ssg/generator
                             (dissoc inv/InvoiceRow
                                     (sc/optional-key :min-unit-price)
                                     (sc/optional-key :max-unit-price))
                             common-invoice-leaf-generators))

(ssg/register-generator inv/InvoiceRow invoice-row-generator)

(def invoice-generator (ssg/generator inv/Invoice common-invoice-leaf-generators))

(ssg/register-generator inv/Invoice invoice-generator)
