(ns lupapalvelu.dummy-ident-itest-util
  (:require [midje.sweet :refer :all]
            [clojure.set :refer [rename-keys]]
            [lupapalvelu.itest-util :refer :all]))

(defn dummy-ident-init
  "Initializes dummy-ident session. Returns transaction ID (TRID)."
  [request-opts token-query]
  ; Request welcome page and query features to init session
  (http-get (str (server-address) "/app/fi/welcome") request-opts)
  (http-get (str (server-address) "/api/query/features") request-opts)
  (let [{:keys [status] :as resp} (http-get (str (server-address) "/dev/saml/init-login")
                                                 (merge request-opts token-query))
        trid (-> resp (decode-response) :body :trid)]

    (fact "Init returned OK" status => 200)
    trid))

(defn dummy-ident-finish
  [request-opts user trid]
  (http-post (str (server-address) "/dev/saml-login")
             (assoc request-opts
                    :form-params
                    (assoc (rename-keys user {:personId :userid}) :stamp trid))))
