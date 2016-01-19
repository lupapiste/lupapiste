(ns lupapalvelu.autologin-itest
  (:require [pandect.core :as pandect]
            [lupapalvelu.itest-util :refer :all]
            [sade.core :refer [now]]
            [sade.http :as http]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(def my-public-ip
  (let [status (http-get (str (server-address) "/system/status") {:as :json})]
    (get-in status [:body :data :proxy-headers :data :x-real-ip] "127.0.0.1")))

(fact "User query with autologin"
  (let [email (email-for "pekka")
        ts (now)
        hash (pandect/sha256-hmac (str email my-public-ip ts) "LUPAPISTE")
        password (str ts "_" hash)
        resp (http-get (str (server-address) "/api/query/user")
                       {:basic-auth [email password]
                        :cookies {"anti-csrf-token" {:value "my-token"}}
                        :headers {"x-anti-forgery-token" "my-token"}
                        :as :json})]

    (-> resp :body) => ok?
    (-> resp :body :user) => map?
    (-> resp :body :user :email) => email
    (-> resp :body :user :role) => "authority"
    (-> resp :body :user :firstName) => "Pekka"))

(fact "Auto login to authority application front page"
 (let [email (email-for "pekka")
       ts (now)
       hash (pandect/sha256-hmac (str email my-public-ip ts) "LUPAPISTE")
       password (str ts "_" hash)
       url (str (server-address) "/app/fi/")
       resp (http-get url {:basic-auth [email password]})]

   (-> resp :trace-redirects last) => (str (server-address) "/app/fi/authority")))

(fact "Sipoo does not have allowed IPs in fixture, autologin fails"
  (let [email (email-for "sonja")
       ts (now)
       hash (pandect/sha256-hmac (str email my-public-ip ts) "LUPAPISTE")
       password (str ts "_" hash)
       url (str (server-address) "/app/fi/")
       resp (http-get url {:basic-auth [email password]})]

   (-> resp :trace-redirects last) => (str (server-address) "/app/fi/welcome")))
