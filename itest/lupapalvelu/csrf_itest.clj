(ns lupapalvelu.csrf-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [clj-http.client :as c]))

(fact "Valid apikey bypasses CSRF check"
      (success (query mikko :allowed-actions)) => true)

(fact "CSRF check must not be bypassed if there is no apikey"
      (invalid-csrf-token (query nil :allowed-actions)) => true)

(fact "Calling a non-protected resource returns a csrf token"
      (let [resp (c/get (str (server-address) "/"))
            cookie (get-in resp [:cookies "lupapiste-token"])]
        (:status resp) => 200
        cookie => truthy
        (:path cookie) => "/"))

(fact "Sending the cookie and a header passes CSRF protection"
      (let [resp (c/get (str (server-address) "/api/query/allowed-actions")
                        {:cookies {"lupapiste-token" {:value "my-token"}}
                         :headers {"x-anti-forgery-token" "my-token"}})]
        (success (decode-response resp)) => true))

(fact "Failing to send the header fails CSRF check"
      (let [resp (c/get (str (server-address) "/api/query/allowed-actions")
                        {:cookies {"lupapiste-token" {:value "my-token"}}
                         :query-params {:id 123}
                         :headers {"Referer" "http://attacker.example.com/"}})]
        (invalid-csrf-token (decode-response resp)) => true))
