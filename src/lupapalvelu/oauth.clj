(ns lupapalvelu.oauth
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.company :as company]
            [sade.strings :as str]
            [lupapalvelu.token :as token]
            [sade.http :as http]
            [lupapalvelu.user :as usr]
            [lupapalvelu.templates.base :as bt]
            [lupapalvelu.templates.header :as ht]))

(defn authorization-page-hiccup [client scope lang user response-type anti-csrf]
  (let [company-id (get-in user [:company :id])
        company (when company-id (company/find-company-by-id company-id))
        user-name (str (:firstName user) " " (:lastName user))
        scopes (str/split scope #",")]
    (bt/page-hiccup
      (ht/simple-header lang)
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
         [:input {:type "hidden" :name "response_type" :value response-type}]
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

(defn grant-access-token [client scope user & [expires-in]]
  (token/make-token :oauth-access
                    user
                    {:client-id (get-in client [:oauth :client-id])
                     :scopes (if (string? scope)
                               (str/split scope #",")
                               scope)}
                    :ttl
                    (or expires-in (* 10 60 1000))
                    :auto-consume
                    false))

(defn grant-authorization-code [client scope user]
  (token/make-token :oauth-code
                    user
                    {:client-id (get-in client [:oauth :client-id])
                     :scopes (if (string? scope)
                               (str/split scope #",")
                               scope)}
                    :ttl
                    (* 60 1000)
                    :auto-consume
                    true))

(defn access-token-response [client-id client-secret code]
  (when-let [client (usr/get-user {:oauth.client-id client-id
                                   :oauth.client-secret client-secret})]
    (let [{:keys [token-type user-id data]} (token/get-token code)]
      (when (and (= token-type :oauth-code)
                 (= (:client-id data) client-id))
        (let [expires-in (* 10 60 1000)
              token (grant-access-token client (:scopes data) {:id user-id} expires-in)]
          {:access_token token
           :token_type "bearer"
           :expires_in expires-in
           :scope (str/join "," (:scopes data))})))))

(defn user-for-access-token [request]
  (when-let [token-id (http/parse-bearer request)]
    (when-let [{:keys [token-type user-id data]} (token/get-token token-id)]
      (when (= token-type :oauth-access)
        (when-let [user (usr/get-user-by-id user-id)]
          (assoc user :scopes (map keyword (:scopes data))))))))
