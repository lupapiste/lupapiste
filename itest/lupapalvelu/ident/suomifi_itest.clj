(ns lupapalvelu.ident.suomifi-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]))

(apply-remote-minimal)

(defn saml-get [path params]
  (http-get (str (server-address) "/api/saml/" path)
            {:query-params     params
             :throw-exceptions false}))

(defn good-login
  "A successul login call results in 404 Not found, since the redirects cannot be disabled
  in the testing environment and the actual login site cannot be accessed. Returns trid
  that is parsed from the redirect."
  [params]
  (let [response (saml-get "login" params)
        trid (-> response :trace-redirects last
                 (ss/split #"/") last)]
    (fact "404 is good"
      response => http404?)
    (fact "Trid is found"
      trid => ss/not-blank?)
    trid))

(defn cancel-url [trid cancel-path]
  (let [url (str (server-address) cancel-path)]
    (fact {:midje/description (str "Cancel url is " url)}
      (-> (saml-get "error" {:RelayState    (str "hello/world/" trid)
                             :statusCode    "This"
                             :statusCode2   "is"
                             :statusMessage "fine."})
          :trace-redirects
          second) => url)))

(fact "Login path parameters must be absolute"
  (let [trid (good-login {:success "/ok"
                          :error   "/bad"
                          :cancel  "/nope"})]
    (cancel-url trid "/nope")))

(fact "Surrounding whitespace is trimmed"
  (let [trid (good-login {:success " /ok "
                          :error   "  /bad  "
                          :cancel  "  /nope "})]
    (cancel-url trid "/nope")))

(doseq [k    [:success :error :cancel]
        path ["http://example.com/path" "path" " http://example.com/path " " path "]
        :let [m (assoc {:success "/ok"
                        :error   "/bad"
                        :cancel  "/nope"}
                       k path)]]
  (fact {:midje/description (format "Bad %s path %s " (name k) path)}
    (saml-get "login" m) => http400?))

(fact "Cancel path fallbacks to root"
  (let [trid (good-login {:success "/ok"
                          :error   "/bad"})]
    (cancel-url trid "/")))
