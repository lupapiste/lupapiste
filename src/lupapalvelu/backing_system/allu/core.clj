(ns lupapalvelu.backing-system.allu.core
  "JSON REST API integration with ALLU as backing system. Used for Helsinki YA instead of SFTP/HTTP KRYSP XML
  integration."
  (:require [clojure.string :as s]
            [clojure.core.match :refer [match]]
            [clojure.walk :refer [postwalk]]
            [monger.operators :refer [$set $in]]
            [cheshire.core :as json]
            [lupapiste-jms.client :as jms-client]
            [mount.core :refer [defstate]]
            [schema.core :as sc :refer [defschema optional-key enum]]
            [reitit.core :as reitit]
            [reitit.coercion.schema]
            [reitit.ring :as reitit-ring]
            [reitit.ring.coercion]
            [reitit.ring.middleware.multipart :refer [multipart-middleware]]
            [taoensso.timbre :refer [info error]]
            [taoensso.nippy :as nippy]
            [sade.util :refer [dissoc-in assoc-when fn->]]
            [sade.core :refer [def- fail! now]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [lupapalvelu.application :as application]
            [lupapalvelu.attachment :refer [get-attachment-file!]]
            [lupapalvelu.backing-system.allu.conversion :refer [lang application->allu-placement-contract]]
            [lupapalvelu.backing-system.allu.schemas :refer [LoginCredentials PlacementContract AttachmentMetadata]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.messages :as imessages :refer [IntegrationMessage]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states])
  (:import [java.lang AutoCloseable]
           [java.io InputStream]))

(defstate ^:private current-jwt
  "The JWT to be used for ALLU API authorization. Occasionally replaced by re-login."
  :start (atom (env/value :allu :jwt)))

;;;; Initial request construction
;;;; ===================================================================================================================

(defschema ^:private MiniCommand
  "A command that has been abridged for sending through JMS."
  {:application                               {:id           ssc/ApplicationId
                                               :organization sc/Str
                                               :state        (apply sc/enum (map name states/all-states))}
   :action                                    sc/Str
   :user                                      {:id       sc/Str
                                               :username sc/Str}
   ;; TODO: Refactor `get-attachment-files!`, then remove this:
   (sc/optional-key :latestAttachmentVersion) {:fileId        sssc/FileId
                                               :storageSystem sssc/StorageSystem}})

(sc/defn ^{:private true, :always-validate true} minimize-command :- MiniCommand
  ([{:keys [application action user]}]
    {:application (select-keys application (keys (:application MiniCommand)))
     :action      action
     :user        (select-keys user (keys (:user MiniCommand)))})
  ([command attachment]
    (let [mini-attachment (-> (:latestVersion attachment)
                              (select-keys (keys (get MiniCommand (sc/optional-key :latestAttachmentVersion))))
                              (update :storageSystem keyword))]
      (assoc (minimize-command command) :latestAttachmentVersion mini-attachment))))

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
        _ (assert allu-id (str (:id application) " does not contain an ALLU id"))
        route-match (reitit/match-by-name allu-router [:applications :cancel] {:id allu-id})]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

(defmulti application-creation-request
          "Construct an ALLU application creation request for `send-allu-request!` based on reverse routing on
          `allu-router` and `command`. Dispatches on the command :application :permitSubtype."
          (fn [command] (-> command :application :permitSubtype)))

(defmethod application-creation-request :default [{{:keys [permitSubtype]} :application}]
  (fail! :error.allu.unsupportedPermitSubtype {:permitSubtype permitSubtype}))

(defmethod application-creation-request "sijoitussopimus" [{:keys [application] :as command}]
  (let [route-match (reitit/match-by-name allu-router [:placementcontracts :create])]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (application->allu-placement-contract true application)}))

(defmulti application-update-request
          "Construct an ALLU application update request for `send-allu-request!` based on reverse routing on
          `allu-router`, `_pending-on-client` and `command`. Dispatches on the command :application :permitSubtype."
          (fn [_pending-on-client command] (-> command :application :permitSubtype)))

(defmethod application-update-request :default [_pending-on-client {{:keys [permitSubtype]} :application}]
  (fail! :error.allu.unsupportedPermitSubtype {:permitSubtype permitSubtype}))

