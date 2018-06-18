(ns lupapalvelu.integrations.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
  (:require [sade.core :refer [def- ok?]]
            [mount.core :as mount]
            [schema.core :as sc]
            [cheshire.core :as json]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$set]]
            [lupapalvelu.document.data-schema :as dds]

            [midje.sweet :refer [facts fact =>]]
            [sade.schema-generators :as ssg]
            [lupapalvelu.itest-util :as itu :refer [pena]]

            [lupapalvelu.integrations.allu-schemas :refer [PlacementContract]]
            [lupapalvelu.integrations.allu :as allu]))

;;; FIXME: vary :operation

(defn- create-and-fill-placement-app [apikey permitSubtype]
  (let [{:keys [id] :as response}
        (itu/create-local-app apikey
                              :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen"
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

(defn- checking-succeeding-creation-post! [sent-allu-requests]
  (fn [endpoint request]
    (fact "endpoint is correct"
      endpoint => (str (env/value :allu :url) "/placementcontracts"))

    (fact "request is well-formed"
      (-> request :headers :authorization) => (str "Bearer " (env/value :allu :jwt))
      (:content-type request) => :json
      (-> request :body (json/decode true)) => #(nil? (sc/check PlacementContract %)))

    (let [allu-id @sent-allu-requests]
      (swap! sent-allu-requests inc)
      {:status 200, :body (str allu-id)})))

(facts "Usage of ALLU integration in submit-application command"
  (mongo/with-db itu/test-db-name
    (lupapalvelu.fixture.core/apply-fixture "minimal")

    (let [sent-allu-requests (atom 0)]
      (mount/start-with {#'allu/allu-instance
                         (allu/->ALLUService (checking-succeeding-creation-post! sent-allu-requests))})

      (fact "enabled and sending correctly to ALLU for Helsinki YA sijoituslupa and sijoitussopimus."
        (doseq [permitSubtype ["sijoituslupa" "sijoitussopimus"]]
          (let [{:keys [id]} (create-and-fill-placement-app pena permitSubtype) => ok?]
            (itu/local-command pena :submit-application :id id) => ok?))

        @sent-allu-requests => 2)

      (fact "disabled for everything else."
        (reset! sent-allu-requests 0)

        (let [{:keys [id]}
              (itu/create-local-app pena
                                    :operation "ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen") => ok?]
          (itu/local-command pena :submit-application :id id) => ok?)

        (let [{:keys [id]} (itu/create-local-app pena
                                                 :operation "pientalo"
                                                 :x "385770.46" :y "6672188.964"
                                                 :address "Kaivokatu 1"
                                                 :propertyId "09143200010023") => ok?]
          (itu/local-command pena :submit-application :id id) => ok?)

        @sent-allu-requests => 0))

    (fact "error responses from ALLU produce `fail!`ures"
      (mount/start-with {#'allu/allu-instance
                         (allu/->ALLUService (constantly {:status 400, :body "Your data was bad."}))})

      (let [{:keys [id]} (create-and-fill-placement-app pena "sijoituslupa") => ok?]
        (itu/local-command pena :submit-application :id id) => {:ok   false, :text "error.allu.malformed-application"
                                                                :body "Your data was bad."})

      (mount/start-with {#'allu/allu-instance
                         (allu/->ALLUService (constantly {:status 401, :body "You are unauthorized."}))})

      (let [{:keys [id]} (create-and-fill-placement-app pena "sijoitussopimus") => ok?]
        (itu/local-command pena :submit-application :id id) => {:ok     false, :text "error.allu.http"
                                                                :status 401, :body "You are unauthorized."}))

    (mount/start #'allu/allu-instance)))
