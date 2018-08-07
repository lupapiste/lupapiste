(ns lupapalvelu.printing-order.processor
  (:require [lupapalvelu.printing-order.mylly-client :as mylly]
            [lupapalvelu.printing-order.domain :as domain]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [fail!]]
            [lupapiste-commons.threads :as threads]
            [sade.util :as util]
            [lupapalvelu.integrations.messages :as messages]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [taoensso.timbre :as timbre]))

(defonce send-order-thread-pool (threads/threadpool 3 "mylly-send-order-worker"))

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
                     :additionalInformation (case path
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

(defn enrich-with-file-content [user {files :files :as prepared-order} application]
  (assoc prepared-order :files (map (fn [{fileId :fileId :as file}]
                                      (with-open [content-is ((:content (att/get-attachment-file-as! user application fileId)))]
                                        (assoc file :content (mylly/encode-file-from-stream content-is)))) files)))

(defn save-integration-message [user created-ts cmd-name {:keys [id organization state]} {:keys [internalOrderId delivery]}]
  (messages/save {:id internalOrderId
                  :direction "out"
                  :messageType "printing-order"
                  :transferType "http"
                  :partner "mylly"
                  :format "xml"
                  :status "processing"
                  :created created-ts
                  :external-reference ""
                  :action cmd-name
                  :application {:id id :organization organization :state state}
                  :initator (select-keys user [:id :username])
                  :attached-files (map :fileId (-> delivery :printedMaterials))
                  :attachmentsCount (reduce + (map :copyAmount (-> delivery :printedMaterials)))}))

(defn mark-acknowledged [internalOrderId order-number]
  (timbre/infof "mark-acknowledged %s %s" internalOrderId order-number)
  (messages/mark-acknowledged-and-return internalOrderId (tc/to-long (t/now)) {:external-reference order-number
                                                                               :status "done"}))

(defn do-submit-order [{user :user created-ts :created cmd-name :action} application {:keys [internalOrderId] :as prepared-order}]
  (save-integration-message user created-ts cmd-name application prepared-order)
  (timbre/infof "Submitting printing order %s into integration thread pool" internalOrderId)
  (threads/submit
   send-order-thread-pool
   ;; bound-fn needed for itest, so that the db-name binding is visible inside the thread
   (let [result (mylly/login-and-send-order! (enrich-with-file-content user prepared-order application))]
     (timbre/infof "Printing order %s sent with result %s" internalOrderId result)
     (if (:ok result)
       (mark-acknowledged internalOrderId (:orderNumber result))
       (timbre/errorf "PRINTING ORDER SUBMISSION FAILED, integration-messages id %s" internalOrderId)))))
