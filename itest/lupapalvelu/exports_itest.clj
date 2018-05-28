(ns lupapalvelu.exports-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :as ss]))

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

(fact "Can not use api with incorrect role"
  (let [resp (decoded-get (str (server-address) "/data-api/json/export-applications")
                          {:basic-auth ["salesforce-etl" "salesforce-etl"]
                           :follow-redirects false
                           :throw-exceptions false})]
    (:status resp) => 200
    (:body resp) => unauthorized?))

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

(facts "Salesforce export"
  (let [endpoint (str (server-address) "/data-api/json/salesforce-export")]
    (doseq [tester [{:role "trusted-etl"
                     :auth ["solita-etl" "solita-etl"]}
                    {:role "applicant"
                     :auth ["pena" "pena"]}]]
      (fact {:midje/description (str (:role tester) "not allowed")}
        (:body (decoded-get endpoint
                            {:basic-auth (:auth tester)
                             :follow-redirects false
                             :throw-exceptions false})) => unauthorized?))
    (let [resp (decoded-get endpoint
                            {:basic-auth       ["salesforce-etl" "salesforce-etl"]
                             :follow-redirects false
                             :throw-exceptions false})
          data (get-in resp [:body :applications])]
      (fact "correct credentials"
        (:status resp) => 200
        (count data) => 2)

      (fact "correct keys"
        (-> data first keys) => (contains #{:id :operations :permitType :municipality :organization :state :submitted} :gaps-ok)
        (-> data first keys) =not=> (contains [:applicant :verdicts :authority]))

      (fact "price classes"
        (map :priceClass (flatten (map :operations data))) => (just ["D" "B"]))

      (fact "truncated-op-description"
        (command pena
                 :update-op-description
                 :id (get-in data [0 :id])
                 :op-id (get-in data [0 :operations 0 :id])
                 :desc (ss/join (repeat 100 "abc"))) => ok?

        (let [op (-> (decoded-get endpoint
                                  {:basic-auth       ["salesforce-etl" "salesforce-etl"]
                                   :follow-redirects false
                                   :throw-exceptions false})
                     :body :applications
                     first :operations first)]
          (fact "suffix" (:description op) => #(ss/ends-with % "..."))
          (fact "length 255" (count (:description op)) => 255))))))

(when (env/feature? :api-usage-export)
  (facts "Archive API usage Salesforce export"
    (let [endpoint (str (server-address) "/data-api/json/export-archive-api-usage")]
      (doseq [tester [{:role "trusted-etl"
                       :auth ["solita-etl" "solita-etl"]}
                      {:role "applicant"
                       :auth ["pena" "pena"]}]]
        (fact {:midje/description (str (:role tester) "not allowed")}
          (:body (decoded-get endpoint
                              {:basic-auth (:auth tester)
                               :follow-redirects false
                               :throw-exceptions false})) => unauthorized?))
      (let [resp (decoded-get endpoint
                              {:basic-auth       ["salesforce-etl" "salesforce-etl"]
                               :follow-redirects false
                               :throw-exceptions false})
            transactions (get-in resp [:body :transactions])
            most-recent-download-ts (get-in resp [:body :lastRunTimestampMillis])]
        (fact "correct credentials"
          (:status resp) => 200)

        (fact "correct keys"
          (when (count transactions)
            (-> transactions first keys) => (contains #{:organization :lastDateOfTransactionMonth :quantity} :gaps-ok)))

        (fact "has most recent download timestamp"
              most-recent-download-ts => number?)))))
