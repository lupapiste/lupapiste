(ns lupapalvelu.ident.ad-login-itest
  "Integration tests for AD-login."
  (:require [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.ident.ad-login-util :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [monger.operators :refer [$set]]
            [noir.response :as resp]
            [sade.core :refer [def- now]]
            [sade.env :as env]
            [sade.xml :as sxml]
            [sade.strings :as ss]
            [midje.sweet :refer :all]))

(defn- parse-route [domain & [metadata?]]
  (format "%s/api/saml/%s/%s" (env/value :host)
          (if metadata? "metadata" "ad-login")
          domain))

(def- test-ad-config
  {:app-name "Lupapiste"
   :private-key (slurp "./dev-resources/test-private-key.pem")
   :sp-cert (slurp "./dev-resources/test-public-cert.pem")})

(def- response
  (-> "./dev-resources/mock-saml-response.edn" slurp read-string :encrypted-base64))

(defn- break-string [s]
  (->> s seq shuffle (apply str)))

(when (env/feature? :ad-login)
  (mongo/connect!)
  (let [test-db (format "test_ad_login_itest_%s" (now))
        pori-route (parse-route "pori.fi")
        {:keys [private-key sp-cert]} test-ad-config]
    (mongo/with-db test-db
      (fixture/apply-fixture "minimal")

      (fact "Pori-R has ad-login enabled"
        (-> (org/get-organizations-by-ad-domain "pori.fi") first (get-in [:ad-login :enabled])) => true)

      (fact "update-or-create-user! can create or update users"
        (let [user (ad-login/update-or-create-user! "Terttu" "Panaani" "terttu@panaani.fi" {})]
          (fact "User creation works as expected"
            (= user (usr/get-user-by-email "terttu@panaani.fi")) => true)
          (facts "Updating users should work as well"
            (let [updated-user (ad-login/update-or-create-user! "Terttu" "Panaani" "terttu@panaani.fi" {:609-R #{"reader"}})]
              (= {:609-R ["reader"]} (:orgAuthz (usr/get-user-by-email "terttu@panaani.fi"))) => true))))

      (fact "log-user-in! should... Log user in"
        (let [user (usr/get-user-by-email "terttu@panaani.fi")
              {:keys [headers session status]} (ad-login/log-user-in! {} user)]
          (facts "User is redirected to authority landing page"
            (= 302 status) => true
            (= (str (env/value :host) "/app/fi/authority") (get headers "Location")) => true)
          (fact "User is inserted into the session"
            (= "terttu@panaani.fi" (get-in session [:user :email])) => true)))

      (fact "The metadata route works"
        (let [res (client/get (parse-route "pori.fi" true))
              body (-> res :body sxml/parse sxml/xml->edn)
              cert (-> body
                       (get-in [:md:EntityDescriptor :md:SPSSODescriptor :md:KeyDescriptor])
                       first
                       (get-in [:ds:KeyInfo :ds:X509Data :ds:X509Certificate]))]
          (fact "Status code is 200"
            (:status res) => 200
          (fact "Content-Type is XML"
            (-> res :headers keywordize-keys :Content-Type) => "text/xml; charset=UTF-8")
          (facts "The response contains the Service Provider certificate"
            (string? cert) => true
            (count cert) => 1224
            (= cert (env/value :sso :cert)) => false
            (= cert (parse-certificate (env/value :sso :cert))) => true))))

      (fact "Testing the main login route"
        (fact "If the endpoint receives POST request without :SAMLResponse map, it returns 400"
          (let [{:keys [status body]} (client/post pori-route {:throw-exceptions false})]
            (facts "Server answers with 400 and an error message in the response body"
              status => 400
              body => "No SAML data found in request")))

        (fact "GET requests to SAML paths of organizations with ad-login activated result to a successful redirect" ;; EI TOIMI
          (let [res (client/get pori-route)]
            (-> pori-route client/get :body) => "Redirect succeeded"
            (provided
              (saml20-clj.sp/get-idp-redirect anything anything anything) => {:status 302 :body "Redirect succeeded"})))

        ; (fact "GET requests to SAML paths of organizations with no ad-login settings should not work" ;; EI TOIMI
        ;   (let [res (client/get (parse-route "torttu paikka"))]
        ;     (:body res) => "No AD-settings active"))

        (fact "Login attempt with invalid SAMLResponse fails"
          (let [res (client/post pori-route {:form-params {:SAMLResponse {:kikka "kukka"}} :throw-exceptions false})]
            (:body res) => "Parsing SAML response failed")))

      ; (with-redefs [ad-login/log-user-in! (constantly {:status 302 :body "Login successful!"})]
      ;   (fact "Login attempt with valid response works" ;; EI TOIMI
      ;     (let [resp (client/post pori-route {:form-params {:SAMLResponse response}
      ;                                         :content-type :json
      ;                                         :throw-exceptions false})]
      ;       (:body resp) => "Login successful!"))

        (fact "Login attempt with broken response will not work"
          (let [resp (client/post pori-route {:form-params {:SAMLResponse (->> response drop-last (apply str))}
                                              :content-type :json
                                              :throw-exceptions false})]
            (:body resp) => "Parsing SAML response failed"))

      (with-redefs [ad-login/ad-config (assoc test-ad-config :private-key (break-string private-key))]
        (fact "Deciphering the response fails if the key is invalid" ;; EI TOIMI
          (let [resp (client/post pori-route {:form-params {:SAMLResponse response} :throw-exceptions false})]
            (:body resp) => "Parsing SAML response failed")))

      #_(let [ad-settings (-> (org/get-organizations-by-ad-domain "pori.fi") first)
            idp-cert (get-in ad-settings [:ad-login :idp-cert])
            broken-ad-settings (-> ad-settings
                                   (assoc-in [:ad-login :idp-cert] (break-string idp-cert))
                                   list)]
        (with-redefs [org/get-organizations-by-ad-domain (constantly broken-ad-settings)
                      resp/redirect (constantly {:body "Login failed"})]
          (fact "Response parsing fails if the IdP certificate has been messed up" ;; EI TOIMI
            (let [resp (client/post pori-route {:form-params {:SAMLResponse response} :throw-exceptions false})]
              (:body resp) => "Login failed"))))))

  #_(with-redefs [resp/redirect (constantly {:status 302 :body "No AD-settings active"})]
    (fact "Ad-login as Pori will not work if we disable their ad-login" ;; EI TOIMI
      (do
        (org/update-organization "609-R" {$set {:ad-login.enabled false}})
        (-> (parse-route "pori.fi") client/get :body) => "No AD-settings active"
        (org/update-organization "609-R" {$set {:ad-login.enabled true}})))))
