(ns lupapalvelu.oauth
  (:require [sade.env :as env]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.company :as company]
            [sade.strings :as str]
            [lupapalvelu.token :as token]))

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

(defn authorization-page-hiccup [client scope lang user anti-csrf]
  (let [company-id (get-in user [:company :id])
        company (when company-id (company/find-company-by-id company-id))
        user-name (str (:firstName user) " " (:lastName user))
        scopes (str/split scope #",")]
    (content-to-template
      [:div.oauth
       [:h2 (i18n/localize lang "oauth.accept.header")]
       [:div.user
        [:i.lupicon-circle-attention.primary]
        [:span
         (if company
           (i18n/localize-and-fill lang "oauth.user.company" (:name company) user-name (:email user))
           (i18n/localize-and-fill lang "oauth.user.individual" user-name (:email user)))]]
       [:div.scopes
        [:div (i18n/localize-and-fill lang "oauth.scope.title" (get-in client [:oauth :display-name (keyword lang)]))]
        [:div
         [:ul
          (for [s scopes]
            [:li (i18n/localize-and-fill lang (str "oauth.scope." s) (:name company))])]]]
       [:div.buttons
        [:form.accept {:method "post" :action "/oauth/authorize"}
         [:input {:type "hidden" :name "client_id" :value (get-in client [:oauth :client-id])}]
         [:input {:type "hidden" :name "scope" :value scope}]
         [:input {:type "hidden" :name "lang" :value lang}]
         [:input {:type "hidden" :name "__anti-forgery-token" :value anti-csrf}]
         [:button.accept.positive {:name "accept" :value "true"}
          [:i.lupicon-check]
          [:span (i18n/localize lang "oauth.button.accept")]]]
        [:form {:method "post" :action "/oauth/authorize"}
         [:input {:type "hidden" :name "client_id" :value (get-in client [:oauth :client-id])}]
         [:input {:type "hidden" :name "lang" :value lang}]
         [:button.cancel.reject {:name "cancel" :value "true"}
          [:i.lupicon-remove]
          [:span (i18n/localize lang "oauth.button.cancel")]]]]])))

(defn payment-required-but-not-available? [scope user]
  (and (some #(= % "pay") (str/split scope #","))
       (nil?
         (when-let [id (get-in user [:company :id])]
           (company/find-company-by-id id)))))

(defn grant-access-token [client scope user]
  (token/make-token :oauth
                    user
                    {:client-id (get-in client [:oauth :client-id])
                     :scopes (str/split scope #",")}
                    :ttl
                    (* 10 60 1000)))
