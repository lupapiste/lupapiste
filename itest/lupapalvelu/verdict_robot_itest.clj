(ns lupapalvelu.verdict-robot-itest
  (:require [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.verdict-robot.schemas :refer [Sanoma PaatosSanoma PoistoSanoma]]
            [midje.sweet :refer :all]
            [ring.util.response :as resp]
            [sade.core :as core]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(apply-remote-fixture "pate-verdict")

(def verdict-messages (str (server-address) "/rest/verdict-messages"))
(def ack-messages (str (server-address) "/rest/ack-verdict-messages"))
(def runeberg "2020-02-05")
(def today (date/xml-date (core/now)))
(def tomorrow (date/xml-date (-> (date/now) (date/plus :day) (date/timestamp))))
(def unauthorized (contains {:status 401 :body "Unauthorized"}))
(def status200 (contains {:status 200}))
(def template-id (-> pate-fixture/verdict-templates-setting-r :templates first :id))

(defn body-ok [body-check]
  (contains {:status 200
             :body   body-check}))

(defn- api-call [method-fn endpoint params]
  (method-fn endpoint (merge params {:throw-exceptions false})))

(defn- rest-call [& kvs]
  (let [res (api-call http-get
                      verdict-messages
                      {:query-params (merge {:organization "753-R"}
                                            (apply hash-map kvs))
                       :basic-auth   ["sipoo-r-backend" "sipoo"]})
        ct  (or (resp/get-header  res "content-type") "")]
    (cond-> res
      (re-find  #"application/json" ct) decode-response)))

(defn- ack-call
  [& kvs]
  (api-call http-post
            ack-messages
            {:headers {"content-type" "application/json;charset=utf-8"}
             :body  (json/encode (merge {:organization "753-R"}
                                        (apply hash-map kvs)))
             :basic-auth   ["sipoo-r-backend" "sipoo"]}))

(defn publish-verdict [apikey app-id]
  (let [{verdict-id :verdict-id} (command apikey :new-pate-verdict-draft
                                          :id app-id
                                          :template-id template-id)]

    (fact "Verdict handler title"
      (command apikey :edit-pate-verdict :id app-id :verdict-id verdict-id
               :path [:handler-title] :value "Handler") => no-errors?)
    (fact "Verdict handler"
      (command apikey :edit-pate-verdict :id app-id :verdict-id verdict-id
               :path [:handler] :value "Han Dler") => no-errors?)
    (fact "Verdict giver title"
      (command apikey :edit-pate-verdict :id app-id :verdict-id verdict-id
               :path [:giver-title] :value "Giver") => no-errors?)
    (fact "Verdict giver"
      (command apikey :edit-pate-verdict :id app-id :verdict-id verdict-id
               :path [:giver] :value "Gi Ver") => no-errors?)
    (fact "Set automatic calculation of other dates"
      (command apikey :edit-pate-verdict :id app-id :verdict-id verdict-id
               :path [:automatic-verdict-dates] :value true) => no-errors?)
    (fact "Verdict date"
        (command apikey :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-date] :value (core/now)) => no-errors?)
    (fact "Verdict code"
      (command apikey :edit-pate-verdict :id app-id :verdict-id verdict-id
               :path [:verdict-code] :value "hyvaksytty") => no-errors?)
    (fact "Publish verdict"
      (command apikey :publish-pate-verdict :id app-id :verdict-id verdict-id)
      => no-errors?)
    verdict-id))

(defn robot-integration-messages [app-id]
  (filter #(= (:partner %) "robot") (integration-messages app-id)))

(fact "Publish the verdict template, so the reviews are included in the verdict"
  (command sipoo :publish-verdict-template :organizationId "753-R"
           :template-id template-id) => ok?)

(let [app-id1        (:id (create-and-submit-application pena :propertyId sipoo-property-id
                                                         :operation "pientalo"))
      robot-messages #(robot-integration-messages app-id1)]
  (fact "Publish the first verdict"
    (publish-verdict sonja app-id1))
  (fact "No robot integration message"
    (count (robot-messages)) => 0
    (:body (rest-call :from runeberg)) => [])

  (fact "Enable robot integration"
    (command admin :update-organization
             :permitType "R"
             :municipality "753"
             :pateRobot true) => ok?)
  (let [vid1                     (publish-verdict ronja app-id1)
        [{msg-id1 :id} :as msgs] (robot-messages)]
    (fact "Robot integration message is created"
      msgs => (just [(contains {:direction    "out"
                                :transferType "http"
                                :format       "json"
                                :status       "published"
                                :target       {:type "verdict"
                                               :id   vid1}
                                :initiator    {:id       ronja-id
                                               :username "ronja"}
                                :messageType  "publish-verdict"
                                :application  {:id           app-id1
                                               :organization "753-R"}
                                :id           msg-id1
                                :created      truthy})]))
    (fact "Reviews are included in the message"
      (-> msgs first :data :paatos :vaaditutKatselmukset)
      ;; Review names and types are defined in the fixture.
      => (just [(just {:laji "aloituskokous" :nimi "Startti" :tunnus truthy})
                (just {:laji "loppukatselmus" :nimi "Loppukatselmus" :tunnus truthy})
                (just {:laji "muu katselmus" :nimi "Katselmus" :tunnus truthy})]
               :in-any-order))
    (fact "Verdict giver and handler"
      (-> msgs first :data :paatos)
      => (contains {:paatoksentekija {:nimike "Giver" :nimi "Gi Ver"}
                    :kasittelija {:nimike "Handler" :nimi "Han Dler"}}))
    (fact "REST API call returns one verdict"
      (let [{body :body :as res} (rest-call :from runeberg)]
        res => (body-ok (just [(contains {:sanomatunnus msg-id1})]))
        (sc/check Sanoma (first body)) => nil
        (sc/check PaatosSanoma (dissoc (first body) :sanomatunnus)) => nil
        (-> body first :paatos :tunnus) => vid1))

    (facts "Nonmatching calls"
      (fact "Unauthorized"
        (rest-call :organization "091-R" :from runeberg) => unauthorized)
      (fact "Invalid organization"
        (rest-call :organization "BAD" :from runeberg) => unauthorized)
      (fact "No verdicts in Vantaa"
        (decode-response (api-call http-get verdict-messages
                                   {:basic-auth   ["matti-rest-api-user" "vantaa"]
                                    :query-params {:organization "092-R"
                                                   :from         runeberg}}))
        => (body-ok []))
      (fact "Future time range"
        (rest-call :from tomorrow) => (body-ok []))
      (fact "Past time range"
        (rest-call :from runeberg :until today) => (body-ok []))
      (fact "Insane time range"
        (rest-call :from today :until runeberg) => (body-ok [])))

    (fact "Matching until parameter"
      (count (:body (rest-call :from today :until tomorrow))) => 1)
    (facts "Acknowledgements"
      (fact "Unauthorized"
        (ack-call :organization "091-R" :ids []) => unauthorized)
      (fact "Ack"
        (ack-call :ids [msg-id1]) => status200
        (robot-messages)
        => (just [(contains {:id           msg-id1
                             :acknowledged truthy})]))
      (fact "No matches"
        (rest-call :from runeberg) => (body-ok []))
      (fact "Matches with all parameter"
        (rest-call :from runeberg :all true) => (body-ok (just [truthy])))
      (fact "Publish new verdict and check it is found"
        (let [vid2   (publish-verdict sonja app-id1)
              [data] (:body (rest-call :from runeberg))]
          (sc/check Sanoma data) => nil
          (sc/check PaatosSanoma (dissoc data :sanomatunnus)) => nil
          (-> data :paatos :tunnus) => vid2
          (fact "All finds both"
            (rest-call :from runeberg :all true) => (body-ok (just [truthy truthy])))
          (fact "Delete verdict"
            (command sonja :delete-pate-verdict :id app-id1 :verdict-id vid1) => ok?)
          (facts "Published verdict message has been replaced with removal message"
            (fact "Integration messages"
              (filter #(some-> % :target :id (= vid1))
                      (robot-messages))
              => (just [(contains {:messageType "delete-verdict"
                                   :direction   "out"
                                   :application {:id app-id1 :organization "753-R"}
                                   :target      {:id vid1 :type "verdict"}
                                   :status      "published"
                                   :data        (partial sc/validate PoistoSanoma)})]))
            (fact "REST"
              (rest-call :from runeberg)
              => (body-ok (just [(contains {:asiointitunnus  app-id1
                                            :poistettuPaatos vid1})
                                 (contains {:asiointitunnus app-id1
                                            :paatos         (contains {:tunnus vid2})})]
                                :in-any-order))))
          (facts "Removal message can be acknowledged too"
            (let [{acked     :acknowledged
                   rm-msg-id :id} (util/find-first #(= (select-keys % [:messageType :target])
                                                       {:messageType "delete-verdict"
                                                        :target      {:id vid1 :type "verdict"}})
                                                   (robot-messages))]
              acked => nil
              (fact "ACK ids are case-insensitive and bad ids are ignored"
                (ack-call :ids [(ss/upper-case rm-msg-id) "foo" "bar"])
                => status200)
              (fact "REST"
                (rest-call :from runeberg)
                => (body-ok (just [(contains {:asiointitunnus app-id1
                                              :paatos         (contains {:tunnus vid2})})]))))))))))
