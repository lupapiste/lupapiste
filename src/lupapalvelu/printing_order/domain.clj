(ns lupapalvelu.printing-order.domain
  (:require [sade.env :as env]
            [sade.files :as files]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [schema.core :as sc])
  (:import (java.util Date)))

(set! *warn-on-reflection* true)

(defn str-max-length [l]
  (sc/constrained sc/Str
                  (fn [s] (>= l (count s)))
                  (str "max-length[" l "]")))

(sc/defschema ContactDetails
  {:companyName   (str-max-length  150)
   :streetAddress (str-max-length   50)
   :postalCode    sc/Str
   :city          (str-max-length   50)
   :firstName     (str-max-length  150)
   :lastName      (str-max-length  150)
   :phoneNumber   (str-max-length   25)
   :email         ssc/Email})

(def Orderer
  ContactDetails)

(def Payer
  (assoc ContactDetails
    (sc/optional-key :additionalInformation) (str-max-length 1024)))

(sc/defschema PrintedMaterial
  {:fileId     sssc/FileId
   :copyAmount sc/Int})

(sc/defschema Delivery
  (merge ContactDetails
         {(sc/optional-key :additionalInformation) sc/Str
          :printedMaterials [PrintedMaterial]}))

(sc/defschema PrintingOrderFile
  {:fileId  sssc/FileId
   :name    sc/Str
   :size    sc/Int
   :content sc/Any})

(sc/defschema PrintingOrder
  {:projectName     (str-max-length 150)
   :orderer         Orderer
   :payer           Payer
   :delivery        Delivery
   :internalOrderId sc/Str
   :files           [PrintingOrderFile]})

(sc/defschema LocalizedLabel
  {sc/Any sc/Str})

(sc/defschema PricingItem
  {:min sc/Int
   (sc/optional-key :max) sc/Int
   (sc/optional-key :fixed) sc/Num
   (sc/optional-key :pricelist-label) LocalizedLabel
   (sc/optional-key :additionalInformation) LocalizedLabel})

(sc/defschema PricingConfiguration
  {:by-volume   [PricingItem]})

(sc/defschema VatConfiguration
  [{:value sc/Int :start-ts sc/Inst}])

(defn pdf-attachment? [attachment]
  (some-> attachment :latestVersion :contentType (= "application/pdf")))

(def valid-pricing-configuration!
  (sc/validator PricingConfiguration))

(def valid-vat-configuration!
  (sc/validator VatConfiguration))

(def pricing
  (files/read-config-edn valid-pricing-configuration! "printing-order/pricing.edn"))

(def vats
  (files/read-config-edn valid-vat-configuration! "printing-order/vats.edn"))

(defn vat-for-timestamp
  "Returns the correct VAT value for the given timestamp"
  [timestamp & [config]]
  (let [ts (if (number? timestamp)
             (Date. (long timestamp))
             timestamp)]
    (->> (or config vats)
         (sort-by :start-ts (fn [^Date a ^Date b] (.after a b))) ; descending with newest VAT first
         (some (fn [{:keys [^Date start-ts value]}]
                 (when-not (.after start-ts ts) ; first VAT that is not in the future
                   value))))))
