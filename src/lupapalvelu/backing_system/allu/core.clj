(ns lupapalvelu.backing-system.allu.core
  "JSON REST API integration with ALLU as backing system. Used for Helsinki YA instead of SFTP/HTTP KRYSP XML
  integration."
  (:require [clojure.string :as s]
            [clojure.core.match :refer [match]]
            [clojure.walk :refer [postwalk]]
            [clj-time.core :as t]
            [monger.operators :refer [$set $in]]
            [lupapalvelu.json :as json]
            [lupapiste-jms.client :as jms-client]
            [mount.core :refer [defstate]]
            [schema.core :as sc :refer [defschema optional-key enum]]
            [reitit.core :as reitit]
            [reitit.coercion.schema]
            [reitit.ring :as reitit-ring]
            [reitit.ring.coercion]
            [reitit.ring.middleware.multipart :refer [multipart-middleware]]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :refer [info error]]
            [taoensso.nippy :as nippy]
            [sade.util :refer [dissoc-in assoc-when fn-> file->byte-array]]
            [sade.core :refer [def- fail! now]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc :refer [NonBlankStr date-string]]
            [sade.shared-schemas :as sssc]
            [lupapalvelu.application :as application]
            [lupapalvelu.attachment :refer [get-attachment-file!]]
            [lupapalvelu.backing-system.allu.conversion :refer [lang application->allu-placement-contract
                                                                format-date-time]]
            [lupapalvelu.backing-system.allu.schemas :refer [LoginCredentials PlacementContract AttachmentMetadata AttachmentFile]]
            [lupapalvelu.file-upload :refer [save-file]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.messages :as imessages :refer [IntegrationMessage]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.allu.allu-application :as allu-application]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.pdf.pdf-export-api :as pdf-export-api])
  (:import [java.lang AutoCloseable]
           [java.io ByteArrayInputStream]))

(defstate ^:private current-jwt
  "The JWT to be used for ALLU API authorization. Occasionally replaced by re-login."
  :start (atom (env/value :allu :jwt)))

;;;; Initial request construction
;;;; ===================================================================================================================

(defschema ^:private MiniCommand
  "A command that has been abridged for sending through JMS."
  {:application {:id ssc/ApplicationId
                 :organization sc/Str
                 :state (apply sc/enum (map name states/all-states))}
   :action sc/Str
   :user {:id sc/Str
          :username sc/Str}})

(sc/defn ^{:private true, :always-validate true} minimize-command :- MiniCommand
  [{:keys [application action user]}]
  {:application          (select-keys application (keys (:application MiniCommand)))
   :action               action
   :user                 (select-keys user (keys (:user MiniCommand)))})

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
        params {:path {:id allu-id}
                :body (application->allu-placement-contract pending-on-client
                                                            application)}
        route-match (reitit/match-by-name allu-router [:placementcontracts :update] (:path params))]
    {::command       (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)
     :body           (:body params)}))

(defn- attachment-send-self
  "Construct an ALLU attachment upload request to convert the application to pdf and send it to ALLU as an attachement."
  [{:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}
                :multipart {:metadata {:name        (str (localize lang :application.applicationSummary) ".pdf")
                                       :description (let [type (localize lang :attachmentType "muut" "muu")
                                                          description (localize lang :application.applicationSummary)]
                                                      (if (or (not description) (= type description))
                                                        type
                                                        (str type ": " description)))
                                       :mimeType    "application/pdf"}
                            :file {:attach-self true}}}
        route-match (reitit/match-by-name allu-router [:attachments :create] (:path params))]
    {::command (minimize-command command)
     :uri (:path route-match)
     :request-method (route-match->request-method route-match)
     :multipart [{:name "metadata"
                  :mime-type "application/json"
                  :encoding "UTF-8"
                  :content (-> params :multipart :metadata)}
                 {:name "file"
                  :mime-type (-> params :multipart :metadata :mimeType)
                  :content (-> params :multipart :file)}]}))

