(ns lupapalvelu.web-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [clojure.walk :refer [keywordize-keys]]
            [sade.http :as http]
            [midje.sweet :refer :all]
            [cheshire.core :as json]))

(facts "hashbang functionality"
  (let [store (atom {})
        params {:cookie-store (->cookie-store store)
                :follow-redirects false
                :throw-exceptions false}
        resp (http/get (str (server-address) "/app/fi/applicant" "?hashbang=/foo/bar") params)]
    (:status resp) => 302
    (:headers resp) => (contains {"location" "/app/fi/welcome"})
    (let [resp (http/get (str (server-address) "/api/hashbang") params)]
      resp => http200?
      (get-in (decode-response resp) [:body :bang]) => "foo/bar")))
