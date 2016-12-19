(ns lupapalvelu.kopiolaitos
  (:require [taoensso.timbre :as timbre :refer [warnf info error errorf]]
            [monger.operators :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [sade.strings :as ss]
            [sade.core :refer [ok fail fail! def-]]
            [sade.email :as email]
            [sade.util :as util]
            [sade.validators :as v]
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

(defn- send-kopiolaitos-email! [lang email-addresses attachments orderInfo]
  (let [zip (attachment/get-all-attachments! attachments)]
    (try
      (let [email-attachment {:content zip :filename zip-file-name}
            email-subject (str (localize lang :kopiolaitos-email-subject) \space (:ordererOrganization orderInfo))
            orderInfo (merge orderInfo {:titles (get-kopiolaitos-order-email-titles lang)
                                        :contentsTable (get-kopiolaitos-html-table lang attachments)})
            email-msg (email/apply-template "kopiolaitos-order.html" orderInfo)
            results-failed-emails (reduce
                                    (fn [failed addr]
                                      (let [res (do-send-email orderInfo email-subject email-msg email-attachment addr)]
                                        (if (:sending-succeeded res)
                                          failed
                                          (conj failed (:email-address res)))))
                                    []
                                    email-addresses)]

        (when (-> results-failed-emails count pos?)
          (fail! :kopiolaitos-email-sending-failed-with-emails :failedEmails (s/join "," results-failed-emails))))

      (finally
        (io/delete-file zip :silently)))))


;; Resolving kopiolaitos emails set by the authority admin of the organization

(defn- get-kopiolaitos-email-addresses [organization-id]
  (let [email (organization/with-organization organization-id :kopiolaitos-email)]
    (when-not (ss/blank? email)
      (let [emails (util/separate-emails email)]
        (when (some (complement v/email-and-domain-valid?) emails)
          (fail! :kopiolaitos-invalid-email))
        emails))))


;; Send the the prints order

(defn do-order-verdict-attachment-prints [{{:keys [lang attachmentsWithAmounts orderInfo]} :data application :application created :created user :user :as command}]
  (if-let [email-addresses (get-kopiolaitos-email-addresses (:organization application))]
    (let [attachments (->> attachmentsWithAmounts
                        (filter (comp pos? util/->long :amount))
                        (map (fn [{:keys [id amount]}]
                               (when-let [attachment (util/find-by-id id (:attachments application))]
                                 (merge
                                   (assoc attachment :amount amount)
                                   (select-keys (-> attachment :versions last) [:fileId :filename])))))
                        (filter :forPrinting)
                        (filter (comp not-empty :versions)))]

      (if (not-empty attachments)
        (let [order {:type "verdict-attachment-print-order"
                     :user (select-keys user [:id :role :firstName :lastName])
                     :timestamp created
                     :orderInfo orderInfo
                     :attachments (map #(select-keys % [:id :fileId :amount :filename :contents :type]) attachments)}]
          (send-kopiolaitos-email! lang email-addresses attachments orderInfo)
          (action/update-application command {$push {:transfers order}, $set {:modified created}})
          (ok))
        (fail :error.kopiolaitos-print-order-invalid-parameters-content)))
    (fail :no-kopiolaitos-email-defined)))
