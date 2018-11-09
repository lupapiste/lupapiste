(ns lupapalvelu.backing-system.allu-itest
  "Integration tests for ALLU integration. Using local (i.e. not over HTTP) testing style."
  (:require [clojure.string :as s]
            [lupapalvelu.json :as json]
            [monger.operators :refer [$set]]
            [mount.core :as mount]
            [reitit.ring :as reitit-ring]
            [schema.core :as sc]
            [taoensso.timbre :refer [warn]]
            [sade.core :refer [def- ok? fail?]]
            [sade.env :as env]
            [sade.files :refer [with-temp-file]]
            [sade.schema-generators :as ssg]
            [sade.shared-schemas :as sssc]
            [sade.util :refer [file->byte-array]]
            [lupapalvelu.attachment :refer [get-attachment-file!]]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]

            [midje.sweet :refer [facts fact => contains]]
            [lupapalvelu.itest-util :as itu :refer [pena pena-id raktark-helsinki]]
            [lupapalvelu.itest-util.model-based :refer [state-graph->transitions traverse-state-transitions]]

            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [SijoituslupaOperation PlacementContract AttachmentMetadata]])
  (:import [java.io InputStream]))

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

(defn- nullify-doc-ids
  "Make some foreign keys nil so that the tests don't try to load nonexistent users with generated id:s."
  [doc]
  (-> doc
      (assoc-in [:data :henkilo :userId :value] nil)
      (assoc-in [:data :yritys :companyId :value] nil)))

(defn- add-canceled->*
  "HACK: undo-cancellation bypasses the state graph so we add [:cancel *] arcs to the state graph with this."
  [state-graph]
  (update state-graph :canceled
          into
          (comp (filter (fn [[_ succs]] (some (partial = :canceled) succs)))
                (map key))
          state-graph))

(defn- create-and-fill-placement-app [apikey permitSubtype]
  (let [{:keys [id] :as response} (itu/create-local-app apikey
                                                        :operation (ssg/generate SijoituslupaOperation)
                                                        :x "385770.46" :y "6672188.964"
                                                        :address "Kaivokatu 1"
                                                        :propertyId "09143200010023")
        _ (fact "placement application created succesfully" response => ok?)
        documents [(nullify-doc-ids (ssg/generate (dds/doc-data-schema "hakija-ya" true)))
                   (ssg/generate (dds/doc-data-schema "yleiset-alueet-hankkeen-kuvaus-sijoituslupa" true))
                   (ssg/generate (dds/doc-data-schema "yleiset-alueet-maksaja" true))]]
    (mongo/update-by-id :applications id {$set {:permitSubtype permitSubtype, :documents documents}})
    id))

(defn- upload-attachment
  ([apikey app-id] (upload-attachment apikey app-id nil))
  ([apikey app-id attachment]
   (let [attachmentId (:id attachment)
         description "Test file"
         filedata {:type     {:type-group "muut", :type-id "muu"}
                   :contents description}]
     (fact "upload attachment file and bind"
       (itu/upload-file-and-bind apikey app-id filedata :attachment-id attachmentId)
       => (comp nil? (partial sc/check sssc/FileId)))
     (fact "add-comment"
       (itu/command apikey :add-comment :id app-id :text "Added my test text file." :target {:type "application"}
                    :roles ["applicant" "authority"]) => ok?)
     attachmentId)))

(defn- open [apikey app-id msg]
  (fact ":draft -> :open"
    (itu/command apikey :add-comment :id app-id :text msg :target {:type "application"}
                 :roles ["applicant" "authority"]) => ok?))

(defn- submit [apikey app-id]
  (fact "submit application"
    (itu/command apikey :submit-application :id app-id) => ok?))

