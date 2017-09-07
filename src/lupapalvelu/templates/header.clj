(ns lupapalvelu.templates.header
  (:require [lupapalvelu.i18n :as i18n]))

(defn simple-header [lang]
  [:nav.nav-wrapper
   [:div.nav-top
    [:div.nav-box
     [:div.brand
      [:a.logo {:href "/"}
       [:img {:src "/img/lupapiste-logo.png"
              :alt "Lupapiste"}]]]
     [:div.header-menu
      [:div.header-box
       [:a {:href (str "/app/" lang "/logout") :title (i18n/localize lang "logout")}
        [:span.header-icon.lupicon-log-out]
        [:span.narrow-hide (i18n/localize lang "logout")]]]]]]])
