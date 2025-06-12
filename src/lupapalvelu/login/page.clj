(ns lupapalvelu.login.page
  "Login and other toplevel pages for non-logged in users."
  (:require [clojure.java.io :as io]
            [lupapalvelu.chatbot :refer [chatbot-hiccup]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.vetuma :as vetuma]
            [rum.core :as rum]
            [sade.env :as env]
            [sade.security-headers :as headers]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- button [lang style loc-key icon options]
  (let [text (i18n/localize lang loc-key)]
    [(util/kw-path :button style)
     (assoc options
            :style {:border-width "2px"}
            :aria-label text)
     [:div.flex--center
      {:aria-hidden true}
      [:span.txt--upper.txt--bold.pad--r4
       text]
      [(keyword (str "i.lupicon-" (name icon)))
       {:aria-hidden true}]]]))

(defn- warning [lang warning]
  (when warning
    [:div.error-note.flex--g1
     {:role         :alert
      :id           "error"
      :data-test-id warning}
     (i18n/localize lang warning)]))

(defn view-url
  ([view lang page-id]
   (ss/join-non-blanks "/" [(str "/" (name view))
                            page-id
                            (name lang)]))
  ([view lang]
   (view-url view lang nil))
  ([{:keys [view lang]}]
   (view-url view lang)))

(defn language-select
  [{:keys [view page-id lang]}]
  (let [lang (if (util/=as-kw lang :fi)
               "sv"
               "fi")]
    [:div.txt--right
     [:a.btn.tertiary.txt--upper
      {:href (view-url view lang page-id)}
      (i18n/localize lang :lang lang)]]))

(defn logo []
  [:div.gap--2.lupapiste-dark-logo
   {:role       "img"
    :aria-label "Lupapiste"}])

(defn white-box
  ([fields form cls]
   [(util/kw-path :div.city-white-box.w--100.gap--4 cls)
    (language-select fields)
    (logo)
    form])
  ([fields form]
   (white-box fields form :w--max-50em)))

(defn link-button
  ([lang url loc-key]
   (link-button lang url loc-key nil))
  ([lang url loc-key icon]
   (let [text (i18n/localize lang loc-key)]
     [:a.btn.tertiary {:href       url
                       :aria-label text}
      [:span.txt--bold.pad--r4.txt--upper
       {:aria-hidden true}
       text]
      (when icon
        [(keyword (str "i.lupicon-" (name icon)))
         {:aria-hidden true}])])))

(defn return-to-login-link [lang]
  (link-button lang
               (view-url :login lang)
               :loginframe.return
               :chevron-small-left))

(defn register-link [lang]
  (link-button lang
               (format "/app/%s/welcome#!/register" (name lang))
               :a11y.register
               :user-plus))

(defn reset-password-link [lang]
  (link-button lang
               (view-url :reset-password lang)
               :a11y.reset-password
               :envelope))

(defn login-with-suomifi-link [lang]
  (let [url  (str (:loginUrl vetuma/ident-urls)
                  "?success=" (format "/app/%s/suomifi-login%%23!/suomifi-login-success" (name lang))
                  "&error=" (format "/app/%s/suomifi-login%%23!/suomifi-login-error" (name lang))
                  (format "&cancel=/login/%s" (name lang)))
        text (i18n/localize lang :login.strong-identification)]
    [:a.btn.primary.flex--center
     {:href       url
      :aria-label text}
     [:span.txt--upper.txt--bold.pad--r4
      {:aria-hidden true}
      text]]))

(defn reset-password-with-suomifi-link [lang]
  (let [url  (str (:loginUrl vetuma/ident-urls)
                  "?success=" (format "/app/%s/suomifi-login%%23!/suomifi-login-success%%3freset-pw%%3dtrue" (name lang))
                  "&error=" (format "/app/%s/suomifi-login%%23!/suomifi-login-error" (name lang))
                  (format "&cancel=/login/%s" (name lang)))
        text (i18n/localize lang :loginframe.reset-suomifi)]
    [:a.btn.primary.flex--center
     {:href       url
      :aria-label text}
     [:span.txt--upper.txt--bold.pad--r4
      {:aria-hidden true}
      text]]))

(defn login-view [{:keys [lang form-error username password hide-next?]
                   :as   fields}]
  (let [error? (util/=as-kw form-error :error.login)]
    (rum/fragment
      (white-box fields
                 (rum/fragment
                   [:div.flex--around.txt--center.gap--v2.pad--h4
                    [:div.w--max-40em.txt--upper (i18n/localize lang :a11y.boast)]]
                   [:div.gap--v2.pad--4
                    [:div.flex--column
                     [:h2.dsp--block.gap--b2 (i18n/localize lang :loginframe.login)]
                     [:div.bd--gray.pad--2.gap--v2
                      [:label.lux.gap--t1 {:for :login-username}
                       (i18n/localize lang :oauth.username)]
                      [:div.dsp--flex.flex--gap2.flex--column
                       [:input.lux.flex--g1
                        {:type              "text"
                         :aria-invalid      error?
                         :aria-errormessage "error"
                         :autofocus         (not hide-next?)
                         :name              :username
                         :placeholder       (i18n/localize lang :oauth.placeholder.username)
                         :id                :login-username
                         :value             username
                         :autocomplete      "username"}]
                       (when-not hide-next?
                         (button lang :primary.w--100 :login.with-email :chevron-small-right
                                 {:type :submit
                                  :id   :button-next}))]
                      (when hide-next?
                        (rum/fragment
                          [:label.lux.gap--t1 {:for :login-password}
                           (i18n/localize lang :userinfo.password)]
                          [:div.dsp--flex.flex--gap2.flex--column.gap--b1
                           [:input.lux.flex--g1
                            {:type              "password"
                             :name              :password
                             :autofocus         true
                             :aria-invalid      error?
                             :aria-errormessage "error"
                             :placeholder       (i18n/localize lang :oauth.placeholder.password)
                             :id                :login-password
                             :value             password
                             :autocomplete      "current-password"}]
                           (warning lang form-error)
                           (button lang :primary.w--100 :loginframe.login :chevron-small-right
                                   {:type :submit
                                    :id   :button-login})]
                          [:div.show-password
                           [:input {:id       :password-visibility-toggle
                                    :type     "checkbox"
                                    :on-click "togglePasswordVisibility(event, 'login-password')"}]
                           [:label {:for :password-visibility-toggle}
                            (i18n/localize lang :a11y.show-password)]]))]
                     (when (seq vetuma/ident-urls)
                       [:div.flex--column
                        [:span.w--100.txt--center.gap--b2
                         (i18n/localize lang :loginframe.or-suomifi)]
                        (login-with-suomifi-link lang)])
                     [:div.dsp--flex.flex--gap2.flex--wrap.gap--t4
                      (register-link lang)
                      (reset-password-link lang)]]]))
      [:div.city-white-box.w--100.gap--4.w--max-50em.pad--4
       [:h2 (i18n/localize lang :a11y.docstore.title)]
       [:div.flex--between.flex--wrap.flex--align-center.gap--t2.flex--gap2
        [:div (i18n/localize lang :a11y.docstore.description)]
        [:a.btn.primary
         {:href  (env/value :docstore :url)
          :style {:border-width "2px"}}
         [:span.txt--upper.txt--bold.pad--r4
          (i18n/localize lang :a11y.docstore.button)]
         [:i.lupicon-chevron-small-right]]]])))

(defn reset-password-view [{:keys [lang sent-email form-error username]
                            :as   fields}]
  (let [error? (boolean form-error)]
    (white-box fields
               [:div.gap--v2.pad--4
                [:div.flex--column
                 (return-to-login-link lang)
                 [:h2.dsp--block.gap--b2 (i18n/localize lang :reset.title)]
                 [:div.bd--gray.pad--2.gap--v2
                  [:div (i18n/localize lang :reset.info)]
                  [:label.lux.gap--t1 {:for :reset-password-email}
                   (i18n/localize lang :email)]
                  [:div.dsp--flex.flex--gap2.flex--column
                   [:input.lux.flex--g1
                    {:type              "text"
                     :autofocus         true
                     :aria-invalid      error?
                     :aria-errormessage "error"
                     :name              :username
                     :placeholder       (i18n/localize lang :reset.email-placeholder)
                     :id                :reset-password-email
                     :value             (when-not sent-email username)}]
                   (warning lang form-error)
                   (button lang :primary.w--100 :reset.send :chevron-small-right
                           {:type       :submit
                            :id         "button-reset-password"
                            :formaction "/reset-password"})
                   (when sent-email
                     [:div.primary-note
                      [:div (i18n/localize-and-fill
                              lang
                              :a11y.password-reset.email-sent
                              sent-email)]
                      [:div (i18n/localize lang :reset.sent.2)]])]]
                 (when (seq vetuma/ident-urls)
                   [:div.flex--column
                    [:span.w--100.txt--center.gap--b2
                     (i18n/localize lang :loginframe.or-suomifi)]
                    (reset-password-with-suomifi-link lang)])
                 [:div.dsp--flex.flex--gap2.flex--wrap.gap--t4
                  (register-link lang)]]])))

(defn page-content-path
  "Resource path pages/page-id.lang.html if the resource exists. Otherwise nil."
  ([{:keys [lang]} page-id]
   (let [page-id (some->> page-id
                          ss/lower-case
                          ;; We allow only letters and - just in case
                          (re-matches #"[a-z-]+"))
         path    (when page-id
                   (format "pages/%s.%s.html" page-id (name lang)))]
     (when (some-> path io/resource)
       path)))
  ([fields]
   (page-content-path fields (:page-id fields))))

(defn page-content-html
  "Resource data or nil."
  [fields page-id]
  (some-> (page-content-path fields page-id)
          io/resource
          slurp))

(defn info-view [{:keys [lang] :as fields}]
  (let [sections (cond-> ["accessibility"]
                   (= lang :fi) (conj "terms" "licenses"))]
    (rum/fragment
      [:div.city-white-box.w--100.w--max-60em.gap--4.pad--b4
       (language-select fields)
       (logo)
       [:div.flex--center
        [:ul.section-toc.gap--t2
         (for [section sections]
           [:li [:a
                 {:href (str "#" section)}
                 (i18n/localize lang :a11y.info section)]])]]]
      (for [section sections]
        [:div.city-white-box.w--100.w--max-60em.gap--4.pad--6.page-content
         {:id section
          :dangerouslySetInnerHTML
          {:__html (page-content-html fields section)}}]))))

(defn page-view [{:keys [lang html] :as fields}]
  [:div.city-white-box.w--100.w--max-60em.gap--4
   (if (page-content-path (assoc fields :lang (case lang
                                                :fi :sv
                                                :sv :fi)))
     (language-select fields)
     [:div.pad--2])
   (logo)
   [:div.pad--6.page-content
    {:dangerouslySetInnerHTML
     {:__html html}}]])

(defn footer-link
  ([lang view url]
   [:a.btn.tertiary.footer
    {:href url}
    (i18n/localize lang (util/kw-path :a11y.footer view))])
  ([lang view]
   (footer-link lang view (format "/%s/%s" (name view) (name lang)))))

(defn footer [{:keys [lang view page-id logged-in?]}]
  [:footer.footer-bottom.flex--around.bg--violet.pad--h2.pad--v0.flex--gap05.flex--wrap
   (when logged-in?
     [:a.btn.tertiary.footer {:href "/"}
      (i18n/localize lang :requests)])
   (when-not (or (= :login view) logged-in?)
     (footer-link lang :login))
   (footer-link lang :help (i18n/localize lang :footer.report-issue.link-href))
   (when-not (= view :info)
     (footer-link lang :info))
   (when-not (= page-id "registry")
     (footer-link lang :registry (view-url :page lang "registry")))
   (footer-link lang :status (i18n/localize lang :a11y.footer.status-href))])

(defn view-title [{:keys [lang view]}]
  (when-let [k (case view
                 :page nil
                 (:login :register) :login
                 view)]
    [:div.dsp--flex.flex--center
     (->> k
          (util/kw-path :a11y.title)
          (i18n/localize lang)
          (conj [:h1.gap--b8]))]))

(defn form [{:keys [view anti-csrf] :as fields} contents]
  (letfn [(hidden
            ([field-name field-value]
             (when field-value
               [:input {:type  "hidden"
                        :name  (name field-name)
                        :value field-value}]))
            ([field-key]
             (hidden field-key (field-key fields))))]
    [:form.w--100.dsp--block
     {:method "POST"
      :action (case view
                :login "/login"
                :reset-password "/reset-password"
                nil)}
     (hidden :lang)
     (hidden :hide-next?)
     (hidden :view)
     (hidden :sent-email)
     (hidden :form-error)
     (hidden :__anti-forgery-token anti-csrf)
     [:div.dsp--flex.flex--align-center.flex--column
      contents]]))

(defn- password-visibility-toggle-script []
  (let [js-script (-> "scripts/togglePasswordVisibility.js"
                      io/resource
                      slurp)]
    [:script {:dangerouslySetInnerHTML {:__html js-script}}]))

(defn- page [{:keys [lang view] :as fields}]
  (letfn [(hidden
            ([field-name field-value]
             (when field-value
               [:input {:type  "hidden"
                        :name  (name field-name)
                        :value field-value}]))
            ([field-key]
             (hidden field-key (field-key fields))))]
    [:html.cityscape {:lang lang}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name    "description"
              :content "Lupapiste"}]
      [:meta {:name    "author"
              :content "Cloudpermit Oy -- https://cloudpermit.com/"}]
      [:meta {:name    "viewport"
              :content "width=device-width, initial-scale=1"}]
      [:link {:href "/lp-static/css/fonts.css"
              :rel  "stylesheet"
              :type "text/css"}]
      [:link {:id   "main-css"
              :href "/lp-static/css/main.css"
              :rel  "stylesheet"}]
      [:link {:id   "lupicons-css"
              :href "/lp-static/css/lupicons.css"
              :rel  "stylesheet"}]
      [:link {:rel   "icon"
              :href  "/lp-static/img/favicon-v2.png"
              :type  "image/png"
              :media "(prefers-color-scheme: light)"}]
      [:link {:rel   "icon"
              :href  "/lp-static/img/favicon-v2-white.png"
              :type  "image/png"
              :media "(prefers-color-scheme: dark)"}]
      [:title "Lupapiste"]]
     [:body.gap--0.cityscape
      (chatbot-hiccup)
      [:div.dsp--flex.flex--align-center.flex--column
       {:role :main}
       (view-title fields)
       (case view
         :login (form fields (login-view fields))
         :reset-password (form fields (reset-password-view fields))
         :info (info-view fields)
         :page (page-view fields))]
      (footer fields)
      (password-visibility-toggle-script)]]))

(defn page-response [fields]
  {:status  200
   :headers {"Content-Type"            "text/html; charset=utf-8"
             ;; form-action directive cannot be set here, because it prevents AD redirect from the login form POST
             "Content-Security-Policy" headers/csp-with-frame-ancestors}
   :body    (str "<!DOCTYPE html>\n"
                 (rum/render-static-markup (page fields)))})
