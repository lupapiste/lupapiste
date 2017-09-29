(ns lupapalvelu.ui.bulletins.local-bulletins
  (:require [rum.core :as rum]))

(defonce args (atom {}))

(rum/defc local-bulletins < rum/reactive
  [_]
  [:div
   [:div.full.content.orange-bg
    [:div.content-center
     [:h1.slogan.municipal-heading "Kunnan julkipanolista"]
     [:h2.slogan.municipal-heading "Kunnan julkipanolista"]]]
   [:div.full.content
    [:div.content-center.municipal-caption
     [:p "Kunnan rakennuslupapäätökset annetaan julkipanon jälkeen, jolloin niiden katsotaan tulleen asianosaisten tietoon. Oikaisuvaatimusaika on 30 päivää."]
     [:p "Kunnan rakennuslupapäätökset annetaan julkipanon jälkeen, jolloin niiden katsotaan tulleen asianosaisten tietoon."]
     [:p "Kunnan rakennuslupapäätökset annetaan julkipanon jälkeen, jolloin niiden katsotaan tulleen asianosaisten tietoon."]]]
   [:div.full.content
    [:div.content-center
     [:table.application-bulletins-list
      [:thead
       [:tr
        [:th "Pykälä (§)"]
        [:th "Lupatunnus"]
        [:th "Rakennuspaikka"]
        [:th "Asia/Toimenpide"]
        [:th "Päättäjä"]
        [:th "Päätöksen antopäivä"]
        [:th "Viimeinen oikaisuvaatimuspäivä"]]
       [:tbody]]]]]])

(defn mount-component []
  (rum/mount (local-bulletins)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :dom-id (name domId))
  (mount-component))
