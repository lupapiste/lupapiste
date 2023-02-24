(ns lupapalvelu.web-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [ring.util.codec :as codec]
            [sade.env :as env]))

(apply-remote-minimal)

(defn init []
  (let [store (atom {})]
    [store {:cookie-store     (->cookie-store store)
            :follow-redirects false
            :throw-exceptions false}]))

(facts "redirect-after-login"
  (let [[store initial-params] (init)
        resp                   (http-get (str (server-address)
                                              "/app/fi/applicant?redirect-after-login=/foo/bar")
                                         initial-params)
        anti-csrf-token        (codec/url-decode (.getValue (@store "anti-csrf-token")))
        params                 (assoc-in initial-params [:headers "x-anti-forgery-token"] anti-csrf-token)]
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
  (let [[store initial-params] (init)
        resp                   (http-get (str (server-address)
                                              "/app/fi/applicant?redirect-after-login=/foo/bar&redirect-after-login=/foo/bar2")
                                         initial-params)
        anti-csrf-token        (codec/url-decode (.getValue (@store "anti-csrf-token")))
        params                 (assoc-in initial-params [:headers "x-anti-forgery-token"] anti-csrf-token)]
    (:status resp) => 302
    (:headers resp) => (contains {"Location" (str (server-address) (env/value :frontpage :fi))})

    (fact "Login succeeds"
      (let [{body :body :as login} (http-post (str (server-address) "/api/login")
                                              (assoc params
                                                     :form-params {:username "pena" :password "pena"}
                                                     :as :json))]
        body => ok?
        login => http200?))

    (fact "url is returned"
      (let [{body :body :as resp} (http-get (str (server-address)
                                                 "/api/query/redirect-after-login")
                                            (assoc params :as :json))]
        resp => http200?
        (:url body) => "foo/bar2"))

    (facts "Alive OK when logged in"
      (let [q                        #(http-get (str (server-address) %) (assoc params :as :json))
            {body :body :as alive}   (q "/api/alive")
            {body2 :body :as alive2} (q "/api/alive?bulletins=1")]
        body => ok?
        alive => http200?
        body2 => ok?
        alive2 => http200?))))

(fact "Alive vs. logged out"
  (let [[store initial-params] (init)
        _                      (http-get (str (server-address) "/app/fi/bulletins")
                                         initial-params)
        anti-csrf-token        (codec/url-decode (.getValue (@store "anti-csrf-token")))
        params                 (assoc-in initial-params [:headers "x-anti-forgery-token"] anti-csrf-token)
        q                      (fn [alive & p]
                                 (http-get (str (server-address) alive)
                                           (-> params
                                               (assoc :as :json)
                                               (assoc :query-params (apply hash-map p)))))]
    (fact "Plain alive fails"
      (let [alive (q "/api/alive")]
        (:body alive) => unauthorized?
        alive => http200?))

    (fact "Bulletins alive is OK"
      (let [alive1 (q "/api/alive?bulletins=hello")
            alive2 (q "/api/alive" :bulletins true)]
        (:body alive1) => ok?
        alive1 => http200?
        (:body alive2) => ok?
        alive2 => http200?))))
