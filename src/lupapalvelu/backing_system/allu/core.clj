(ns lupapalvelu.backing-system.allu.core
  "JSON REST API integration with ALLU as backing system. Used for Helsinki YA instead of SFTP/HTTP KRYSP XML
  integration."
  (:require [clojure.core.match :refer [match]]
            [lupapalvelu.allu.allu-application :as allu-application]
            [lupapalvelu.application :as application]
            [lupapalvelu.backing-system.allu.conversion :refer [application->allu-placement-contract
                                                                application->allu-promotion
                                                                application->allu-short-term-rental format-date-time
                                                                lang]]
            [lupapalvelu.backing-system.allu.schemas :refer [allu-date-string ApplicationHistory
                                                             ApplicationHistoryParams ApplicationKind ApplicationType
                                                             AttachmentFile AttachmentMetadata ContractMetadata
                                                             DecisionMetadata LoginCredentials PlacementContract
                                                             Promotion ShortTermRental]]
            [lupapalvelu.document.allu-schemas :refer [application-kinds application-types]]
            [lupapalvelu.file-upload :refer [save-file SavedFileData]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.integrations.messages :as imessages :refer [IntegrationMessage]]
            [lupapalvelu.integrations.pubsub :as lip]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :as log :refer [debug error errorf info warn]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer [$in $set]]
            [reitit.coercion.schema]
            [reitit.core :as reitit]
            [reitit.ring :as reitit-ring]
            [reitit.ring.coercion]
            [reitit.ring.middleware.multipart :refer [multipart-middleware]]
            [sade.core :refer [def- fail! now]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc :refer [NonBlankStr]]
            [sade.strings :as ss]
            [sade.util :as util :refer [assoc-when file->byte-array fn->]]
            [schema.core :as sc :refer [defschema optional-key]]
            [slingshot.slingshot :refer [try+]])
  (:import [java.io ByteArrayInputStream InputStream]
           [java.time ZonedDateTime]))

(set! *warn-on-reflection* true)

;;;; Login State
;;;; ===================================================================================================================

(defonce ^{:private true
           :doc     "The JWT to be used for ALLU API authorization. Occasionally replaced by re-login."}
  current-jwt
  (atom (env/value :allu :jwt)))

(defn logged-in?
  "Has login been performed? NOTE: even if this returns true, login might have expired."
  []
  ;; Default value has been replaced (by login).
  (not= @current-jwt (env/value :allu :jwt)))

;;;; Initial request construction
;;;; ===================================================================================================================

(defschema ^:private MiniCommand
  "A command that has been abridged for sending through the message queue."
  {(optional-key :application) {:id           ssc/ApplicationId
                                :organization sc/Str
                                :state        (apply sc/enum (map name states/all-states))}
   :action                     sc/Str
   :user                       {:id       sc/Str
                                :username sc/Str}})

(sc/defn ^{:private true, :always-validate true} minimize-command :- MiniCommand
  [{:keys [application action user]}]
  (assoc-when {:action action
               :user   (select-keys user (keys (:user MiniCommand)))}
              :application (some-> application
                                   (select-keys (keys (get MiniCommand (optional-key :application)))))))

(declare allu-router)

(defn- route-match->request-method
  "Get the HTTP method from a Reitit route match."
  [route-match]
  (some #{:get :put :post :delete} (keys (:data route-match))))

(defn- login-request
  "Construct an ALLU login request for `send-allu-request!` based on reverse routing on `allu-router`, `username`
  and `password`."
  [username password]
  (let [route-match (reitit/match-by-name allu-router [:login])]
    {:uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           {:username username, :password password}}))

(defn- application-cancel-request
  "Construct an ALLU application cancel request for `send-allu-request!` based on reverse routing on `allu-router`
  and `command`."
  [{:keys [application] :as command}]
  (let [allu-id (get-in application [:integrationKeys :ALLU :id])
        route-match (reitit/match-by-name allu-router [:applications :cancel] {:id allu-id})]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

(sc/defn ^:private application-histories-request [command applications, after :- ZonedDateTime]
  (let [params      {:body {:applicationIds (map #(Long/parseLong (get-in % [:integrationKeys :ALLU :id]))
                                                 applications)
                            :eventsAfter    (format-date-time after)}}
        route-match (reitit/match-by-name allu-router [:applicationhistory])]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (:body params)}))

(defn application-type
  "Dispatches on the command :application :permitSubtype for placement contracts (=sijoitussopimus) and
  :application :operation-name for other permits for public area usage (e.g. Lyhytaikainen maanvuokra, promootio etc.)."
  [application]
  (or (:permitSubtype application) (:operation-name application)))

(defn- allu-application-routes-base [application]
  (case (application-type application)
    "sijoitussopimus"            :placementcontracts
    "lyhytaikainen-maanvuokraus" :short-term-rental
    "promootio"                  :promotion))

(defmulti ^:private application-creation-request
  "Construct an ALLU application creation request for `send-allu-request!` based on reverse routing on
  `allu-router` and `command`."
  (fn [{:keys [application] :as _command}] (application-type application)))

(defn application-creation-request! [command]
  (try
    (application-creation-request command)
    (catch Exception e
      (fail! :error.integration.create-message :details (ex-message e)))))

;;; TODO: DRY these up:

(defmethod application-creation-request :default [{{:keys [permitSubtype operation-name]} :application}]
  (fail! :error.allu.unsupportedPermitSubtype {:permitSubtype permitSubtype :operation-name operation-name}))

(defmethod application-creation-request "sijoitussopimus" [{:keys [application] :as command}]
  (let [route-match (reitit/match-by-name allu-router [:placementcontracts :create])]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (application->allu-placement-contract true application)}))

(defmethod application-creation-request "lyhytaikainen-maanvuokraus" [{:keys [application] :as command}]
  (let [route-match (reitit/match-by-name allu-router [:short-term-rental :create])]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (application->allu-short-term-rental true application)}))

(defmethod application-creation-request "promootio" [{:keys [application] :as command}]
  (let [route-match (reitit/match-by-name allu-router [:promotion :create])]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (application->allu-promotion true application)}))

