(ns lupapalvelu.templates.base
  (:require [sade.env :as env]))

(defn page-hiccup [header content]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0"}]
    [:meta {:name "description", :content "Lupapiste"}]
    [:meta {:name "author", :content "Evolta Oy -- https://evolta.fi/"}]
    [:link {:href "https://fonts.googleapis.com/css?family=Source+Sans+Pro:400,200,200italic,300italic,300,400italic,600,600italic,700,700italic", :rel "stylesheet", :type "text/css"}]
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
    [:section.page.welcome-page.visible
     [:div.container
      content]]]])
