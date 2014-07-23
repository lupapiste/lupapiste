(ns lupapalvelu.exports-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.http :as http]))

(apply-remote-minimal)

(fact "Can not use api without credentials"
  (let [resp (http/get (str (server-address) "/data-api/json/export-applications")
               {:follow-redirects false
                :throw-exceptions false})]
    (:status resp) => 401))

(fact "Can not use api with incorrect password"
  (let [resp (http/get (str (server-address) "/data-api/json/export-applications")
               {:basic-auth ["solita-etl" "foo"]
                :follow-redirects false
                :throw-exceptions false})]
    (:status resp) => 401))

(fact "With valid credentials, api returns one application"
  (let [application-id (create-app-id pena)
        http-resp (http/get (str (server-address) "/data-api/json/export-applications")
                    {:basic-auth ["solita-etl" "solita-etl"]
                     :follow-redirects false
                     :throw-exceptions false})
        resp (:body (decode-response http-resp))]
    (:status http-resp) => 200
    resp => ok?
    (count (:applications resp)) => 1
    (-> resp :applications first :id) => application-id)

  (fact "but not if modified timestamp is too old"
    (let [http-resp (http/get (str (server-address) "/data-api/json/export-applications")
                    {:basic-auth ["solita-etl" "solita-etl"]
                     :query-params {:modifiedAfterTimestampMillis (+ (lupapalvelu.core/now) (* 1000 60))}
                     :follow-redirects false
                     :throw-exceptions false})
          resp (:body (decode-response http-resp))]
      resp => ok?
      (:applications resp) => sequential?
      (count (:applications resp)) => 0)))

(fact "Applicant can not use the api"
  (let [http-resp (http/get (str (server-address) "/data-api/json/export-applications")
                    {:basic-auth ["pena" "pena"]
                     :follow-redirects false
                     :throw-exceptions false})
        resp (:body (decode-response http-resp))]
    (:status http-resp) => 200
    resp => unauthorized?
    (:applications resp) => nil?))