(defmulti application-update-request
  "Construct an ALLU application update request for `send-allu-request!` based on reverse routing on
  `allu-router`, `_pending-on-client` and `command`."
  (fn [_pending-on-client {:keys [application] :as _command}] (application-type application)))

(defmethod application-update-request :default
  [_pending-on-client {{:keys [permitSubtype operation-name]} :application}]
  (fail! :error.allu.unsupportedPermitSubtype {:permitSubtype permitSubtype :operation-name operation-name}))

(defmethod application-update-request "sijoitussopimus" [pending-on-client {:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        _ (when-not allu-id
            (error "missing allu-id in application" application)
            (throw (Exception. "Missing allu-id in application")))
        params {:path {:id allu-id}
                :body (application->allu-placement-contract pending-on-client
                                                            application)}
        route-match (reitit/match-by-name allu-router [:placementcontracts :update] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (:body params)}))

(defmethod application-update-request "lyhytaikainen-maanvuokraus" [pending-on-client {:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}
                :body (application->allu-short-term-rental pending-on-client
                                                           application)}
        route-match (reitit/match-by-name allu-router [:short-term-rental :update] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (:body params)}))

(defmethod application-update-request "promootio" [pending-on-client {:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}
                :body (application->allu-promotion pending-on-client
                                                   application)}
        route-match (reitit/match-by-name allu-router [:promotion :update] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (:body params)}))

(defn- any-attachment-send
  [{:keys [application] :as command} metadata file-description]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path      {:id allu-id}
                :multipart {:metadata metadata
                            :file     file-description}}
        route-match (reitit/match-by-name allu-router [:attachments :create] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :multipart      [{:name      "metadata"
                       :mime-type "application/json"
                       :encoding  "UTF-8"
                       :content   (-> params :multipart :metadata)}
                      {:name      "file"
                       :mime-type (-> params :multipart :metadata :mimeType)
                       :content   (-> params :multipart :file)}]}))

(defn- attachment-send-self
  "Construct an ALLU attachment upload request to convert the application to pdf and send it to ALLU as an attachment."
  [command]
  (any-attachment-send command
                       {:name        (str (localize lang :application.applicationSummary) ".pdf")
                        :description (let [type (localize lang :attachmentType "muut" "muu")
                                           description (localize lang :application.applicationSummary)]
                                       (if (or (not description) (= type description))
                                         type
                                         (str type ": " description)))
                        :mimeType    "application/pdf"}
                       {:attach-self true}))

(defn- attachment-send
  "Construct an ALLU attachment upload request for `send-allu-request!` based on reverse routing on `allu-router`,
  `command` and `attachment` (one of `(-> command :application :attachments)`)."
  [command {{:keys [type-group type-id]} :type :keys [latestVersion] :as attachment}]
  (any-attachment-send command
                       {:name        (:filename latestVersion)
                        :description (let [type (localize lang :attachmentType type-group type-id)
                                           description (:contents attachment)]
                                       (if (or (not description) (= type description))
                                         type
                                         (str type ": " description)))
                        :mimeType    (:contentType latestVersion)}
                       (select-keys latestVersion [:fileId :storageSystem])))