(defn- attachment-send
  "Construct an ALLU attachment upload request for `send-allu-request!` based on reverse routing on `allu-router`,
  `command` and `attachment` (one of `(-> command :application :attachments)`)."
  [{:keys [application] :as command} {{:keys [type-group type-id]} :type :keys [latestVersion] :as attachment}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)

        params {:path {:id allu-id}
                :multipart {:metadata {:name (:filename latestVersion)
                                       :description (let [type (localize lang :attachmentType type-group type-id)
                                                          description (:contents attachment)]
                                                      (if (or (not description) (= type description))
                                                        type
                                                        (str type ": " description)))
                                       :mimeType (:contentType latestVersion)}
                            :file (select-keys latestVersion [:fileId :storageSystem])}}
        route-match (reitit/match-by-name allu-router [:attachments :create] (:path params))]
    {::command (minimize-command command)
     :uri (:path route-match)
     :request-method (route-match->request-method route-match)
     :multipart [{:name "metadata"
                  :mime-type "application/json"
                  :encoding "UTF-8"
                  :content (-> params :multipart :metadata)}
                 {:name "file"
                  :mime-type (-> params :multipart :metadata :mimeType)
                  :content (-> params :multipart :file)}]}))

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
                       :signingTime (format-date-time (t/now))}}
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

(defn- allu-application-data [{:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)
        params {:path {:id allu-id}}
        route-match (reitit/match-by-name allu-router [:applications :allu-data] (:path params))]
    {::command        (minimize-command command)
     :uri            (:path route-match)
     :request-method (route-match->request-method route-match)}))

(defn- contract-metadata [{:keys [application] :as command}]
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
  (mongo/any? :integration-messages {:direction            "in" ; i.e. the response
                                     :messageType          message-type
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
                                  (if (response-ok? id "placementcontracts.create")
                                    {:status 200, :body ""}
                                    {:status 404, :body (str "Not Found: " id)}))

             [:placementcontracts :create] (let [body (json/decode (:body request) true)]
                                             (if-let [validation-error (sc/check PlacementContract body)]
                                               {:status 400, :body validation-error}
                                               {:status 200
                                                :body   (.replace (subs (:identificationNumber body) 3) "-" "")}))

             [:placementcontracts :update] (let [id   (-> route-match :path-params :id)
                                                 body (json/decode (:body request) true)]
                                             (if-let [validation-error (sc/check PlacementContract body)]
                                               {:status 400, :body validation-error}
                                          (if (response-ok? id "placementcontracts.create")
                                            {:status 200, :body id}
                                            {:status 404, :body (str "Not Found: " id)})))

             [:applications :allu-data] (let [id (-> route-match :path-params :id)]
                                          {:status 400}
                                          {:status 200
                                           :body (str "SL19000" id)})

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
          (if (response-ok? id "placementcontracts.contract.approved")
            {:status  200, :body (json/encode {:handler {:name  "Hannu Helsinki"
                                                         :title "Director"}}),
             :headers {"Content-Type" "application/json"}}
            {:status 404, :body (str "Not Found: " id)}))

        [:placementcontracts :contract :approved] (let [id (-> route-match :path-params :id)]
                                                    (if (response-ok? id "placementcontracts.create")
                                                      {:status 200, :body ""}
                                                      {:status 404, :body (str "Not Found: " id)}))

        [:attachments :create] (let [id (-> route-match :path-params :id)]
                                 (if (response-ok? id "placementcontracts.create")
                                   {:status 200, :body ""}
                                   {:status 404, :body (str "Not Found: " id)}))))))

;;;; Custom middlewares
;;;; ===================================================================================================================

;;; TODO: Use interceptors instead, the *->middleware fn:s are kind of silly when interceptors exist.

(defn- route-name->string
  "Join a `[Keyword]` into a string with dots and throwing away the keyword semicolons."
  [path] (s/join \. (map name path)))

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

;; HACK:
(defn- try-reload-allu-id
  "Reload ALLU id from application if the request has empty string for it."
  [{:keys [uri] :as request}]
  (let [{:keys [path-params] :as route-match} (reitit/match-by-path allu-router uri)] ; OPTIMIZE
    (if (and (contains? path-params :id) (empty? (:id path-params)))
      (let [application (domain/get-application-no-access-checking (-> request ::command :application :id)
                                                                   {:integrationKeys true})
            allu-id (-> application :integrationKeys :ALLU :id)]
        (assert allu-id (str (:id application) " does not contain an ALLU id"))
        (assoc request :uri (:path (reitit/match-by-name allu-router (-> route-match :path :name) {:id allu-id}))))
      request)))

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

(defn json-response [handler]
  (fn [request]
    (-> (handler request)
        (update :body json/decode true))))

