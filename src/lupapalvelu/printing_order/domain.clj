(ns lupapalvelu.printing-order.domain
  (:require [schema.core :as sc]
            [sade.core :refer [fail!]]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :as att]))

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

(defn prepare-contact-to-order
  [contacts path]
  (let [contact (cond
                  (and (= path :payer)
                       (:payer-same-as-orderer contacts))    (:orderer contacts)
                  (and (= path :delivery)
                       (:delivery-same-as-orderer contacts)) (:orderer contacts)
                  :default                                   (get contacts path))
        {:keys [companyName firstName lastName]} contact]
    (util/assoc-when contact
      :companyName (when (empty? companyName)
                     (str firstName " " lastName))
      :additionalInformation (condp = path
                               :payer (:billingReference contacts)
                               :delivery (:deliveryInstructions contacts)
                               ""))))

(defn pdf-attachment? [attachment]
  (= (-> attachment :latestVersion :contentType) "application/pdf"))

(defn prepare-attachments
  [application order-map]
  (for [[k amount] order-map]
    (let [attachment-id (name k)
          attachment-info (att/get-attachment-info application attachment-id)]
      (when-not attachment-info
        (fail! :error.attachment-not-found))
      (when-not (pdf-attachment? attachment-info)
        (fail! :error.not-pdf))
      {:id attachment-id
       :fileId (-> attachment-info :latestVersion :fileId)
       :name (-> attachment-info :latestVersion :filename)
       :size (-> attachment-info :latestVersion :size)
       :amount amount})))

(defn prepare-order
  [{id :id :as application} order-map contacts]
  {:projectName     (str "Lupapisteen hankkeen " id " liitteet")
   :orderer         (prepare-contact-to-order contacts :orderer)
   :payer           (prepare-contact-to-order contacts :payer)
   :delivery        (prepare-contact-to-order contacts :delivery)
   :internalOrderId (mongo/create-id)
   :files           (prepare-attachments application order-map)})