(ns lupapalvelu.ui.bulletins.bulletin-verdict-data
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.util :as util]))

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

(defn- kv-pair [label value]
  (when value
    [:div.key-value-pair
     {:style {:width "80%"}}
     [:label (common/loc label)]
     [:span.value value]]))

(rum/defc detailed-verdict-data [bulletin]
  (when-let [verdict (first (:verdicts bulletin))]
    [:div.container
     (for [paatos (:paatokset verdict)]
      ^{:key (util/unique-elem-id "verdict-")}
      [:div.verdict-content
       [:h3
        [:span (common/loc :application.verdict.title)]]
       [:div.spacerL
        [:div.key-value-pair
         {:style {:width "80%"}}
         [:label (common/loc :verdict.id)]
         [:span.value  (:kuntalupatunnus verdict)]]
        [:div.key-value-pair
         {:style {:width "80%"}}
         [:label (common/loc :verdict.anto)]
         [:span.value  (common/format-timestamp (:verdictGivenAt bulletin))]]
        [:div.key-value-pair
         {:style {:width "80%"}}
         [:label (common/loc :verdict.muutoksenhaku.paattyy)]
         [:span.value  (common/format-timestamp (:appealPeriodEndsAt bulletin))]]]
       [:div.spacerL
        [:h4 (common/loc :verdict.lupamaaraukset)]
        [:div.accordion-content-part.spacerM
         (kv-pair :verdict.autopaikkojaEnintaan (-> paatos :lupamaaraykset :autopaikkojaEnintaan))
         (kv-pair :verdict.autopaikkojaVahintaan (-> paatos :lupamaaraykset :autopaikkojaVahintaan))
         (kv-pair :verdict.autopaikkojaRakennettava (-> paatos :lupamaaraykset :autopaikkojaRakennettava))
         (kv-pair :verdict.autopaikkojaRakennettu (-> paatos :lupamaaraykset :autopaikkojaRakennettu))
         (kv-pair :verdict.autopaikkojaKiinteistolla (-> paatos :lupamaaraykset :autopaikkojaKiinteistolla))
         (kv-pair :verdict.autopaikkojaUlkopuolella (-> paatos :lupamaaraykset :autopaikkojaUlkopuolella))
         (kv-pair :verdict.kerrosala (-> paatos :lupamaaraykset :kerrosala))
         (kv-pair :verdict.vaaditutTyonjohtajat (-> paatos :lupamaaraykset :vaaditutTyonjohtajat))
         (kv-pair :verdict.vaaditutErityissuunnitelmat (clojure.string/join ", " (-> paatos :lupamaaraykset :vaaditutErityissuunnitelmat)))]
        [:h4 (common/loc :verdict.poytakirjat)]
        [:div.accordion-content-part.spacerM 1234]
        [:h4 (common/loc :application.attachments.paapiirustus)]
        [:div.accordion-content-part.spacerM 1234435]]])]))

(rum/defc init-identification-link [bulletin]
  (let [pathname (aget js/window.location "pathname")
        search (aget js/window.location "search")
        hash (aget js/window.location "hash")
        action-url (str "/dev/saml-login?success=" (js/escape (str pathname search hash "/success")))]
    [:div.container
     [:a.btn.btn-primary
      {:href action-url}
      (common/loc :register.action)]]))