(defn- fill [apikey app-id]
  (let [{:keys [documents]} (domain/get-application-no-access-checking app-id)
        {descr-id :id} (first (filter #(= (doc-name %) "yleiset-alueet-hankkeen-kuvaus-sijoituslupa") documents))
        {applicant-id :id} (first (filter #(= (doc-name %) "hakija-ya") documents))]
    (fact "fill application"
      (itu/command apikey :update-doc :id app-id :doc descr-id :updates [["kayttotarkoitus" "tuijottelu"]]) => ok?
      (itu/command apikey :save-application-drawings :id app-id :drawings drawings) => ok?

      (itu/command apikey :set-user-to-document :id app-id :documentId applicant-id :userId pena-id :path "henkilo")
      => ok?

      (itu/command apikey :set-current-user-to-document :id app-id :documentId applicant-id :path "henkilo") => ok?

      (itu/command apikey :set-company-to-document :id app-id :documentId applicant-id :companyId "esimerkki"
                   :path "yritys") => ok?
      (let [user (usr/get-user-by-id pena-id)]
        (itu/command apikey :update-doc :id app-id :doc applicant-id
                     :updates [["yritys.yhteyshenkilo.henkilotiedot.etunimi" (:firstName user)]
                               ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" (:lastName user)]
                               ["yritys.yhteyshenkilo.yhteystiedot.email" (:email user)]
                               ["yritys.yhteyshenkilo.yhteystiedot.puhelin" (:phone user)]])))

    #_(upload-attachment apikey app-id attachment)))

(defn- approve [apikey app-id]
  (fact "approve application"
    (itu/command apikey :approve-application :id app-id :lang "fi") => ok?

    ;; HACK: Testing move-attachments-to-backing-system here for lack of a better place:
    #_(upload-attachment apikey app-id)
    #_(let [{[{attachment-id :id}] :attachments} (domain/get-application-no-access-checking app-id {:attachments true})]
        (itu/command apikey :move-attachments-to-backing-system :id app-id :lang "fi" :attachmentIds [attachment-id])
        => ok?)))

(defn- return-to-draft [apikey app-id msg]
  (fact "return to draft"
    (itu/command apikey :return-to-draft :id app-id :text msg :lang "fi") => ok?))

(defn- request-for-complement [apikey id]
  (fact "request for complement"
    (itu/command apikey :request-for-complement :id id)
    => (contains {:ok     false
                  :text   "error.integration.unsupported-action"
                  :action "request-for-complement"})))

(defn- cancel [apikey app-id msg]
  (fact "cancel application"
    (itu/command apikey :cancel-application :id app-id :text msg :lang "fi") => ok?))

(defn- undo-cancellation [apikey app-id]
  (fact "undo cancellation"
    (itu/command apikey :undo-cancellation :id app-id)
    => (contains {:ok     false
                  :text   "error.integration.unsupported-action"
                  :action "undo-cancellation"})))

(defn- fetch-contract [apikey app-id]
  (itu/command apikey :check-for-verdict :id app-id))

(defn- sign-contract [apikey app-id]
  (fact "sign contract"
    (let [verdict-id (-> (lupapalvelu.domain/get-application-no-access-checking app-id)
                         :pate-verdicts
                         first
                         :id)]
      (itu/command apikey :sign-allu-contract :id app-id :verdict-id verdict-id :password "pena") => ok?)))

;;;; Mock Handler
;;;; ===================================================================================================================

(defn- check-response-ok-middleware
  "Middleware that tests that the response HTTP status is successful or that we should just re-login."
  [handler]
  (let [unauth-counter (atom 0)]
    (fn [request]
      (let [response (handler request)]
        (cond
          (and (= (:status response) 401)) (do (fact "login only needs to happen once" @unauth-counter => 0)
                                               (swap! unauth-counter inc))
          (= (-> request reitit-ring/get-match :data :name) [:placementcontracts :contract :proposal])
          (fact "proposal exists" response => (comp #{403 200 201} :status))

          :else (fact "response is successful" response => (comp #{200 201} :status)))
        response))))

(defn- check-imessages-middleware
  "Middleware that checks that `integration-messages` is updated with the request and response."
  [handler]
  (fn [{{:keys [application]} ::allu/command :as request}]
    (let [imsg-query (fn [direction]
                       {:partner        "allu"
                        :messageType    (s/join \. (map name (-> request reitit-ring/get-match :data :name)))
                        :direction      direction
                        :status         "done"
                        :application.id (:id application)})
          res (handler request)]
      (when-not (= (:uri request) "/login")                 ; HACK
        (fact "integration messages are saved"
          (mongo/any? :integration-messages (imsg-query "out")) => true
          (mongo/any? :integration-messages (imsg-query "in")) => true))
      res)))

(def- mock-jwt "foo.bar.baz")

(defn- mock-routes
  "Create mock Reitit routes whose handlers use `allu-state` as the ALLU DB."
  [allu-state]
  [["/login" {:post {:handler (fn [_] {:status 200, :body (json/encode mock-jwt)})}}]

   ["/"
    ["applications"
     ["/:id/cancelled"
      {:put {:handler (fn [{{:keys [id]} :path-params :keys [headers]}]
                        (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                          (if (contains? (:applications @allu-state) id)
                            (do (swap! allu-state update :applications dissoc id)
                                {:status 200, :body ""})
                            {:status 404, :body (str "Not Found: " id)})
                          {:status 401, :body "Unauthorized"}))}}]

     ["/:id/attachments"
      {:post {:handler (fn [{{:keys [id]} :path-params :keys [headers multipart]}]
                         (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                           (let [metadata-error (sc/check {:name      (sc/eq "metadata")
                                                           :mime-type (sc/eq "application/json")
                                                           :encoding  (sc/eq "UTF-8")
                                                           :content   AttachmentMetadata}
                                                          (update (first multipart) :content json/decode true))
                                 file-error (sc/check {:name      (sc/eq "file")
                                                       :mime-type sc/Str
                                                       :content   InputStream}
                                                      (second multipart))]
                             (if-let [validation-error (or metadata-error file-error)]
                               {:status 400, :body validation-error}
                               (if (contains? (:applications @allu-state) id)
                                 (let [attachment {:metadata (-> multipart (get-in [0 :content]) (json/decode true))}]
                                   (swap! allu-state update-in [:applications id :attachments] (fnil conj []) attachment)
                                   {:status 200, :body ""})
                                 {:status 404, :body (str "Not Found: " id)})))
                           {:status 401, :body "Unauthorized"}))}}]]


    ["placementcontracts"
     [""
      {:post {:handler (fn [{:keys [headers body]}]
                         (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                           (let [placement-contract (json/decode body true)]
                             (if-let [validation-error (sc/check PlacementContract placement-contract)]
                               {:status 400, :body validation-error}
                               (let [{:keys [id-counter]}
                                     (swap! allu-state (fn [{:keys [id-counter] :as state}]
                                                         (-> state
                                                             (update :id-counter inc)
                                                             (update :applications assoc (str id-counter)
                                                                     placement-contract))))]
                                 {:status 200, :body (str (dec id-counter))})))
                           {:status 401, :body "Unauthorized"}))}}]

     ["/:id"
      ["" {:put {:handler (fn [{{:keys [id]} :path-params :keys [headers body]}]
                            (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                              (let [placement-contract (json/decode body true)]
                                (if-let [validation-error (sc/check PlacementContract placement-contract)]
                                  {:status 400, :body validation-error}
                                  (if (contains? (:applications @allu-state) id)
                                    (if (get-in @allu-state [:applications id :pendingOnClient])
                                      (do (swap! allu-state assoc-in [:applications id] placement-contract)
                                          {:status 200, :body id})
                                      {:status 403, :body (str id " is not pendingOnClient")})
                                    {:status 404, :body (str "Not Found: " id)})))
                              {:status 401, :body "Unauthorized"}))}}]
      ["/contract"
       ["/proposal"
        {:get {:handler (fn [{{:keys [id]} :path-params :keys [headers]}]
                          (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                            (if (contains? (:applications @allu-state) id)
                              (if-not (get-in @allu-state [:applications id :pendingOnClient])
                                {:status  200, :body (file->byte-array "dev-resources/test-pdf.pdf"),
                                 :headers {"Content-Type" "application/pdf"}}
                                {:status 403, :body (str id " is pendingOnClient")})
                              {:status 404, :body (str "Not Found: " id)})
                            {:status 401, :body "Unauthorized"}))}}]
       ["/approved"
        {:post {:handler (fn [{{:keys [id]} :path-params :keys [headers]}]
                           (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                             (if (contains? (:applications @allu-state) id)
                               (if-not (get-in @allu-state [:applications id :pendingOnClient])
                                 (if-not (get-in @allu-state [:application id :approved])
                                   (do
                                     (swap! allu-state assoc-in [:application id :approved] true)
                                     {:status 200, :body ""})
                                   {:status 400, :body "Already signed"})
                                 {:status 403, :body (str id " is not pendingOnClient")})
                               {:status 404, :body (str "Not Found: " id)})
                             {:status 401, :body "Unauthorized"}))}}]
       ["/final"
        {:get {:handler (fn [{{:keys [id]} :path-params :keys [headers]}]
                          (if (= (get headers "authorization") (str "Bearer " mock-jwt))
                            (if (contains? (:applications @allu-state) id)
                              (if-not (get-in @allu-state [:applications id :pendingOnClient])
                                (if (get-in @allu-state [:application id :approved])
                                  {:status  200, :body (file->byte-array "dev-resources/test-pdf.pdf"),
                                   :headers {"Content-Type" "application/pdf"}}
                                  {:status 400, :body "Not signed"})
                                {:status 403, :body (str id " is not pendingOnClient")})
                              {:status 404, :body (str "Not Found: " id)})
                            {:status 401, :body "Unauthorized"}))}}]]]]]])

(defn- mock-router [allu-id]
  (reitit-ring/router (mock-routes allu-id)))

(defn- mock-allu
  "This pretends to be ALLU or more specifically, clj-http talking to ALLU."
  [allu-id]
  (reitit-ring/ring-handler (mock-router allu-id)))

(defn- make-test-router
  "A replacement for `allu/allu-router` that talks to `mock-allu` instead of clj-http and adds some middleware to do
  Midje checks."
  [allu-state]
  (reitit-ring/router (#'allu/routes false (mock-allu allu-state))
                      {:reitit.middleware/transform (fn [middlewares]
                                                      (-> [check-imessages-middleware]
                                                          (into middlewares)
                                                          (conj check-response-ok-middleware)))}))

;;;; Actual Tests
;;;; ===================================================================================================================

;;; TODO: (sc/with-fn-validation ...)

(env/with-feature-value :allu true
  (itu/with-local-actions
    (mongo/connect!)
    (mount/start #'allu/allu-jms-session #'allu/allu-jms-consumer)

    (facts "Usage of ALLU integration in commands"
      (mongo/with-db itu/test-db-name
        (lupapalvelu.fixture.core/apply-fixture "minimal")

        (let [initial-allu-state {:id-counter 0, :applications {}}
              allu-state (atom initial-allu-state)
              full-sijoitussopimus-state-graph (->> states/ya-sijoitussopimus-state-graph
                                                    add-canceled->*
                                                    ;; :complementNeeded should be unreachable:
                                                    (into {} (remove (fn [[src _]] (= src :complementNeeded)))))
              router (make-test-router allu-state)
              handler (comp (reitit-ring/ring-handler router) @#'allu/try-reload-allu-id)]
          (with-redefs [allu/allu-router router
                        allu/allu-request-handler handler]
            (facts "state transitions"
              (traverse-state-transitions
                :states full-sijoitussopimus-state-graph
                :initial-state :draft
                :init! (fn [] (create-and-fill-placement-app pena "sijoitussopimus"))
                :transition-adapters
                (into {} (map (fn [[src dest :as transition]]
                                [transition
                                 (if (= src :canceled)
                                   (fn [[current _] id]
                                     (undo-cancellation raktark-helsinki id)
                                     current)
                                   (case dest
                                     :draft (fn [[_ dest] id]
                                              (return-to-draft raktark-helsinki id "Nolo!")
                                              dest)
                                     :open (fn [[_ dest] id] (open pena id "YOLO") dest)
                                     :submitted (fn [[_ dest] id]
                                                  (fill pena id)
                                                  (submit pena id)
                                                  dest)
                                     :sent (fn [[_ dest] id] (approve raktark-helsinki id) dest)
                                     :canceled (fn [[current dest] id]
                                                 (if (contains? #{:draft :open} current)
                                                   (cancel pena id "Alkoi nolottaa.")
                                                   (cancel raktark-helsinki id "Nolo!"))
                                                 dest)
                                     :complementNeeded (fn [[current _] id]
                                                         (request-for-complement raktark-helsinki id)
                                                         current)
                                     :agreementPrepared (fn [[current dest] id]
                                                          (cond
                                                            (= current dest)
                                                            dest

                                                            (= current :submitted)
                                                            (do (fact "fetch contract"
                                                                  (fetch-contract raktark-helsinki id) => ok?)
                                                                current)

                                                            :else
                                                            (do (fact "fetch contract"
                                                                  (fetch-contract raktark-helsinki id) => ok?)
                                                                dest)))
                                     :agreementSigned (fn [[_ dest] id]
                                                        (sign-contract pena id)
                                                        dest)))]))
                      (state-graph->transitions full-sijoitussopimus-state-graph))
                :visit-goal 1))

            (let [old-id-counter (:id-counter @allu-state)]
              (fact "ALLU integration disabled for"
                (fact "Non-Helsinki sijoituslupa"
                  (let [{:keys [id]} (itu/create-local-app pena :operation (ssg/generate SijoituslupaOperation)) => ok?]
                    (itu/command pena :submit-application :id id) => ok?))

                (fact "Helsinki non-sijoituslupa"
                  (let [{:keys [id]} (itu/create-local-app pena
                                                           :operation "pientalo"
                                                           :x "385770.46" :y "6672188.964"
                                                           :address "Kaivokatu 1"
                                                           :propertyId "09143200010023") => ok?]
                    (itu/command pena :submit-application :id id) => ok?
                    (itu/command raktark-helsinki :approve-application :id id :lang "fi") => ok?))

                (:id-counter @allu-state) => (partial = old-id-counter)))))))))
