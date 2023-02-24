(ns lupapalvelu.oauth.page
  "OAuth login/consent page."
  (:require [lupapalvelu.company :as com]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.oauth.core :as oauth]
            [lupapalvelu.user :as usr]
            [rum.core :as rum]
            [rum.server-render :refer [escape-html]]
            [sade.security-headers :as headers]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- lang-buttons [{:keys [lang]}]
  [:div.dsp--flex
   (for [x     (map name i18n/languages)
         :when (util/not=as-kw x lang)]
     [:button.navi {:formaction (str "/oauth/language/" x)
                    :aria-label (i18n/loc x)}
      (ss/upper-case x)])])

(defn- logout-button [{:keys [lang user]}]
  (when user
    [:button.navi {:formaction "/oauth/logout"}
     [:i.lupicon-log-out {:aria-hidden true}]
     [:span (i18n/localize lang :logout)]]))

(defn inner-html [lang loc-key & args]
  {:dangerouslySetInnerHTML
   {:__html (apply i18n/localize-and-fill lang loc-key (map escape-html args))}})

(defn client-name [{:keys [lang client]}]
  (get-in client [:oauth :display-name lang]))

(defn scope-list [{:keys [lang scope-vec] :as fields}]
  [:div
   [:span (inner-html lang :oauth.scope.title (client-name fields))]
   [:ul
    (for [scope scope-vec]
      [:li (i18n/localize lang :oauth.scope scope)])]])

(defn- userinfo [{:keys [lang user ::company-name]}]
  (let [fullname       (ss/trim (usr/full-name user))
        {email :email} user]
    [:div.gap--b2
     (if company-name
       (inner-html lang :oauth.user.company
                   company-name fullname email)
       (inner-html lang :oauth.user.individual
                   fullname email))]))

(defn- warning [lang warning]
  (when warning
    [:div.error-note.gap--v2
     {:role         :alert
      :data-test-id warning}
     (i18n/localize lang warning)]))

(defn- registration [{:keys [lang client]}]
  (when (some-> client :oauth :registration?)
    [:div.dsp--flex.flex--column-gap2.gap--t2
     {:data-test-id "oauth-registration"}
     [:i.lupicon-circle-info.primary {:aria-hidden true}]
     [:div
      [:div (i18n/localize lang :oauth.registration.info)]
      [:a {:href (format "/app/%s/welcome#!/register" (name lang))}
       (i18n/localize lang :oauth.registration.link)]]]))

(defn- button [lang style loc-key icon options]
  [(util/kw-path :button style)
   (assoc options :style {:border-width "2px"})
   [:span.txt--upper.txt--bold.w--min-8em
    (i18n/localize lang loc-key)]
   [(keyword (str "i.lupicon-" (name icon)))
    {:aria-hidden true}]])

(defn- cancel-button [lang]
  (button lang :secondary :cancel :remove
          {:formaction "/oauth/cancel"}))

(defn- consent-buttons [{:keys [lang ::user-warning]}]
  [:div.btn-.dsp--flex.flex--column-gap4.gap--v2
   (button lang :primary :approve :check
           {:formaction "/oauth/consent"
            :disabled   (boolean user-warning)})
   (cancel-button lang)])

(defn consent-view [{:keys [lang ::user-warning] :as fields}]
  [:div.flex--column
   (userinfo fields)
   (scope-list fields)
   (warning lang user-warning)
   (consent-buttons fields)])

(defn- login-buttons [{:keys [lang]}]
  [:div.dsp--flex.flex--column-gap4.gap--v2
   (button lang :primary :approve :check
           {:formaction "/oauth/login"
            :type       "submit"})
   (cancel-button lang)])

(defn login-view [{:keys [lang ::form-error ::username ::password] :as fields}]
  (let [error? (util/=as-kw form-error :error.login)]
    [:div.flex--column
     (scope-list fields)
     [:label.lux.gap--t1 {:for :oauth-username}
      (i18n/localize lang :oauth.username)]
     [:input.lux
      {:type         "text"
       :aria-invalid error?
       :name         :username
       :placeholder  (i18n/localize lang :oauth.placeholder.username)
       :id           :oauth-username
       :value        username}]
     [:label.lux.gap--t1 {:for :oauth-password}
      (i18n/localize lang :userinfo.password)]
     [:input.lux.gap--b1
      {:type         "password"
       :name         :password
       :aria-invalid error?
       :placeholder  (i18n/localize lang :oauth.placeholder.password)
       :id           :oauth-password
       :value        password}]
     (warning lang form-error)
     (login-buttons fields)
     (registration fields)]))

(defn- page [{:keys [user client lang anti-csrf] :as fields}]
  (letfn [(hidden
            ([field-name field-value]
             (when field-value
               [:input {:type  "hidden"
                        :name  (name field-name)
                        :value field-value}]))
            ([field-key]
             (hidden field-key (field-key fields))))]
    [:html {:lang :fi}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name    "description"
              :content "Lupapiste"}]
      [:meta {:name    "author"
              :content "Cloudpermit Oy -- https://cloudpermit.com/"}]
      [:link {:href "/lp-static/css/fonts.css"
              :rel  "stylesheet"
              :type "text/css"}]
      [:link {:id   "main-css"
              :href "/lp-static/css/main.css"
              :rel  "stylesheet"}]
      [:link {:id   "lupicons-css"
              :href "/lp-static/css/lupicons.css"
              :rel  "stylesheet"}]
      [:link {:rel  "icon"
              :href "/lp-static/img/favicon-v2.png"
              :type "image/png"}]
      [:title "Lupapiste"]]
     [:body
      [:form {:method "POST"}
       (when-not user
         [:input.hidden {:type       "submit"
                         :formaction "/oauth/login"}])
       (hidden :client_id (some-> client :oauth :client-id))
       (hidden :scope)
       (hidden :lang)
       (hidden :response_type)
       (hidden :success_callback)
       (hidden :cancel_callback)
       (hidden :error_callback)
       (hidden :__anti-forgery-token anti-csrf)

       [:nav.nav-flex.pad--h2.flex--between.flex--align-center
        [:div.dsp--flex.flex--gap4.flex--align-center
         [:div.logo.lupapiste-logo {:title "Lupapiste"}]
         (lang-buttons fields)]
        (logout-button fields)]
       [:div.pad--t15.flex--center.bg--white
        [:div.bg--blue.gap--v2.pad--4
         {:role :main}
         [:h1.h2.gap--b2 (inner-html lang :oauth.accept.header (client-name fields))]
         (if user
           (consent-view fields)
           (login-view fields))]]]]]))

(defn page-response [fields & {:keys [form-error username password]}]
  (let [company-name (some-> fields :user :company :id
                             com/find-company-by-id
                             :name ss/trim)]
    {:status  200
     :headers {"Content-Type"            "text/html;charset=utf-8"
               ;; form-action directive cannot be set here, because it prevents redirects from the login form POST
               "Content-Security-Policy" headers/csp-with-frame-ancestors}
     :body    (-> (util/assoc-when fields
                                   ::company-name company-name
                                   ::user-warning (oauth/check-user fields)
                                   ::form-error form-error
                                   ::username username
                                   ::password password)
                  page
                  rum/render-static-markup)}))
