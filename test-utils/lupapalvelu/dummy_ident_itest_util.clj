(ns lupapalvelu.dummy-ident-itest-util
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(defn dummy-ident-init
  "Initializes dummy-ident session. Returns transaction ID (TRID)."
  [request-opts token-query]
  ; Request welcome page and query features to init session
  (http-get (str (server-address) "/app/fi/welcome") request-opts)
  (http-get (str (server-address) "/api/query/features") request-opts)
  (let [{:keys [status body] :as resp} (http-get (str (server-address) "/dev/saml/init-login")
                                                 (merge request-opts token-query))
        trid (-> resp (decode-response) :body :trid)]

    (fact "Init returned OK" status => 200)
    trid))

(defn dummy-ident-finish
  [request-opts trid]
  (http-post (str (server-address) "/dev/saml-login")
             (assoc request-opts
                    :form-params
                    {:userid "itest@example.com"
                     :stamp trid})))
