(ns lupapalvelu.backing_system.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
  (:require [clojure.java.io :as io]
            [mount.core :as mount]
            [schema.core :as sc]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [monger.operators :refer [$set]]
            [sade.core :refer [ok?]]
            [sade.schema-generators :as ssg]
            [sade.env :as env]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]

            [midje.sweet :refer [facts fact =>]]
            [lupapalvelu.itest-util :as itu :refer [pena pena-id raktark-helsinki]]

            [lupapalvelu.backing-system.allu :as allu
             :refer [ALLUApplications cancel-allu-application! ALLUAttachments send-allu-attachment!
                     ALLUPlacementContracts ->LocalMockALLU ->MessageSavingALLU PlacementContract
                     update-contract! create-contract! lock-contract! allu-fail!]]))

;;;; Refutation Utilities
;;;; ===================================================================================================================

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
  ALLUApplications
  (cancel-allu-application! [_ command endpoint request]
    (fact "endpoint is correct" endpoint => (re-pattern (str (env/value :allu :url) "/applications/\\d+/cancelled")))
    (fact "request is well-formed"
      (-> request :headers :authorization) => (str "Bearer " (env/value :allu :jwt)))

    (cancel-allu-application! inner command endpoint request))

  ALLUAttachments
  (send-allu-attachment! [_ command endpoint request]
    (fact "endpoint is correct" endpoint => (re-pattern (str (env/value :allu :url) "/applications/\\d+/attachments")))
    (fact "request is well-formed"
      (-> request :headers :authorization) => (str "Bearer " (env/value :allu :jwt))
      (-> request :multipart count) => 2
      (-> request (get-in [:multipart 0]) keys set) => #{:name :mime-type :encoding :content}
      (-> request (get-in [:multipart 0]) (select-keys [:name :mime-type :encoding]))
      => {:name "metadata", :mime-type "application/json", :encoding "UTF-8"}
      (-> request (get-in [:multipart 1]) keys set) => #{:name :mime-type :content}
      (-> request (get-in [:multipart 1]) :name) => "file")

    (send-allu-attachment! inner command endpoint request))

  ALLUPlacementContracts
  (create-contract! [_ command endpoint request]
    (fact "endpoint is correct" endpoint => (str (env/value :allu :url) "/placementcontracts"))
    (check-request true true request)

    (create-contract! inner command endpoint request))

  (update-contract! [_ command endpoint request]
    (fact "endpoint is correct" endpoint => (re-pattern (str (env/value :allu :url) "/placementcontracts/\\d+")))
    (check-request false                                    ; Is allowed to be invalid so no schema check
                   true request)

    (update-contract! inner command endpoint request))

  (lock-contract! [_ command endpoint request]
    (fact "endpoint is correct" endpoint => (re-pattern (str (env/value :allu :url) "/placementcontracts/\\d+")))
    (check-request true false request)

    (lock-contract! inner command endpoint request)))

(deftype ConstALLU [cancel-response attach-response creation-response update-response]
  ALLUApplications
  (cancel-allu-application! [_ _ _ _] cancel-response)
  ALLUAttachments
  (send-allu-attachment! [_ _ _ _] attach-response)
  ALLUPlacementContracts
  (create-contract! [_ _ _ _] creation-response)
  (update-contract! [_ _ _ _] update-response)
  (lock-contract! [_ _ _ _] update-response))

;;;; Actual Tests
;;;; ===================================================================================================================

