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

(def- response
  (-> "./dev-resources/mock-saml-response.edn" slurp read-string :encrypted-base64))

(def- response-wo-groups
  (-> "./dev-resources/mock-saml-response_without_groups.edn" slurp read-string :base64-encoded))

(defn- parse-route [domain & [metadata?]]
  (format "%s/api/saml/%s/%s" (server-address)
          (if metadata? "metadata" "ad-login")
          domain))

(apply-remote-minimal)

(mongo/connect!)

(let [pori-route (parse-route "pori.fi")]

  (fact "update-or-create-user! can create or update users"
        (let [user (ad-login/update-or-create-user! "Pedro" "Banana" "pedro@banana.fi" {})]
          (fact "User creation works as expected"
                (= user (usr/get-user-by-email "pedro@banana.fi")) => true)
          (facts "Updating users should work as well"
                 (let [updated-user (ad-login/update-or-create-user! "Pedro" "Banana" "pedro@banana.fi" {:609-R #{"reader"}})]
                   (= {:609-R ["reader"]} (:orgAuthz (usr/get-user-by-email "pedro@banana.fi"))) => true))))

  (fact "log-user-in! should... Log user in"
        (let [user (usr/get-user-by-email "pedro@banana.fi")
              {:keys [headers session status]} (ad-login/log-user-in! {} user)]
          (facts "User is redirected to authority landing page"
                 (= 302 status) => true
                 (= (str (env/value :host) "/app/fi/authority") (get headers "Location")) => true)
          (fact "User is inserted into the session"
                (= "pedro@banana.fi" (get-in session [:user :email])) => true)))

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

        (fact "Login attempt with a valid response works"
              (command admin :create-user
                       :email "terttu@panaani.fi"
                       :role "authority"
                       :orgAuthz {:609-R ["reader"]}) => ok?
              (let [resp (client/post pori-route {:form-params {:SAMLResponse response}
                                                  :content-type :json
                                                  :throw-exceptions false})]
                (:status resp) => 302))

        (fact "The user is created from the SAML data"
              (let [resp (query admin :users :email "terttu@panaani.fi")]
                resp => ok?
                (count (:users resp)) => 1))

        (fact "Now erase Terttu's account"
              (command admin :erase-user :email "terttu@panaani.fi") => ok?)

        (fact "Login attempt with an invalid response fails"
              (let [resp (client/post pori-route {:form-params {:SAMLResponse (apply str (drop-last response))}
                                                  :content-type :json
                                                  :throw-exceptions false})]
                (:status resp) => 400))

        (fact "When an applicant-type user account exists, the user is logged in even if no ad-groups are found"
              (let [resp (client/post pori-route {:form-params {:SAMLResponse response-wo-groups}
                                                  :content-type :json
                                                  :throw-exceptions false})]
                (:status resp) => 302))))
