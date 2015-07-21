(ns lupapalvelu.web-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [clojure.walk :refer [keywordize-keys]]
            [sade.http :as http]
            [sade.env :as env]
            [midje.sweet :refer :all]
            [cheshire.core :as json]))

(facts "redirect-after-login functionality"
  (let [store (atom {})
        initial-params {:cookie-store (->cookie-store store)
                        :follow-redirects false
                        :throw-exceptions false}
        resp (http/get (str (server-address) "/app/fi/applicant" "?redirect-after-login=/foo/bar") initial-params)
        anti-csrf-token (ring.util.codec/url-decode (.getValue (@store "anti-csrf-token")))
        params (assoc-in initial-params [:headers "x-anti-forgery-token"] anti-csrf-token)]
    (:status resp) => 302
    (:headers resp) => (contains {"Location" (str (server-address) (env/value :frontpage :fi))})

    (http/post (str (server-address) "/api/login") (assoc params :form-params {:username "pena" :password "pena"})) => http200?

    (let [resp (http/get (str (server-address) "/api/query/redirect-after-login") params)]
      resp => http200?
      (get-in (decode-response resp) [:body :url]) => "foo/bar")))
