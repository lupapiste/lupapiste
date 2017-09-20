(ns lupapalvelu.printing-order.processor
  (:require [lupapalvelu.printing-order.mylly-client :as mylly]
            [lupapalvelu.printing-order.domain :as domain]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [fail!]]
            [sade.util :as util]
            [lupapalvelu.integrations.messages :as messages]))

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

(defn prepare-attachments
  [application order-map]
  (for [[k amount] (util/filter-map-by-val #(and (pos? %) (integer? %)) order-map)]
    (let [attachment-id (name k)
          attachment-info (att/get-attachment-info application attachment-id)]
      (when-not attachment-info
        (fail! :error.attachment-not-found))
      (when-not (domain/pdf-attachment? attachment-info)
        (fail! :error.not-pdf))
      {:fileId (-> attachment-info :latestVersion :fileId)
       :name (-> attachment-info :latestVersion :filename)
       :size (-> attachment-info :latestVersion :size)
       :copyAmount amount})))

(defn prepare-order
  [{id :id :as application} order-map contacts]
  (let [files (prepare-attachments application order-map)]
    {:projectName     (str "Lupapisteen hankkeen " id " liitteet")
     :orderer         (prepare-contact-to-order contacts :orderer)
     :payer           (prepare-contact-to-order contacts :payer)
     :delivery        (merge (prepare-contact-to-order contacts :delivery)
                             {:printedMaterials (map #(select-keys % [:fileId :copyAmount]) files)})
     :internalOrderId (mongo/create-id)
     :files           files}))

(defn enrich-with-file-content [user {files :files :as prepared-order}]
  (assoc prepared-order :files (map (fn [{fileId :fileId :as file}]
                                      (with-open [content-is ((:content (att/get-attachment-file-as! user fileId)))]
                                        (assoc file :content (mylly/encode-file-from-stream content-is)))) files)))

(defn save-integration-message [user created-ts application {:keys [internalOrderId delivery] :as prepared-order} order-number]
  (messages/save {:id internalOrderId
                  :direction "out"
                  :messageType "printing-order"
                  :partner "mylly"
                  :format "xml"
                  :created created-ts
                  :external-reference order-number
                  :initator (select-keys user [:id :username])
                  :attached-files (map :fileId (-> delivery :printedMaterials))
                  :attachmentsCount (reduce + (map :copyAmount (-> delivery :printedMaterials)))}))