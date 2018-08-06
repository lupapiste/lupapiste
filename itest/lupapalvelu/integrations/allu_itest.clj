(ns lupapalvelu.integrations.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
  (:require [mount.core :as mount]
            [schema.core :as sc]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [monger.operators :refer [$set]]
            [sade.core :refer [ok? fail]]
            [sade.schema-generators :as ssg]
            [sade.env :as env]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.mongo :as mongo]

            [midje.sweet :refer [facts fact =>]]
            [lupapalvelu.itest-util :as itu :refer [pena raktark-helsinki]]

            [lupapalvelu.integrations.allu :as allu
             :refer [ALLUPlacementContracts cancel-allu-application! create-contract! lock-contract! allu-fail!
                     ->LocalMockALLU PlacementContract]]))

;;;; Refutation Utilities
;;;; ===================================================================================================================

(defn- create-and-fill-placement-app [apikey permitSubtype]
  (let [{:keys [id] :as response}
        (itu/create-local-app apikey
                              :operation (ssg/generate allu/SijoituslupaOperation)
                              :x "385770.46" :y "6672188.964"
                              :address "Kaivokatu 1"
                              :propertyId "09143200010023")]
    (mongo/update-by-id :applications id
                        {$set {:documents     (for [doc-name ["hakija-ya"
                                                              "yleiset-alueet-hankkeen-kuvaus-sijoituslupa"
                                                              "yleiset-alueet-maksaja"]]
                                                (ssg/generate (dds/doc-data-schema doc-name true)))
                               :permitSubtype permitSubtype}})
    response))

(defn- check-request [pending-on-client request]
  (fact "request is well-formed"
    (-> request :headers :authorization) => (str "Bearer " (env/value :allu :jwt))
    (:content-type request) => :json
    (let [contract (-> request :body (json/decode true))]
      contract => #(nil? (sc/check PlacementContract %))
      (:pendingOnClient contract) => pending-on-client)))

(deftype CheckingALLU [inner]
  ALLUPlacementContracts
  (cancel-allu-application! [_ endpoint request]
    (fact "endpoint is correct" endpoint => (re-pattern (str (env/value :allu :url) "/applications/\\d+/cancelled")))
    (fact "request is well-formed"
      (-> request :headers :authorization) => (str "Bearer " (env/value :allu :jwt)))

    (cancel-allu-application! inner endpoint request))

  (create-contract! [_ endpoint request]
    (fact "endpoint is correct" endpoint => (str (env/value :allu :url) "/placementcontracts"))
    (check-request true request)

    (create-contract! inner endpoint request))

  (lock-contract! [_ endpoint request]
    (fact "endpoint is correct" endpoint => (re-pattern (str (env/value :allu :url) "/placementcontracts/\\d+")))
    (check-request false request)

    (lock-contract! inner endpoint request))

  (allu-fail! [_ text info-map] (allu-fail! inner text info-map)))

(deftype ConstALLU [cancel-response creation-response locking-response fail-map failure-counter]
  ALLUPlacementContracts
  (cancel-allu-application! [_ _ _] cancel-response)
  (create-contract! [_ _ _] creation-response)
  (lock-contract! [_ _ _] locking-response)
  (allu-fail! [_ text info-map]
    (fact "response is 4**" creation-response => http/client-error?)
    (fact "error has the expected contents" (fail text info-map) => fail-map)
    (swap! failure-counter inc)))

;;;; Actual Tests
;;;; ===================================================================================================================

(env/with-feature-value :allu true
  (mongo/connect!)

  (facts "Usage of ALLU integration in commands"
    (mongo/with-db itu/test-db-name
      (lupapalvelu.fixture.core/apply-fixture "minimal")

      (let [initial-allu-state {:id-counter 0, :applications {}}
            allu-state (atom initial-allu-state)]
        (mount/start-with {#'allu/allu-instance (->CheckingALLU (->LocalMockALLU allu-state))})

      (fact "enabled and sending correctly to ALLU for Helsinki YA sijoituslupa and sijoitussopimus."
        (let [{:keys [id]} (create-and-fill-placement-app pena "sijoituslupa") => ok?]
          (itu/local-command pena :submit-application :id id) => ok?
          (count (:applications @allu-state)) => 1
          (-> (:applications @allu-state) first val :pendingOnClient) => true

          (itu/local-command raktark-helsinki :approve-application :id id :lang "fi") => ok?
          (count (:applications @allu-state)) => 1
          (-> (:applications @allu-state) first val :pendingOnClient) => false)

        (let [{:keys [id]} (create-and-fill-placement-app pena "sijoitussopimus") => ok?]
          (itu/local-command pena :submit-application :id id) => ok?

          (itu/local-command pena :cancel-application :id id :text "Alkoi nolottaa." :lang "fi") => ok?
          (:id-counter @allu-state) => 2
          (count (:applications @allu-state)) => 1)

        (let [{:keys [id]} (create-and-fill-placement-app pena "sijoitussopimus") => ok?]
          (itu/local-command pena :submit-application :id id) => ok?

          (itu/local-command raktark-helsinki :return-to-draft
                             :id id :text "Tällaisenaan nolo ehdotus." :lang "fi") => ok?
          (:id-counter @allu-state) => 3
          (count (:applications @allu-state)) => 1))

      (fact "disabled for everything else."
        (reset! allu-state initial-allu-state)

        (let [{:keys [id]} (itu/create-local-app pena :operation (ssg/generate allu/SijoituslupaOperation)) => ok?]
          (itu/local-command pena :submit-application :id id) => ok?)

        (let [{:keys [id]} (itu/create-local-app pena
                                                 :operation "pientalo"
                                                 :x "385770.46" :y "6672188.964"
                                                 :address "Kaivokatu 1"
                                                 :propertyId "09143200010023") => ok?]
          (itu/local-command pena :submit-application :id id) => ok?
          (itu/local-command raktark-helsinki :approve-application :id id :lang "fi") => ok?)

        (:id-counter @allu-state) => 0))

    (fact "error responses from ALLU produce `fail!`ures"
      (let [failure-counter (atom 0)]
        (mount/start-with {#'allu/allu-instance (->ConstALLU {:status 200}
                                                             {:status 400, :body "Your data was bad."} nil
                                                             {:ok   false, :text "error.allu.http"
                                                              :status 400 :body "Your data was bad."}
                                                             failure-counter)})
        (let [{:keys [id]} (create-and-fill-placement-app pena "sijoituslupa") => ok?]
          (itu/local-command pena :submit-application :id id)
          @failure-counter => 1))

      (let [failure-counter (atom 0)]
        (mount/start-with {#'allu/allu-instance (->ConstALLU {:status 200}
                                                             {:status 401, :body "You are unauthorized."} nil
                                                             {:ok     false, :text "error.allu.http"
                                                              :status 401, :body "You are unauthorized."}
                                                             failure-counter)})
        (let [{:keys [id]} (create-and-fill-placement-app pena "sijoitussopimus") => ok?]
          (itu/local-command pena :submit-application :id id)
          @failure-counter => 1)))

    (mount/start #'allu/allu-instance))))