(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts."
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.html-template-common :as common]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn verdict-body [lang application {:keys [published data]}]
  (let [loc (partial i18n/localize-and-fill lang)]
    [:div
     [:span (loc :pate.published-date (util/to-local-datetime published))]]))

(enlive/deftemplate pate-verdict-body (enlive/html [:body])
  [lang application verdict]
  [:body] (-> (verdict-body lang application verdict)
              enlive/html
              enlive/content))

(defn verdict-pdf [lang application {:keys [published] :as verdict}]
  (html-pdf/create-and-upload-pdf
   application
   "pate-verdict"
   (common/apply-page pate-verdict-body lang application verdict)
   {:filename (i18n/localize-and-fill lang
                                      :pate.pdf-filename
                                      (:id application)
                                      (util/to-local-datetime published))}))


(defn create-verdict-attachment
  "1. Create PDF file for the verdict.
   2. Create verdict attachment.
   3. Bind 1 into 2."
  [{:keys [lang application user created] :as command} verdict]
  (let [{:keys [file-id mongo-file]} (verdict-pdf lang application verdict)
        _                            (when-not file-id
                                       (fail! :pate.pdf-verdict-error))
        {attachment-id :id
         :as           attachment}   (att/create-attachment!
                                      application
                                      {:created           created
                                       :set-app-modified? false
                                       :attachment-type   {:type-group "paatoksenteko"
                                                           :type-id    "paatos"}
                                       :target            {:type "verdict"
                                                           :id   (:id verdict)}
                                       :locked            true
                                       :read-only         true
                                       :contents          (i18n/localize lang
                                                                         :pate-verdict)})]
    (bind/bind-single-attachment! (update-in command
                                             [:application :attachments]
                                             #(conj % attachment))
                                  (mongo/download file-id)
                                  {:fileId       file-id
                                   :attachmentId attachment-id}
                                  nil)))
