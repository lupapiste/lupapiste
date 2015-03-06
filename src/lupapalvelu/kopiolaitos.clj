(ns lupapalvelu.kopiolaitos
  (:require [taoensso.timbre :as timbre :refer [warnf info error]]
            [clojure.java.io :as io :refer [delete-file]]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! def-]]
            [sade.email :as email]
            [sade.strings :as ss]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.action :as action]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.i18n :refer [with-lang loc]]))

(def- kopiolaitos-html-table-str
  "<table><thead><tr>%s</tr></thead><tbody>%s</tbody></table>")

(defn- get-kopiolaitos-html-table-header-str [lang]
  (with-lang lang
    (str
      "<th>" (loc "application.attachmentType") "</th>"
      "<th>" (loc "application.attachmentContents") "</th>"
      "<th>" (loc "application.attachmentFile") "</th>"
      "<th>" (loc "verdict-attachment-prints-order.order-dialog.orderCount") "</th>")))

(defn- get-kopiolaitos-html-table-content [lang attachments]
  "Return attachments' type, content and amount as HTML table rows string."
  (reduce
    (fn [s att]
      (let [att-map (merge att (:type att))
            file-name (str (:fileId att-map) "_" (:filename att-map))
            type-str (with-lang lang
                       (loc
                         (clojure.string/join
                           "."
                           ["attachmentType" (:type-group att-map) (:type-id att-map)])))
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

(def- zip-file-name "Lupakuvat.zip")

(defn- send-kopiolaitos-email [lang email-address attachments orderInfo]
  (let [zip (attachment/get-all-attachments attachments)
        email-attachment {:content zip :file-name zip-file-name}
        email-subject (str (with-lang lang (loc :kopiolaitos-email-subject)) \space (:ordererOrganization orderInfo))
        orderInfo (assoc orderInfo :contentsTable (get-kopiolaitos-html-table lang attachments))]
    (try
      ;; from email/send-email-message false = success, true = failure -> turn it other way around
      (let [sending-succeeded? (not (email/send-email-message
                                      email-address
                                      email-subject
                                      (email/apply-template "kopiolaitos-order.html" orderInfo)
                                      [email-attachment]))]
        (if sending-succeeded?
          (info "Kopiolaitos email was sent successfully to" email-address "from" (:ordererOrganization orderInfo))
          (error "Kopiolaitos email sending error to" email-address "from" (:ordererOrganization orderInfo)))
        (io/delete-file zip)
        sending-succeeded?)
      (catch java.io.IOException ioe
        (warnf "Could not delete temporary zip file: %s" (.getAbsolutePath zip)))
      (catch Exception e
        (fail! :kopiolaitos-email-sending-failed)))))

(defn- get-kopiolaitos-email-address [{:keys [organization] :as application}]
  (let [email (organization/with-organization organization :kopiolaitos-email)]
    (if-not (ss/blank? email)
      email
      nil)))

(defn do-order-verdict-attachment-prints [{{:keys [lang attachmentsWithAmounts orderInfo]} :data application :application created :created user :user :as command}]
  (if-let [email-address (get-kopiolaitos-email-address application)]
    (if (send-kopiolaitos-email lang email-address attachmentsWithAmounts orderInfo)
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
        (ok))
      (fail! :kopiolaitos-email-sending-failed))
    (fail! :no-kopiolaitos-email-defined)))
