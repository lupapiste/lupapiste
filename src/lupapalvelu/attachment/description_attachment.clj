(ns lupapalvelu.attachment.description-attachment
  "Functions for generating a PDF attachment of the app description.
  The KRYSP spec requires that a description attachment is included when handling the encumbrance
  (rakennusrasite-tai-yhteisjarjestely) operation"
  (:require [clojure.string :as str]
            [lupapalvelu.bulletin-report.page :as page]
            [lupapalvelu.comment-html :as comment-html]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pdf.html-template :as html-pdf]))

(defn- get-description-title
  [lang {:keys [id]} title-loc]
  (format "%s - %s" id (i18n/localize lang title-loc)))

(defn get-description-filename
  "Localizes the description filename"
  [lang {:keys [id]} title-loc]
  (format "%s-%s.pdf" id (i18n/localize lang title-loc)))

(defn- description-html-body [lang application title-loc text]
  (comment-html/html
    [:div
     [:h3 (get-description-title lang application title-loc)]
     [:p (->> (str/split text #"\n")
              (interpose [:br]))]]))

(defn get-description-as-pdf
  "Returns the PDF contents of the encumbrance description"
  [description-text lang application title-loc]
  (html-pdf/html->pdf
    {:body   (description-html-body lang application title-loc description-text)
     :header (page/render-template :header {:lang lang})
     :footer (page/render-template :footer {:lang lang})
     :title  (get-description-title lang title-loc application)}
    {:top    "22mm"
     :bottom "28mm"
     :left   "11mm"
     :right  "11mm"}))
