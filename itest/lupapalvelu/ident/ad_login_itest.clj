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

(def- response
  (-> "./dev-resources/mock-saml-response.edn" slurp read-string :encrypted-base64))

(defn- break-string [s]
  (->> s seq shuffle (apply str)))

(when (env/feature? :ad-login)
  (mongo/connect!)
  (let [pori-route (parse-route "pori.fi")]

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
                body => "No SAML data found in request"))

          (fact "Login attempt with valid response works"
            (let [resp (client/post pori-route {:form-params {:SAMLResponse response}
                                                :content-type :json
                                                :throw-exceptions false})]
              (:status resp) => 302))

          (fact "Login attempt with invalid response fails"
            (let [resp (client/post pori-route {:form-params {:SAMLResponse (apply str (drop-last response))}
                                                :content-type :json
                                                :throw-exceptions false})]
              (:status resp) => 400))))))
