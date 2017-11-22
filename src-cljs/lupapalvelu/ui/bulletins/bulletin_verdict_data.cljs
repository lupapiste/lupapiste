(ns lupapalvelu.ui.bulletins.bulletin-verdict-data
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :as common]))

(rum/defc verdict-data [bulletin]
  (when bulletin
    [:div.container
     [:div.bulletin-tab-content
      [:h3
       [:span (common/loc :application.verdict.title)]]
      [:div.spacerL
       [:div.key-value-pair
        {:style {:width "80%"}}
        [:label (common/loc :verdict.id)]
        [:span.value  (-> bulletin :verdicts first :kuntalupatunnus)]]
       [:div.key-value-pair
        {:style {:width "80%"}}
        [:label (common/loc :verdict.anto)]
        [:span.value  (common/format-timestamp (:verdictGivenAt bulletin))]]
       [:div.key-value-pair
        {:style {:width "80%"}}
        [:label (common/loc :verdict.muutoksenhaku.paattyy)]
        [:span.value  (common/format-timestamp (:appealPeriodEndsAt bulletin))]]]
      [:div.spacerL
       [:pre.wrapped_text (-> bulletin :verdictData :text)]]]]))

(rum/defc init-identification-link [bulletin]
  [:div.container
   [:a.btn.btn-primary
    {:href (str "/dev/saml-login?success=/app/fi/bulletins%23!/bulletin/" (:id bulletin) "/success")}
    (common/loc :register.action)]])
;;     <a class="btn btn-primary"
;; data-bind="ltext: 'register.action', attr: {href: href, id: id}, visible: visible"
;; data-test-id="vetuma-init"></a>
