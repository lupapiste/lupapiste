(ns lupapalvelu.printing-order.schemas
  (:require [schema.core :as sc]
            [sade.schemas :as ssc]))


(defn str-max-length [l]
  (sc/constrained sc/Str
                  (fn [s] (>= l (count s)))
                  (str "max-length[" l "]")))

(sc/defschema ContactDetails
              {:companyName                     (str-max-length  150)
               (sc/optional-key :streetAddress) (str-max-length   50)
               (sc/optional-key :postalCode)    sc/Str
               (sc/optional-key :city)          (str-max-length   50)
               (sc/optional-key :firstName)     (str-max-length  150)
               (sc/optional-key :surname)       (str-max-length  150)
               (sc/optional-key :phone)         (str-max-length   25)
               (sc/optional-key :email)         (str-max-length 1024)})

(def Orderer
  ContactDetails)

(def Payer
  (assoc ContactDetails
    (sc/optional-key :additionalInformation) (str-max-length 1024)))

(sc/defschema PrintedMaterial
  {:fileId     ssc/ObjectIdStr
   :copyAmount sc/Int})

(sc/defschema Delivery
  (merge ContactDetails
         {(sc/optional-key :additionalInformation) sc/Str
          :printedMaterials [PrintedMaterial]}))

(sc/defschema PrintingOrderFile
  {:fileId  ssc/ObjectIdStr
   :name    sc/Str
   :size    sc/Int
   :content sc/Any #_???})

(sc/defschema PrintingOrder
  {:projectName     (str-max-length 150)
                           ;Granon tuotannossa tilausnimi, moniosainen tilaus
                           ; osoitetaan laittamalla nimen perään
                           ; -1, -2, -3 ...
   :orderer         Orderer
   :payer           Payer
   :delivery        Delivery
   :internalOrderId sc/Str
   :files           [PrintingOrderFile]})

(sc/defschema PricingItem
              {:min sc/Int
               (sc/optional-key :max) sc/Int
               (sc/optional-key :fixed) sc/Int
               (sc/optional-key :additionalInformation) sc/Str})

(sc/defschema PricingConfiguration
              {:delivery sc/Num
               :volume   [PricingItem]})
