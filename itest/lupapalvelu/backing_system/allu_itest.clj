(ns lupapalvelu.backing_system.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
  (:require [clojure.java.io :as io]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [monger.operators :refer [$set]]
            [mount.core :as mount]
            [schema.core :as sc]
            [sade.core :refer [def- ok?]]
            [sade.env :as env]
            [sade.files :refer [with-temp-file]]
            [sade.schema-generators :as ssg]
            [lupapalvelu.attachment :refer [get-attachment-file!]]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]

            [midje.sweet :refer [facts fact =>]]
            [lupapalvelu.itest-util :as itu :refer [pena pena-id raktark-helsinki]]

            [lupapalvelu.backing-system.allu :as allu
             :refer [ALLUApplications -cancel-application! ALLUAttachments -send-attachment!
                     ALLUPlacementContracts ->MessageSavingALLU ->GetAttachmentFiles PlacementContract
                     -update-placement-contract! -create-placement-contract! allu-fail!]]))

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

(defn- check-request [schema-check? http-request]
  (fact "request is well-formed"
    (-> http-request :headers :authorization) => (str "Bearer " (env/value :allu :jwt))
    (let [contract (-> http-request :body (json/decode true))]
      (when schema-check?
        contract => #(nil? (sc/check PlacementContract %))))))

(deftype AtomMockALLU [state]
  ALLUApplications
  (-cancel-application! [_ _ {{endpoint :uri} :http-request}]
    (let [allu-id (second (re-find #".*/(\d+)/cancelled" endpoint))]
      (if (contains? (:applications @state) allu-id)
        (do (swap! state update :applications dissoc allu-id)
            {:status 200, :body ""})
        {:status 404, :body (str "Not Found: " allu-id)})))

  ALLUPlacementContracts
  (-create-placement-contract! [_ _ {:keys [http-request]}]
    (let [placement-contract (json/decode (:body http-request) true)]
      (if-let [validation-error (sc/check PlacementContract placement-contract)]
        {:status 400, :body validation-error}
        (let [local-mock-allu-state-push (fn [{:keys [id-counter] :as state}]
                                           (-> state
                                               (update :id-counter inc)
                                               (update :applications assoc (str id-counter) placement-contract)))
              {:keys [id-counter]} (swap! state local-mock-allu-state-push)]
          {:status 200, :body (str (dec id-counter))}))))

  (-update-placement-contract! [_ _ {{endpoint :uri :as http-request} :http-request}]
    (let [allu-id (second (re-find #".*/(\d+)" endpoint))
          placement-contract (json/decode (:body http-request) true)]
      (if-let [validation-error (sc/check PlacementContract placement-contract)]
        {:status 400, :body validation-error}
        (if (contains? (:applications @state) allu-id)
          (do (swap! state assoc-in [:applications allu-id] placement-contract)
              {:status 200, :body allu-id})
          {:status 404, :body (str "Not Found: " allu-id)}))))

  ALLUAttachments
  (-send-attachment! [_ _ {{endpoint :uri :as http-request} :http-request}]
    (let [allu-id (second (re-find #".*/applications/(\d+)/attachments" endpoint))]
      (if (contains? (:applications @state) allu-id)
        (let [attachment {:metadata (-> http-request (get-in [:body 0 :content]) (json/decode true))}]
          (swap! state update-in [:applications allu-id :attachments] (fnil conj []) attachment)
          {:status 200, :body ""})
        {:status 404, :body (str "Not Found: " allu-id)}))))

(defn- checking-integration-messages [{:keys [id]} message-type body]
  (let [imsg-query (fn [direction]
                     {:partner        "allu"
                      :messageType    message-type
                      :direction      direction
                      :status         "done"
                      :application.id id})
        res (body)]
    (fact "integration messages are saved"
      (mongo/any? :integration-messages (imsg-query "out")) => true
      (mongo/any? :integration-messages (imsg-query "in")) => true)
    res))

(deftype CheckingALLU [inner]
  ALLUApplications
  (-cancel-application! [_ {:keys [application] :as command} {:keys [http-request] :as request}]
    (:uri http-request)

    (fact "endpoint is correct"
      (:uri http-request) => (re-pattern (str (env/value :allu :url) "/applications/\\d+/cancelled")))
    (fact "request is well-formed"
      (-> http-request :headers :authorization) => (str "Bearer " (env/value :allu :jwt)))

    (checking-integration-messages application "applications.cancel" #(-cancel-application! inner command request)))

  ALLUPlacementContracts
  (-create-placement-contract! [_ {:keys [application] :as command} {:keys [http-request] :as request}]
    (fact "endpoint is correct" (:uri http-request) => (str (env/value :allu :url) "/placementcontracts"))
    (check-request true http-request)

    (checking-integration-messages application "placementcontracts.create"
                                   #(-create-placement-contract! inner command request)))

  (-update-placement-contract! [_ {:keys [application] :as command} {:keys [http-request] :as request}]
    (fact "endpoint is correct" (:uri http-request) => (re-pattern (str (env/value :allu :url) "/placementcontracts/\\d+")))
    (check-request false http-request)

    (checking-integration-messages application "placementcontracts.update"
                                   #(-update-placement-contract! inner command request)))

  ALLUAttachments
  (-send-attachment! [_ {:keys [application] :as command} {:keys [http-request] :as request}]
    (fact "endpoint is correct"
      (:uri http-request) => (re-pattern (str (env/value :allu :url) "/applications/\\d+/attachments")))
    (fact "request is well-formed"
      (-> http-request :headers :authorization) => (str "Bearer " (env/value :allu :jwt))
      (-> http-request (get-in [:body 0 :content]) (json/decode true) keys set) => #{:name :description :mimeType}
      (get-attachment-file! application (-> http-request (get-in [:body 1]) :content)) => some?)

    (checking-integration-messages application "attachments.create" #(-send-attachment! inner command request))))

(deftype ConstALLU [cancel-response attach-response creation-response update-response]
  ALLUApplications
  (-cancel-application! [_ _ _] cancel-response)
  ALLUPlacementContracts
  (-create-placement-contract! [_ _ _] creation-response)
  (-update-placement-contract! [_ _ _ ] update-response)
  ALLUAttachments
  (-send-attachment! [_ _ _] attach-response))

;;;; Actual Tests
;;;; ===================================================================================================================

;;; TODO: (sc/with-fn-validation ...)

(env/with-feature-value :allu true
  (mongo/connect!)

  (facts "Usage of ALLU integration in commands"
    (mongo/with-db itu/test-db-name
      (lupapalvelu.fixture.core/apply-fixture "minimal")

      (let [initial-allu-state {:id-counter 0, :applications {}}
            allu-state (atom initial-allu-state)
            failure-counter (atom 0)]
        (mount/start-with {#'allu/allu-instance
                           (->CheckingALLU (->MessageSavingALLU (->GetAttachmentFiles (->AtomMockALLU allu-state))))})

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

              (let [filename "dev-resources/test-attachment.txt"]
                ;; HACK: Have to use a temp file as :upload-attachment expects to get one and deletes it in the end.
                (with-temp-file file
                  (io/copy (io/file filename) file)
                  (let [description "Test file"
                        description* "The best file"
                        _ (itu/local-command pena :upload-attachment :id id :attachmentId (:id attachment)
                                             :attachmentType {:type-group "muut", :type-id "muu"} :group {}
                                             :filename filename :tempfile file :size (.length file)) => ok?
                        _ (itu/local-command pena :set-attachment-meta :id id :attachmentId (:id attachment)
                                             :meta {:contents description}) => ok?
                        {[attachment] :attachments} (domain/get-application-no-access-checking id)
                        expected-attachments [{:metadata {:name        description
                                                          :description (localize "fi" :attachmentType
                                                                                 (-> attachment :type :type-group)
                                                                                 (-> attachment :type :type-id))
                                                          :mimeType    (-> attachment :latestVersion :contentType)}}
                                              {:metadata {:name        ""
                                                          :description (localize "fi" :attachmentType :muut
                                                                                 :keskustelu)
                                                          :mimeType    "application/pdf"}}]
                        expected-attachments* (conj expected-attachments
                                                    (assoc-in (first expected-attachments)
                                                              [:metadata :name] description*))]
                    (itu/local-command pena :add-comment :id id :text "Added my test text file."
                                       :target {:type "application"} :roles ["applicant" "authority"]) => ok?
                    (itu/local-command raktark-helsinki :approve-application :id id :lang "fi") => ok?

                    (count (:applications @allu-state)) => 1
                    (-> (:applications @allu-state) first val :pendingOnClient) => false
                    (-> (:applications @allu-state) first val :attachments) => expected-attachments

                    (io/copy (io/file filename) file)
                    ;; Upload another attachment for :move-attachments-to-backing-system to send:
                    (itu/local-command raktark-helsinki :upload-attachment :id id :attachmentId (:id attachment)
                                       :attachmentType {:type-group "muut", :type-id "muu"} :group {}
                                       :filename filename :tempfile file :size (.length file)) => ok?
                    (itu/local-command raktark-helsinki :set-attachment-meta :id id :attachmentId (:id attachment)
                                       :meta {:contents description*}) => ok?
                    (itu/local-command raktark-helsinki :move-attachments-to-backing-system :id id :lang "fi"
                                       :attachmentIds [(:id attachment)]) => ok?

                    (-> (:applications @allu-state) first val :attachments) => expected-attachments*

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
                      (count (:applications @allu-state)) => 1))))))

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
            (mount/start-with {#'allu/allu-instance
                               (->MessageSavingALLU (->GetAttachmentFiles
                                                      (->ConstALLU {:status 200} {:status 200}
                                                                   {:status 400, :body "Your data was bad."} nil)))})
            (let [{:keys [id]} (create-and-fill-placement-app pena "sijoituslupa") => ok?]
              (itu/local-command pena :submit-application :id id)
              @failure-counter => 1)

            (reset! failure-counter 0)

            (mount/start-with {#'allu/allu-instance
                               (->MessageSavingALLU (->GetAttachmentFiles
                                                      (->ConstALLU {:status 200} {:status 200}
                                                                   {:status 401, :body "You are unauthorized."} nil)))})
            (let [{:keys [id]} (create-and-fill-placement-app pena "sijoitussopimus") => ok?]
              (itu/local-command pena :submit-application :id id)
              @failure-counter => 1))))))

  (mount/stop #'allu/allu-instance)
  (mount/start-with {#'allu/allu-instance (allu/make-allu)}))
