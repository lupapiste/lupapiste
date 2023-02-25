(ns lupapalvelu.web
  (:require [clj-time.core :as time]
            [clj-time.local :as local]
            [clojure.java.io :as io]
            [clojure.string :refer [replace-first]]
            [lupapalvelu.action :as action]
            [lupapalvelu.activation :as activation]
            [lupapalvelu.api-common :refer :all]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.backing-system.asianhallinta.reader :as ah-reader]
            [lupapalvelu.calendars-api :as calendars]
            [lupapalvelu.control-api :as control]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.dummy-krysp-service]
            [lupapalvelu.i18n :refer [*lang*] :as i18n]
            [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.ident.ad-login-api]
            [lupapalvelu.ident.dummy]
            [lupapalvelu.ident.suomifi]
            [lupapalvelu.idf.idf-api :as idf-api]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :refer [with-logging-context]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.neighbors-api]
            [lupapalvelu.proxy-services :as proxy-services]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.singlepage :as singlepage]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.token :as token]
            [lupapalvelu.user :as usr]
            [lupapalvelu.ya-extension :as yax]
            [me.raynes.fs :as fs]
            [monger.operators :refer [$set $push $elemMatch]]
            [net.cgrand.enlive-html :as enlive]
            [noir.cookies :as cookies]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.middleware.content-type :refer [content-type-response]]
            [ring.util.io :as ring-io]
            [ring.util.request :as ring-request]
            [ring.util.response :refer [resource-response]]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.coordinate :as coord]
            [sade.core :refer [ok fail ok? fail? now def-] :as core]
            [sade.date :as date]
            [sade.env :as env]
            [sade.files :as files]
            [sade.http :as http]
            [sade.property :as p]
            [sade.session :as ssess]
            [sade.status :as status]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [taoensso.timbre :refer [trace infof warnf]])
  (:import (java.io OutputStream OutputStreamWriter BufferedWriter ByteArrayInputStream)
           (java.nio.charset StandardCharsets)))

;;
;; Helpers
;;

(defn- json-response [content]
  (resp/content-type "application/json; charset=utf-8"
                     (json/encode content)))

