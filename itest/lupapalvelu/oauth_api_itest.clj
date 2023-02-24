(ns lupapalvelu.oauth-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.test-util :refer [in-text not-in-text]]
            [midje.sweet :refer :all]
            [sade.http :as http]
            [ring.util.codec :refer [form-decode]]
            [sade.strings :as ss]
            [saml20-clj.shared :refer [str->base64]]))


(apply-remote-minimal)

(def token-endpoint (str (server-address) "/oauth/token"))

(def cookie-store (atom {}))

(def client-id "oauth-test")
(def client-secret "oauth-test-secret")

(defn- oauth-req [endpoint method params kvs]
  (http method
        (str (server-address) "/oauth/" (name endpoint))
        (merge {:throw-exceptions false
                :follow-redirects false
                :cookie-store     (->cookie-store cookie-store)}
               {(if (= method http/get)
                  :query-params :form-params) (merge params
                                                     (apply hash-map kvs))})))

(defn- authorize-call [params & kvs]
  (oauth-req :authorize http/get params kvs))

(defn- language-call [lang params & kvs]
  (oauth-req (str "language/" (name lang)) http/post params kvs))

(defn- login-call [username password params & kvs]
  (oauth-req :login http/post
             (assoc params
                    :username username
                    :password password)
             kvs))

(defn- logout-call [params & kvs]
  (oauth-req :logout http/post params kvs))

(defn- consent-call [params & kvs]
  (oauth-req :consent http/post params kvs))

(defn- cancel-call [params & kvs]
  (oauth-req :cancel http/post params kvs))

(def kaino-data {:company   {:id   "solita"
                             :name "Solita Oy"}
                 :email     "kaino@solita.fi"
                 :id        kaino-id
                 :firstName "Kaino"
                 :lastName  "Solita"
                 :role      "applicant"})

(def base-params {:client_id        client-id
                  :lang             "fi"
                  :success_callback "/success"
                  :error_callback   "/failure"
                  :cancel_callback  "/cancel"})

(def good-params (assoc base-params
                        :response_type "code"
                        :scope "read"))

(defn rest-user [token]
  (http-get (str (server-address) "/rest/user")
            {:headers          {"authorization" (str "Bearer " token)}
             :throw-exceptions false}))

(defn refresh-call [refresh-token]
  (http-post token-endpoint {:form-params      {:client_id     client-id
                                                :client_secret client-secret
                                                :grant_type    "refresh_token"
                                                :refresh_token refresh-token}
                             :throw-exceptions false}))


(defn in-html [response & xs]
  (let [{:keys [status headers body]} response]
    (fact "Status OK"
      status => 200)
    (fact "HTML"
      (get headers "Content-Type") => (contains "text/html"))
    (apply in-text body xs)))

(defn is-error
  ([response & errors]
   (let [{:keys [status body]} response]
    (fact "Bad request"
      status => 400)
    (apply in-text body (cons "ERROR" errors)))))

(defn bad-csrf? [response]
  (is-error response "anti-csrf-check-failed!")
  true)

