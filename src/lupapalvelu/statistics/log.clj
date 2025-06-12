(ns lupapalvelu.statistics.log
  "HTML and PDF versions of the procesing log."
  (:require [garden.core :as garden]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pageutil :refer [try-again-page]]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.statistics.core :as stats]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.statistics.entry :as stats-e]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [lupapalvelu.statistics.tags :as stats-tags]
            [rum.server-render :as rum-server]
            [sade.date :as date]
            [sade.util :as util]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(sc/defn processing-logdata :- (sc/maybe stats-sc/ProcessingLogData)
  [application]
  (let [processing (stats-db/calculate-processing application)
        delays     (some->> (stats-e/delay-entries application)
                            (mapv (fn [{:keys [delay-start created explanation]
                                        :as   entry}]
                                    (util/assoc-when
                                      {:start-ts delay-start
                                       :text     (:text created)}
                                      :explanation explanation
                                      :end-ts (stats-e/delay-end-ts entry))))
                            (sort-by :start-ts)
                            seq)]
    (util/assoc-when {:submitted-ts (:submitted application)
                      :processing   processing}
                     :delays     delays
                     :verdict-ts (stats/verdict-ts application))))

(def ^:private header-css
  (garden/css [[:div.header {:font-family     "'Mulish'"
                             :font-size       "9pt"
                             :margin          "0 4em"
                             :display         :flex
                             :justify-content :space-between
                             :align-items     :center
                             :padding         0
                             :border-bottom   "1px solid black"}]
               [:.logo [:img :svg {:width "120px"}]]]))

(defn- header [application]
  [:div {:style {:width "100%"}}
   [:style header-css]
   [:div.header
    [:div (:id application)]
    [:div (date/finnish-date (date/now))]
    [:div.logo
     html-pdf/lupapiste-logo]]])

(def ^:private footer-css
  (garden/css [[:div.footer {:font-family "'Mulish'"
                             :font-size   "9pt"
                             :margin      "0 4em"
                             :border-top  "1px solid black"
                             :padding     "0.5em 0 0 0"}
                [:.right {:float   "right"
                          :padding "0"}]]]))

(defn- footer [lang]
  [:div {:style {:width "100%"}}
   [:style footer-css]
   [:div.footer
    [:div.right
     (i18n/localize lang :pdf.page) " " [:span.pageNumber]]]])

(def ^:private tags-css
  (garden/css [[:body {:margin "0 1em"}]
               [:body :table {:font-family "'Mulish'"
                              :font-size   "10pt"}]]))

(defn- loc-fn [lang & terms]
  (apply (if (= 1 (count terms))
           i18n/localize i18n/localize-and-fill)
         lang terms))

(defn processing-log-pdf
  "PDF response or try again page."
  [{:keys [application data system] :as command}]
  (let [app-id (:id application)
        lang   (:lang data)
        tags   [:div
                [:style tags-css]
                [:h1 (i18n/localize lang :statistics.processing.log)]
                [:p (i18n/localize-and-fill lang :statistics.processing.log.lede
                                            app-id
                                            (date/finnish-datetime (date/now)))]
                (stats-tags/processing-log-tags (processing-logdata application)
                                                {:loc-fn  (partial loc-fn lang)
                                                 :date-fn date/finnish-date})]
        title  (i18n/localize-and-fill lang :statistics.processing.log.title
                                       app-id (date/finnish-date (date/now)))
        pdf    (html-pdf/html->pdf system
                                   {:title  title
                                    :header (rum-server/render-static-markup (header application))
                                    :body   (rum-server/render-static-markup tags)
                                    :footer (rum-server/render-static-markup (footer lang))}
                                   {:top    "25mm"
                                    :bottom "25mm"
                                    :left   "9mm"
                                    :right  "9mm"})]
    (if (:ok pdf)
      {:status  200
       :headers {"Content-Type"        "application/pdf"
                 "Content-Disposition" (format "filename=\"%s\"" (str title ".pdf"))}
       :body    (:pdf-file-stream pdf)}
      (try-again-page command  {:status 503 ;; Service Unavailable
                                :error  :statistics.processing.log.error
                                :raw    (keyword (:action command))}))))
