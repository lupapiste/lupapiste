(ns lupapalvelu.kopiolaitos
  (:require [taoensso.timbre :as timbre :refer [warnf info error]]
            [monger.operators :refer :all]
            [clojure.java.io :as io :refer [delete-file]]
            [clojure.string :as s]
            [sade.strings :as ss]
            [sade.core :refer [ok fail fail! def-]]
            [sade.email :as email]
            [sade.util :as util]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.action :as action]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.i18n :refer [with-lang loc localize]]))


;; Email contents generation

(def- kopiolaitos-html-table-str
  "<table><thead><tr>%s</tr></thead><tbody>%s</tbody></table>")

(defn- get-kopiolaitos-html-table-header-str [lang]
  (with-lang lang
    (str
      "<th>" (loc "application.attachmentType") "</th>"
      "<th>" (loc "application.attachmentContents") "</th>"
      "<th>" (loc "application.attachmentFile") "</th>"
      "<th>" (loc "verdict-attachment-prints-order.order-dialog.orderCount") "</th>")))

(defn- get-kopiolaitos-html-table-content
  "Return attachments' type, content and amount as HTML table rows string."
  [lang attachments]
  (reduce
    (fn [s att]
      (let [att-map (merge att (:type att))
            file-name (str (:fileId att-map) "_" (:filename att-map))
            type-str (localize lang
                       (s/join
                         "."
                         ["attachmentType" (:type-group att-map) (:type-id att-map)]))
            contents-str (or (:contents att-map) type-str)]
        (str s (format "<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
                 type-str
                 contents-str
                 file-name
                 (:amount att-map)))))
    "" attachments))

(defn- get-kopiolaitos-html-table [lang attachments]
  (format
    kopiolaitos-html-table-str
    (get-kopiolaitos-html-table-header-str lang)
    (get-kopiolaitos-html-table-content lang attachments)))

;; To be applied to email template, along with the orderInfo.
(defn- get-kopiolaitos-order-email-titles [lang]
  (with-lang lang {:orderedPrints       (loc "kopiolaitos-order-email.titles.orderedPrints")
                   :orderDetails        (loc "kopiolaitos-order-email.titles.orderDetails")
                   :ordererOrganization (loc "kopiolaitos-order-email.titles.ordererOrganization")
                   :ordererEmail        (loc "kopiolaitos-order-email.titles.ordererEmail")
                   :ordererPhone        (loc "kopiolaitos-order-email.titles.ordererPhone")
                   :ordererAddress      (loc "kopiolaitos-order-email.titles.ordererAddress")
                   :applicantName       (loc "kopiolaitos-order-email.titles.applicantName")
                   :kuntalupatunnus     (loc "kopiolaitos-order-email.titles.kuntalupatunnus")
                   :propertyId          (loc "kopiolaitos-order-email.titles.propertyId")
                   :lupapisteId         (loc "kopiolaitos-order-email.titles.lupapisteId")
                   :address             (loc "kopiolaitos-order-email.titles.address")}))


;; Sending the email

(def- zip-file-name "Lupakuvat.zip")

(defn- do-send-email [orderInfo subject message attachment address]
  ;; from email/send-email-message false = success, true = failure -> turn it other way around
  (let [sending-succeeded? (not (email/send-email-message address subject message [attachment]))]
    (if sending-succeeded?
      (info "Kopiolaitos email was sent successfully to" address "from" (:ordererOrganization orderInfo))
      (error "Kopiolaitos email sending error to" address "from" (:ordererOrganization orderInfo)))
    {:email-address address :sending-succeeded sending-succeeded?}))

(defn- send-kopiolaitos-email [lang email-addresses attachments orderInfo]
  (let [zip (attachment/get-all-attachments! attachments)
        email-attachment {:content zip :file-name zip-file-name}
        email-subject (str (localize lang :kopiolaitos-email-subject) \space (:ordererOrganization orderInfo))
        orderInfo (merge orderInfo {:titles (get-kopiolaitos-order-email-titles lang)
                                    :contentsTable (get-kopiolaitos-html-table lang attachments)})
        email-msg (email/apply-template "kopiolaitos-order.html" orderInfo)
        results-failed-emails (try
                                (reduce
                                  (fn [failed addr]
                                    (let [res (do-send-email orderInfo email-subject email-msg email-attachment addr)]
                                      (if (:sending-succeeded res)
                                        failed
                                        (conj failed (:email-address res)))))
                                  []
                                  email-addresses)

                                (finally
                                  (try
                                    (io/delete-file zip)
                                    (catch Exception e
                                      (warnf e "Could not delete temporary zip file: %s" (.getAbsolutePath zip))))))]

    (when (-> results-failed-emails count pos?)
      (fail! :kopiolaitos-email-sending-failed-with-emails :failedEmails (s/join "," results-failed-emails)))))


;; Resolving kopiolaitos emails set by the authority admin of the organization

(defn- get-kopiolaitos-email-addresses [organization-id]
  (let [email (organization/with-organization organization-id :kopiolaitos-email)]
    (if-not (ss/blank? email)
      (let [emails (util/separate-emails email)]
        ;; action/email-validator returns nil if email was valid
        (when (some #(action/email-validator :email {:data {:email %}}) emails)
          (fail! :kopiolaitos-invalid-email))
        emails)
      nil)))


;; Send the the prints order

(defn do-order-verdict-attachment-prints [{{:keys [lang attachmentsWithAmounts orderInfo]} :data application :application created :created user :user :as command}]
  (if-let [email-addresses (get-kopiolaitos-email-addresses (:organization application))]
    (do
      (send-kopiolaitos-email lang email-addresses attachmentsWithAmounts orderInfo)
      (let [order {:type "verdict-attachment-print-order"
                   :user (select-keys user [:id :role :firstName :lastName])
                   :timestamp created
                   :orderInfo orderInfo}
            normalize-attachment-for-db (fn [attachment]
                                          (select-keys attachment [:id :fileId :amount :filename :contents :type]))
            order-with-normalized-attachments (assoc order :attachments (map normalize-attachment-for-db attachmentsWithAmounts))]
        (action/update-application command
          {$push {:transfers order-with-normalized-attachments}
           $set {:modified created}})
        (ok)))
    (fail :no-kopiolaitos-email-defined)))
