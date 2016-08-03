(ns lupapalvelu.web-itest
  (:require [midje.sweet :refer :all]
            [ring.util.codec :as codec]
            [lupapalvelu.itest-util :refer :all]
            [sade.env :as env]))

(apply-remote-minimal)

(facts "redirect-after-login"
  (let [store (atom {})
        initial-params {:cookie-store (->cookie-store store)
                        :follow-redirects false
                        :throw-exceptions false}
        resp (http-get (str (server-address) "/app/fi/applicant" "?redirect-after-login=/foo/bar") initial-params)
        anti-csrf-token (codec/url-decode (.getValue (@store "anti-csrf-token")))
        params (assoc-in initial-params [:headers "x-anti-forgery-token"] anti-csrf-token)]
    (:status resp) => 302
    (:headers resp) => (contains {"Location" (str (server-address) (env/value :frontpage :fi))})

    (fact "Login succeeds"
      (let [{body :body :as login} (http-post (str (server-address) "/api/login") (assoc params :form-params {:username "pena" :password "pena"} :as :json))]
       body => ok?
       login => http200?))

    (fact "url is returned"
      (let [{body :body :as resp} (http-get (str (server-address) "/api/query/redirect-after-login") (assoc params :as :json))]
        resp => http200?
        (:url body) => "foo/bar"))))

(facts "2x redirect-after-login"
  (let [store (atom {})
        initial-params {:cookie-store (->cookie-store store)
                        :follow-redirects false
                        :throw-exceptions false}
        resp (http-get (str (server-address) "/app/fi/applicant" "?redirect-after-login=/foo/bar&redirect-after-login=/foo/bar2") initial-params)
        anti-csrf-token (codec/url-decode (.getValue (@store "anti-csrf-token")))
        params (assoc-in initial-params [:headers "x-anti-forgery-token"] anti-csrf-token)]
    (:status resp) => 302
    (:headers resp) => (contains {"Location" (str (server-address) (env/value :frontpage :fi))})

    (fact "Login succeeds"
      (let [{body :body :as login} (http-post (str (server-address) "/api/login") (assoc params :form-params {:username "pena" :password "pena"} :as :json))]
        body => ok?
        login => http200?))

    (fact "url is returned"
      (let [{body :body :as resp} (http-get (str (server-address) "/api/query/redirect-after-login") (assoc params :as :json))]
        resp => http200?
        (:url body) => "foo/bar2"))))
