(ns lupapalvelu.ident.suomifi-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [http-get http302? http400? server-address with-test-system]]
            [midje.sweet :refer :all]
            [sade.strings :as ss]))

(defn saml-get [path params]
  (http-get (str (server-address) "/api/saml/" path)
            {:query-params      params
             :throw-exceptions  false
             :redirect-strategy :none}))

(defn good-login
  [params]
  (let [response (saml-get "login" params)
        trid (-> response :headers :location
                 (ss/split #"/") last)]
    (fact "302 Found"
      response => http302?)
    (fact "Trid is found"
      trid => ss/not-blank?)
    trid))

(defn cancel-url [trid cancel-path]
  (let [response (saml-get "error" {:RelayState    (str "hello/world/" trid)
                                    :statusCode    "This"
                                    :statusCode2   "is"
                                    :statusMessage "fine."})]
    (fact {:midje/description (str "Cancel url is " cancel-path)}
      (-> response :headers :location) =>  cancel-path)))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
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
      (cancel-url trid "/"))))
