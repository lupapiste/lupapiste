(ns lupapalvelu.rest-configuration-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(facts "/rest/current-configuration"
  (fact "invalid creds"
    (http-get (str (server-address) "/rest/current-configuration")
              {:throw-exceptions false}) => http401?)

  (let [resp (http-get (str (server-address) "/rest/current-configuration")
                       {:basic-auth ["sipoo-r-backend" "sipoo"]
                        :throw-exceptions false
                        :as :json})]
    (fact "200 OK" resp => http200?)
    (fact "json keys"
      (keys (:body resp)) => (contains #{:municipalities :permitTypes :operations :states}))))

(facts "/api/query/current-configuration"
  (let [resp (query pena :current-configuration)]
    resp => ok?
    (keys (dissoc resp :ok)) => (contains #{:municipalities :operations :permitTypes :states})))