(declare allu-fail!)

(defn- delimit-file-contents!
  "Replace the attachment file id in [:multipart 1 :content] with the file contents `InputStream` when sending
  attachment. Store pdf file to file storage when downloading contract."
  [handler]
  (fn [{{:keys [application user]} ::command :as request}]
    (match (-> request reitit-ring/get-match :data :name)
      [:attachments :create]
      (let [{:keys [attach-self fileId storageSystem]} (get-in request [:multipart 1 :content])]
        (if-some [file-map (if attach-self
                             {:content (fn [] (:body (pdf-export-api/raw-submitted-application-pdf-export {:application application
                                                                                                           :user user
                                                                                                           :lang lang})))}
                             (storage/download-from-system (:id application) fileId storageSystem))]
          (with-open [contents ((:content file-map))]
            (handler (assoc-in request [:multipart 1 :content] contents)))
          (allu-fail! :error.file-not-found {:fileId fileId})))

      [:placementcontracts :contract (:or :proposal :final)]
      (let [request (assoc request :as :byte-array)
            response (handler request)
            file-name-suffix (case (last (-> request reitit-ring/get-match :data :name))
                               :proposal "-sopimusehdotus.pdf"
                               :final    "-sopimus.pdf"
                               ".pdf")]
        (if (= (:status response) 200)
          (let [content-bytes (:body response)
                file-data {:filename     (str (:id application) file-name-suffix)
                           :content      (ByteArrayInputStream. content-bytes)
                           :content-type (get-in response [:headers "Content-Type"])
                           :size         (alength content-bytes)}
                metadata {:linked      false
                          :uploader-user-id (:id user)}]
            (assoc response :body (save-file file-data metadata)))
          response))

      _ (handler request))))

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

(def allu-fail!
  "A hook for testing error cases. Calls `sade.core/fail!` by default."
  (fn [text info-map] (fail! text info-map)))

(declare allu-request-handler)

(defn- login!
  "Log in to ALLU, `reset!`:ing `current-jwt`."
  []
  (allu-request-handler (login-request (env/value :allu :username) (env/value :allu :password))))


(defn- re-login-on-auth-errors!
  "Re-login on HTTP 401."
  [{:keys [status]}]
  (when (or (= status 401) (= status 403))
    (login!)))

(defn- reset-jwt!
  "Reset `current-jwt` to the new one from login response."
  [{:keys [body]}]
  (reset! current-jwt (json/decode body)))                  ; for some reason (consistency?) body is a JSON-encoded string

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
      ({:status (:or 200 201), :body body} :as response) (do (application/set-kuntalupatunnus app-id (:applicationId body))
                                                             response)
      response response)))

(declare load-allu-application-data!)

(defn- get-allu-kuntalupatunnus!
  "Makes a new GET request to fetch kuntalupatunnus from ALLU (middleware in that request stores it to db)"
  [handler]
  (fn [request]
    (let [response (handler request)
          allu-integration-key (:body response)
          new-command (assoc-in (::command request) [:application :integrationKeys :ALLU :id] allu-integration-key)]
      (load-allu-application-data! new-command)
      response)))

(defn- log-or-fail!
  "`allu-fail!` on HTTP errors, else do logging."
  [handler]
  (fn [request]
    (match (handler request)
      ({:status (:or 200 201), :body body} :as response)
      (do (info "ALLU operation" (route-name->string (-> request reitit-ring/get-match :data :name)) "succeeded")
          response)

      response (allu-fail! :error.allu.http (select-keys response [:status :body])))))

;;;; Router and request handler
;;;; ===================================================================================================================

(defn- innermost-handler
  "Make the innermost handler that either is an ALLU-specific HTTP client function or a mockery thereof."
  ([] (innermost-handler (env/dev-mode?)))
  ([dev-mode?] (if dev-mode? imessages-mock-handler (make-remote-handler (env/value :allu :url)))))

