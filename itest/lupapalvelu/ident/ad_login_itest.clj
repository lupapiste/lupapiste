(ns lupapalvelu.ident.ad-login-itest
  "Integration tests for AD-login."
  (:require [lupapalvelu.ident.ad-login :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [now]]
            [sade.env :as env]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

;; Create a test database

(def test-db
  (format "test_ad_login_itest_%s" (now)))

(when (env/feature? :ad-login)
  (mongo/connect!)
  (println "jee")
  (facts "test_db_name is respected"
         (mongo/drop-collection :testi)
         (mongo/with-db test-db
           (mongo/drop-collection :testi))))

;; Enter a test organization into the DB, with ad-login activated


;; Test the domain-specific routes (metadata route, login route with GET and POST)
;; Ensure that the signature validation fails if the cert is tampered with
