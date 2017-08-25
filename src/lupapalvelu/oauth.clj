(ns lupapalvelu.oauth
  (:require [sade.env :as env]))

(def header
  [:nav.nav-wrapper
   [:div.nav-top
    [:div.nav-box
     [:div.brand
      [:a.logo {:href "/app"}
       [:img {:src "/img/lupapiste-logo.png"
              :alt "Lupapiste"}]]]

     #_[:div.header-menu
        [:div.header-box
         [:a {:href (app-link lang "authority#!/applications") :title (t "navigation")}
          [:span.header-icon.lupicon-documents]
          [:span.narrow-hide (t "navigation")]]]
        [:div.header-box
         [:a {:href "/document-search" :title (t "Dokumentit")}
          [:span.header-icon.lupicon-archives]
          [:span.narrow-hide (t "Dokumentit")]]]
        [:div.header-box
         [:a {:href (str "/tiedonohjaus?" lang) :title (t "Tiedonohjaus")}
          [:span.header-icon.lupicon-tree-path]
          [:span.narrow-hide (t "Tiedonohjaus")]]]
        [:div.header-box
         [:a {:href (t "path.guide") :target "_blank" :title (t "help")}
          [:span.header-icon.lupicon-circle-question]
          [:span.narrow-hide (t "help")]]]
        [:div.header-box
         [:a {:href (app-link lang "#!/mypage")
              :title (t "mypage.title")}
          [:span.header-icon.lupicon-user]
          [:span.narrow-hide (or user-name (t "Ei käyttäjää"))]]]
        [:div.header-box
         [:a {:href (app-link lang "logout") :title (t "logout")}
          [:span.header-icon.lupicon-log-out]
          [:span.narrow-hide (t "logout")]]]]]]])

(defn- content-to-template [content]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
    [:meta {:name "description", :content "Lupapiste"}]
    [:meta {:name "author", :content "Evolta Oy -- https://evolta.fi/"}]
    [:link {:href "https://fonts.googleapis.com/css?family=Source+Sans+Pro:400,200,200italic,300italic,300,400italic,600,600italic,700,700italic", :rel "stylesheet", :type "text/css"}] "<!-- Cloak content until css is loaded -->"
    [:link {:id "lupicons-css", :href "/lp-static/css/lupicons.css", :rel "stylesheet"}]
    [:link {:id "main-css", :href (str "/lp-static/css/main.css?b=" env/build-number), :rel "stylesheet"}]
    [:link {:rel "icon", :href "/lp-static/img/favicon-v2.png", :type "image/png"}]
    [:link {:rel "shortcut icon", :href "/lp-static/img/favicon-v2.ico"}]
    [:link {:rel "apple-touch-icon", :href "/lp-static/img/apple-touch-icon.png"}]
    [:link {:rel "apple-touch-icon", :sizes "72x72", :href "/lp-static/img/ios/lp-v2-apple-touch-icon-72x72-precomposed.png"}]
    [:link {:rel "apple-touch-icon", :sizes "76x76", :href "/lp-static/img/ios/lp-v2-apple-touch-icon-76x76-precomposed.png"}]
    [:link {:rel "apple-touch-icon", :sizes "114x114", :href "/lp-static/img/ios/lp-v2-apple-touch-icon-114x114-precomposed.png"}]
    [:link {:rel "apple-touch-icon", :sizes "120x120", :href "/lp-static/img/ios/lp-v2-apple-touch-icon-120x120-precomposed.png"}]
    [:link {:rel "apple-touch-icon", :sizes "144x144", :href "/lp-static/img/ios/lp-v2-apple-touch-icon-144x144-precomposed.png"}]
    [:link {:rel "apple-touch-icon", :sizes "152x152", :href "/lp-static/img/ios/lp-v2-apple-touch-icon-152x152-precomposed.png"}]
    [:title "Lupapiste"]]
   [:body
    header
    [:section
     content]]])



(defn authorization-page-hiccup []
  (content-to-template
    [:div
     "MORO"]))