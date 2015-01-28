(ns lupapalvelu.kopiolaitos
  (:require [taoensso.timbre :as timbre :refer [warnf]]
            [clojure.java.io :as io :refer [delete-file]]
            [sade.core :refer [ok fail fail! def-]]
            [sade.email :as email]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.i18n :refer [with-lang loc]]))

(defn- get-kopiolaitos-html-table-content [attachments lang]
  "Return attachments' type, content and amount as HTML table rows string."
  (reduce
    (fn [s att]
      (let [att-map (merge (:type att) (select-keys att [:amount :contents]))
            type-str (with-lang lang
                       (loc
                         (clojure.string/join
                           "."
                           ["attachmentType"
                            (:type-group att-map)
                            (:type-id att-map)])))
            contents-str (or (:contents att-map) type-str)
            amount-str (:amount att-map)]
        (str s (format "<tr><td>%s</td><td>%s</td><td>%s</td></tr>"
                 type-str
                 contents-str
                 amount-str)))) "" attachments))

(def- kopiolaitos-html-table-str
  "<table><thead><tr>%s</tr></thead><tbody>%s</tbody></table>")

(defn- get-kopiolaitos-html-table-header-str [lang]
  (with-lang lang
    (str
      "<th>" (loc "application.attachmentType") "</th>"
      "<th>" (loc "application.attachmentContents") "</th>"
      "<th>" (loc "verdict-attachment-prints-order.order-dialog.orderCount") "</th>")))

(def- zip-file-name "Lupakuvat.zip")

(defn- send-kopiolaitos-email [lang email-address attachments orderInfo]
  (let [zip (attachment/get-all-attachments attachments)
        email-attachment {:content zip :file-name zip-file-name}
        email-subject (str (with-lang lang (loc :kopiolaitos-email-subject)) \space (:ordererOrganization orderInfo))
        html-table (format
                     kopiolaitos-html-table-str
                     (get-kopiolaitos-html-table-header-str lang)
                     (get-kopiolaitos-html-table-content attachments lang))
        orderInfo (assoc orderInfo :contentsTable html-table)]
    (try
      ;; from email/send-email-message false = success, true = failure -> turn it other way around
      (not (email/send-email-message
             email-address
             email-subject
             (email/apply-template "kopiolaitos-order.html" orderInfo)
             [email-attachment]))
      (io/delete-file zip)
      (catch java.io.IOException ioe
        (warnf "Could not delete temporary zip file: %s" (.getAbsolutePath zip)))
      (catch Exception e
        (fail! :kopiolaitos-email-sending-failed)))))

(defn- get-kopiolaitos-email-address [{:keys [organization] :as application}]
  (organization/with-organization organization :kopiolaitos-email))

(defn do-order-verdict-attachment-prints [{{:keys [lang attachmentsWithAmounts orderInfo]} :data application :application}]
  (if-let [email-address (get-kopiolaitos-email-address application)]
    (if (send-kopiolaitos-email lang email-address attachmentsWithAmounts orderInfo)
      (ok)
      (fail! :kopiolaitos-email-sending-failed))
    (fail! :no-kopiolaitos-email-defined)))