(defn- routes
  "Compute the Reitit routes that call `handler` at their core. Special use case of reitit as this does not declare a
  public API but an internal routing for requests to external APIs."
  ([handler] (routes (env/dev-mode?) handler))
  ([disable-io-middlewares? handler]
   (let [file-middleware (if disable-io-middlewares? [] [delimit-file-contents!])]
     [["/login" {:middleware [(preprocessor->middleware body&multipart-as-params)
                              reitit.ring.coercion/coerce-request-middleware
                              (post-action->middleware reset-jwt!)
                              log-or-fail!
                              (preprocessor->middleware content->json)]
                 :name [:login]
                 :parameters {:body LoginCredentials}
                 :post {:handler handler}}]


      ["/" {:middleware (-> [(preprocessor->middleware body&multipart-as-params)
                             multipart-middleware
                             reitit.ring.coercion/coerce-request-middleware
                             log-or-fail!
                             (post-action->middleware re-login-on-auth-errors!)]
                            (into (if disable-io-middlewares? [] [save-messages!]))
                            (conj (preprocessor->middleware (fn-> content->json jwt-authorize))))
            :coercion reitit.coercion.schema/coercion}
       ["applications"
        ["/:id" {:parameters {:path {:id ssc/NatString}}}
         ["" {:name [:applications :allu-data]
              :middleware [set-allu-kuntalupatunnus! json-response]
              :get {:handler handler}}]

         ["/cancelled" {:name [:applications :cancel]
                        :put {:handler handler}}]

         ["/attachments" {:name [:attachments :create]
                          :parameters {:multipart {:metadata AttachmentMetadata
                                                   :file AttachmentFile}}
                          :middleware file-middleware
                          :post {:handler handler}}]]]

       ["placementcontracts"
        ["" {:name [:placementcontracts :create]
             :parameters {:body PlacementContract}
             :middleware (if disable-io-middlewares? [] [get-allu-kuntalupatunnus! set-allu-integration-key!])
             :post {:handler handler}}]

        ["/:id" {:parameters {:path {:id ssc/NatString}}}
         ["" {:name [:placementcontracts :update]
              :parameters {:body PlacementContract}
              :put {:handler handler}}]

         ["/contract"
          ["/proposal" {:name [:placementcontracts :contract :proposal]
                        :middleware file-middleware
                        :get {:handler handler}}]

          ["/approved" {:name [:placementcontracts :contract :approved]
                        :parameters {:body {:signer NonBlankStr
                                            :signingTime (date-string :date-time-no-ms)}}
                        :post {:handler handler}}]

          ["/final" {:name [:placementcontracts :contract :final]
                     :middleware file-middleware
                     :get {:handler handler}}]
          ["/metadata" {:name [:placementcontracts :contract :metadata]
                        :middleware [json-response]
                        :get {:handler handler}}]]]]
       ["fixedlocations" {:name [:fixedlocations]
                          :parameters {:query {:applicationKind NonBlankStr}}
                          :get {:handler handler}}]]])))

(def- allu-router
  "The Reitit router for ALLU requests."
  (reitit-ring/router (routes false (innermost-handler))))

