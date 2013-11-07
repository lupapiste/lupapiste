(ns lupapalvelu.csrf-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.http :as http]))

(fact "Valid apikey bypasses CSRF check"
  (with-anti-csrf
    (raw-query mikko :allowed-actions) => (contains {:status 200 :body (contains {:ok true})})))

(fact "CSRF check must not be bypassed if there is no apikey"
  (with-anti-csrf
    (invalid-csrf-token? (raw-query nil :allowed-actions)) => true))

(fact "Calling a non-protected resource returns a csrf token"
  (with-anti-csrf
    (let [resp (http/get (str (server-address) "/app/fi/welcome"))
          cookie (get-in resp [:cookies "anti-csrf-token"])]
      (:status resp) => 200
      cookie => truthy
      (:path cookie) => "/")))

(fact "Sending the cookie and a header passes CSRF protection"
  (with-anti-csrf
    (let [resp (http/get (str (server-address) "/api/query/allowed-actions")
                 {:cookies {"anti-csrf-token" {:value "my-token"}}
                  :headers {"x-anti-forgery-token" "my-token"}})]
      resp => (contains {:status 200}))))

(fact "Failing to send the header fails CSRF check"
  (with-anti-csrf
    (let [resp (http/get (str (server-address) "/api/query/allowed-actions")
                 {:cookies {"anti-csrf-token" {:value "my-token"}}
                  :query-params {:id 123}
                  :headers {"Referer" "http://attacker.example.com/"}
                  :follow-redirects false
                  :throw-exceptions false})]
      (invalid-csrf-token? (decode-response resp)) => true)))