(defmethod application-update-request "sijoitussopimus" [pending-on-client {:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        _ (assert allu-id (str (:id application) " does not contain an ALLU id"))
        params {:path {:id allu-id}
                :body (application->allu-placement-contract pending-on-client
                                                            application)}
        route-match (reitit/match-by-name allu-router [:placementcontracts :update] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (:body params)}))

(defn- attachment-send
  "Construct an ALLU attachment upload request for `send-allu-request!` based on reverse routing on `allu-router`,
  `command` and `attachment` (one of `(-> command :application :attachments)`)."
  [{:keys [application] :as command} {{:keys [type-group type-id]} :type :keys [latestVersion] :as attachment}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        _ (assert allu-id (str (:id application) " does not contain an ALLU id"))
        params {:path      {:id allu-id}
                :multipart {:metadata {:name        (:filename latestVersion)
                                       :description (let [type (localize lang :attachmentType type-group type-id)
                                                          description (:contents attachment)]
                                                      (if (or (not description) (= type description))
                                                        type
                                                        (str type ": " description)))
                                       :mimeType    (:contentType latestVersion)}
                            :file     (:fileId latestVersion)}}
        route-match (reitit/match-by-name allu-router [:attachments :create] (:path params))]
    {::command       (minimize-command command attachment)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :multipart      [{:name      "metadata"
                       :mime-type "application/json"
                       :encoding  "UTF-8"
                       :content   (-> params :multipart :metadata)}
                      {:name      "file"
                       :mime-type (:contentType latestVersion)
                       :content   (-> params :multipart :file)}]}))

;;;; IntegrationMessage construction
;;;; ===================================================================================================================

(sc/defn ^{:private true :always-validate true} base-integration-message :- IntegrationMessage
  "Construct an ALLU integration specific `IntegrationMessage` for the `integration-messages` DB collection."
  [{:keys [application user action]} :- MiniCommand message-subtype direction status data]
  {:id           (mongo/create-id)
   :direction    direction
   :messageType  message-subtype
   :transferType "http"
   :partner      "allu"
   :format       "json"
   :created      (now)
   :status       status
   :application  application
   :initator     user
   :action       action
   :data         data})

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
(def- http-method-fns {:post http/post, :put http/put})

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

(defn- creation-response-ok?
  "Does the `integration-messages DB collection contain a successful creation response of the application with
  `allu-id`."
  [allu-id]
  (mongo/any? :integration-messages {:direction            "in" ; i.e. the response
                                     :messageType          "placementcontracts.create"
                                     :status               "done"
                                     :application.id       (format "LP-%s-%s-%s"
                                                                   (subs allu-id 0 3)
                                                                   (subs allu-id 3 7)
                                                                   (subs allu-id 7 12))
                                     :data.response.status {$in [200 201]}}))

(defstate ^:private mock-logged-in?
  "Are we logged in to the dev mock ALLU?"
  :start (atom false))

;; This approximates the ALLU state with the `imessages` data:
(defn- imessages-mock-handler
  "Mock for the ALLU API, can be called instead of `perform-http-request!`:ing to the actual remote ALLU API."
  [request]
  (let [route-match (reitit-ring/get-match request)]
    (if (and (not @mock-logged-in?) (not= (-> route-match :data :name) [:login]))
      {:status 401 :body "Unauthorized"}
      (match (-> route-match :data :name)
        [:login] (let [{:keys [username password]} (json/decode (:body request) true)]
                   (if (and (= username (env/value :allu :username))
                            (= password (env/value :allu :password)))
                     (do (reset! mock-logged-in? true)
                         {:status 200, :body (json/encode password)})
                     {:status 404, :body "Wrong username and/or password"}))

        [:applications :cancel] (let [id (-> route-match :path-params :id)]
                                  (if (creation-response-ok? id)
                                    {:status 200, :body ""}
                                    {:status 404, :body (str "Not Found: " id)}))

        [:placementcontracts :create] (let [body (json/decode (:body request) true)]
                                        (if-let [validation-error (sc/check PlacementContract body)]
                                          {:status 400, :body validation-error}
                                          {:status 200
                                           :body   (.replace (subs (:identificationNumber body) 3) "-" "")}))

        [:placementcontracts :update] (let [id (-> route-match :path-params :id)
                                            body (json/decode (:body request) true)]
                                        (if-let [validation-error (sc/check PlacementContract body)]
                                          {:status 400, :body validation-error}
                                          (if (creation-response-ok? id)
                                            {:status 200, :body id}
                                            {:status 404, :body (str "Not Found: " id)})))

        [:attachments :create] (let [id (-> route-match :path-params :id)]
                                 (if (creation-response-ok? id)
                                   {:status 200, :body ""}
                                   {:status 404, :body (str "Not Found: " id)}))))))

;;;; Custom middlewares
;;;; ===================================================================================================================

(defn- route-name->string
  "Join a `[Keyword]` into a string with dots and throwing away the keyword semicolons."
  [path] (s/join \. (map name path)))

