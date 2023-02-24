(ns lupapalvelu.exports-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer [fact*]]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :as ss]))

(apply-remote-minimal)

(defn create-archiving-project [propertyId kuntalupatunnus refreshBuildings user]
  (fact "create-archiving-project"
    (command (or user digitoija) :create-archiving-project
             :lang "fi"
             :x "404262.00"
             :y "6694511.00"
             :address "Street 1"
             :propertyId propertyId
             :organizationId "186-R"
             :kuntalupatunnus kuntalupatunnus
             :createWithoutPreviousPermit true
             :createWithoutBuildings true
             :createWithDefaultLocation false
             :refreshBuildings refreshBuildings)
    => ok?))

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
  (let [application (create-and-open-application pena :operation "kerrostalo-rivitalo")
        uusi-rakennus-doc-id (:id (domain/get-document-by-name application "uusiRakennus"))
        _ (command pena :update-doc :id (:id application) :doc uusi-rakennus-doc-id
                   :updates [["kaytto.kayttotarkoitus", "021 rivitalot"]] :collection "documents")
        http-resp (http-get (str (server-address) "/data-api/json/export-applications")
                    {:basic-auth ["solita-etl" "solita-etl"]
                     :follow-redirects false
                     :throw-exceptions false})
        resp (:body (decode-response http-resp))]

    resp => ok?
    (count (:applications resp)) => 2
    (-> resp :applications last :operations first :priceClass) => "B" ; from kayttotarkoitus-hinnasto.xlsx
    (-> resp :applications last :operations first :use) => "021 rivitalot"))

(fact* "When using rakennusluokka operation, price and rakennusluokka are included in operation"
  (let [application (create-and-open-application pena :operation "kerrostalo-rivitalo")
        uusi-rakennus-doc-id (:id (domain/get-document-by-name application "uusiRakennus"))
        _ (update-rakennusluokat "753-R" true)
        _ (set-krysp-version "753-R" :R sipoo "2.2.4")
        _ (command pena :update-doc :id (:id application) :doc uusi-rakennus-doc-id
                   :updates [["kaytto.rakennusluokka", "0120 pienkerrostalot"]] :collection "documents") => ok?
        http-resp (http-get (str (server-address) "/data-api/json/export-applications")
                            {:basic-auth ["solita-etl" "solita-etl"]
                             :follow-redirects false
                             :throw-exceptions false})
        resp (:body (decode-response http-resp))]

    resp => ok?
    (count (:applications resp)) => 3
    (-> resp :applications last :operations first :priceClass) => "A" ; from rakennusluokka-hinnasto.xlsx
    (-> resp :applications last :operations first :use) => "0120 pienkerrostalot"))

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

(create-archiving-project "18600101140001" "186-0001" true digitization-project-user) ;;Is not exported to salesforce
(create-archiving-project "18600101140002" "186-0002" true digitoija)

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
        (count data) => 4)

      (fact "ARK app that was created by digitoija is only exported"
        (let [ark-apps (filter #(= "ARK" (:permitType %)) data)]
          (count ark-apps) => 1
          (:propertyId (first ark-apps)) => "18600101140002"))

      (fact "correct keys"
        (-> data first keys) => (contains #{:id :operations :permitType :municipality :organization :state :submitted} :gaps-ok)
        (-> data first keys) =not=> (contains [:applicant :verdicts :authority]))

      (fact "price classes"
        (map :priceClass (flatten (map :operations data))) => (just ["D" "B" "A" "Z" "Z"])) ;;Note that the ARK app creted by digitoija has 2 operations

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
    (let [endpoint (str (server-address)
                        "/data-api/json/export-archive-api-usage?startTimestampMillis=0&"
                        "endTimestampMillis=" (now))]
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
          (-> transactions first keys) => (contains #{:organization :lastDateOfTransactionMonth :quantity} :gaps-ok))

        (fact "correct quantity"
              (->> transactions (map :quantity) (reduce +)) => 3) ; From minimal fixture
        (fact "has most recent download timestamp"
              most-recent-download-ts => number?)))))