(defn is-success [response type]
  (let [{:keys [status headers]
         }      response
        type    (name type)
        url     (get headers "Location")
        pattern (if (= type "code")
                  #"^http://localhost:8000/dev/oauth-test/success\?code=\w+$"
                  #"^http://localhost:8000/dev/oauth-test/success#token=\w+$")]
    (fact {:midje/description (str "Success: " type)}
          status => 302
          url => pattern)))

(defn is-canceled
  ([response error]
   (let [{:keys [status headers]
          }      response
         url     (get headers "Location")
         location (if error
                    (str "http://localhost:8000/dev/oauth-test/failure?error=" error)
                    "http://localhost:8000/dev/oauth-test/cancel")]
     (fact "Canceled"
       status => 302
       url => location)))
  ([response]
   (is-canceled response nil)))

(def saml-response (->> (slurp "dev-resources/saml/response-with-groups.xml")
                        ss/trim
                        str->base64))

(defn ad-login-call [login-response]
  (let [relay-state (-> login-response
                        (get-in [:headers "Location"])
                        form-decode
                        (get "RelayState"))]
    (http-post (str (server-address) "/api/saml/ad-login/pori.fi")
               {:form-params      {:SAMLResponse saml-response
                                   :RelayState   relay-state}
                :content-type     :json
                :cookie-store     (->cookie-store cookie-store)
                :throw-exceptions false})))

(defn toggle-oauth-registration [enabled?]
  (fact {:midje/description (str "OAuth Test registration -> " enabled?)}
    (http-get (str (server-address) "/dev/oauth-test-toggle")
              {:query-params      {:registration enabled?}
               :cookie-store     (->cookie-store cookie-store)
               :follow-redirects false
               :throw-exceptions false})
    => http302?))

(facts "OAuth API"

  (fact "Authorize, not logged in"
    (in-html (authorize-call good-params)
             "oauth-username" "oauth-password"
             "oauth-registration" "/app/fi/welcome#!/register"))

  (facts "Non-corporate user cannot pay"
    (fact "Pena not logged in"
      (in-html (login-call "pena" "pena" good-params
                           :scope "pay")
               ["Pena Panaani" "pena@example.com"]
               "oauth.warning.company-pay-only") "(suomi)")
    (fact "Language can be changed"
      (in-html (language-call :sv good-params)
               "oauth-username" "oauth-password" "(svenska)"
               "/app/sv/welcome#!/register"
               ["(suomi)"]))

    (fact "Unsupported language"
      (is-error (language-call :cn good-params)
                "lang"))

      (fact "Registration possibility depends on mongo"
        (toggle-oauth-registration false)
        (in-html (authorize-call good-params)
                 "oauth-username" "oauth-password"
                 ["oauth-registration" "/app/fi/welcome#!/register"]))

    (fact "Pena logged in"
      (login "pena" "pena"
             {:cookie-store (->cookie-store cookie-store)})
      (in-html (authorize-call good-params :scope "pay")
               "Pena Panaani" "pena@example.com"
               "oauth.warning.company-pay-only")))

  (fact "Non-corporate user can use read scope"
    (in-html (authorize-call good-params)
             "Pena Panaani" "pena@example.com"
             ["oauth-username" "oauth-password"]))

  (fact "Session is active"
    (let [cookie (get @cookie-store "lupapiste-login")]
      (.getValue cookie) =not=> "delete"
      (.isExpired cookie (java.util.Date.)) => false))

  (fact "Pena logs out"
    (in-html (logout-call good-params)
             ["Pena Panaani" "pena@example.com"]
             "oauth-username" "oauth-password"))

  (fact "Session has been nuked"
    (let [cookie (get @cookie-store "lupapiste-login")]
      (.getValue cookie) => "delete"
      (.isExpired cookie (java.util.Date.)) => true))

  (fact "Logout does not crash, if the user is not logged in"
    (reset! cookie-store {})
    (in-html (logout-call good-params)
             ["Pena Panaani" "pena@example.com"]
             "oauth-username" "oauth-password"))

  (fact "Corporate user can also use pay scope"
    (reset! cookie-store {})
    (fact "Kaino logged in"
      (login "kaino@solita.fi" "kaino123"
             {:cookie-store (->cookie-store cookie-store)})
      (in-html (authorize-call good-params :scope "pay")
               ["oauth.warning.company-pay-only"]
               "Kaino Solita" "kaino@solita.fi")))

  (fact "Kaino consents"
    (fact "Not remembered yet"
      (in-html (authorize-call good-params :scope "pay")
               ["oauth.warning.company-pay-only"]
               "Kaino Solita" "kaino@solita.fi"))
    (fact "Consent"
      (is-success (consent-call good-params :scope "pay")
                  :code)))

  (fact "Kaino's consent is remembered"
    (is-success (authorize-call good-params
                                :scope "pay"
                                :response_type "token")
                :token))

  (reset! cookie-store {})

  (fact "However, consent is scoped. New scope requires new consent"
    (in-html (authorize-call good-params)
             ["Kaino Solita" "kaino@solita.fi"]
             "oauth-username" "oauth-password")
    (is-success (login-call "kaino@solita.fi" "kaino123"
                            good-params)
                :code))

  (fact "Kaino logs in properly"
    (login "kaino@solita.fi" "kaino123"
           {:cookie-store (->cookie-store cookie-store)}))

  (fact "Both consent scopes are remembered"
    (is-success (authorize-call good-params :scope "read,pay")
                :code))

  (facts "Invalid parameters"
    (is-error (authorize-call {})
              "missing-required-key")
    (is-error (authorize-call good-params :scope "bad")
              "scope-vec" "Must have scopes")
    (is-error (authorize-call good-params :success_callback "foobar")
              "success_callback" "Path must start with /")
    (is-error (authorize-call good-params :error_callback "foobar")
              "error_callback" "Path must start with /")
    (is-error (authorize-call good-params :cancel_callback "foobar")
              "cancel_callback" "Path must start with /")
    (is-error (authorize-call good-params :response_type "bad")
              "response_type")
    (is-error (authorize-call good-params :client_id "bad")
              "client")
    (is-error (authorize-call good-params :lang "bad")
              "lang"))

  (facts "Cancel"
    (fact "Cancel callback"
      (is-canceled (cancel-call good-params)))
    (fact "Error endpoint as a cancel fallback"
      (is-canceled (cancel-call (dissoc good-params :cancel_callback))
                   "authorization_cancelled")))


  (facts "about accepted authorization"
    (let [res                       (authorize-call good-params
                                                    :scope "read,pay")
          code                      (last (re-find #"code=([A-Za-z0-9]+)"
                                                   (get-in res [:headers "Location"])))
          token-params              {:query-params     {:client_id     client-id
                                                        :client_secret client-secret
                                                        :grant_type    "authorization_code"
                                                        :code          code}
                                     :throw-exceptions false}
          token-res                 (http-post token-endpoint token-params)
          {access-token :access_token refresh-token :refresh_token
           :as          token-info} (-> token-res :body (json/decode true))]

      (fact "Authorization leads to redirection with a code"
        (:status res) => 302
        (get-in res [:headers "Location"])
        => (str "http://localhost:8000/dev/oauth-test/success?code=" code)
        code => truthy)

      (fact "Code can be exchanged for a token"
        token-res => http200?
        token-info = (just {:access_token  ss/not-blank?
                            :refresh_token ss/not-blank?
                            :expires_in    pos?
                            :token_type    "bearer"}))

      (fact "Code cannot be exchanged to a second token"
        (http-post token-endpoint token-params) => http401?)

      (fact "Token can be used to access REST endpoint"
        (let [user-res (rest-user access-token)]
          user-res => http200?
          (json/decode (:body user-res) true) => kaino-data))

      (facts "Refresh token"
        (let [refresh-res                  (refresh-call refresh-token)
              {access-token2 :access_token refresh-token2 :refresh_token
               :as           refresh-info} (-> refresh-res :body (json/decode true))]
          (fact "New tokens"
            refresh-res => http200?
            refresh-info => (just {:access_token  #(and (ss/not-blank? %) (not= % access-token))
                                   :refresh_token #(and (ss/not-blank? %) (not= % refresh-token))
                                   :expires_in    (:expires_in token-info)
                                   :token_type    "bearer"}))
          (fact "Refresh token can be used only once"
            (http-post token-endpoint {:form-params      {:client_id     client-id
                                                          :client_secret client-secret
                                                          :grant_type    "refresh_token"
                                                          :refresh_token refresh-token}
                                       :throw-exceptions false})
            => http401?)
          (fact "New access token works"
            (rest-user access-token2) => http200?)
          (fact ".. as does the old one (until expiration)"
            (rest-user access-token) => http200?)
          (fact "Access token cannot be used for refreshing"
            (refresh-call access-token2) => http401?)
          (fact "New refresh token works"
            (let [refresh-res2                   (refresh-call refresh-token2)
                  {refresh-token3 :refresh_token
                   :as            refresh-info2} (-> refresh-res2 :body (json/decode true))]
              refresh-res2 => http200?
              refresh-info2 => (just {:access_token  ss/not-blank?
                                      :refresh_token ss/not-blank?
                                      :expires_in    pos?
                                      :token_type    "bearer"})
              (fact "Invalid use of refresh token can invalidate it (auto-consome)"
                (rest-user refresh-token3) => http401?
                (refresh-call refresh-token3) => http401?)))))

      (fact "Params validation"
        (letfn [(token-post [& params]
                  (http-post token-endpoint
                             {:form-params      (merge {:client_id     "client-id"
                                                        :client_secret "secret"
                                                        :grant_type    "authorization_code"
                                                        :code          "code"
                                                        :refresh_token "refresh"}
                                                       (apply hash-map params))
                              :throw-exceptions false}))]
          (fact "Fully formed with bad data"
            (token-post)
            => (contains {:status 401
                          :body   "Invalid client credentials or authorization code"})
            (token-post :grant_type "refresh_token")
            => (contains {:status 401
                          :body   "Refresh token failed."}))
          (fact "Bad grant type"
            (token-post :grant_type "cary_grant")
            => (contains {:status 400
                          :body   "Unknown grant type"}))
          (facts "None of the required params for token endpoint can be blank"
            (doseq [field [:client_id :client_secret :code]]
              (fact {:midje/description (name field)}
                (token-post field " ") => http400?))
            (doseq [field [:client_id :client_secret :refresh_token]]
              (fact {:midje/description (name field)}
                (token-post :grant_type "refresh_token" field " ") => http400?)))))))

  (fact "Implicit flow provides the token straight away"
    (let [res      (authorize-call good-params
                                   :scope "read,pay"
                                   :response_type "token")
          token    (last (re-find #"#token=([A-Za-z0-9]+)" (get-in res [:headers "Location"])))
          user-res (http-get (str (server-address) "/rest/user") {:headers          {"authorization" (str "Bearer " token)}
                                                                  :throw-exceptions false})]
      user-res => http200?
      (json/decode (:body user-res) true) => kaino-data))

  (fact "Token cannot be used to access \"normal\" REST endpoints"
    (reset! cookie-store {})
    (login "docstore" "basicauth" {:cookie-store (->cookie-store cookie-store)})
    (let [res          (consent-call good-params)
          code         (last (re-find #"code=([A-Za-z0-9]+)" (get-in res [:headers "Location"])))
          token-params {:query-params     {:client_id     client-id
                                           :client_secret client-secret
                                           :grant_type    "authorization_code"
                                           :code          code}
                        :throw-exceptions false}
          token-res    (http-post token-endpoint token-params)
          access-token (-> (json/decode (:body token-res) true)
                           :access_token)
          rest-res     (http-get (str (server-address) "/rest/docstore/organizations")
                                 {:headers          {"authorization" (str "Bearer " access-token)}
                                  :throw-exceptions false})]
      rest-res => http401?))

  (fact "Authority user has organization info"
    (reset! cookie-store {})
    (login "sonja" "sonja" {:cookie-store (->cookie-store cookie-store)})
    (let [res (authorize-call good-params)]
      res => http200?
      (let [res      (consent-call good-params
                                   :response_type "token")
            token    (last (re-find #"#token=([A-Za-z0-9]+)" (get-in res [:headers "Location"])))
            user-res (http-get (str (server-address) "/rest/user")
                               {:headers          {"authorization" (str "Bearer " token)}
                                :throw-exceptions false})]
        user-res => http200?
        (json/decode (:body user-res) true)
        => {:email         "sonja.sibbo@sipoo.fi"
            :firstName     "Sonja"
            :lastName      "Sibbo"
            :id            sonja-id
            :organizations [{:id "753-R" :roles ["authority" "approver"]}
                            {:id "753-YA" :roles ["authority" "approver"]}
                            {:id "998-R-TESTI-2" :roles ["authority" "approver"]}]
            :role          "authority"})))


  (facts "OAuth consent via AD login"
    (fact "Redirect to IdP"
      (reset! cookie-store {})
      (let [{:keys [status headers]
             :as   response} (login-call "hii@pori.fi" "" good-params)]
        status => 302
        (get headers "Location") => (contains "SAMLRequest")
        (fact "AD login: success"
          (is-success (ad-login-call response)
                      :code))))
    (fact "AD login: no pay scope"
      (in-html (ad-login-call (login-call "juu@pori.fi" nil
                                          (assoc good-params
                                                 :domain "pori.fi"
                                                 :scope "pay")))
               "terttu.panaani@pori.fi" "oauth-username" "oauth-password"
               "oauth.warning.company-pay-only")))

  (facts "OAuth roles"
    (reset! cookie-store {})
    (letfn [(login-user [username password]
              (let [res   (login-call username password
                                      (assoc good-params :response_type "token"))
                    token (last (re-find #"#token=([A-Za-z0-9]+)"
                                         (get-in res [:headers "Location"])))]
                (-> (rest-user token) :body (json/decode true))))]
      (fact "Luukas is admin for oauth-test"
        (login-user "luukas" "luukas")
        => {:id            "777777777777777777000025"
            :email         "luukas.lukija@sipoo.fi"
            :firstName     "Luukas"
            :lastName      "Lukija"
            :organizations [{:id "753-R" :roles ["reader"]}]
            :role          "admin"})
      (fact "Kosti is admin in (non-existing) foobar but not for oauth-test"
        (login-user "kosti" "kosti")
        => {:id            "777777777777777777000026"
            :email         "kosti.kommentoija@sipoo.fi"
            :firstName     "Kosti"
            :lastName      "Kommentoija"
            :organizations [{:id "753-R" :roles ["commenter"]}]
            :role          "authority"}))))