(defn- contract-proposal-request [{:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}}
        route-match (reitit/match-by-name allu-router [:placementcontracts :contract :proposal] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

(defn- contract-approval [{:keys [application user] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}
                :body {:signer      (str (:firstName user) " " (:lastName user))
                       :signingTime (format-date-time (now))}}
        route-match (reitit/match-by-name allu-router [:placementcontracts :contract :approved] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (:body params)}))

(defn- final-contract-request [{:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}}
        route-match (reitit/match-by-name allu-router [:placementcontracts :contract :final] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

(defn- decision-request [{:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}}
        route-match (reitit/match-by-name allu-router
                                          [(allu-application-routes-base application) :decision]
                                          (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

(defn- decision-metadata-request [{:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}}
        route-match (reitit/match-by-name allu-router
                                          [(allu-application-routes-base application) :decision :metadata]
                                          (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

(defn- allu-application-data [{:keys [application] :as command} raw?]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}}
        route-match (reitit/match-by-name allu-router [:applications :allu-data] (:path params))]
    {::command       (minimize-command command)
     ::raw-response? raw?
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

(defn- contract-metadata
  [{:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}}
        route-match (reitit/match-by-name allu-router [:placementcontracts :contract :metadata] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

;;;; IntegrationMessage construction
;;;; ===================================================================================================================

(sc/defn ^{:private true :always-validate true} base-integration-message :- IntegrationMessage
  "Construct an ALLU integration specific `IntegrationMessage` for the `integration-messages` DB collection."
  [{:keys [application user action]} :- MiniCommand message-subtype direction status data]
  (assoc-when {:id           (mongo/create-id)
               :direction    direction
               :messageType  message-subtype
               :transferType "http"
               :partner      "allu"
               :format       "json"
               :created      (now)
               :status       status
               :initiator     user
               :action       action
               :data         data}
              :application application))

(defn- request-integration-message
  "Construct an `IntegrationMessage` for an ALLU API request."
  [command http-request message-subtype]
  (base-integration-message command message-subtype "out" "processing" http-request))

(defn- response-integration-message
  "Construct an `IntegrationMessage` for an ALLU API response."
  [command endpoint http-response message-subtype]
  (base-integration-message command message-subtype "in" "done"
                            {:endpoint endpoint
                             :response (select-keys http-response [:status :body])}))

;;;; HTTP request sender for production
;;;; ===================================================================================================================

;; TODO: Use clj-http directly so that this isn't needed:
(def- http-method-fns {:get http/get, :post http/post, :put http/put})

(defn- perform-http-request!
  "Send a HTTP `request` to `(str base-url uri)`."
  [base-url {:keys [request-method uri] :as request}]
  ((request-method http-method-fns) (str base-url uri) (assoc request :throw-exceptions false)))

(defn- make-remote-handler
  "Curried version of `perform-http-request!`."
  [allu-url]
  (fn [request]
    (perform-http-request! allu-url request)))

;;;; Mock for interactive development
;;;; ===================================================================================================================

(defn- response-ok?
  "Does the `integration-messages DB collection contain a successful response (for the message type) of the
  application with `allu-id`."
  [allu-id message-type]
  (if allu-id
    (mongo/any? :integration-messages {:direction            "in" ; i.e. the response
                                       :messageType          message-type
                                       :status               "done"
                                       :application.id       (format "LP-%s-%s-%s"
                                                                     (ss/substring allu-id 0 3)
                                                                     (ss/substring allu-id 3 7)
                                                                     (ss/substring allu-id 7 12))
                                       :data.response.status {$in [200 201]}})
    false))

(defonce ^{:private true :doc "Are we logged in to the dev mock ALLU?"}
  mock-logged-in? (atom false))

(def placement-contract-checker (sc/checker PlacementContract))
(def short-term-rental-checker (sc/checker ShortTermRental))
(def promotion-checker (sc/checker Promotion))

;; This approximates the ALLU state with the `imessages` data:
(defn- imessages-mock-handler
  "Mock for the ALLU API, can be called instead of `perform-http-request!`:ing to the actual remote ALLU API."
  [request]
  (let [route-match (reitit-ring/get-match request)]
    (if (and (not @mock-logged-in?) (not= (-> route-match :data :name) [:login]))
      {:status 401 :body "Unauthorized"}
      (match (-> route-match :data :name)
        ;; Login

        [:login] (let [{:keys [username password]} (json/decode (:body request) true)]
                   (if (and (= username (env/value :allu :username))
                            (= password (env/value :allu :password)))
                     (do (reset! mock-logged-in? true)
                         {:status 200, :body (json/encode password)})
                     {:status 404, :body "Wrong username and/or password"}))

        ;; Generic Application

        [:applications :cancel] (let [id (-> route-match :path-params :id)]
                                  (if (response-ok? id "placementcontracts.create")
                                    {:status 200, :body ""}
                                    {:status 404, :body (str "Not Found: " id)}))

        [:applications :allu-data] (let [id (-> route-match :path-params :id)]
                                     {:status 400}
                                     {:status 200
                                      :body   (json/encode {:applicationId (str "SL19000" id)})})

        [:applicationhistory] (let [ids (-> request :body (json/decode true) :applicationIds)
                                    histories (map (fn [id] (assoc (ssg/generate ApplicationHistory) :applicationId id))
                                                   ids)]
                                {:status 200, :body (json/encode histories)})

        ;; Sijoitussopimus

        [:placementcontracts :create] (let [body (json/decode (:body request) true)]
                                        (if-let [validation-error (placement-contract-checker body)]
                                          {:status 400, :body validation-error}
                                          {:status 200
                                           :body   (.replace (subs (:identificationNumber body) 3) "-" "")}))

        [:placementcontracts :update] (let [id (-> route-match :path-params :id)
                                            body (json/decode (:body request) true)]
                                        (if-let [validation-error (placement-contract-checker body)]
                                          {:status 400, :body validation-error}
                                          (if (response-ok? id "placementcontracts.create")
                                            {:status 200, :body id}
                                            {:status 404, :body (str "Not Found: " id)})))

        [:placementcontracts :decision] (let [id (-> route-match :path-params :id)]
                                          (if (response-ok? id "placementcontracts.create")
                                            {:status  200
                                             :headers {"Content-Type" "application/pdf"}
                                             :body    (file->byte-array "dev-resources/test-pdf.pdf"),}
                                            {:status 404, :body (str "Not Found: " id)}))

        [:placementcontracts :decision :metadata] {:status  200
                                                   :headers {"Content-Type" "application/json"},
                                                   :body    (json/encode {:handler       {:name  "Hannu Helsinki"
                                                                                          :title "Director"}
                                                                          :decisionMaker {:name  "Decider Dave"
                                                                                          :title "Uber-driver"}})}

        [:placementcontracts :contract :proposal]
        (let [id (-> route-match :path-params :id)]
          (if (response-ok? id "placementcontracts.create")
            {:status  200, :body (file->byte-array "dev-resources/test-pdf.pdf"),
             :headers {"Content-Type" "application/pdf"}}
            {:status 404, :body (str "Not Found: " id)}))

        [:placementcontracts :contract :final]
        (let [id (-> route-match :path-params :id)]
          (if (response-ok? id "placementcontracts.contract.approved")
            {:status  200, :body (file->byte-array "dev-resources/test-pdf.pdf"),
             :headers {"Content-Type" "application/pdf"}}
            {:status 404, :body (str "Not Found: " id)}))

        [:placementcontracts :contract :metadata]
        (let [id (-> route-match :path-params :id)]
          (if (response-ok? id "placementcontracts.create")
            {:status  200
             :headers {"Content-Type" "application/json"}
             :body    (json/encode {:creationTime  (format-date-time (now))
                                    :handler       {:name  "Hannu Helsinki"
                                                    :title "Director"}
                                    :decisionMaker {:name  "Decider Dave"
                                                    :title "Uber-driver"}
                                    :status        (if (response-ok? id "placementcontracts.contract.approved")
                                                     "FINAL"
                                                     "PROPOSAL")})}
            {:status 404, :body (str "Not Found: " id)}))

        [:placementcontracts :contract :approved] (let [id (-> route-match :path-params :id)]
                                                    (if (response-ok? id "placementcontracts.create")
                                                      {:status 200, :body ""}
                                                      {:status 404, :body (str "Not Found: " id)}))

        ;; Lyhytaikainen maanvuokraus

        [:short-term-rental :create] (let [body (json/decode (:body request) true)]
                                       (if-let [validation-error (short-term-rental-checker body)]
                                         {:status 400, :body validation-error}
                                         {:status 200
                                          :body   (.replace (subs (:identificationNumber body) 3) "-" "")}))

        [:short-term-rental :update] (let [id (-> route-match :path-params :id)
                                           body (json/decode (:body request) true)]
                                       (if-let [validation-error (short-term-rental-checker body)]
                                         {:status 400, :body validation-error}
                                         (if (response-ok? id "short-term-rental.create")
                                           {:status 200, :body id}
                                           {:status 404, :body (str "Not Found: " id)})))

        [:short-term-rental :decision] (let [id (-> route-match :path-params :id)]
                                         (if (response-ok? id "short-term-rental.create")
                                           {:status  200
                                            :headers {"Content-Type" "application/pdf"}
                                            :body    (file->byte-array "dev-resources/test-pdf.pdf"),}
                                           {:status 404, :body (str "Not Found: " id)}))

        [:short-term-rental :decision :metadata] {:status  200
                                                  :headers {"Content-Type" "application/json"},
                                                  :body    (json/encode {:handler       {:name  "Hannu Helsinki"
                                                                                         :title "Director"}
                                                                         :decisionMaker {:name  "Decider Dave"
                                                                                         :title "Uber-driver"}})}

        ;; Promootio

        [:promotion :create] (let [body (json/decode (:body request) true)]
                               (if-let [validation-error (promotion-checker body)]
                                 {:status 400, :body validation-error}
                                 {:status 200
                                  :body   (.replace (subs (:identificationNumber body) 3) "-" "")}))

        [:promotion :update] (let [id (-> route-match :path-params :id)
                                   body (json/decode (:body request) true)]
                               (if-let [validation-error (promotion-checker body)]
                                 {:status 400, :body validation-error}
                                 (if (response-ok? id "promotion.create")
                                   {:status 200, :body id}
                                   {:status 404, :body (str "Not Found: " id)})))

        [:promotion :decision] (let [id (-> route-match :path-params :id)]
                                 (if (response-ok? id "promotion.create")
                                   {:status  200
                                    :headers {"Content-Type" "application/pdf"}
                                    :body    (file->byte-array "dev-resources/test-pdf.pdf")}
                                   {:status 404, :body (str "Not Found: " id)}))

        [:promotion :decision :metadata] {:status  200
                                          :headers {"Content-Type" "application/json"},
                                          :body    (json/encode {:handler       {:name  "Hannu Helsinki"
                                                                                 :title "Director"}
                                                                 :decisionMaker {:name  "Decider Dave"
                                                                                 :title "Uber-driver"}})}

        ;; Attachments

        [:attachments :create] (let [id (-> route-match :path-params :id)]
                                 (if (response-ok? id "placementcontracts.create")
                                   {:status 200, :body ""}
                                   {:status 404, :body (str "Not Found: " id)}))))))

;;;; Custom middlewares
;;;; ===================================================================================================================

;;; TODO: Use interceptors instead, the *->middleware fn:s are kind of silly when interceptors exist.

(defn route-name->string
  "Join a `[Keyword]` into a string with dots and throwing away the keyword semicolons."
  [path] (ss/join \. (map name path)))

(defn- preprocessor->middleware
  "(Request -> Request) -> ((Request -> Response) -> (Request -> Response))
  For the ML illiterates, turn a fn from request to request into a middleware function."
  [preprocess]
  (fn [handler] (fn [request] (handler (preprocess request)))))

(defn- post-action->middleware
  "(Response -> ()) -> ((Request -> Response) -> (Request -> Response))
  i.e. turn a response-using imperative action into a middleware function."
  [post-act!]
  (fn [handler] (fn [request] (let [response (handler request)] (post-act! response) response))))

(defn- load-allu-id
  "Load ALLU id from DB, throw if not found."
  [app-id]
  (let [allu-id (:id (application/load-integration-key app-id :ALLU))]
    (assert allu-id (str app-id " does not contain an ALLU id"))
    allu-id))

;; HACK:
(defn- try-reload-allu-id
  "Reload ALLU id from application if the request has empty string for it."
  [{:keys [uri] :as request}]
  (let [{:keys [path-params] :as route-match} (reitit/match-by-path allu-router uri)] ; OPTIMIZE
    (cond
      (and (some? route-match) (contains? path-params :id) (empty? (:id path-params)))
      (let [allu-id (load-allu-id (-> request ::command :application :id))]
        (assoc request :uri (:path (reitit/match-by-name allu-router (-> route-match :path :name) {:id allu-id}))))

      (and (nil? route-match) (ss/includes? uri "//"))
      (let [allu-id (load-allu-id (-> request ::command :application :id))]
        (assoc request :uri (ss/replace uri "//" (str "/" allu-id "/"))))

      :else request)))

(defn- body&multipart-as-params
  "Copy the request :body into :body-params or :multipart into :multipart-params when they exist."
  [request]
  (cond
    (contains? request :body) (assoc request :body-params (:body request))
    (contains? request :multipart) (letfn [(params+part [multipart-params part]
                                             (assoc multipart-params (keyword (:name part)) (:content part)))]
                                     (assoc request :multipart-params (reduce params+part {} (:multipart request))))
    :else request))

(defn- jwt-authorize
  "Add the authorization header for `current-jwt` to `request`."
  [request]
  (assoc-in request [:headers "authorization"] (str "Bearer " @current-jwt)))

(defn- content->json
  "Convert the request :body or :multipart content into JSON (unless the :multipart part is the attachment file)."
  [request]
  (cond
    (contains? request :body) (-> request (update :body json/encode) (assoc :content-type :json))
    (contains? request :multipart) (update request :multipart
                                           (partial mapv (fn [part]
                                                           (if (= (:name part) "file")
                                                             part
                                                             (update part :content json/encode)))))
    :else request))

(defn- json-response
  "Convert response body to JSON (if status is succesful)."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (case (int (:status response))
        (200 201) (if-not (::raw-response? request)
                    (update response :body json/decode true)
                    response)
        response))))

(declare allu-fail!)

(sc/defn ^:private verdict-filename :- sc/Str
  [id :- ssc/ApplicationId, verdict-type :- (sc/enum :decision :proposal :final)]
  (str id (case verdict-type
            :decision "-paatos.pdf"
            :proposal "-sopimusehdotus.pdf"
            :final "-sopimus.pdf")))

(defn- open-request-file-id
  "Replace the attachment file id in the request [:multipart 1 :content] with the file contents `InputStream`."
  [disable-io? handler]
  (if disable-io?
    (fn [request]
      (with-open [^InputStream contents (ByteArrayInputStream. (byte-array 0))]
        (handler (assoc-in request [:multipart 1 :content] contents))))
    (fn [{{:keys [application user]} ::command :as request}]
      (let [{:keys [attach-self fileId storageSystem]} (get-in request [:multipart 1 :content])]
        (if-some [file-map (if attach-self
                             {:content (fn [] (pdf-export/export-submitted-application user lang (:id application)))}
                             (storage/download-from-system (:id application) fileId storageSystem))]
          (with-open [^InputStream contents ((:content file-map))]
            (handler (assoc-in request [:multipart 1 :content] contents)))
          (allu-fail! :error.file-not-found {:fileId fileId}))))))

(defn- save-response-body!
  "Store pdf file to file storage when downloading contract."
  [disable-io? handler]
  (if disable-io?
    (fn [{{:keys [application user]} ::command :as request}]
      (let [request (assoc request :as :byte-array)
            response (handler request)]
        (if (= (:status response) 200)
          (let [^bytes content-bytes (:body response)
                saved-file-data {:fileId      (str (random-uuid))
                                 :filename    (verdict-filename (:id application)
                                                                (last (-> request reitit-ring/get-match :data :name)))
                                 :size        (alength content-bytes)
                                 :contentType (get-in response [:headers "Content-Type"])
                                 :metadata    {:linked           false
                                               :uploader-user-id (:id user)}}]
            (assoc response :body saved-file-data))
          response)))
    (fn [{{:keys [application user]} ::command :as request}]
      (let [request (assoc request :as :byte-array)
            response (handler request)]
        (if (= (:status response) 200)
          (let [^bytes content-bytes (:body response)
                file-data {:filename     (verdict-filename (:id application)
                                                           (last (-> request reitit-ring/get-match :data :name)))
                           :content      (ByteArrayInputStream. content-bytes)
                           :content-type (get-in response [:headers "Content-Type"])
                           :size         (alength content-bytes)}
                metadata {:linked           false
                          :uploader-user-id (:id user)}]
            (assoc response :body (save-file file-data metadata)))
          response)))))

(defn- save-messages!
  "Save the request and response into the `integration-messages` DB collection."
  [handler]
  (fn [{command ::command :as request}]
    (let [endpoint (-> request :uri)
          message-subtype (route-name->string (-> request reitit-ring/get-match :data :name))
          {msg-id :id :as msg} (request-integration-message
                                 command
                                 (select-keys request [:uri :request-method :body :multipart])
                                 message-subtype)
          _ (imessages/save msg)
          response (handler request)]
      (imessages/update-message msg-id {$set {:status "done", :acknowledged (now)}})
      (imessages/save (response-integration-message command endpoint response message-subtype))
      response)))

(def- allu-fail!
  "A hook for testing error cases. Calls `sade.core/fail!` by default."
  (fn [text info-map] (fail! text info-map)))

(declare allu-request-handler)

(defn login!
  "Log in to ALLU, `reset!`:ing `current-jwt`."
  []
  (allu-request-handler (login-request (env/value :allu :username) (env/value :allu :password))))


(defn- relogin-and-retry-on-auth-errors!
  "Re-login and retry original call on HTTP 401 or 403."
  [handler]
  (fn [request]
    (letfn [(retry [{:keys [status] :as response} & [exception]]
              (cond
                (util/includes-as-kw? #{401 403} status)
                (do
                  ;; Retry (but just once since in tail position this time).
                  (info "ALLU login and retry:"
                        (select-keys response [:status :body])
                        (some-> exception ex-message))
                  (login!)
                  (handler request))

                exception (throw exception)
                :else     response))]
      (try
        (retry (handler request))
        (catch Exception e
          (retry (ex-data e) e))))))

(defn- reset-jwt!
  "Reset `current-jwt` to the new one from login response."
  [{:keys [body]}]
  (reset! current-jwt (json/decode body)))                  ; for consistency body is a JSON-encoded string

(defn- set-allu-integration-key!
  "If creation was successful, set the ALLU integration key to be the creation response body."
  [handler]
  (fn [{{{app-id :id} :application} ::command :as request}]
    (match (handler request)
      ({:status (:or 200 201), :body body} :as response) (do (application/set-integration-key app-id :ALLU {:id body})
                                                             response)
      response response)))

(defn- set-allu-kuntalupatunnus!
  "If request was successful, store ALLU details about the application to db"
  [handler]
  (fn [{{{app-id :id} :application} ::command :as request}]
    (match (handler request)
      ({:status (:or 200 201), :body body} :as response)
      (do (application/set-kuntalupatunnus app-id (:applicationId body))
          response)

      response response)))

(declare save-allu-application-data!)

(defn- get-allu-kuntalupatunnus!
  "Makes a new GET request to fetch kuntalupatunnus from ALLU (middleware in that request stores it to db)"
  [handler]
  (fn [request]
    (let [{:keys [body status] :as response} (handler request)
          _           (when-not (contains? #{200 201 202 203 204} status)
                        (throw (ex-info (str "Getting ALLU id failed: " body) response)))
          ;; Successful response should contain the ALLU id in the body
          new-command (assoc-in (::command request) [:application :integrationKeys :ALLU :id] body)]
      (save-allu-application-data! new-command)
      response)))

(defn- log-or-fail!
  "`allu-fail!` on HTTP errors, else do logging."
  [handler]
  (fn [request]
    (let [route-name (route-name->string (-> request reitit-ring/get-match :data :name))]
      (match (handler request)
        ({:status (:or 200 201), :body body} :as response)
        (do (info "ALLU operation" route-name "succeeded")
            response)

        response (allu-fail! :error.allu.http (-> response (select-keys [:status :body]) (assoc :route-name route-name)))))))

;;;; Router and request handler
;;;; ===================================================================================================================

(defn- innermost-handler
  "Make the innermost handler that either is an ALLU-specific HTTP client function or a mockery thereof."
  ([] (innermost-handler (env/dev-mode?)))
  ([dev-mode?] (if dev-mode? imessages-mock-handler (make-remote-handler (env/value :allu :url)))))

(declare uses-contract-documents?)

(defn- decision-routes [base-name handler {:keys [save-responses? disable-io?]}]
  ["/decision"
   ["" {:name       [base-name :decision]
        :middleware (if save-responses? [(partial save-response-body! disable-io?)] [])
        :get        {:handler handler}}]

   ["/metadata" {:name       [base-name :decision :metadata]
                 :middleware [json-response]
                 :get        {:handler handler}}]])

(defn- contract-routes [base-name handler {:keys [save-responses? disable-io?]}]
  ["/contract"
   ["/proposal" {:name       [base-name :contract :proposal]
                 :middleware (if save-responses? [(partial save-response-body! disable-io?)] [])
                 :get        {:handler handler}}]

   ["/approved" {:name       [base-name :contract :approved]
                 :parameters {:body {:signer      NonBlankStr
                                     :signingTime allu-date-string}}
                 :post       {:handler handler}}]

   ["/final" {:name       [base-name :contract :final]
              :middleware (if save-responses? [(partial save-response-body! disable-io?)] [])
              :get        {:handler handler}}]

   ["/metadata" {:name       [base-name :contract :metadata]
                 :middleware [json-response]
                 :get        {:handler handler}}]])

(defn- application-type-routes
  [base-fragment base-name ApplicationSubtype handler {:keys [save-responses? disable-io?] :as options}]
  [base-fragment
   ["" {:name       [base-name :create]
        :parameters {:body ApplicationSubtype}
        :middleware (if (or disable-io? (not save-responses?))
                      [get-allu-kuntalupatunnus!]
                      [get-allu-kuntalupatunnus! set-allu-integration-key!])
        :post       {:handler handler}}]

   (let [id-routes ["/:id" {:parameters {:path {:id ssc/NatString}}}
                    ["" {:name       [base-name :update]
                         :parameters {:body ApplicationSubtype}
                         :put        {:handler handler}}]
                    (decision-routes base-name handler options)]]
     (if (= base-name :placementcontracts)
       (conj id-routes (contract-routes base-name handler options))
       id-routes))])

(defn- routes
  "Compute the Reitit routes that call `handler` at their core. Special use case of reitit as this does not declare a
  public API but an internal routing for requests to external APIs."
  [handler {:keys [save-responses? disable-io?] :as options}]
  [["/login" {:middleware [(preprocessor->middleware body&multipart-as-params)
                           reitit.ring.coercion/coerce-request-middleware
                           (post-action->middleware reset-jwt!)
                           log-or-fail!
                           (preprocessor->middleware content->json)]
              :name       [:login]
              :parameters {:body LoginCredentials}
              :post       {:handler handler}}]

   ["/" {:middleware (-> [(preprocessor->middleware body&multipart-as-params)
                          multipart-middleware
                          reitit.ring.coercion/coerce-request-middleware
                          log-or-fail!
                          relogin-and-retry-on-auth-errors!]
                         (into (if disable-io? [] [save-messages!]))
                         (conj (preprocessor->middleware (fn-> content->json jwt-authorize))))
         :coercion   reitit.coercion.schema/coercion}
    ["applications"
     ["/:id" {:parameters {:path {:id ssc/NatString}}}
      ["" {:name       [:applications :allu-data]
           :middleware (if (or disable-io? (not save-responses?))
                         [json-response]
                         [set-allu-kuntalupatunnus! json-response])
           :get        {:handler handler}}]

      ["/cancelled" {:name [:applications :cancel]
                     :put  {:handler handler}}]

      ["/attachments" {:name       [:attachments :create]
                       :parameters {:multipart {:metadata AttachmentMetadata
                                                :file     AttachmentFile}}
                       :middleware [(partial open-request-file-id disable-io?)]
                       :post       {:handler handler}}]]]

    ["applicationhistory" {:name       [:applicationhistory]
                           :parameters {:body ApplicationHistoryParams}
                           :middleware [json-response]
                           :post       {:handler handler}}]

    (application-type-routes "placementcontracts" :placementcontracts PlacementContract handler options)
    (application-type-routes "shorttermrentals" :short-term-rental ShortTermRental handler options)
    (application-type-routes "events" :promotion Promotion handler options)]

   ["/" {:middleware [reitit.ring.coercion/coerce-request-middleware
                      log-or-fail!
                      (preprocessor->middleware (fn-> content->json jwt-authorize))
                      json-response]
         :coercion   reitit.coercion.schema/coercion}
    ["fixedlocations" {:name       [:fixedlocations]
                       :parameters {:query {:applicationKind (apply sc/enum (vals application-kinds))}}
                       :get        {:handler handler}}]
    ["applicationkinds" {:name       [:applicationkinds]
                         :parameters {:query {:applicationType (apply sc/enum (vals application-types))}}
                         :get        {:handler handler}}]]])

(def- allu-router
  "The Reitit router for ALLU requests."
  (reitit-ring/router (routes (innermost-handler) {:save-responses? true, :disable-io? false})))

(def- allu-nosave-router
  "Like [[allu-router]], but does not save returned Allu data to database or blob storage."
  (reitit-ring/router (routes (innermost-handler) {:save-responses? false, :disable-io? false})))

(def allu-request-handler
  "ALLU request handler. Returns HTTP response, calls `allu-fail!` on HTTP errors."
  (comp (reitit-ring/ring-handler allu-router) ;; <- this crashes because id is "{\"timestamp\":\"2023-11-29T07:19:49.316+00:00\",\"status\":403,\"error\":\"Forbidden\",\"path\":\"/v1/placementcontracts\"}"
        try-reload-allu-id))

(def- allu-nosave-request-handler
  "Like [[allu-request-handler]], but uses [[allu-nosave-router]] instead of [[allu-router]]."
  (comp (reitit-ring/ring-handler allu-nosave-router) try-reload-allu-id))

;;;; Message queue resources
;;;; ===================================================================================================================

(defn- handle-pubsub-message
  [_system {{{app-id :id} :application {user-id :user} :user} ::command uri :uri :as msg}]
  (log/with-logging-context {:userId user-id :applicationId app-id}
    (try
      (debug "Handling ALLU message:" (pr-str msg))
      (allu-request-handler (dissoc msg :db-name))
      true
      (catch Throwable ex
        (let [operation-name        (route-name->string (-> (reitit/match-by-path allu-router uri) :data :name))
              {:keys [status body]} (ex-data ex)]
          (cond
            (contains? #{400 403 404} status)
            ;; ACK because presumably this error is not recoverable
            (do (error ex "ACKing message, ALLU responded with a client error:" (.getMessage ex) status body
                       "- Pub/Sub message content was:" (pr-str msg))
                true)

            (some-> ex .getMessage (ss/includes? "Request coercion failed"))
            ;; ACK because we botched the payload previously ourselves and it cannot be sent
            (do (error ex "ACKing message because the request was not compatible with our ALLU API schema"
                       "- Pub/Sub message content was:" (pr-str msg))
                true)

            :else
            (error ex "NACKing failed operation" operation-name "- Pub/Sub message content was:" (pr-str msg))))))))

(defmethod lip/get-pubsub-subscription-handler :allu/message-queue
  [_id]
  handle-pubsub-message)

(defn- send-allu-request!
  "Send an ALLU request. Will put the request in the `allu-message-queue-name` queue if the message queue is enabled, otherwise calls
  `allu-request-handler` directly."
  [{:keys [system]} request]
  (when-not (:uri request)
    (error "Request message missing uri" request)
    (throw (Exception. "Request message missing uri")))
  ;; TODO: `system` should be passed forward (maybe in `request`?). If we are called outside Allu,
  ;; everything is fine as real commands contain `system`. But Allu also invokes itself using
  ;; requests with "minified" commands, which are stored in DB and do NOT contain `system`. These
  ;; requests may end up calling `send-allu-request!` _again_, but without the `system`.
  (if-let [publisher (and system (components/system->publisher system :allu/message-queue))]
    (do (debug "Publishing ALLU message:" (pr-str request))
        (publisher request))
    (allu-request-handler request)))

;;;; Mix up pure and impure into an API
;;;; ===================================================================================================================

(def allu-application?
  "Should ALLU integration be used?"
  allu-application/allu-application?)

(defn uses-contract-documents?
  "Does this application use contract documents from ALLU (in addition to decision documents)?"
  [application]
  (= (application-type application) "sijoitussopimus"))

(declare update-application!)

(defn- log-http-error
  "ALLU can respond with 404 when a request is correct but the document is not yet available."
  [usecase {:keys [status] :as err}]
  (if (= 404 status)
    (warn "ALLU" usecase "not found:" err)
    (errorf "HTTP error while loading %s: %s" usecase err)))

(defn submit-application!
  "Submit application to ALLU and save the returned id as application.integrationKeys.ALLU.id. If this is a resubmit
  (after return-to-draft) just does `update-application!` instead."
  [{:keys [application] :as command}]
  {:pre [(map? (:system command))]}
  (if-not (get-in application [:integrationKeys :ALLU :id])
    (send-allu-request! command (application-creation-request! command))
    (update-application! command)))

(defn load-allu-application-data
  "GET application data from ALLU."
  [command raw?]
  (try+
    (:body (allu-nosave-request-handler (allu-application-data command raw?)))
    (catch [:text "error.allu.http"] err
      (log-http-error "contract document" err))))

(defn save-allu-application-data!
  "GET application data from ALLU and store it to db."
  [command]
  (send-allu-request! command (allu-application-data command false)))

;; TODO: If the update message data is the same as the previous one or invalid, don't send/enqueue the message at all:
(defn update-application!
  "Update application in ALLU (if it had been sent there)."
  [{:keys [application] :as command}]
  (when (application/submitted? application)
    (send-allu-request! command (application-update-request true command))))

(defn lock-application!
  "Lock application in ALLU for verdict evaluation."
  [command]
  (send-allu-request! command (application-update-request false command)))

(defn cancel-application!
  "Cancel application in ALLU (if it had been sent there)."
  [{:keys [application] :as command}]
  (when (application/submitted? application)
    (send-allu-request! command (application-cancel-request command))))

(defn- send-attachment!
  "Send `attachment` of `application` to ALLU. Return the fileId of the file that was sent."
  [command attachment]
  (send-allu-request! command (attachment-send command attachment))
  (-> attachment :latestVersion :fileId))

(defn send-attachments!
  "Send the specified `attachments` of `(:application command)` to ALLU.
  Returns a seq of attachment file IDs that were sent."
  [command attachments]
  (doall (for [attachment attachments]
           (send-attachment! command attachment))))

(defn send-application-as-attachment!
  "Convert application to pdf and send it to ALLU as an attachment"
  [command]
  (send-allu-request! command (attachment-send-self command)))

(sc/defn application-history-fetchable? :- sc/Bool
  "Is contract or decision for the application available in ALLU according to the [[ApplicationHistory]]?"
  [{:keys [events]} :- ApplicationHistory]
  (boolean (some (fn [{:keys [newStatus]}]
                   (or (= newStatus "WAITING_CONTRACT_APPROVAL")
                       (= newStatus "DECISION")))
                 events)))

(sc/defn application-histories :- [ApplicationHistory]
  "Get history events starting at `after` for `applications` from ALLU. Bypasses the message queue."
  [command applications, after :- ZonedDateTime]
  (:body (allu-request-handler (application-histories-request command applications after))))

(sc/defn application-history :- ApplicationHistory
  "Get history events starting at `after` for `application` from ALLU. Bypasses the message queue. When handling multiple applications
  prefer [[application-histories]] for efficiency."
  [{:keys [application] :as command}, after :- ZonedDateTime]
  (first (application-histories command [application] after)))

(declare load-contract-metadata)

(defn approve-placementcontract!
  "Approve placementcontract proposal in ALLU.
  Despite the similar name, has nothing to do with the approve-application command."
  [command]
  (send-allu-request! command (contract-approval command)))

(sc/defn load-contract-document! :- (sc/maybe SavedFileData)
  "Load placement contract proposal or final from ALLU. Returns SavedFileData of the pdf or nil. Bypasses the message queue."
  [command agreement-state]
  (try+
    (:body (allu-request-handler (case agreement-state
                                   :proposal (contract-proposal-request command)
                                   :final (final-contract-request command))))
    (catch [:text "error.allu.http"] err
      (log-http-error "contract document" err))))

(sc/defn load-decision-document! :- (sc/maybe SavedFileData)
  "Try to load application decision document from ALLU. Bypasses the message queue."
  [command]
  (try+
    (:body (allu-request-handler (decision-request command)))
    (catch [:text "error.allu.http"] err
      (log-http-error "decision document " err))))

(sc/defn load-contract-metadata :- (sc/maybe ContractMetadata)
  "GET placement contract metadata from ALLU."
  [command]
  (try+
    (:body (allu-request-handler (contract-metadata command)))
    (catch [:text "error.allu.http"] err
      (log-http-error "contract metadata " err))))

(sc/defn load-decision-metadata :- (sc/maybe DecisionMetadata)
  "GET application decision metadata from ALLU."
  [command]
  (try+
    (:body (allu-request-handler (decision-metadata-request command)))
    (catch [:text "error.allu.http"] err
      (log-http-error "decision metadata" err))))

(defn- semiconstants-call [route-name params]
  (let [route-match (reitit/match-by-name allu-router route-name)
        uri (:path route-match)]
    (try+
      (:body (allu-request-handler {:uri            uri
                                    :query-params   (:query params)
                                    :request-method (route-match->request-method route-match)
                                    :insecure?      true}))
      (catch [:text "error.allu.http"] err
        (error uri " HTTP error " err)
        nil))))

(sc/defn ^:always-validate load-application-kinds!
  "Fetch [[ApplicationKind]]:s for `type` from ALLU."
  [type :- ApplicationType]
  (semiconstants-call [:applicationkinds] {:query {:applicationType (get application-types type)}}))

(sc/defn ^:always-validate load-fixed-locations!
  "Fetch [[FixedLocation]]:s for `kind` from ALLU."
  [kind :- ApplicationKind]
  (semiconstants-call [:fixedlocations] {:query {:applicationKind (get application-kinds kind)}}))