(env/with-feature-value :allu true
  (mongo/connect!)

  (facts "Usage of ALLU integration in commands"
    (mongo/with-db itu/test-db-name
      (lupapalvelu.fixture.core/apply-fixture "minimal")

      (let [initial-allu-state {:id-counter 0, :applications {}}
            allu-state (atom initial-allu-state)
            failure-counter (atom 0)]
        (mount/start-with {#'allu/allu-instance (->CheckingALLU (->MessageSavingALLU (->LocalMockALLU allu-state)))})

        (binding [allu-fail! (fn [text info-map]
                               (fact "error text" text => :error.allu.http)
                               (fact "response is 4**" info-map => http/client-error?)
                               (swap! failure-counter inc))]
          (fact "enabled and sending correctly to ALLU for Helsinki YA sijoituslupa and sijoitussopimus."
            (let [{:keys [id]} (create-and-fill-placement-app pena "sijoituslupa") => ok?
                  {[attachment] :attachments :keys [documents]} (domain/get-application-no-access-checking id)
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
              ;; Leads to ALLU failure because contact person info is not set:
              (itu/local-command pena :set-company-to-document :id id :documentId applicant-id
                                 :companyId "esimerkki" :path "yritys") => ok?
              (let [user (usr/get-user-by-id pena-id)]
                (itu/local-command pena :update-doc :id id :doc applicant-id
                                   :updates [["yritys.yhteyshenkilo.henkilotiedot.etunimi" (:firstName user)]
                                             ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" (:lastName user)]
                                             ["yritys.yhteyshenkilo.yhteystiedot.email" (:email user)]
                                             ["yritys.yhteyshenkilo.yhteystiedot.puhelin" (:phone user)]]))
              (-> (:applications @allu-state) first val :customerWithContacts :customer :name) => "Esimerkki Oy"

              (let [filename "dev-resources/test-attachment.txt"
                    file (io/file filename)
                    description "Test file"
                    description* "The best file"
                    _ (itu/local-command pena :upload-attachment :id id :attachmentId (:id attachment)
                                         :attachmentType {:type-group "muut", :type-id "muu"} :group {}
                                         :filename filename :tempfile file :size (.length file)) => ok?
                    _ (itu/local-command pena :set-attachment-meta :id id :attachmentId (:id attachment)
                                         :meta {:contents description}) => ok?
                    {[attachment] :attachments} (domain/get-application-no-access-checking id)]
                (itu/local-command raktark-helsinki :approve-application :id id :lang "fi") => ok?

                (count (:applications @allu-state)) => 1
                (-> (:applications @allu-state) first val :pendingOnClient) => false
                (-> (:applications @allu-state) first val :attachments)
                => [{:metadata {:name        description
                                :description (localize "fi" :attachmentType
                                                       (-> attachment :type :type-group)
                                                       (-> attachment :type :type-id))
                                :mimeType    (-> attachment :latestVersion :contentType)}}]

                ;; Upload another attachment for :move-attachments-to-backing-system to send:
                (itu/local-command raktark-helsinki :upload-attachment :id id :attachmentId (:id attachment)
                                   :attachmentType {:type-group "muut", :type-id "muu"} :group {}
                                   :filename filename :tempfile file :size (.length file)) => ok?
                (itu/local-command raktark-helsinki :set-attachment-meta :id id :attachmentId (:id attachment)
                                   :meta {:contents description*}) => ok?
                (itu/local-command raktark-helsinki :move-attachments-to-backing-system :id id :lang "fi"
                                   :attachmentIds [(:id attachment)]) => ok?

                (-> (:applications @allu-state) first val :attachments (get 1))
                => {:metadata {:name        description*
                               :description (localize "fi" :attachmentType
                                                      (-> attachment :type :type-group)
                                                      (-> attachment :type :type-id))
                               :mimeType    (-> attachment :latestVersion :contentType)}}))

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

            (:id-counter @allu-state) => 0)

          (fact "error responses from ALLU produce `fail!`ures"
            (mount/start-with {#'allu/allu-instance (->MessageSavingALLU
                                                      (->ConstALLU {:status 200} {:status 200}
                                                                   {:status 400, :body "Your data was bad."} nil))})
            (let [{:keys [id]} (create-and-fill-placement-app pena "sijoituslupa") => ok?]
              (itu/local-command pena :submit-application :id id)
              @failure-counter => 2)

            (reset! failure-counter 0)

            (mount/start-with {#'allu/allu-instance (->MessageSavingALLU
                                                      (->ConstALLU {:status 200} {:status 200}
                                                                   {:status 401, :body "You are unauthorized."} nil))})
            (let [{:keys [id]} (create-and-fill-placement-app pena "sijoitussopimus") => ok?]
              (itu/local-command pena :submit-application :id id)
              @failure-counter => 1))))

      ;; FIXME: Does not actually restore an instance of the correct type:
      (mount/start #'allu/allu-instance))))
