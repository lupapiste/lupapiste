(ns lupapalvelu.rest-application-data-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(def rest-address (str (server-address) "/rest/submitted-applications"))

(defn- api-call [params]
  (http-get rest-address (merge params {:throw-exceptions false})))

(defn- sipoo-r-api-call []
  (decode-response
    (api-call {:query-params {:organization "753-R"}
               :basic-auth   ["sipoo-r-backend" "sipoo"]})))

(facts "REST interface for application data -"
  (fact "not available as anonymous user"
    (let [params {:query-params {:organization "753-R"}}
          response (api-call params)]
      response => http401?))
  (fact "Sipoo Backend can access"
    (sipoo-r-api-call) => http200?)
  (fact "Sipoo Backend can not access other municipalities"
    (decode-response
      (api-call {:query-params {:organization "091-R"}
                 :basic-auth   ["sipoo-r-backend" "sipoo"]})) =not=> ok?)
  (let [{r-app-id1 :id} (create-app pena :propertyId sipoo-property-id :operation "kerrostalo-rivitalo")
        {r-app-id2 :id} (create-app pena :propertyId sipoo-property-id :operation "kerrostalo-rivitalo")
        _ (command pena :submit-application :id r-app-id1)]
    (let [resp (sipoo-r-api-call)
          body (-> resp :body)]
      resp => http200?
      body => ok?
      (fact "Application submitted => contained in API call result"
        (:data body) => (has some (contains {:asiointitunnus r-app-id1})))
      (fact "Application draft => not contained in API call result"
        (:data body) =not=> (has some (contains {:asiointitunnus r-app-id2}))))))