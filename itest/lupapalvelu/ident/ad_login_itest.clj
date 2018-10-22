(ns lupapalvelu.ident.ad-login-itest
  "Integration tests for AD-login."
  (:require [lupapalvelu.ident.ad-login :refer :all]
            [lupapalvelu.ident.ad-login-util :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [noir.response :as resp]
            [reitit.ring :as reitit-ring]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.xml :as sxml]
            [midje.sweet :refer :all]))

(defn metadata-route [domain]
  (format "%s/api/saml/metadata/%s" (env/value :host) domain))

(defn saml-route [domain]
  (format "%s/api/saml/ad-login/%s" (env/value :host) domain))

(when (env/feature? :ad-login)
  (let [test-db (format "test_ad_login_itest_%s" (now))]
    (mongo/with-db test-db
      (fixture/apply-fixture "minimal")

      (fact "Pori-R has ad-login enabled"
        (-> (org/get-organizations-by-ad-domain "pori.fi") first (get-in [:ad-login :enabled])) => true)

      (facts "The metadata route works when ad-login is enabled"
        (let [res (client/get (metadata-route "pori.fi"))
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
              (= cert (parse-certificate (env/value :sso :cert))) => true)))))))



;; Enter a test organization into the DB, with ad-login activated


;; Test the domain-specific routes (metadata route, login route with GET and POST)
;; The minimal fixture contains the organization 609-R ("Pori rakennusvalvonta") that has
;; the ad-login settings enabled.



;; Ensure that the signature validation fails if the cert is tampered with
