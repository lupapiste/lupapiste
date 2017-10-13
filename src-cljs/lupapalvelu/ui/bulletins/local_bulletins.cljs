(ns lupapalvelu.ui.bulletins.local-bulletins
  (:require [rum.core :as rum]
            [lupapalvelu.ui.bulletins.state :as state]
            [lupapalvelu.ui.common :as common]))

(defonce args (atom {}))

(defn init
  [init-state props]
  (reset! state/current-organization "753-R")
  (common/query :local-application-bulletins
                (fn [{:keys [data]}] (reset! state/local-bulletins data))
                :organization @state/current-organization :searchText "" :page 1)
  init-state)

(rum/defc bulletins-table < rum/reactive
  [_]
  (let [bulletins (rum/react state/local-bulletins)]
    [:table.application-bulletins-list
     [:thead
      [:tr
       [:th (common/loc :bulletin.verdict-section)]
       [:th (common/loc :bulletin.lupatunnus)]
       [:th (common/loc :bulletin.building-site)]
       [:th (common/loc :bulletin.type)]
       [:th (common/loc :bulletin.verdict-giver)]
       [:th (common/loc :bulletin.verdict.verdictGivenAt)]
       [:th (common/loc :bulletin.rectification-period-ends)]]]
      [:tbody
       (for [{:keys [id address verdictGivenAt application-id
                     appealPeriodStartsAt bulletin-op-description]
              {verdictData :data category :category} :matti-verdict}  bulletins]
         [:tr
          {:key id}
          [:td (str (:verdict-section verdictData) " "
                    (common/loc (str "matti-" category ".verdict-code." (:verdict-code verdictData))))]
          [:td (or application-id id)]
          [:td address]
          [:td bulletin-op-description]
          [:td (:contact verdictData)]
          [:td (common/format-timestamp verdictGivenAt)]
          [:td (common/format-timestamp appealPeriodStartsAt)]])]]))

(rum/defc local-bulletins < {:init         init}
  [_]
  [:div
   [:div.full.content.orange-bg
    [:div.content-center
     [:h1.slogan.municipal-heading "Kunnan julkipanolista"] ; NB: These will be read from db, no need now for localization
     [:h2.slogan.municipal-heading "Kunnan julkipanolista"]]]
   [:div.full.content
    [:div.content-center.municipal-caption
     [:p "Kunnan rakennuslupapäätökset annetaan julkipanon jälkeen, jolloin niiden katsotaan tulleen asianosaisten tietoon. Oikaisuvaatimusaika on 30 päivää."]
     [:p "Kunnan rakennuslupapäätökset annetaan julkipanon jälkeen, jolloin niiden katsotaan tulleen asianosaisten tietoon."]
     [:p "Kunnan rakennuslupapäätökset annetaan julkipanon jälkeen, jolloin niiden katsotaan tulleen asianosaisten tietoon."]]]
   [:div.full.content
    [:div.content-center
     (bulletins-table)]]])

(defn mount-component []
  (rum/mount (local-bulletins)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :dom-id (name domId))
  (mount-component))
