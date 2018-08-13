(ns lupapalvelu.integrations.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
  (:require [mount.core :as mount]
            [schema.core :as sc]
            [clj-http.client :as http]
            [monger.operators :refer [$set]]
            [sade.core :refer [def- ok? fail]]
            [sade.schema-generators :as ssg]
            [sade.env :as env]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]

            [midje.sweet :refer [facts fact =>]]
            [lupapalvelu.itest-util :as itu :refer [pena pena-id raktark-helsinki]]

            [lupapalvelu.integrations.allu :as allu
             :refer [ALLUPlacementContracts ->LocalMockALLU PlacementContract
                     update-contract! cancel-allu-application! create-contract! lock-contract! allu-fail!]]))

;;;; Refutation Utilities
;;;; ===================================================================================================================

(def- drawings
  [{:id       1,
    :name     "A",
    :desc     "A desc",
    :category "123",
    :geometry "POLYGON((438952 6666883.25,441420 6666039.25,441920 6667359.25,439508 6667543.25,438952 6666883.25))",
    :area     "2686992",
    :height   "1"}
   {:id       2,
    :name     "B",
    :desc     "B desc",
    :category "123",
    :geometry "POLYGON((440652 6667459.25,442520 6668435.25,441912 6667359.25,440652 6667459.25))",
    :area     "708280",
    :height   "12"}])

(defn- nullify-doc-ids [doc]
  (-> doc
      (assoc-in [:data :henkilo :userId :value] nil)
      (assoc-in [:data :yritys :companyId :value] nil)))

(defn- create-and-fill-placement-app [apikey permitSubtype]
  (let [{:keys [id] :as response} (itu/create-local-app apikey
                                                        :operation (ssg/generate allu/SijoituslupaOperation)
                                                        :x "385770.46" :y "6672188.964"
                                                        :address "Kaivokatu 1"
                                                        :propertyId "09143200010023")
        documents [(nullify-doc-ids (ssg/generate (dds/doc-data-schema "hakija-ya" true)))
                   (ssg/generate (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true))
                   (ssg/generate (dds/doc-data-schema "yleiset-alueet-maksaja" true))]]
    (mongo/update-by-id :applications id {$set {:permitSubtype permitSubtype, :documents documents}})
    response))

(defn- check-request [schema-check? pending-on-client request]
  (fact "request is well-formed"
    (-> request :headers :authorization) => (str "Bearer " (env/value :allu :jwt))
    (:content-type request) => :json
    (let [contract (:form-params request)]
      (when schema-check?
        contract => #(nil? (sc/check PlacementContract %)))
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
    (check-request true true request)

    (create-contract! inner endpoint request))

  (update-contract! [_ endpoint request]
    (fact "endpoint is correct" endpoint => (re-pattern (str (env/value :allu :url) "/placementcontracts/\\d+")))
    (check-request false                                    ; Is allowed to be invalid so no schema check
                   true request)

    (update-contract! inner endpoint request))

  (lock-contract! [_ endpoint request]
    (fact "endpoint is correct" endpoint => (re-pattern (str (env/value :allu :url) "/placementcontracts/\\d+")))
    (check-request true false request)

    (lock-contract! inner endpoint request))

  (allu-fail! [_ text info-map] (allu-fail! inner text info-map)))

;; FIXME: DRY it up
(deftype ConstALLU [cancel-response creation-response locking-response fail-map failure-counter]
  ALLUPlacementContracts
  (cancel-allu-application! [_ _ _] cancel-response)
  (create-contract! [_ _ _] creation-response)
  (update-contract! [_ _ _] locking-response)
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
          (let [{:keys [id]} (create-and-fill-placement-app pena "sijoituslupa") => ok?
                {:keys [documents]} (domain/get-application-no-access-checking id)
                {descr-id :id} (first (filter #(= (doc-name %) "yleiset-alueet-hankkeen-kuvaus-sijoituslupa")
                                              documents))
                {applicant-id :id} (first (filter #(= (doc-name %) "hakija-ya") documents))]
            (itu/local-command pena :submit-application :id id) => ok?
            (count (:applications @allu-state)) => 1
            (-> (:applications @allu-state) first val :pendingOnClient) => true

            (itu/local-command pena :update-doc :id id :doc descr-id :updates [["kayttotarkoitus" "tuijottelu"]]) => ok?
            (-> (:applications @allu-state) first val :workDescription) => "tuijottelu"

            (itu/local-command pena :set-user-to-document :id id :documentId applicant-id
                               :userId pena-id :path "henkilo") => ok?
            (-> (:applications @allu-state) first val :customerWithContacts :customer :name) => "Pena Panaani"
            (itu/local-command pena :set-current-user-to-document :id id :documentId applicant-id :path "henkilo")
            => ok?
            (-> (:applications @allu-state) first val :customerWithContacts :customer :name) => "Pena Panaani"

            (itu/local-command pena :update-doc :id id :doc applicant-id :updates [["_selected" "yritys"]])
            (itu/local-command pena :set-company-to-document :id id :documentId applicant-id
                               :companyId "esimerkki" :path "yritys") => ok?
            (let [user (usr/get-user-by-id pena-id)]
              (itu/local-command pena :update-doc :id id :doc applicant-id
                                 :updates [["yritys.yhteyshenkilo.henkilotiedot.etunimi" (:firstName user)]
                                           ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" (:lastName user)]
                                           ["yritys.yhteyshenkilo.yhteystiedot.email" (:email user)]
                                           ["yritys.yhteyshenkilo.yhteystiedot.puhelin" (:phone user)]]))
            (-> (:applications @allu-state) first val :customerWithContacts :customer :name) => "Esimerkki Oy"

            (itu/local-command pena :save-application-drawings :id id :drawings drawings) => ok?
            (-> (:applications @allu-state) first val :geometry)
            => {:crs        {:type       "name"
                             :properties {:name "EPSG:4326"}}
                :type       "GeometryCollection"
                :geometries [{:type        "Polygon"
                              :coordinates [[[25.901018731807 60.134367126801]
                                             [25.945680922149 60.127151199835]
                                             [25.954302160106 60.139072939081]
                                             [25.910829826268 60.140374902637]
                                             [25.901018731807 60.134367126801]]]}
                             {:type        "Polygon"
                              :coordinates [[[25.931448002235 60.139788526718]
                                             [25.9647992127 60.148817662889]
                                             [25.954158152623 60.139071802112]
                                             [25.931448002235 60.139788526718]]]}]}

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
                                                               {:ok     false, :text "error.allu.http"
                                                                :status 400, :body "Your data was bad."}
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

      ;; FIXME: Does not actually restore an instance of the correct type:
      (mount/start #'allu/allu-instance))))
