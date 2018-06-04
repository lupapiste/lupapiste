(ns lupapalvelu.rest-prev-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(def rest-address (str (server-address) "/rest/get-lp-id-from-previous-permit"))

(defn- api-call [params]
  (http-get rest-address (merge params {:throw-exceptions false})))

(facts "Create app from previous permit"
  (fact "creation succeeds"
    (let [resp (decode-response
                 (api-call {:query-params {:kuntalupatunnus "14-0241-R 3"}
                            :basic-auth   ["jarvenpaa-backend" "jarvenpaa"]}))
          {:keys [id text] :as body} (:body resp)]
      resp => http200?
      text => "created-new-application"
      (fact "applicants are authorized"
        (->> (query-application raktark-jarvenpaa id) :documents (map :schema-info))
            => (has some (contains {:name "hakija-r"}))))))

