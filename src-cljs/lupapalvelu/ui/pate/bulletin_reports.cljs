(ns lupapalvelu.ui.pate.bulletin-reports
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-strings :as ss]))

(rum/defc verdict-bulletins < rum/reactive
  [app-id]
  (when-let [bulletins (seq (rum/react state/verdict-bulletins))]

    (let [lang        (common/get-current-language)
          button-text (common/loc :bulletin-report.report)]
      [:div
       [:br] [:br] [:br]
       [:h2 (common/loc :bulletin-report.bulletins)]
       [:table.pate-verdicts-table
        [:tbody
         (for [{:keys [start-date end-date section
                       id]} bulletins
               :let         [section (some->> section (str "§"))
                             period (when (or start-date end-date)
                                      (str (some-> start-date
                                                   (common/format-timestamp))
                                           " - "
                                           (some-> end-date
                                                   (common/format-timestamp))))
                             text (ss/join-non-blanks " " [section period])]]
           [:tr {:key id}
            [:td..no-wrap-whitespace
             [:a.like-bth {:target "_blank"
                           :href   (js/sprintf "/app/%s/bulletins#!/bulletin/%s"
                                               lang id)}
              text]]
            [:td
             [:a.btn.primary {:target     "_blank"
                              :aria-label (str button-text ": " text)
                              :href       (js/sprintf "/api/raw/bulletin-report-pdf?id=%s&bulletinId=%s&lang=%s"
                                                  app-id id lang)}
              button-text]]
            [:td {:width "100%"}]])]]])))
