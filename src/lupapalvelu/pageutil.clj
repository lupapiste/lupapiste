(ns lupapalvelu.pageutil
  (:require [lupapalvelu.i18n :as i18n]
            [ring.util.codec :as codec]
            [rum.core :as rum]))

(defn try-again-page
  "Error response that shows a page with a try again link. The link is constructed from
  `raw` (action name) and `data` (params). `status` is the HTTP status code (typically 503
  Service Unavailable) and `error` the l10n key for the error message shown (`lang`
  denotes the language)."
  [{:keys [lang data]} {:keys [raw status error]}]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (let [msg (i18n/localize lang error)]
              (rum/render-static-markup
               [:html
                [:head [:title msg]]
                [:body
                 [:div
                  {:style {:margin  "2em 2em"
                           :border  "2px solid red"
                           :padding "1em 1em"}}
                  [:h3 {:style {:margin-top 0}} msg]
                  [:a {:href (format "/api/raw/%s?%s"
                                     (name raw)
                                     (codec/form-encode data))}
                   (i18n/localize lang :pate.try-again)]]]]))})
