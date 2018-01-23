(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [garden.selectors :as sel]
            [hiccup.core :as hiccup]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.html-template-common :as common]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn html [body]
  (str "<!DOCTYPE html>"
       (hiccup/html
        [:head
         [:meta {:http-equiv "content-type"
                 :content "text/html; charset=UTF-8"}]
         [:style {:type "text/css"}
          (garden/css [:.header
                       [(sel/+ :.header-row :.header-row) {:margin-top "1em"}]
                       [:.header-left {:width "30%"
                                       :display :inline-block}]
                       [:.header-center {:width "40%"
                                         :text-align :center
                                         :display :inline-block}]
                       [:.header-right {:width "30%"
                                        :text-align :right
                                        :display :inline-block}]
                       [:.permit {:text-transform :uppercase
                                  :font-weight :bold}]
                       {:border-bottom "1px solid black"}])]]
        [:body body])))




(defn verdict-body [lang application {:keys [published data]}]
  (let [loc (partial i18n/localize-and-fill lang)]
    [:div
     [:span (loc :pate.published-date (util/to-local-datetime published))]]))

(defn verdict-header
  [lang {:keys [organization]} {:keys [published category data]}]
  [:div.header
   [:div.header-row
    [:div.header-left (org/get-organization-name organization lang)]
    [:div.header-center
     [:div (i18n/localize lang :attachmentType.paatoksenteko.paatos)]]
    [:div.header-right
     [:div.permit (i18n/localize lang (format "pdf.%s.permit" category))]]]
   [:div.header-row
    [:div.header-left (:section data)]
    [:div.header-center [:div (util/to-local-date published)]]
    [:div.header-right []]]]
  )

(defn verdict-pdf [lang application {:keys [published] :as verdict}]
  (html-pdf/create-and-upload-pdf
   application
   "pate-verdict"
   (html (verdict-body lang application verdict))
   {:filename (i18n/localize-and-fill lang
                                      :pate.pdf-filename
                                      (:id application)
                                      (util/to-local-datetime published))
    :header (html (verdict-header lang application verdict))
    :footer (html nil)}))


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