(defn- preprocessor->middleware
  "(Request -> Request) -> ((Request -> Response) -> (Request -> Response))
  For the ML illiterates, turn a fn from request to request into a middleware function."
  [preprocess]
  (fn [handler] (fn [request] (handler (preprocess request)))))

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
  "Convert the request :body or :multipart content into JSON (unless the :multipart part is just a string or an
  InputStream i.e. attachment file bytes, which is a hacky)."
  [request]
  (cond
    (contains? request :body) (-> request (update :body json/encode) (assoc :content-type :json))
    (contains? request :multipart) (update request :multipart
                                           (partial mapv (fn [{:keys [content] :as part}]
                                                           (if (or (string? content)
                                                                   (instance? InputStream content)) ; HACK
                                                             part
                                                             (update part :content json/encode)))))
    :else request))

(defn- get-attachment-files!
  "Replace the attachment file id in [:multipart 1 :content] with the file contents `InputStream`."
  [{{:keys [application latestAttachmentVersion]} ::command :as request}]
  (if-let [file-map (get-attachment-file! (:id application) (:fileId latestAttachmentVersion)
                                          {:versions [latestAttachmentVersion]})] ; HACK
    (assoc-in request [:multipart 1 :content] ((:content file-map)))
    (assert false "unimplemented")))

(defn- save-messages!
  "Save the request and response into the `integration-messages` DB collection."
  [handler]
  (fn [{command ::command :as request}]
    (let [endpoint (-> request :uri)
          message-subtype (route-name->string (-> request reitit-ring/get-match :data :name))
          {msg-id :id :as msg} (request-integration-message
                                 command
                                 (select-keys request [:uri :request-method (if (contains? request :multipart)
                                                                              :multipart
                                                                              :body)])
                                 message-subtype)
          _ (imessages/save msg)
          response (handler request)]
      (imessages/update-message msg-id {$set {:status "done", :acknowledged (now)}})
      (imessages/save (response-integration-message command endpoint response message-subtype))
      response)))

(def allu-fail!
  "A hook for testing error cases. Calls `sade.core/fail!` by default."
  (fn [text info-map] (fail! text info-map)))

(declare allu-request-handler)

(defn- login!
  "Log in to ALLU, `reset!`:ing `current-jwt`."
  []
  (allu-request-handler (login-request (env/value :allu :username) (env/value :allu :password))))

(defn- handle-response
  "Error handling (`allu-fail!` on HTTP errors, re-login on HTTP 401) and a grap-bag of other post-request actions."
  [disable-io-middlewares?]
  (fn [handler]
    (fn [{{{app-id :id} :application} ::command :as request}]
      (let [route-name (-> request reitit-ring/get-match :data :name)
            {:keys [status] :as response} (handler request)]
        (when (and (= status 401)
                   (not= route-name [:login]))              ; guard against runaway recursion
          (login!))

        (match response
          {:status (:or 200 201), :body body}
          (do (info "ALLU operation" (route-name->string route-name) "succeeded")
              ;; TODO: Move these ad-hoc conditionals into route-specific middlewares:
              (when (= route-name [:login])
                (reset! current-jwt (json/decode body)))    ; for some reason body is a JSON-encoded string
              (when (and (not disable-io-middlewares?) (= route-name [:placementcontracts :create]))
                (application/set-integration-key app-id :ALLU {:id body}))
              response)

          response (allu-fail! :error.allu.http (select-keys response [:status :body])))))))

;;;; Router and request handler
;;;; ===================================================================================================================

(defn- innermost-handler
  "Make the innermost handler that either is an ALLU-specific HTTP client function or a mockery thereof."
  ([] (innermost-handler (env/dev-mode?)))
  ([dev-mode?] (if dev-mode? imessages-mock-handler (make-remote-handler (env/value :allu :url)))))

(defn- routes
  "Compute the Reitit routes that call `handler` at their core."
  ([handler] (routes (env/dev-mode?) handler))
  ([disable-io-middlewares? handler]
   [["/login" {:middleware [(preprocessor->middleware body&multipart-as-params)
                            reitit.ring.coercion/coerce-request-middleware
                            (handle-response disable-io-middlewares?)
                            (preprocessor->middleware content->json)]
               :name       [:login]
               :parameters {:body LoginCredentials}
               :post       {:handler handler}}]


    ["/" {:middleware (-> [(preprocessor->middleware body&multipart-as-params)
                           multipart-middleware
                           reitit.ring.coercion/coerce-request-middleware
                           (handle-response disable-io-middlewares?)]
                          (into (if disable-io-middlewares? [] [save-messages!]))
                          (conj (preprocessor->middleware (fn-> content->json jwt-authorize))))
          :coercion   reitit.coercion.schema/coercion}
     ["applications"
      ["/:id/cancelled" {:name       [:applications :cancel]
                         :parameters {:path {:id ssc/NatString}}
                         :put        {:handler handler}}]

      ["/:id/attachments" {:name       [:attachments :create]
                           :parameters {:path      {:id ssc/NatString}
                                        :multipart {:metadata AttachmentMetadata
                                                    :file     sssc/FileId}}
                           :middleware (if disable-io-middlewares?
                                         []
                                         [(preprocessor->middleware get-attachment-files!)])
                           :post       {:handler handler}}]]


     ["placementcontracts"
      ["" {:name       [:placementcontracts :create]
           :parameters {:body PlacementContract}
           :post       {:handler handler}}]

      ["/:id" {:name       [:placementcontracts :update]
               :parameters {:path {:id ssc/NatString}
                            :body PlacementContract}
               :put        {:handler handler}}]]]]))

