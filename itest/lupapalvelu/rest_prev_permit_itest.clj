(ns lupapalvelu.rest-prev-permit-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(defn- rest-address []
  (str (server-address) "/rest/get-lp-id-from-previous-permit"))

(defn- api-call [params]
  (http-get (rest-address) (merge params {:throw-exceptions false})))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Create app from previous permit"
    (fact "creation succeeds"
      (let [resp              (decode-response
                                (api-call {:query-params {:kuntalupatunnus "14-0241-R 3"}
                                           :basic-auth   ["jarvenpaa-backend" "jarvenpaa"]}))
            {:keys [id text]} (:body resp)]
        resp => http200?
        text => "created-new-application"
        (fact "applicants are authorized"
          (->> (query-application raktark-jarvenpaa id) :documents (map :schema-info))
          => (has some (contains {:name "hakija-r"})))))))
