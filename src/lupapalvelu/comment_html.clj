(ns lupapalvelu.comment-html
  (:require [garden.core :as garden]
            [garden.selectors :as sel]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.date :as date]
            [rum.core :as rum]
            [sade.core :refer :all]
            [sade.property :as property]
            [sade.util :as util])
  (:import (java.text SimpleDateFormat)))

(defn html [body]
  (str "<!DOCTYPE html>"
       (rum/render-static-markup
         [:html
          [:head
           [:meta {:http-equiv "content-type"
                   :content    "text/html; charset=UTF-8"}]
           [:style
            {:type "text/css"
             :dangerouslySetInnerHTML
                   {:__html (garden/css
                              [[:* {:font-family "'Carlito', sans-serif"}]
                               [:.block {:margin-top "2em"}]
                               [:.content-container {:margin-top "1.5em"}]
                               [:h3 {:padding "0px"
                                     :margin  "0px"}]
                               [:.header-info {:font-weight :bold
                                               :line-height "1.6"}]
                               [:table {:width "100%"}]
                               [:tr {:margin "1em"}
                                [(sel/& (sel/nth-child :even)) {:background "#EEE"}]
                                [(sel/& (sel/nth-child :odd)) {:background "#FFF"}]]
                               [:td {:padding "0.5em"
                                     :width "50%"}
                                [:.operations {:width "100%"}]]
                               [:div.header {:padding-bottom "1em"}]
                               [:div.footer {:padding-top "1em"}]
                               [:.comment {:padding "0.5em"}
                                [(sel/& (sel/nth-child :even)) {:background "#EEE"}]
                                [(sel/& (sel/nth-child :odd)) {:background "#FFF"}]]
                               [:.commenter-date {:font-weight :bold
                                                  :line-height "1.6"}]
                               [:.page-break {:page-break-before :always}]])}}]]
          [:body body]])))

(defn info-box [lang key value]
  (let [info-box-div [:div [:div.header-info (i18n/localize lang key)]]]
    (if (sequential? value)
      (reduce #(conj %1 [:div.info %2]) info-box-div value)
      (conj info-box-div [:div.info value]))))

(defn- concat-names [lang org {:keys [firstName lastName roleId]}]
  (let [lang (keyword lang)
        role (->> org
                  :handler-roles
                  (util/find-first #(= (:id %) roleId))
                  (#(get-in % [:name lang])))]
    (str firstName " " lastName " (" role ")")))

(defn info-grid [lang org application]
  [:div.content-container
   [:table
    [:tr
     [:td (info-box lang :application.id (:id application))]
     [:td (info-box lang :application.address (:address application))]]
    [:tr
     [:td (info-box lang :application.property (property/to-human-readable-property-id (:propertyId application)))]
     [:td (info-box lang :application.municipality (->> application
                                                        :municipality
                                                        (str "municipality.")
                                                        (i18n/localize lang)))]]
    [:tr
     [:td (info-box lang :application.submissionDate (date/finnish-date (:submitted application)))]
     [:td (info-box lang :state.application (->> application
                                                 :state
                                                 (i18n/localize lang)))]]
    [:tr
     [:td (info-box lang :application.applicant (:applicant application))]
     [:td (info-box lang :application.handlers (->> application
                                                    :handlers
                                                    (map (partial concat-names lang org))))]]
    [:tr
     [:td (info-box lang :verdictGiven (-> application
                                           :verdicts
                                           (last)
                                           :paatokset
                                           (last)
                                           :paivamaarat
                                           :anto
                                           (date/finnish-date)))]
     [:td (info-box lang :application.kuntalupatunnus (-> application
                                                          :verdicts
                                                          (last)
                                                          :kuntalupatunnus))]]
    [:tr
     (let [operations-div [:td.operations {:colspan 2}
                           [:div.header-info (i18n/localize lang :operations)]]
           operations (cons (:primaryOperation application) (:secondaryOperations application))]
       (reduce #(conj %1 [:div.info (->> %2 :name (str "operations.") (i18n/localize lang))]) operations-div operations))]]])

(def- time-format (SimpleDateFormat. "dd.MM.yyyy HH:mm"))

(defn format-comment [lang comment]
  (let [{:keys [firstName lastName role]} (:user comment)
        user-string (str firstName " " lastName " (" (i18n/localize lang role) ")")
        date (util/to-local-datetime (:created comment))]
    (when-not (-> comment :target :type (keyword) (= :attachment))
      [:div.comment
       [:div.commenter-date user-string " - " date]
       [:div.comment-text (:text comment)]])))

(defn comment-rows [lang comments]
  (let [comment-list [:div.comments]]
    (reduce #(conj %1 (format-comment lang %2)) comment-list comments)))

(defn comment-html-body [lang org application]
  [:div
   [:div
    [:h3.info-header (i18n/localize lang :conversation.pdf.application-info-header)]
    (info-grid lang org application)]
   [:div.block
    [:h3.conversation-header (i18n/localize lang :conversation.pdf.conversation-header)]
    [:div.conversation-info (i18n/localize lang :conversation.pdf.conversation-info)]
    [:div.content-container
     (comment-rows lang (:comments application))]]])

(defn comment-html
  "Source-data is a map containing keys referred in pdf-layout source
  definitions. Returns :header, :body, :footer map."
  [lang org application]
  {:body (html (comment-html-body lang org application))})
