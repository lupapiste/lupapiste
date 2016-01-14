(ns lupapalvelu.autologin-itest
  (:require [pandect.core :as pandect]
            [lupapalvelu.itest-util :refer :all]
            [sade.core :refer [now]]
            [sade.http :as http]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(defn resolve-public-ip! []
  (let [status (http-get (str (server-address) "/system/status") {:as :json})]
    (get-in status [:body :data :proxy-headers :data :x-real-ip] "127.0.0.1")))

(facts "Autologin"
  (let [email (email-for "pekka")
        ts (now)
        ip (resolve-public-ip!)
        hash (pandect/sha256-hmac (str email ip ts) "LUPAPISTE")
        password (str ts "_" hash)
        resp (http-get (str (server-address) "/api/query/user")
                       {:basic-auth [email password]
                        :cookies {"anti-csrf-token" {:value "my-token"}}
                        :headers {"x-anti-forgery-token" "my-token"}
                        :as :json})]

    (-> resp :body :user) => map?
    (-> resp :body :user :email) => email
    (-> resp :body :user :role) => "authority"
    (-> resp :body :user :firstName) => "Pekka"))
