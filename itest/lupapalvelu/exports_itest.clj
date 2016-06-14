(ns lupapalvelu.exports-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]))

(apply-remote-minimal)

(fact "Can not use api without credentials"
  (let [resp (http-get (str (server-address) "/data-api/json/export-applications")
               {:follow-redirects false
                :throw-exceptions false})]
    (:status resp) => 401))

(fact "Can not use api with incorrect password"
  (let [resp (http-get (str (server-address) "/data-api/json/export-applications")
               {:basic-auth ["solita-etl" "foo"]
                :follow-redirects false
                :throw-exceptions false})]
    (:status resp) => 401))

(fact "With valid credentials, api returns one application"
  (let [application-id (create-app-id pena :operation "markatilan-laajentaminen")
        http-resp (http-get (str (server-address) "/data-api/json/export-applications")
                    {:basic-auth ["solita-etl" "solita-etl"]
                     :follow-redirects false
                     :throw-exceptions false})
        resp (:body (decode-response http-resp))]
    (:status http-resp) => 200
    resp => ok?
    (count (:applications resp)) => 1
    (-> resp :applications first :id) => application-id
    (fact "with price class"
      (-> resp :applications first :operations first :priceClass) => "D"))

  (fact "but not if modified timestamp is too old"
    (let [http-resp (http-get (str (server-address) "/data-api/json/export-applications")
                    {:basic-auth ["solita-etl" "solita-etl"]
                     :query-params {:modifiedAfterTimestampMillis (+ (now) (* 1000 60))}
                     :follow-redirects false
                     :throw-exceptions false})
          resp (:body (decode-response http-resp))]
      resp => ok?
      (:applications resp) => sequential?
      (count (:applications resp)) => 0)))

(fact "When using kayttotarkoitus operation, price and kayttotarkoitus is included in operation"
  (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo")
        http-resp (http-get (str (server-address) "/data-api/json/export-applications")
                    {:basic-auth ["solita-etl" "solita-etl"]
                     :follow-redirects false
                     :throw-exceptions false})
        resp (:body (decode-response http-resp))]

    resp => ok?
    (count (:applications resp)) => 2
    (-> resp :applications last :operations first :priceClass) => "B" ; from kayttotarkoitus-hinnasto.xlsx
    (-> resp :applications last :operations first :use) => "021 rivitalot"))

(fact "Applicant can not use the api"
  (let [http-resp (http-get (str (server-address) "/data-api/json/export-applications")
                    {:basic-auth ["pena" "pena"]
                     :follow-redirects false
                     :throw-exceptions false})
        resp (:body (decode-response http-resp))]
    (:status http-resp) => 200
    resp => unauthorized?
    (:applications resp) => nil?))

(facts "export-organizations"
  (let [http-resp (http-get (str (server-address) "/data-api/json/export-organizations")
                    {:basic-auth ["solita-etl" "solita-etl"]
                     :follow-redirects false
                     :throw-exceptions false})
          {organizations :organizations :as resp} (:body (decode-response http-resp))]
      resp => ok?

      (fact "Some organizations returned"
        organizations => sequential?
        (count organizations) => pos?)

      (fact "Every organization has expected keys"
        (doseq [organization organizations]
          organization => (contains {:id string?, :name map?, :scope sequential?})))))
