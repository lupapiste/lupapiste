(ns lupapalvelu.printing-order.domain
  (:require [schema.core :as sc]
            [sade.core :refer [fail!]]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]))

(defn str-max-length [l]
  (sc/constrained sc/Str
                  (fn [s] (>= l (count s)))
                  (str "max-length[" l "]")))

(sc/defschema ContactDetails
              {:companyName                     (str-max-length  150)
               :streetAddress                   (str-max-length   50)
               :postalCode                      sc/Str
               :city                            (str-max-length   50)
               :firstName                       (str-max-length  150)
               :lastName                        (str-max-length  150)
               (sc/optional-key :phoneNumber)   (str-max-length   25)
               :email                           ssc/Email})

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

(defn pdf-attachment? [attachment]
  (= (-> attachment :latestVersion :contentType) "application/pdf"))