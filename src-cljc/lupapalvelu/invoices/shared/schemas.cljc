(ns lupapalvelu.invoices.shared.schemas
  (:require [sade.shared-schemas :as ssc]
            [schema.core :as sc :refer [defschema]]))

(defschema DiscountPercent
  (sc/constrained sc/Num #(and (>= % 0) (<= % 100))))

(def invoice-row-units #{"m2" "m3" "kpl" "t" "pv" "vk"})

(defschema InvoiceRowUnit
  (apply sc/enum invoice-row-units))

(defschema ProductConstants
  {(sc/optional-key :kustannuspaikka)  sc/Str
   (sc/optional-key :alv)              sc/Str
   (sc/optional-key :laskentatunniste) sc/Str
   (sc/optional-key :projekti)         sc/Str
   (sc/optional-key :kohde)            sc/Str
   (sc/optional-key :toiminto)         sc/Str
   (sc/optional-key :muu-tunniste)     sc/Str})

(defschema CatalogueRow
  {:id                                  ssc/ObjectIdStr
   :code                                sc/Str
   :text                                ssc/NonBlankStr
   :unit                                InvoiceRowUnit
   :price-per-unit                      sc/Num
   (sc/optional-key :max-total-price)   sc/Num
   (sc/optional-key :min-total-price)   sc/Num
   :discount-percent                    DiscountPercent
   :operations                          [sc/Str]
   (sc/optional-key :product-constants) ProductConstants})