(defonce apis (atom #{}))

(defmacro defjson [path params & content]
  `(let [[m# p#] (if (string? ~path) [:get ~path] ~path)]
     (swap! apis conj {(keyword m#) p#})
     (defpage ~path ~params
       (let [response-data# (do ~@content)
             response-session# (:session response-data#)
             response-cookies# (:cookies response-data#)]
         (resp/set-headers
           http/no-cache-headers
           (-> (dissoc response-data# :session :cookies)
               json-response
               (util/assoc-when :session response-session#
                                :cookies response-cookies#)))))))

(defjson "/system/apis" [] @apis)

(def- keyword-no-non-printables (comp keyword ss/strip-non-printables))

(defn parse-json-body [{:keys [content-type character-encoding body] :as request}]
  (let [json-body (when (or (ss/starts-with content-type "application/json")
                            (ss/starts-with content-type "application/csp-report"))
                    (if body
                      (-> body
                          (io/reader :encoding (or character-encoding "utf-8"))
                          (json/decode-stream keyword-no-non-printables))
                      {}))]                                 ; no body
    (if json-body
      (assoc request :json json-body :params json-body)
      (assoc request :json nil))))

(defn parse-json-body-middleware [handler]
  (fn [request]
    (handler (parse-json-body request))))

(defn from-json [request]
  (:json request))

(defn- logged-in? [request]
  (not (nil? (:id (usr/current-user request)))))

(defn- in-role? [role request]
  (= role (keyword (:role (usr/current-user request)))))

(defn- user-has-org-role?
  "check that given user has top-level role `:authority` and given `org-role` in some
   of her organizations."
  [org-role user]
  (and (->> user
            :role
            (= "authority"))
       (->> user
            :orgAuthz
            (some (comp org-role val)))))

(defn- has-org-role?
  "check that current user has top-level role `:authority` and given `org-role` in some
   of her organizations."
  [org-role request]
  (user-has-org-role? org-role (usr/current-user request)))

(def applicant? (partial in-role? :applicant))
(def authority? (partial in-role? :authority))
(def oir? (partial in-role? :oirAuthority))
(def authority-admin? (partial has-org-role? :authorityAdmin))
(def admin? (partial in-role? :admin))
(def financial-authority? (partial in-role? :financialAuthority))
(defn- anyone [_] true)
(defn- nobody [_] false)

;;
;; Status
;;

(defn remove-sensitive-keys [m]
  (util/postwalk-map
    (partial filter (fn [[k _]] (if (or (string? k) (keyword? k)) (not (re-matches #"(?i).*(passw(or)?d.*|key)$" (name k))) true)))
    m))

(status/defstatus :build (assoc env/buildinfo :server-mode env/mode))
(status/defstatus :time  (. (new org.joda.time.DateTime) toString "dd.MM.yyyy HH:mm:ss"))
(status/defstatus :mode  env/mode)
(status/defstatus :system-env (remove-sensitive-keys (System/getenv)))
(status/defstatus :system-properties (remove-sensitive-keys (System/getProperties)))
(status/defstatus :sade-env (remove-sensitive-keys (env/get-config)))
(status/defstatus :proxy-headers (-> (request/ring-request) :headers (select-keys ["host" "x-real-ip" "x-forwarded-for" "x-forwarded-proto"])))

;;
;; Commands
;;

(defjson [:post "/api/command/:name"] {name :name}
  (let [request (request/ring-request)]
    (execute-command name (from-json request) request)))

(defjson "/api/query/:name" {name :name}
  (let [request (request/ring-request)]
    (execute-query name (from-query request) request)))

(defjson [:post "/api/datatables/:name"] {name :name}
  (let [request (request/ring-request)]
    (execute-query name (:params request) request)))

(defn json-data-as-stream [data]
  (ring-io/piped-input-stream
    #(json/encode-stream data (BufferedWriter. (OutputStreamWriter. ^OutputStream % "utf-8")))))

(defn json-stream-response [data]
  (resp/content-type
    "application/json; charset=utf-8"
    (json-data-as-stream data)))

(defn data-api-handler [params-fn export]
  (let [request (request/ring-request)
        user (basic-authentication request)]
    (if user
      (json-stream-response (execute-export export (params-fn request) (assoc request :user user)))
      basic-401)))

(defpage [:get "/data-api/json/:name"] {name :name}
  (data-api-handler from-query name))

(defpage [:post "/data-api/json/:name"] {name :name}
  (data-api-handler from-json name))

(defpage [:any "/api/raw/:name"] {name :name :as params}
  (let [request (request/ring-request)
        data (if (= :post (:request-method request))
               (:params request)
               (from-query request))
        response (execute (enriched (action/make-raw name data) request))]
    (cond
      (= response core/unauthorized) (resp/status 401 "unauthorized")
      (false? (:ok response)) (resp/status 404 (resp/json response))
      :else response)))

;;
;; Web UI:
;;

(def- build-ts (let [ts (:time env/buildinfo)] (if (pos? ts) ts (now))))

(def last-modified (date/zoned-format build-ts :rfc))

(def content-type {:html "text/html; charset=utf-8"
                   :js   "application/javascript; charset=utf-8"
                   :css  "text/css; charset=utf-8"})

(def auth-methods {:init anyone
                   :cdn-fallback anyone
                   :common anyone
                   :hashbang anyone
                   :upload logged-in?
                   :applicant applicant?
                   :authority authority?
                   :oir oir?
                   :authority-admin authority-admin?
                   :admin admin?
                   :wordpress anyone
                   :welcome anyone
                   :neighbor anyone
                   :bulletins anyone
                   :local-bulletins anyone
                   :financial-authority financial-authority?})

(defn cache-headers [resource-type]
  (if (env/feature? :no-cache)
    {"Cache-Control" "no-cache"}
    (if (= :html resource-type)
      {"Cache-Control" "no-cache"
       "Last-Modified" last-modified}
      {"Cache-Control" "public, max-age=864000"
       "Vary"          "Accept-Encoding"
       "Last-Modified" last-modified})))

(defn lang-headers [resource-type]
  (when (= :html resource-type)
    {"Content-Language" (name i18n/*lang*)}))

(def- never-cache #{:hashbang :local-bulletins})

(def default-lang (name i18n/default-lang))

(def- compose
  (if (env/feature? :no-cache)
    singlepage/compose
    (memoize singlepage/compose)))

(defn- unchecked-single-resource [resource-type app theme]
  (if (or (never-cache app) (env/feature? :no-cache)
          (not= (get-in (request/ring-request) [:headers "if-modified-since"]) last-modified))
    (->> (ByteArrayInputStream. (compose resource-type app *lang* theme))
         (resp/content-type (resource-type content-type))
         (resp/set-headers (cache-headers resource-type))
         (resp/set-headers (lang-headers resource-type)))
    {:status 304}))

(defn- single-resource [resource-type app theme failure]
  (if ((auth-methods app nobody) (request/ring-request))
    (unchecked-single-resource resource-type app theme)
    failure))

(def- unauthorized (resp/status 401 "Unauthorized\r\n"))

;; CSS & JS

(defpage [:get ["/app/:build/:app.:res-type" :res-type #"(css|js)"]] {build :build app :app res-type :res-type}
  (let [build-number env/build-number]
    (if (= build build-number)
      (single-resource (keyword res-type) (keyword app) nil unauthorized)
      (resp/redirect (str "/app/" build-number "/" app "." res-type "?lang=" (name *lang*))))))

;; Single Page App HTML
(def apps-pattern
  (re-pattern (str "(" (ss/join "|" (map name (keys auth-methods))) ")")))

(defn redirect [lang page]
  (resp/redirect (str "/app/" (name lang) "/" page)))

(defn redirect-after-logout [lang]
  (resp/redirect (str (env/value :host) (or (env/value :redirect-after-logout (keyword lang)) "/"))))

(defn redirect-to-frontpage [lang]
  (resp/redirect (str (env/value :host) (or (env/value :frontpage (keyword lang)) "/"))))

(defn redirect-authority-admin-to-default-organization [lang]
  (let [user (usr/current-user (request/ring-request))]
    (redirect lang (str "authority-admin/" (name (roles/default-authority-admin-organization-id user))))))

(defn- landing-page
  ([]     (landing-page default-lang (usr/current-user (request/ring-request))))
  ([lang] (landing-page lang         (usr/current-user (request/ring-request))) )
  ([lang user]
   (let [lang (get user :language lang)]
     (if-let [application-page (and (:id user) (usr/applicationpage-for user))]
       (redirect lang application-page)
       (redirect-to-frontpage lang)))))

(defn- ->hashbang [s]
  (let [hash (cond
               (string? s) s
               (sequential? s) (last s))
        sanitized-hash (some-> hash
                               (replace-first "%21" "!")
                               (ss/replace #"\.+/" ""))]
    (when (util/relative-local-url? sanitized-hash)
      (second (re-matches #"^[#!/]{0,3}(.*)" sanitized-hash)))))

(defn- save-hashbang-on-client [theme]
  (resp/set-headers {"Cache-Control" "no-cache", "Last-Modified" (date/zoned-format 0 :rfc)}
    (single-resource :html :hashbang theme unauthorized)))

(defn- app-resource [resource-type app theme failure]
  (let [request (request/ring-request)]
    (if ((auth-methods app nobody) request)
      ;; HACK: If authority-admin page is requested without organizationId, make a best effort to deduce organizationId
      ;;       from current user and redirect:
      (if-let [[_ org-id] (re-find #"/app/[a-z]{2}/authority-admin/?(.*)" (:uri request))]
        (if (seq org-id)
          (unchecked-single-resource resource-type app theme)
          (redirect-authority-admin-to-default-organization *lang*))
        (unchecked-single-resource resource-type app theme))
      failure)))

(defn serve-app [app hashbang theme]
  ;; hashbangs are not sent to server, query-parameter hashbang used to store where the user wanted to go,
  ;; stored on server, reapplied on login
  (if-let [hashbang (->hashbang hashbang)]
    (ssess/merge-to-session
      (request/ring-request)
      (app-resource :html (keyword app) theme (redirect-to-frontpage *lang*))
      {:redirect-after-login hashbang})
    ;; If current user has no access to the app, save hashbang using JS on client side.
    ;; The next call will then be handled by the "true branch" above.
    (app-resource :html (keyword app) theme (save-hashbang-on-client theme))))

(defpage [:get ["/app/:lang/:app" :lang #"[a-z]{2}" :app apps-pattern]]
  {app :app hashbang :redirect-after-login lang :lang theme :theme}
  (i18n/with-lang lang
    (serve-app app hashbang theme)))

;; Same as above, but with an extra path.
(defpage [:get ["/app/:lang/:app/*" :lang #"[a-z]{2}" :app apps-pattern]]
  {app :app hashbang :redirect-after-login lang :lang theme :theme}
  (i18n/with-lang lang
    (serve-app app hashbang theme)))

;;
;; Login/logout:
;;

(defn- user-to-application-page [user lang]
  (ssess/merge-to-session (request/ring-request) (landing-page lang user) {:user (usr/session-summary user)}))

(defn- logout! []
  (cookies/put! :anti-csrf-token {:value "delete" :path "/" :expires "Thu, 01-Jan-1970 00:00:01 GMT"})
  (cookies/put! :lupapiste-login {:value "delete" :path "/" :expires "Thu, 01-Jan-1970 00:00:01 GMT"})
  {:session nil})

(defn kill-session! []
  (cookies/put! :lupapiste-login {:value   "delete"
                                  :path    "/"
                                  :expires "Thu, 01-Jan-1970 00:00:01 GMT"})
  {:session nil})

(defpage [:get ["/app/:lang/logout" :lang #"[a-z]{2}"]] {lang :lang}
  (let [session-user (usr/current-user (request/ring-request))]
    (if (:impersonating session-user)
      ; Just stop impersonating
      (user-to-application-page (usr/get-user {:id (:id session-user), :enabled true}) lang)
      ; Actually kill the session
      (merge (logout!) (redirect-after-logout lang)))))

;; Login via separate URL outside anti-csrf
(defjson [:post "/api/login"] {username :username :as params}
  (let [request (request/ring-request)
        response (if username
                   (execute-command "login" params request) ; Handles form POST (Nessus)
                   (execute-command "login" (from-json request) request))]
    (select-keys response [:ok :text :session :applicationpage :lang :cookies])))

(defpage [:get "/api/login-sso-uri"] {username :username}
  (resp/json (if-let [uri (ad-login/ad-login-uri username)]
               (ok {:uri uri})
               (fail :error.unauthorized))))

;; Reset password via separate URL outside anti-csrf
(defjson [:post "/api/reset-password"] []
  (let [request (request/ring-request)]
    (execute-command "reset-password" (from-json request) request)))

;;
;; Redirects
;;

(defpage "/" [] (landing-page))
(defpage "/sv" [] (landing-page "sv"))
(defpage "/app/" [] (landing-page))
(defpage [:get ["/app/:lang"  :lang #"[a-z]{2}"]] {lang :lang} (landing-page lang))
(defpage [:get ["/app/:lang/" :lang #"[a-z]{2}"]] {lang :lang} (landing-page lang))


;;
;; FROM SADE
;;

(defjson "/system/ping" [] (ok))
(defjson "/system/status" [] (status/status))
(defjson "/system/action-counters" [] (reduce (fn [m [k v]] (assoc m k (:call-count v))) {} (action/get-actions)))

(def activation-route (str (env/value :activation :path) ":activation-key"))
(defpage activation-route {key :activation-key}
  (if-let [user (activation/activate-account key)]
    (do
      (infof "User account '%s' activated, auto-logging in the user" (:username user))
      (user-to-application-page user default-lang))
    (do
      (warnf "Invalid user account activation attempt with key '%s', possible hacking attempt?" key)
      (landing-page))))

;;
;; Apikey-authentication
;;

(defn- get-apikey [request]
  (http/parse-bearer request))

(defn- authentication [handler request]
  (let [api-key (get-apikey request)
        api-key-auth (when-not (ss/blank? api-key) (usr/get-user-with-apikey api-key))
        session-user (get-in request [:session :user])
        expires (:expires session-user)
        expired? (and expires (not (usr/virtual-user? session-user)) (< expires (now)))
        updated-user (and expired? (usr/session-summary (usr/get-user {:id (:id session-user), :enabled true})))
        user (or api-key-auth updated-user session-user (autologin/autologin request) )]
    (if (and expired? (not updated-user))
      (resp/status 401 "Unauthorized")
      (let [response (handler (assoc request :user user))]
        (if (and response updated-user)
          (ssess/merge-to-session request response {:user updated-user})
          response)))))

(defn wrap-authentication
  "Middleware that adds :user to request. If request has apikey authentication header then
   that is used for authentication. If not, then use user information from session."
  [handler]
  (fn [request] (authentication handler request)))

(defn- logged-in-with-apikey? [request]
  (and (get-apikey request) (logged-in? request)))

;; Verify autologin header and get user info from an another application
(defpage [:get "/internal/autologin/user"] {basic-auth-data :basic-auth original-ip :ip}
  (if (and basic-auth-data original-ip)
    (if-let [user (autologin/autologin {:headers {"authorization" basic-auth-data
                                                  "x-real-ip"     original-ip}})]
      (resp/json user)
      (resp/status 400 "Invalid or expired authorization header data"))
    (resp/status 400 "Missing basic-auth or ip parameter")))

;;
;; File upload
;;

(defpage [:post "/api/upload/attachment"]
  {:keys [applicationId attachmentId attachmentType operationId text upload typeSelector targetId targetType locked] :as data}
  (infof "upload: %s: %s type=[%s] op=[%s] selector=[%s], locked=%s" data upload attachmentType operationId typeSelector locked)
  (let [request (request/ring-request)
        target (when-not (every? ss/blank? [targetId targetType])
                 (if (ss/blank? targetId)
                   {:type targetType}
                   {:type targetType :id targetId}))
        attachment-type (att-type/parse-attachment-type attachmentType)
        group (cond
                (ss/blank? operationId) nil
                ((set att-tags/attachment-groups) (keyword operationId)) {:groupType (keyword operationId)}
                :else {:groupType :operation :operations [{:id operationId}]})
        upload-data (-> upload
                        (assoc :id applicationId
                               :attachmentId attachmentId
                               :target target
                               :locked (java.lang.Boolean/parseBoolean locked)
                               :text text
                               :group group)
                        (util/assoc-when :attachmentType attachment-type))
        result (execute-command "upload-attachment" upload-data request)]
    (if (core/ok? result)
      (resp/status 200 (str "Upload OK, id: " (:attachmentId result)))
      (resp/status 400 (str "Upload failed: " (:text result))))))

(defn tempfile-cleanup
  "Middleware for cleaning up tempfile after each request.
   Depends on other middleware to collect multi-part-params into params and to keywordize keys."
  [handler]
  (fn [request]
    (try
      (handler request)
      (finally
        (when-let [tempfile (or (get-in request [:params :upload :tempfile])
                                (get-in request [:params :files]))]
          (if (sequential? tempfile)
            (doseq [{file :tempfile} tempfile] ; files as array from fileupload-service /api/raw/upload-file
              (fs/delete file))
            (fs/delete tempfile)))))))

;;
;; Server is alive
;;

(defjson "/api/alive" []
  (let [req (request/ring-request)]
    (cond
      (control/lockdown?)             (fail :error.service-lockdown)
      (some-> req :params :bulletins) (ok)
      (usr/current-user req)          (ok)
      :else                           (fail :error.unauthorized))))

;;
;; Proxy
;;

(defpage [:any ["/proxy/:srv" :srv #"[a-z/\-]+"]] {srv :srv}
  (if @env/proxy-off
    {:status 503}
    ((proxy-services/services srv (constantly {:status 404})) (request/ring-request))))

;;
;; Token consuming:
;;

(defpage [:get "/api/token/:token-id"] {token-id :token-id}
  (let [[status token] (token/get-token token-id :consume false)]
    (case status
      :usable (resp/status 200 (resp/json (ok :token token)))
      :used   (resp/status 404 (resp/json (fail :error.token-used)))
      (resp/status 404 (resp/json (fail :error.token-not-found))))))

(defpage [:post "/api/token/:token-id"] {token-id :token-id}
  (let [params (from-json (request/ring-request))
        [token-status response] (token/consume-token token-id params :consume true)]
    (case token-status
      :usable (cond
                (contains? response :status) response
                (ok? response)   (resp/status 200 (resp/json response))
                (fail? response) (resp/status 404 (resp/json response))
                :else (resp/status 404 (resp/json (fail :error.unknown))))
      :used   (resp/status 404 (resp/json (fail :error.token-used)))
      (resp/status 404 (resp/json (fail :error.token-not-found))))))

;;
;; Cross-site request forgery protection
;;


(defn verbose-csrf-block [req]
   (let [ip (http/client-ip req)
         nothing "(not there)"
         cookie-csrf (get-in req [:cookies "anti-csrf-token" :value])
         ring-session-full (or (get-in req [:cookies "ring-session" :value]) nothing)
         ring-session (clojure.string/replace ring-session-full #"^(...).*(...)$" "$1...$2")
         header-csrf (get-in req [:headers "x-anti-forgery-token"])
         req-with (or (get-in req [:headers "x-requested-with"]) nothing)
         user-agent (or (get-in req [:headers "user-agent"]) nothing)
         uri (:uri req)
         user (:username (:user req))
         session-id (:id (:session req))]
      (str "CSRF attempt blocked. " ip
           " requested '" uri
           "' for user '" user
           "' having cookie csrf '" cookie-csrf "' vs header csrf '" header-csrf
           "'. Ring session '" ring-session
              "', session id '" session-id
              "', user agent '" user-agent
              "', requested with '" req-with "'.")))

(defn csrf-attack-handler [request]
  (with-logging-context
    {:applicationId (or (get-in request [:params :id]) (:id (from-json request)))
     :userId        (:id (usr/current-user request) "???")}
    (warnf (verbose-csrf-block request))
    (->> (fail :error.invalid-csrf-token) (resp/json) (resp/status 403))))

(defn tokenless-request? [request]
   (re-matches #"^/proxy/.*" (:uri request)))

(defn bulletin-request? [request]
  (or (some->> (get-in request [:headers "referer"])
               (re-matches #"https://julkipano(-\w+)*.lupapiste.fi/app/fi/local-bulletins.*"))
      (re-matches #"^/app/fi/local-bulletins.*"
                  (:uri request))))

(def anti-csrf-cookie-name "anti-csrf-token")

(defn anti-csrf [handler]
  (fn [request]
    (if (env/feature? :disable-anti-csrf)
      (handler request)
      (let [cookie-attrs (dissoc (env/value :cookie) :http-only)]
        (cond
           (and (re-matches #"^/api/(command|query|datatables|upload).*" (:uri request))
                (not (logged-in-with-apikey? request))
                (not (bulletin-request? request)))
             (anti-forgery/crosscheck-token handler request anti-csrf-cookie-name csrf-attack-handler)
          (tokenless-request? request)
             ;; cookies via /proxy may end up overwriting current valid ones otherwise
             (handler request)
          :else
             (anti-forgery/set-token-in-cookie request (handler request) anti-csrf-cookie-name cookie-attrs))))))

(defn cookie-monster
   "Remove cookies from requests in which only IE would send and update original cookie information
    due to differing behavior in subdomain cookie handling."
   [handler]
   (fn [request]
      ;; use (env/value :host) != (:host request) later, but now just the specific requests
      (let [response (handler request)]
         (if (tokenless-request? request)
            (let [session (:session response)]
               (when session
                  (trace (str "Removing session " session " from tokenless request to " (:uri request) ".")))
               (dissoc response :session :session-cookie-attrs :cookies))
            response))))

;;
;; Session timeout:
;;
;;    Middleware that checks session timeout.
;;

(defn get-session-timeout [request]
  (get-in request [:session :user :session-timeout] (.toMillis java.util.concurrent.TimeUnit/HOURS 4)))

(defn session-timeout-handler [handler request]
  (let [now (now)
        request-session (:session request)
        expires (get request-session :expires now)
        expired? (< expires now)
        response (handler request)
        login-cookie? (get-in request [:cookies "lupapiste-login"])]
    (if expired?
      (assoc response :session nil)
      (if (re-find #"^/api/(command|query|raw|datatables|upload)/" (:uri request))
        (cond-> (ssess/merge-to-session request response {:expires (+ now (get-session-timeout request))})
                login-cookie?
                (usr/merge-login-cookie))
        response))))

(defn session-timeout [handler]
  (fn [request] (session-timeout-handler handler request)))

;;
;; Identity federation
;;

(defpage
  [:post "/api/id-federation"]
  {:keys [etunimi sukunimi
          email puhelin katuosoite postinumero postitoimipaikka
          suoramarkkinointilupa ammattilainen
          app id ts mac]}
  (idf-api/handle-create-user-request etunimi sukunimi
          email puhelin katuosoite postinumero postitoimipaikka
          suoramarkkinointilupa ammattilainen
          app id ts mac))

;;
;; dev utils:
;;

(defn- application-url [user info-request? application-id]
  (format "/app/fi/%s#!/%s/%s"
          (usr/applicationpage-for user)
          (if info-request? "inforequest" "application")
          application-id))

;; Static error responses for testing
(defpage [:get ["/dev/:status"  :status #"[45]0\d"]] {status :status} (resp/status (util/->int status) status))

(env/in-dev
  (defjson [:any "/dev/spy"] []
    (dissoc (request/ring-request) :body))

  (defpage "/dev/header-echo" []
    (resp/status 200 (resp/set-headers (:headers (request/ring-request)) "OK")))

  (defpage "/dev/fixture/:name" {:keys [name]}
    (let [request  (request/ring-request)
          response (execute-query "apply-fixture" {:name name} request)]
      (if (seq (re-matches #"(.*)MSIE [\.\d]+; Windows(.*)" (get-in request [:headers "user-agent"])))
        (resp/status 200 (str response))
        (resp/json response))))

  (defpage "/dev/create" {:keys [infoRequest propertyId message redirect state] :as query-params}
    (let [request                           (request/ring-request)
          property                          (p/to-property-id propertyId)
          params                            (assoc (from-query request) :propertyId property :messages (if message [message] []))
          {application-id :id :as response} (execute-command "create-application" params request)
          user                              (usr/current-user request)]
      (if (core/ok? response)
        (let [app           (domain/get-application-no-access-checking application-id)
              applicant-doc (domain/get-applicant-document (:documents app))
              command       (-> app
                                action/application->command
                                (assoc :user user, :created (now)))]
          (when applicant-doc
            (doc-persistence/do-set-user-to-document app (:id applicant-doc) (:id user) "henkilo" (now) user))
          (cond
            (= state "submitted")
            (app/submit command)

            (and (ss/not-blank? state) (not= state (get-in command [:application :state])))
            (action/update-application command {$set {:state state}, $push {:history (app-state/history-entry state (:created command) user)}}))

          (if redirect
            (resp/redirect (application-url user infoRequest application-id))
            (resp/status 200 application-id)))
        (resp/status 400 (str response)))))

  (defn- create-app-and-publish-bulletin []
    (let [request  (request/ring-request)
          params   (assoc (from-query request) :operation "lannan-varastointi"
                          :address "Vaalantie 540"
                          :propertyId (p/to-property-id "564-404-26-102")
                          :x 430109.3125 :y 7210461.375)
          {id :id} (execute-command "create-application" params request)
          _        (mongo/update-by-id :applications id {$set {:state "sent"}})
          now      (sade.core/now)
          params   (-> (assoc (from-query request) :id id)
                       (assoc :proclamationStartsAt now)
                       (assoc :proclamationEndsAt (+ (* 24 60 60 1000) now))
                       (assoc :proclamationText "proclamation"))
          response (execute-command "move-to-proclaimed" params request)]
      (core/ok? response)))

  (defpage "/dev/publish-bulletin-quickly" {:keys [count] :or {count "1"}}
    (let [results (take (util/to-long count) (repeatedly create-app-and-publish-bulletin))]
      (if (every? true? results)
        (resp/status 200 "OK")
        (resp/status 400 "FAIL"))))

  ;; Note: uses address for ease of use in tests; does not work as intended on multiple apps with the same address
  (defpage "/dev/set-application-timestamp" {:keys [address attr val redirect] :as query-params}
    (if (zero? (mongo/update-by-query :applications
                                      {:address (ss/trim address)}
                                      {$set {(keyword attr) (-> val ss/trim Long/parseLong)}}))
        (resp/status 404 "Not found")
        (if redirect
          (->> (mongo/select-one :applications {:address address} [:_id])
               :id
               (application-url (usr/current-user (request/ring-request)) false)
               (resp/redirect))
          (resp/status 200 "OK"))))

  ;; send ascii over the wire with wrong encoding (case: Vetuma)
  ;; direct:    http --form POST http://localhost:8080/dev/ascii Content-Type:'application/x-www-form-urlencoded' < dev-resources/input.ascii.txt
  ;; via nginx: http --form POST http://localhost/dev/ascii Content-Type:'application/x-www-form-urlencoded' < dev-resources/input.ascii.txt
  (defpage [:post "/dev/ascii"] {:keys [a]}
    (str a))

  (defpage [:get "/dev-pages/:file"] {:keys [file]}
    (->
      (resource-response (str "dev-pages/" file))
      (content-type-response {:uri file})))

  (defjson "/dev/fileinfo/:application/:id" {:keys [application id]}
    (when-let [data (storage/download-from-system application id (storage/default-storage-system-id))]
      (dissoc data :content)))

  (defpage "/dev/by-id/:collection/:id" {:keys [collection id]}
    (if-let [r (mongo/by-id collection id)]
      (resp/status 200 (resp/json {:ok true :data r}))
      (resp/status 404 (resp/json {:ok false :text "not found"}))))

  (defpage "/dev/integration-messages/:id" {:keys [id]}
    (if-let [r (mongo/select :integration-messages {:application.id id})]
      (resp/status 200 (resp/json {:ok true :data r}))
      (resp/status 404 (resp/json {:ok false :text "not found"}))))

  (defpage "/dev/public/:collection/:id" {:keys [collection id]}
    (if-let [r (mongo/by-id collection id)]
      (resp/status 200 (resp/json {:ok true :data (lupapalvelu.neighbors-api/->public r)}))
      (resp/status 404 (resp/json {:ok false :text "not found"}))))

  (defpage "/dev/clear/:collection" {:keys [collection]}
    (resp/status 200 (resp/json {:ok true :status (mongo/remove-many collection {})})))

  (defpage "/dev/ajanvaraus/clear" []
    (resp/status 200 (resp/json {:ok true :status (calendars/clear-database)})))

  (defpage [:get "/api/proxy-ctrl"] []
    (resp/json {:ok true :data (not @env/proxy-off)}))

  (defpage [:post "/api/proxy-ctrl/:value"] {value :value}
    (let [on (condp = value
               true   true
               "true" true
               "on"   true
               false)]
      (resp/json {:ok true :data (swap! env/proxy-off (constantly (not on)))})))

  ;; Development (mockup) Suti server (/dev/suti) treats suti-ids semantically.
  ;; Id parameter is of format id[:seconds], where some ids have special meaning:
  ;;   empty: no products
  ;;   bad: 501
  ;;   auth: requires username (suti) and password (secret)
  ;;   all the other ids return products.
  ;; The optional seconds part causes the corresponding delay when serving the response.
  (defpage [:get "/dev/suti/:id"] {:keys [id]}
    (let [[_ sub-id seconds] (re-find  #"(.*):(\d+)$" id)]
      (when seconds
        (Thread/sleep (* 1000 (Integer/parseInt seconds))))
      (case (keyword (or sub-id id))
        :bad   (resp/status 501 "Bad Suti request.")
        :empty (json/encode {})
        :auth  (let [[username password] (http/decode-basic-auth (request/ring-request))]
                 (if (and (= username "suti") (= password "secret"))
                   (json/encode {:productlist [{:name "Four" :expired true :expirydate "\\/Date(1467883327899)\\/" :downloaded "\\/Date(1467019327022)\\/" }
                                               {:name "Five" :expired true :expirydate "\\/Date(1468056127124)\\/" :downloaded nil}
                                               {:name "Six" :expired false :expirydate nil :downloaded nil}]})
                   (resp/status 401 "Unauthorized")))
        (json/encode {:productlist [{:name "One" :expired false :expirydate nil :downloaded nil}
                                    {:name       "Two" :expired true :expirydate "\\/Date(1467710527123)\\/"
                                     :downloaded "\\/Date(1467364927456)\\/"}
                                    {:name "Three" :expired false :expirydate nil :downloaded nil}]}))))

  ;; Development (mockup) functionality of 3D map server, both backend and frontend
  ;; Username / password: 3dmap / 3dmap
  (defonce lupapisteKeys (atom {}))

  (defpage [:post "/dev/3dmap"] []
    (let [req-map             (request/ring-request)
          [username password] (http/decode-basic-auth req-map)]
      (if (= username password "3dmap")
        (let [keyId (mongo/create-id)]
          (swap! lupapisteKeys assoc keyId (select-keys (:params req-map) [:applicationId :apikey]))
          (resp/redirect (str "/dev/show-3dmap?lupapisteKey=" keyId) :see-other))
        (resp/status 401 "Unauthorized"))))

  (defpage [:get "/dev/show-3dmap"] {:keys [lupapisteKey]}
    (let [{:keys [applicationId apikey]} (get @lupapisteKeys lupapisteKey)
          banner                         " $$$$$$\\  $$$$$$$\\        $$\\      $$\\  $$$$$$\\  $$$$$$$\\        $$\\    $$\\ $$$$$$\\ $$$$$$$$\\ $$\\      $$\\ \n$$ ___$$\\ $$  __$$\\       $$$\\    $$$ |$$  __$$\\ $$  __$$\\       $$ |   $$ |\\_$$  _|$$  _____|$$ | $\\  $$ |\n\\_/   $$ |$$ |  $$ |      $$$$\\  $$$$ |$$ /  $$ |$$ |  $$ |      $$ |   $$ |  $$ |  $$ |      $$ |$$$\\ $$ |\n  $$$$$ / $$ |  $$ |      $$\\$$\\$$ $$ |$$$$$$$$ |$$$$$$$  |      \\$$\\  $$  |  $$ |  $$$$$\\    $$ $$ $$\\$$ |\n  \\___$$\\ $$ |  $$ |      $$ \\$$$  $$ |$$  __$$ |$$  ____/        \\$$\\$$  /   $$ |  $$  __|   $$$$  _$$$$ |\n$$\\   $$ |$$ |  $$ |      $$ |\\$  /$$ |$$ |  $$ |$$ |              \\$$$  /    $$ |  $$ |      $$$  / \\$$$ |\n\\$$$$$$  |$$$$$$$  |      $$ | \\_/ $$ |$$ |  $$ |$$ |               \\$  /   $$$$$$\\ $$$$$$$$\\ $$  /   \\$$ |\n \\______/ \\_______/       \\__|     \\__|\\__|  \\__|\\__|                \\_/    \\______|\\________|\\__/     \\__|\n"
          address                        ((mongo/select-one :applications {:_id applicationId}) :address)
          {:keys [firstName lastName]}   (usr/get-user-with-apikey apikey)]
      (hiccup.core/html [:html
                         [:head [:title "3D Map View"]]
                         [:body {:style "background-color: #008b00; color: white; padding: 4em"} [:pre banner]
                          [:ul
                           [:li (format "Application ID: %s (%s)" applicationId address)]
                           [:li (format "User: %s %s" firstName lastName)]]]])))

  ;; Reads and processes jatkoaika-ya.xml
  ;; Since the the xml is static, this is useful only in robots.
  (defpage "/dev/mock-ya-extension" []
    (-> "krysp/dev/jatkoaika-ya.xml"
        io/resource
        slurp
        (ss/replace "[YEAR]" (str (time/year (local/local-now))))
        xml/parse
        strip-xml-namespaces
        yax/update-application-extensions)
    (resp/status 200 "YA extension KRYSP processed."))

  (defpage [:post "/dev/review-from-background/:appId/:taskId"] {appId :appId taskId :taskId}
    (mongo/update-by-query :applications
                           {:_id appId :tasks {$elemMatch {:id taskId}}}
                           (util/deep-merge
                             {$set {:tasks.$.state "sent"}}
                             {$set {:tasks.$.source {:type "background"}}}
                             {$set {:tasks.$.data.katselmus.pitoPvm.value "1.1.2017"}}))
    (let [application (domain/get-application-no-access-checking appId)]
      (tasks/generate-task-pdfa application
                                (util/find-by-id taskId (:tasks application))
                                (usr/batchrun-user [(:organization application)])
                                "fi")
      (resp/status 200 "PROCESSED")))

  (defpage [:get "/dev/ah/message-response"] {:keys [id messageId ftp-user]} ; LPK-3126
    (let [xml   (-> (io/resource "asianhallinta/sample/ah-example-response.xml")
                    (enlive/xml-resource)
                    (enlive/transform [:ah:AsianTunnusVastaus :ah:HakemusTunnus] (enlive/content id))
                    (enlive/transform [:ah:AsianTunnusVastaus] (enlive/set-attr :messageId messageId)))
          xml-s (apply str (enlive/emit* xml))
          temp  (files/temp-file "asianhallinta" ".xml")]
      (spit temp xml-s)
      (let [result (files/with-zip-file
                     [(.getPath temp)]
                     (ah-reader/process-message zip-file
                                                (or ftp-user (env/value :ely :sftp-user))
                                                (usr/batchrun-user ["123"])))]
        (io/delete-file temp)
        (resp/status 200 (resp/json result)))))

  (defpage [:get "/dev/ah/statement-response"] {:keys [id statement-id ftp-user]} ; LPK-3126
    (let [attachment (files/temp-file "ah-statement-test" ".txt")
          temp       (files/temp-file "asianhallinta" ".xml")]
      (try
        (let [xml   (-> (io/resource "asianhallinta/sample/ah-example-statement-response.xml")
                        (enlive/xml-resource)
                        (enlive/transform [:ah:LausuntoVastaus :ah:HakemusTunnus] (enlive/content id))
                        (enlive/transform [:ah:LausuntoVastaus :ah:Lausunto :ah:LausuntoTunnus] (enlive/content statement-id))
                        (enlive/transform [:ah:LausuntoVastaus :ah:Liitteet :ah:Liite :ah:LinkkiLiitteeseen] (enlive/content (.getName attachment))))
              xml-s (apply str (enlive/emit* xml))]
          (spit temp xml-s)
          (spit attachment "Testi Onnistui!")
          (let [result (files/with-zip-file
                         [(.getPath temp) (.getPath attachment)]
                         (ah-reader/process-message zip-file
                                                    (or ftp-user (env/value :ely :sftp-user))
                                                    (usr/batchrun-user ["123"])))]
            (resp/status 200 (resp/json result))))
        (finally
          (io/delete-file attachment :silently)
          (io/delete-file temp :silently)))))

  (defpage [:get "/dev/filecho/:filename"] {filename :filename}
    (->> filename
         (format "This is file %s\n")
         (resp/content-type "text/plain; charset=utf-8")
         (resp/set-headers (assoc http/no-cache-headers
                                  "Content-Disposition" (String. (.getBytes (format "attachment; filename=\"%s\""
                                                                                    filename)
                                                                            StandardCharsets/UTF_8)
                                                                 StandardCharsets/ISO_8859_1)
                                  "Server" "Microsoft-IIS/7.5"))
         (resp/status 200)))

  (letfn [(response [status consume?]
            (let [request (request/ring-request)]
              (some-> request :params :delay util/->long (* 1000) Thread/sleep)
              (when consume?
                (ring-request/body-string request))
              (resp/status (clojure.edn/read-string status)
                           (format "<FOO>Echo %s status</FOO>" status))))]
    (defpage [:post "/dev/statusecho/:status"] {status :status}
      (response status true))
    (defpage [:post "/dev/statusecho/:status/:any"] {status :status}
      (response status true))
    (defpage [:get "/dev/statusecho/:status"] {status :status}
      (response status false)))

  (defpage [:get "/dev/batchrun-invoke"] {batchrun :batchrun args :args}
    (let [batchruns    {"check-verdict-attachments" 'lupapalvelu.batchrun/check-for-verdict-attachments}
          batchrun-sym (batchruns batchrun)]
      (if (nil? batchrun-sym)
        (fail :error.batchrun-not-defined :batchrun batchrun)
        (do
          (require (symbol (namespace batchrun-sym)))
          (ok (resp/json (apply (resolve batchrun-sym) args)))))))

  (defpage "/invoicing" []
    (let [url (or (env/value :invoicing :url) "http://localhost:8013")]
      (resp/redirect url)))

  ;; Fills application buildings array with (fake) buildings for every
  ;; operation.
  (defpage [:get "/dev/fake-buildings/:app-id"] {app-id :app-id}
    (if-let [application (mongo/by-id :applications app-id)]
      (do
        (mongo/update-by-id :applications
                            app-id
                            {$set {:buildings (map-indexed (fn [i {:keys [description id]}]
                                                             (let [xy [(+ 10000 (rand (- 80000 10001)))
                                                                       (+ 6610000 (rand (- 7779999 6610001)))]]
                                                               {:buildingId     (str "building" (inc i))
                                                                :index          (str (inc i))
                                                                :description    description
                                                                :operationId    id
                                                                :nationalId     (str "VTJ-PRT-" (inc i))
                                                                :propertyId     (:propertyId application)
                                                                :location       xy
                                                                :location-wgs84 (coord/convert "EPSG:3067" "WGS84" 5 xy)}))
                                                           (app-utils/get-operations application))}})
          (resp/status 200 "Buildings faked"))
      (resp/status 404 "Not found")))

  (defpage [:post "/dev/set-application-buildings/:app-id"] params
    (if (zero? (mongo/update-by-query :applications
                                      {:_id (-> params last :app-id)}
                                      {$set {:buildings (from-json (request/ring-request))}}))
      (resp/status 404 "Not found")
      (resp/status 200 "OK")))

  ;; Adds exported-to-backing-system transfer to the application
  ;; This is needed for move-to-attachments-to-backing-system auth model.
  (defpage [:get "/dev/add-transfer/:app-id"] {:keys [app-id]}
    (if (pos? (mongo/update-by-query :applications
                                     {:_id app-id}
                                     {$push {:transfers { :type "exported-to-backing-system"}}}))
      (resp/status 200 "Transfer added")
      (resp/status 404 "Not found")))

  ;; Adds fetched timestamp to attachment that matches given contents.
  (defpage [:get "/dev/fetched-attachment/:app-id/:contents"] {:keys [app-id contents]}
    (if (pos? (mongo/update-by-query :applications {:_id         app-id
                                                    :attachments {$elemMatch {:contents contents}}}
                                     {$set {:attachments.$.fetched (now)}}))
      (resp/status 200 "Attachment marked as fetched")
      (resp/status 404 "Not found")))

  ;; Changes the permit type of the given application
  (defpage [:get "/dev/change-permit-type/:app-id/:permit-type"] {:keys [app-id permit-type]}
    (let [permit-type (ss/upper-case permit-type)]
      (if (and permit-type
               (pos? (mongo/update-by-query :applications {:_id app-id}
                                            {$set {:permitType permit-type}})))
        (resp/status 200 permit-type)
        (resp/status 404 "Not found"))))


  (defpage [:get "/dev/set-kuntagml-version/:org-id/:permit-type/:version"]
    {:keys [org-id permit-type version]}
    (mongo/update-by-id :organizations (ss/upper-case org-id)
                        {$set {(util/kw-path :krysp (ss/upper-case permit-type) :version) version}})
    (resp/status 200 version))

  (defpage [:get "/dev/toggle-rakennusluokat/:org-id/:enable"]
    {:keys [org-id enable]}
    (let [enabled? (= enable "enable")]
      (mongo/update-by-id :organizations (ss/upper-case org-id)
                          {$set {:rakennusluokat-enabled enabled?}})
      (resp/status 200 (str "Rakennusluokat " (if enabled? "en" "dis") "abled"))))

  ;; OAuth test endpoints

  (letfn [(make-link [path args]
            [:a
             {:href (str path "?" (clj-http.client/generate-query-string args))}
             "Start"])
          (html [status code]
            (hiccup.core/html
              [:html {:style "background-color: #c6e2ff"}
               [:head [:title "OAuth Test"]]
               [:body {:style "padding: 4em; font-size: 20px"}
                [:h1 (str "Status: " (ss/upper-case status))]
                (when code
                  [:div [:b "Code: "] code])
                [:p "OAuth login with scopes:"
                 [:pre
                  "read         "
                  (make-link "/oauth/authorize"
                             {:client_id        "oauth-test"
                              :scope            "read"
                              :response_type    "code"
                              :success_callback "/success"
                              :error_callback   "/error"
                              :cancel_callback  "/cancel"})
                  "\npay          "
                  (make-link "/oauth/authorize"
                             {:client_id        "oauth-test"
                              :scope            "pay"
                              :response_type    "code"
                              :success_callback "/success"
                              :error_callback   "/error"
                              :cancel_callback  "/cancel"})
                  "\nread and pay "
                  (make-link "/oauth/authorize"
                             {:client_id        "oauth-test"
                              :scope            "read,pay"
                              :response_type    "code"
                              :success_callback "/success"
                              :error_callback   "/error"
                              :cancel_callback  "/cancel"})
                  "\n\n"
                  (let [registration? (some-> (mongo/by-id :users "oauth-test-client"
                                                           [:oauth.registration?])
                                              :oauth :registration?)]
                    [:a {:href (str "/dev/oauth-test-toggle?registration="
                                    (not registration?))}
                     (str (if registration? "Disable" "Enable") " registration")])
                  "\n\n"
                  [:a {:href "/dev/oauth-test/reset"} "Reset"]]]]]))]
    (defpage [:get "/dev/oauth-test/:status"] {:keys [status code]}
      (html status code))
    (defpage [:post "/dev/oauth-test/:status"] {:keys [status code]}
      (html status code))
    (defpage [:get "/dev/oauth-test-toggle"] {:keys [registration]}
      (let [registration? (= registration "true")]
        (mongo/update-by-id :users "oauth-test-client"
                            {$set {:oauth.registration? registration?}})
        {:status  302
         :headers {"Location" (str "/dev/oauth-test/registration "
                                   (if registration? "enabled" "disabled"))}}))))