(def allu-request-handler
  "ALLU request handler. Returns HTTP response, calls `allu-fail!` on HTTP errors."
  (comp (reitit-ring/ring-handler allu-router) try-reload-allu-id))

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

          (catch Throwable exn
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
    :start (jms-client/listen allu-jms-session (jms/queue allu-jms-session allu-jms-queue-name)
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
  (allu-application/allu-application? organization-id permit-type))

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

(defn send-application-as-attachment!
  "Convert apllication to pdf and send it to ALLU as an attachment"
  [command]
  (send-allu-request! (attachment-send-self command)))

(defn agreement-state
  "Returns :proposal when application is still in the state where agreement proposal should be fetched.
   Returns :final when the final verdict should be fetched."
  [application]
  (case (:state application)
    ("sent" "submitted") :proposal
    "agreementPrepared"  :final
    :else nil))

(defn load-placementcontract-proposal!
  "GET placement contract proposal pdf from ALLU. Saves the proposal pdf using `lupapalvelu.file-upload/save-file`."
  [command]
  (send-allu-request! (contract-proposal-request command)))

(defn approve-placementcontract!
  "Approve placementcontract proposal in ALLU."
  [command]
  (send-allu-request! (contract-approval command)))

(defn load-placementcontract-final!
  "GET final placement contract pdf from ALLU. Saves the contract pdf using `lupapalvelu.file-upload/save-file`."
  [command]
  (send-allu-request! (final-contract-request command)))

(defn load-contract-document!
  "Load placement contract proposal or final from ALLU. Returns SavedFileData of the pdf or nil. Bypasses JMS."
  [{:keys [application] :as command}]
  (try+
    (:body (allu-request-handler (case (agreement-state application)
                                   :proposal (contract-proposal-request command)
                                   :final (final-contract-request command))))
    (catch [:text "error.allu.http"] _ nil)))

(defn load-allu-application-data!
  "GET application data from ALLU and store it to db."
  [command]
  (let [resp (send-allu-request! (allu-application-data command))]
    resp))

(defn load-contract-metadata
  "GET the name of the person who signed the ALLU verdict."
  [command]
  (allu-request-handler (contract-metadata command))
  (try+
    (:body (allu-request-handler (contract-metadata command)))
    (catch [:text "error.allu.http"] _ nil)))


(def FIXED-LOCATION-TYPES {:agile-kiosk-area              "AGILE_KIOSK_AREA"
                           :art                           "ART"
                           :benji                         "BENJI"
                           :bridge-banner                 "BRIDGE_BANNER"
                           :christmas-tree-sales-area     "CHRISTMAS_TREE_SALES_AREA"
                           :circus                        "CIRCUS"
                           :city-cycling-area             "CITY_CYCLING_AREA"
                           :construction                  "CONSTRUCTION"
                           :container-barrack             "CONTAINER_BARRACK"
                           :data-transfer                 "DATA_TRANSFER"
                           :dog-training-event            "DOG_TRAINING_EVENT"
                           :dog-training-field            "DOG_TRAINING_FIELD"
                           :election-add-stand            "ELECTION_ADD_STAND"
                           :electricity                   "ELECTRICITY"
                           :geological-survey             "GEOLOGICAL_SURVEY"
                           :heating-cooling               "HEATING_COOLING"
                           :keskuskatu-sales              "KESKUSKATU_SALES"
                           :lifting                       "LIFTING"
                           :military-excercise            "MILITARY_EXCERCISE"
                           :new-building-construction     "NEW_BUILDING_CONSTRUCTION"
                           :other                         "OTHER"
                           :other-subvision-of-state-area "OTHER_SUBVISION_OF_STATE_AREA"
                           :outdoorevent                  "OUTDOOREVENT"
                           :photo-shooting                "PHOTO_SHOOTING"
                           :promotion                     "PROMOTION"
                           :promotion-or-sales            "PROMOTION_OR_SALES"
                           :property-renovation           "PROPERTY_RENOVATION"
                           :public-event                  "PUBLIC_EVENT"
                           :relocation                    "RELOCATION"
                           :repaving                      "REPAVING"
                           :roll-off                      "ROLL_OFF"
                           :season-sale                   "SEASON_SALE"
                           :small-art-and-culture         "SMALL_ART_AND_CULTURE"
                           :snow-gather-area              "SNOW_GATHER_AREA"
                           :snow-heap-area                "SNOW_HEAP_AREA"
                           :snow-work                     "SNOW_WORK"
                           :statement                     "STATEMENT"
                           :storage-area                  "STORAGE_AREA"
                           :street-and-green              "STREET_AND_GREEN"
                           :summer-theater                "SUMMER_THEATER"
                           :urban-farming                 "URBAN_FARMING"
                           :water-and-sewage              "WATER_AND_SEWAGE"
                           :winter-parking                "WINTER_PARKING"
                           :yard                          "YARD"})

(defn- non-integration-routes
  "Routes for actions that are not part of the integration mechanisms."
  [handler]
  [["/" {:middleware [reitit.ring.coercion/coerce-request-middleware
                      log-or-fail!
                      (preprocessor->middleware (fn-> content->json jwt-authorize))
                      json-response]
         :coercion   reitit.coercion.schema/coercion}
    ["fixedlocations" {:name       [:fixedlocations]
                       :parameters {:query {:applicationKind (apply sc/enum (vals FIXED-LOCATION-TYPES))}}
                       :get        {:handler handler}}]]])

(def- allu-non-router
  "The Reitit router for ALLU requests."
  (reitit-ring/router (non-integration-routes (innermost-handler))))

(defn load-fixed-locations! [kind]
  (try+
   (:body ((reitit-ring/ring-handler allu-non-router) {:uri            "/fixedlocations"
                                                       :query-params   {:applicationKind kind}
                                                       :request-method :get
                                                       :insecure?      true}))
   (catch [:text "error.allu.http"] _ nil)))
