(ns lupapalvelu.ui.bulletins.bulletin-verdict-data
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.rum-util :as rum-util]
            [lupapalvelu.ui.util :as util]
            [rum.core :as rum]
            [sade.shared-util :as shared-util]))

(defn text-span [text markup?]
  (if markup?
    (components/markup-span text)
    [:pre.wrapped_text text]))

(rum/defc verdict-data [bulletin markup?]
  (when bulletin
    [:div.container
     [:div.bulletin-tab-content
      [:h3
       [:span (common/loc :application.verdict.title)]]
      [:div.spacerL
       (when-let [vid (-> bulletin :verdicts first :kuntalupatunnus)]
         [:div.key-value-pair
          [:label (common/loc :verdict.id)]
          [:span.value vid]])
       [:div.key-value-pair
        [:label (common/loc :verdict.anto)]
        [:span.value  (common/format-timestamp (:verdictGivenAt bulletin))]]
       [:div.key-value-pair
        [:label (common/loc :verdict.muutoksenhaku.paattyy)]
        [:span.value  (common/format-timestamp (:appealPeriodEndsAt bulletin))]]]
      [:div.spacerL
       (-> bulletin :verdictData :text (text-span markup?))]]]))

(defn- kv-pair [label value]
  (when value
    [:div.key-value-pair
     [:label (common/loc label)]
     [:span.value value]]))

(rum/defc detailed-verdict-data [bulletin markup?]
  (when-let [verdict (first (:verdicts bulletin))]
    [:div.container
     (for [paatos (:paatokset verdict)]
      ^{:key (util/unique-elem-id "verdict-")}
      [:div.verdict-content
       [:h3
        [:span (common/loc :application.verdict.title)]]
       [:div.spacerL
        (when-let [vid (:kuntalupatunnus verdict)]
          [:div.key-value-pair
           [:label (common/loc :verdict.id)]
           [:span.value vid]])
        [:div.key-value-pair
         [:label (common/loc :verdict.anto)]
         [:span.value  (common/format-timestamp (:verdictGivenAt bulletin))]]
        [:div.key-value-pair
         [:label (common/loc :verdict.muutoksenhaku.paattyy)]
         [:span.value  (common/format-timestamp (:appealPeriodEndsAt bulletin))]]]
       (when-let [maaraykset (:lupamaaraykset paatos)]
         [:div.spacerL
          [:h4 (common/loc :verdict.lupamaaraykset)]
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
               (for [[idx k] (shared-util/indexed (:vaaditutKatselmukset maaraykset))]
                 ^{:key (str "katselmus-" idx)}
                 [:li
                  (if (:tarkastuksenTaiKatselmuksenNimi k)
                    (:tarkastuksenTaiKatselmuksenNimi k)
                    (common/loc (str "task-katselmus.katselmuksenLaji." (:katselmuksenLaji k))))])]]])
          (when (seq (:maaraykset maaraykset))
            [:div
             [:h5 (common/loc :verdict.maaraykset)]
             [:ul
              (for [[idx m] (shared-util/indexed (:maaraykset maaraykset))]
                ^{:key (str "maaraysrivi-" idx)}
                [:li (text-span (:sisalto m) markup?)])]])])
       (when (seq (:poytakirjat paatos))
         [:div.spacerL
          [:h4 (common/loc :verdict.poytakirjat)]
          [:div.accordion-content-part.spacerM
           [:table.table.table-striped.tablesorter
            [:thead
             [:tr
              [:th {:colSpan 2} (common/loc :verdict.status)]
              [:th (common/loc :verdict.pykala)]
              [:th (common/loc :verdict.name)]
              [:th (common/loc :verdict.paatospvm)]]]
            [:tbody
             (for [[idx pk] (shared-util/indexed (:poytakirjat paatos))]
               ^{:key (str "paatospk-" idx)}
               [:tr
                [:td (when (:status pk)
                       (common/loc (str "verdict.status." (:status pk))))
                     (when (and (not (:status pk)) (:paatoskoodi pk))
                       (:paatoskoodi pk))]
                [:td.verdict-text (text-span (:paatos pk) markup?)]
                [:td (:pykala pk)]
                [:td (:paatoksentekija pk)]
                [:td (common/format-timestamp (:paatospvm pk))]])]]]])])]))

(rum/defc init-identification-link [_]
  (let [pathname (aget js/window.location "pathname")
        search (aget js/window.location "search")
        hash (aget js/window.location "hash")
        action-url (str "/dev/saml-login?success=" (js/escape (str pathname search hash "/success")))]
    [:div.container
     [:p
      [:b (common/loc :bulletins.proceed-to-appeal.info)]]
     [:a.btn.btn-primary
      {:href action-url}
      (common/loc :register.action)]]))
