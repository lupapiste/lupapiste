(ns lupapalvelu.ui.bulletins.bulletin-verdict-data
  (:require [rum.core :as rum]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.util :as util]
            [lupapalvelu.ui.rum-util :as rum-util]))

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
       (when-let [maaraykset (:lupamaaraykset paatos)]
         [:div.spacerL
          [:h4 (common/loc :verdict.lupamaaraukset)]
          [:div.accordion-content-part.spacerM
           (kv-pair :verdict.autopaikkojaEnintaan (:autopaikkojaEnintaan maaraykset))
           (kv-pair :verdict.autopaikkojaVahintaan (:autopaikkojaVahintaan maaraykset))
           (kv-pair :verdict.autopaikkojaRakennettava (:autopaikkojaRakennettava maaraykset))
           (kv-pair :verdict.autopaikkojaRakennettu (:autopaikkojaRakennettu maaraykset))
           (kv-pair :verdict.autopaikkojaKiinteistolla (:autopaikkojaKiinteistolla maaraykset))
           (kv-pair :verdict.autopaikkojaUlkopuolella (:autopaikkojaUlkopuolella maaraykset))
           (kv-pair :verdict.kerrosala (:kerrosala maaraykset))
           (kv-pair :verdict.vaaditutTyonjohtajat (:vaaditutTyonjohtajat maaraykset))
           (kv-pair :verdict.vaaditutErityissuunnitelmat (clojure.string/join ", " (:vaaditutErityissuunnitelmat maaraykset)))]
          (when (seq (:muutMaaraykset maaraykset))
            [:div
             [:h5 (common/loc :verdict.muutMaaraykset)]
             [:div.accordion-content-part.spacerM
              [:ul
               (rum-util/map-with-key #([:li %]) (:muutMaaraykset maaraykset))]]])
          (when (seq (:vaaditutKatselmukset maaraykset))
            [:div
             [:h5 (common/loc :verdict.vaaditutKatselmukset)]
             [:div.accordion-content-part.spacerM
              [:ul
               (for [[idx k] (sade.shared-util/indexed (:vaaditutKatselmukset maaraykset))]
                 ^{:key (str "katselmus-" idx)}
                 [:li
                  (if (:tarkastuksenTaiKatselmuksenNimi k)
                    (:tarkastuksenTaiKatselmuksenNimi k)
                    (common/loc (str :task-katselmus.katselmuksenLaji "." (:katselmuksenLaji k))))])]]])
          (when (seq (:maaraykset maaraykset))
            [:div
             [:h5 (common/loc :verdict.maaraykset)]
             [:ul
              (for [[idx m] (sade.shared-util/indexed (:maaraykset maaraykset))]
                ^{:key (str "maaraysrivi-" idx)}
                [:li (:sisalto m)])]])])
       [:div.spacerL
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
