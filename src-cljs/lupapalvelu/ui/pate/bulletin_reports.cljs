(ns lupapalvelu.ui.pate.bulletin-reports
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]))

(rum/defc verdict-bulletins < rum/reactive
  [app-id]
  (when-let [bulletins (seq (rum/react state/verdict-bulletins))]

    (let [lang        (common/get-current-language)
          kuulutus?   (rum/react state/kuulutus?)
          button-text (common/loc (if kuulutus?
                                    :bulletin-report.kuulutus-report
                                    :bulletin-report.report))]
      [:div
       [:br] [:br] [:br]
       [:h2 (common/loc (if kuulutus?
                          :bulletin-report.kuulutukset
                          :bulletin-report.bulletins))]
       [:table.pate-verdicts-table
        [:tbody
         (for [{:keys [id] bulletin-text :text} bulletins]
           [:tr {:key id}
            [:td..no-wrap-whitespace
             [:a.like-bth {:target "_blank"
                           :href   (js/sprintf "/app/%s/bulletins#!/bulletin/%s"
                                               lang id)}
              bulletin-text]]
            [:td
             [:a.btn.primary {:target     "_blank"
                              :aria-label (str button-text ": " bulletin-text)
                              :href       (js/sprintf "/api/raw/bulletin-report-pdf?id=%s&bulletinId=%s&lang=%s"
                                                  app-id id lang)}
              button-text]]
            [:td {:width "100%"}]])]]])))