(def- allu-router
  "The Reitit router for ALLU requests."
  (reitit-ring/router (routes false (innermost-handler))))

(def allu-request-handler
  "ALLU request handler. Returns HTTP response, calls `allu-fail!` on HTTP errors."
  (reitit-ring/ring-handler allu-router))

;;;; JMS resources
;;;; ===================================================================================================================

(def- allu-jms-queue-name "lupapalvelu.backing-system.allu")

(when (env/feature? :jms)
  ;; FIXME: HTTP timeout handling
  ;; FIXME: Error handling is very crude
  (defn- allu-jms-msg-handler
    "Make a JMS message handler in JMS `session`. The handler will call `allu-request-handler` and do a JMS rollback on
    any exceptions."
    [session]
    (fn [{{{app-id :id} :application {user-id :user} :user} ::command uri :uri :as msg}]
      (logging/with-logging-context {:userId user-id :applicationId app-id}
        (try
          (allu-request-handler msg)
          (jms/commit session)

          (catch Exception exn
            (let [operation-name (route-name->string (-> (reitit/match-by-path allu-router uri) :data :name))]
              (error operation-name "failed:" (type exn) (.getMessage exn))
              (error "Rolling back" operation-name))
            (jms/rollback session))))))

  (defstate ^AutoCloseable allu-jms-session
    "JMS session for `allu-jms-consumer`"
    :start (jms/create-transacted-session (jms/get-default-connection))
    :stop (.close allu-jms-session))

  (defstate ^AutoCloseable allu-jms-consumer
    "JMS consumer for the ALLU request JMS queue"
    :start (jms-client/listen allu-jms-session (jms/queue allu-jms-queue-name)
                              (jms/message-listener (jms/nippy-callbacker (allu-jms-msg-handler allu-jms-session))))
    :stop (.close allu-jms-consumer)))

(defn- send-allu-request!
  "Send an ALLU request. Will put the request in the `allu-jms-queue-name` queue if JMS is enabled, otherwise calls
  `allu-request-handler` directly."
  [request]
  (if (env/feature? :jms)
    (jms/produce-with-context allu-jms-queue-name (nippy/freeze request))
    (allu-request-handler request)))

;;;; Mix up pure and impure into an API
;;;; ===================================================================================================================

(defn allu-application?
  "Should ALLU integration be used?"
  [organization-id permit-type]
  (and (env/feature? :allu) (= organization-id "091-YA") (= permit-type "YA")))

(declare update-application!)

(defn submit-application!
  "Submit application to ALLU and save the returned id as application.integrationKeys.ALLU.id. If this is a resubmit
  (after return-to-draft) just does `update-application!` instead."
  [{:keys [application] :as command}]
  (if-not (get-in application [:integrationKeys :ALLU :id])
    (send-allu-request! (application-creation-request command))
    (update-application! command)))

;; TODO: If the update message data is the same as the previous one or invalid, don't send/enqueue the message at all:
(defn update-application!
  "Update application in ALLU (if it had been sent there)."
  [{:keys [application] :as command}]
  (when (application/submitted? application)
    (send-allu-request! (application-update-request true command))))

(defn lock-application!
  "Lock application in ALLU for verdict evaluation."
  [command]
  (send-allu-request! (application-update-request false command)))

(defn cancel-application!
  "Cancel application in ALLU (if it had been sent there)."
  [{:keys [application] :as command}]
  (when (application/submitted? application)
    (send-allu-request! (application-cancel-request command))))

(defn- send-attachment!
  "Send `attachment` of `application` to ALLU. Return the fileId of the file that was sent."
  [command attachment]
  (send-allu-request! (attachment-send command attachment))
  (-> attachment :latestVersion :fileId))

(defn send-attachments!
  "Send the specified `attachments` of `(:application command)` to ALLU.
  Returns a seq of attachment file IDs that were sent."
  [command attachments]
  (doall (for [attachment attachments]
           (send-attachment! command attachment))))
