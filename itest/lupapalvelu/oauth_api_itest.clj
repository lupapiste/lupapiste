(ns lupapalvelu.oauth-api-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [sade.strings :as str]
            [sade.http :as http]
            [lupapalvelu.json :as json]))


(apply-remote-minimal)

(def authorize-endpoint (str (server-address) "/oauth/authorize"))

(def token-endpoint (str (server-address) "/oauth/token"))

(def cookie-store (atom {}))

(def client-id "docstore")

(defn- authorize-req [method params]
  (http method authorize-endpoint (merge params {:throw-exceptions false
                                                 :follow-redirects false
                                                 :cookie-store     (->cookie-store cookie-store)})))

(defn- authorize-call [params]
  (authorize-req http/get params))

(def kaino-data {:company {:id "solita"
                           :name "Solita Oy"}
                 :email "kaino@solita.fi"
                 :firstName "Kaino"
                 :lastName "Solita"
                 :role "applicant"})

(def base-params {:client_id client-id :lang "fi" :success_callback "/success" :error_callback "/failure"})

(facts "about OAuth authorization endpoints"

  (set-anti-csrf! true)

  (fact "Request without logging in redirects to login"
    (let [res (authorize-call {})]
      res => http302?
      (str/contains? (get-in res [:headers "Location"]) "/app/fi/welcome") => truthy))

  (fact "Non-corporate user cannot pay"
    (login "pena" "pena" {:cookie-store (->cookie-store cookie-store)})
    (let [res (authorize-call {:query-params (merge base-params {:scope "read,pay" :response_type "code"})})]
      (:status res) => 307
      (get-in res [:headers "Location"]) => "http://localhost:8000/failure?error=cannot_pay"))

  (fact "Non-corporate user can use read scope - HTML is returned"
    (let [res (authorize-call {:query-params (merge base-params {:scope "read" :response_type "code"})})]
      res => http200?
      (get-in res [:headers "Content-Type"]) => "text/html; charset=UTF-8"
      (str/contains? (:body res) "Pena Panaani") => truthy))

  (fact "Corporate user can also use pay scope - HTML is returned"
    (reset! cookie-store {})
    (login "kaino@solita.fi" "kaino123" {:cookie-store (->cookie-store cookie-store)})
    (let [res (authorize-call {:query-params (merge base-params {:scope "read,pay" :response_type "code"})})]
      res => http200?
      (get-in res [:headers "Content-Type"]) => "text/html; charset=UTF-8"
      (str/contains? (:body res) "Kaino Solita") => truthy))

  (facts "about accepted authorization"
    (let [res (authorize-req http/post {:form-params (merge base-params
                                                            {:scope "read,pay"
                                                             :response_type "code"
                                                             :accept "true"
                                                             :__anti-forgery-token (get-anti-csrf cookie-store)})})
          code (last (re-find #"code=([A-Za-z0-9]+)" (get-in res [:headers "Location"])))
          token-params {:query-params {:client_id client-id
                                       :client_secret client-id
                                       :grant_type "authorization_code"
                                       :code code}
                        :throw-exceptions false}
          token-res (http-post token-endpoint token-params)
          access-token (-> (json/decode (:body token-res) keyword)
                           :access_token)]
      (fact "Authorization leads to redirection with a code"
        (:status res) => 307
        (get-in res [:headers "Location"]) => (str "http://localhost:8000/success?code=" code)
        code => truthy)

      (fact "Code can be exchanged for a token"
        token-res => http200?
        (str/blank? access-token) => falsey)

      (fact "Code cannot be exchanged to a second token"
        (http-post token-endpoint token-params) => http401?)

      (fact "Token can be used to access REST endpoint"
        (let [user-res (http-get (str (server-address) "/rest/user") {:headers {"authorization" (str "Bearer " access-token)}
                                                                      :throw-exceptions false})]
          user-res => http200?
          (json/decode (:body user-res) keyword) => kaino-data))))

  (fact "Implicit flow provides the token straight away"
    (let [res (authorize-req http/post {:form-params (merge base-params
                                                            {:scope "read,pay"
                                                             :response_type "token"
                                                             :accept "true"
                                                             :__anti-forgery-token (get-anti-csrf cookie-store)})})
          token (last (re-find #"#token=([A-Za-z0-9]+)" (get-in res [:headers "Location"])))
          user-res (http-get (str (server-address) "/rest/user") {:headers {"authorization" (str "Bearer " token)}
                                                                  :throw-exceptions false})]
      user-res => http200?
      (json/decode (:body user-res) keyword) => kaino-data))

  (fact "Token cannot be used to access \"normal\" REST endpoints"
    (reset! cookie-store {})
    (login "docstore" "basicauth" {:cookie-store (->cookie-store cookie-store)})
    (let [res (authorize-req http/post {:form-params (merge base-params
                                                            {:scope "read"
                                                             :response_type "code"
                                                             :accept "true"
                                                             :__anti-forgery-token (get-anti-csrf cookie-store)})})
          code (last (re-find #"code=([A-Za-z0-9]+)" (get-in res [:headers "Location"])))
          token-params {:query-params {:client_id client-id
                                       :client_secret client-id
                                       :grant_type "authorization_code"
                                       :code code}
                        :throw-exceptions false}
          token-res (http-post token-endpoint token-params)
          access-token (-> (json/decode (:body token-res) keyword)
                           :access_token)
          rest-res (http-get (str (server-address) "/rest/docstore/organizations") {:headers {"authorization" (str "Bearer " access-token)}
                                                                                   :throw-exceptions false})]
      rest-res => http401?)))
