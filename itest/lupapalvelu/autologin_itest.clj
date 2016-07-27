(ns lupapalvelu.autologin-itest
  (:require [pandect.core :as pandect]
            [lupapalvelu.itest-util :refer :all]
            [sade.core :refer [now]]
            [sade.http :as http]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(def my-public-ip
  (let [status (http-get (str (target-server-or-localhost-address) "/system/status") {:as :json})]
    (get-in status [:body :data :proxy-headers :data :x-real-ip] "127.0.0.1")))


(defn- password-hash [email & timestamp]
  (let [ts (or timestamp (now))]
    (str ts "_" (pandect/sha256-hmac (str email my-public-ip ts) "LUPAPISTE"))))

(fact "User query with autologin"
  (let [email (email-for "pekka")
        password (password-hash email)
        resp (http-get (str (target-server-or-localhost-address) "/api/query/user")
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
       password (password-hash email)
       url (str (target-server-or-localhost-address) "/app/fi/")
       resp (http-get url {:basic-auth [email password] :follow-redirects false})]
   (-> resp :headers (get "Location")) => #"/app/fi/authority"))

(fact "Invalid timestamp, autologin fails"
 (let [email (email-for "pekka")
       timestamp  (- (now) (* 1000 60 60 6))
       password (password-hash email timestamp)
       url (str (target-server-or-localhost-address) "/app/fi/")
       resp (http-get url {:basic-auth [email password] :follow-redirects false})]
   (-> resp :headers (get "Location")) => #"/app/fi/welcome"))

(fact "Sipoo does not have allowed IPs in fixture, autologin fails"
  (let [email (email-for "sonja")
       password (password-hash email)
       url (str (target-server-or-localhost-address) "/app/fi/")
       {:keys [status body]} (http-get url {:basic-auth [email password] :throw-exceptions false})]

   status => 403
   (count body) => (count "Illegal IP address")))

(fact "Unknown user, autologin fails"
  (let [email "whatever@example.com"
       password (password-hash email)
       url (str (target-server-or-localhost-address) "/app/fi/")
       {:keys [status body]} (http-get url {:basic-auth [email password] :throw-exceptions false})]

   status => 403
   (count body) => (count "User not enabled")))
