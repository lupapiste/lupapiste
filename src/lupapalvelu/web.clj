(ns lupapalvelu.web
  (:require [babashka.fs :as fs]
            [clj-http.client :as clj-http]
            [clojure.core.memoize :as memo]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [replace-first]]
            [clojure.walk :refer [keywordize-keys]]
            [hiccup.core :as hiccup]
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
            [lupapalvelu.batchrun.fetch-verdict :as fetch-verdict]
            [lupapalvelu.control-api :as control]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.dummy-krysp-service]
            [lupapalvelu.http.csrf :as csrf]
            [lupapalvelu.http.noir.util :as noir-util]
            [lupapalvelu.i18n :refer [*lang*] :as i18n]
            [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.ident.ad-login-api]
            [lupapalvelu.ident.dummy]
            [lupapalvelu.ident.suomifi]
            [lupapalvelu.json :as json]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.logging :refer [error infof trace warn warnf with-logging-context]]
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
            [monger.operators :refer [$elemMatch $exists $push $set]]
            [net.cgrand.enlive-html :as enlive]
            [noir.cookies :as cookies]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.http-response :as ring-resp]
            [ring.util.io :as ring-io]
            [ring.util.request :as ring-request]
            [ring.util.response :as ring-response]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.coordinate :as coord]
            [sade.core :refer [def- fail fail? now ok ok?] :as core]
            [sade.date :as date]
            [sade.env :as env]
            [sade.files :as files]
            [sade.http :as http]
            [sade.property :as p]
            [sade.security-headers :as security-headers]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [slingshot.slingshot :refer [try+]])
  (:import [com.fasterxml.jackson.core JsonProcessingException]
           [java.io BufferedWriter
                    ByteArrayInputStream
                    OutputStream
                    OutputStreamWriter]
           [java.nio.charset StandardCharsets]
           [java.time Duration]))

(set! *warn-on-reflection* true)

;;
;; Helpers
;;

(defn json-response [content]
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
  (try
    (let [json-body (when (ss/starts-with? content-type "application/json")
                      (if body
                        (-> body
                            (io/reader :encoding (or character-encoding "utf-8"))
                            (json/decode-stream keyword-no-non-printables))
                        {}))]                                 ; no body
      (if json-body
        (assoc request :json json-body :params json-body)
        (assoc request :json nil)))
    (catch JsonProcessingException e
      (warnf "Invalid JSON body. URI %s, remote-addr: %s, user %s. Error: %s"
             (:uri request)
             (:remote-addr request)
             (-> request :user :id)
             (.getMessage e))
      :invalid-json)))

(defn parse-json-body-middleware [handler]
  (fn [request]
    (let [request (parse-json-body request)]
      (if (= :invalid-json request)
        (resp/status 400 "Invalid JSON body")
        (handler request)))))

(defn from-json [request]
  (:json request))

(defn remove-_-param
  "Removes the field :_ from request params. Field :_ is added to jQuery ajax requests
   to avoid browser cacheing."
  [handler]
  (fn [request]
    (handler (-> request
                 (util/dissoc-in [:parameters :query :_])
                 (util/dissoc-in [:params :_])))))

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
;; Commands
;;

;; Noir

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

(def auth-methods {;; TODO: unused?
                   :init anyone
                   ;; The common.js bundle
                   :common anyone
                   ;; Ask unauthenticated user to login before
                   ;; accessing requested page
                   :hashbang anyone
                   ;; TODO: unused? (not event specified in ui_components.clj)
                   :upload logged-in?
                   ;; Frontpage for applicant after logging in
                   :applicant applicant?
                   ;; Frontpage for authority after logging in
                   :authority authority?
                   ;; Open inforequests may be reviewed by people,
                   ;; who do not have an actual account in Lupapiste
                   ;; but can access the OIR view via an email token
                   :oir oir?
                   ;; Frontpage for municipal/organization admin after logging in
                   :authority-admin authority-admin?
                   ;; Frontpage for Lupapiste admin after logging in
                   :admin admin?
                   ;; Registration page
                   :welcome anyone
                   ;; Landing page after suomi.fi authentication
                   :suomifi-login anyone
                   ;; Neighbor hearing page, access via email token
                   :neighbor anyone
                   ;; Public application bulletins (julkipano)
                   :bulletins anyone
                   ;; Public application bulletins (julkipano) for a specific organization
                   :local-bulletins anyone
                   ;; Frontpage for financial authority (= Varke-käsittelijä),
                   ;; after logging in
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

(defn hsts-headers [_]
  {"Strict-Transport-Security" "max-age=63072000; includeSubDomains"}) ; max-age = 2 years

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
         (resp/set-headers (lang-headers resource-type))
         (resp/set-headers (hsts-headers resource-type)))
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
  (resp/redirect (str (noir-util/hostname) (or (env/value :redirect-after-logout (keyword lang)) "/"))))

(defn redirect-to-frontpage [lang]
  (resp/redirect (str (noir-util/hostname) (or (env/value :frontpage (keyword lang)) "/"))))

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
  (-> (ssess/merge-to-session (request/ring-request)
                              (landing-page lang user)
                              {:user            (usr/session-summary user)}
                              ;; Rotate session when user permissions change (impersonating user or account activated)
                              {:rotate-session? true})
      (update :cookies merge (csrf/new-csrf-cookie))))

(defn- logout! []
  (cookies/put! :anti-csrf-token {:value "delete" :path "/" :expires "Thu, 01-Jan-1970 00:00:01 GMT"})
  {:session nil})

(defn kill-session! []
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

(defn- get-authorization-bearer [request]
  (http/parse-bearer request))

(defn- authentication [handler request]
  (let [auth-bearer     (http/parse-bearer request) ; token or apikey
        [status token]  (when (-> auth-bearer count (= 48))
                          (token/get-token auth-bearer :consume false))
        authorized-user (when-not (ss/blank? auth-bearer)
                          (if token
                            (when (= status :usable)
                              (usr/get-user-with-token token))
                            (let [user (usr/get-user-with-apikey auth-bearer)]
                              (warnf "User authenticating using apikey: %s"
                                     (:username user))
                              user)))
        session-user    (get-in request [:session :user])
        expires         (:expires session-user)
        expired?        (and expires (not (usr/virtual-user? session-user)) (< expires (now)))
        updated-user    (when expired?
                          (some-> {:id      (:id session-user)
                                   :enabled true}
                                  usr/get-user
                                  usr/session-summary
                                  (merge (select-keys session-user [:ad-login?]))))
        user            (or authorized-user updated-user session-user (autologin/autologin request))]
    (if (or (and expired? (not updated-user))
            (= status :expired))
      (resp/status 401 "Unauthorized")
      (let [response (handler (assoc request :user user))]
        (if (and response updated-user)
          (ssess/merge-to-session request response {:user updated-user})
          response)))))

(defn wrap-authentication
  "Middleware that adds :user to request. If request has apikey or token authentication header then
   that is used for authentication. If not, then use user information from session."
  [handler]
  (fn [request] (authentication handler request)))

(defn- logged-in-with-apikey-or-token? [request]
  (and (get-authorization-bearer request) (logged-in? request)))

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

(defn- finnish-first-cmp
  [a b]
  (cond
    (= a b)    0
    (= a "fi") 1
    (= b "fi") -1
    :else      0))

(defn- get-name->bulletin-redirect-conf
  "Returns e.g. {\"vantaa\" {:lang \"fi\" :name \"vantaa\" :org-id \"092-R\"}}"
  []
  (->> (mongo/select :organizations {:scope.bulletins.redirect-names {$exists true}} [:scope])
       (mapcat (fn [{:keys [id scope]}]
                 (->> scope
                      (mapcat (comp :redirect-names :bulletins))
                      (map #(assoc % :org-id id)))))
       (sort-by :lang finnish-first-cmp)
       (reduce (fn [acc conf]
                 (assoc acc (:name conf) conf))
               {})))

(def name->bulletin-redirect-conf
  "Not updated very often and not critical to refresh during the same day when it is"
  (memo/ttl get-name->bulletin-redirect-conf :ttl/threshold (-> (Duration/ofHours 1) .toMillis)))

(defn bulletin-redirects
  "Middleware for redirecting the julkipano subdomain to the correct route.
  There is no support in GCP ingress for rewriting the URL so have to process subdomain redirects inside app."
  [handler]
  (fn [{:keys [server-name uri] :as request}]
    (let [server-name (ss/lower-case server-name)
          julkipano?  (ss/starts-with? server-name "julkipano")
          kuulutus?   (ss/starts-with? server-name "kuulutukset")]
      (if (or julkipano? kuulutus?)
        (let [uri           (ss/lower-case uri)
              landing-page? (= "/" uri)
              ;; organization landing pages are e.g. "/hyvinkaa" or "/hyvinge" and not "/app/fi/..."
              org-name      (when (not landing-page?)
                              (some->> uri (re-find #"/([a-z]+)") second)) ; remove the forward slash
              redirect-conf (get (name->bulletin-redirect-conf) org-name)]
          (cond
            ;; Generic bulletins landing page
            landing-page? (redirect default-lang "bulletins")
            ;; Organization specific landing page
            redirect-conf (redirect (or (:lang redirect-conf) default-lang)
                                    (format "local-bulletins?organization=%s&bulletinProcess=%s"
                                            (:org-id redirect-conf)
                                            (if kuulutus? "kuulutus" "julkipano")))
            ;; Other bulletin page; no redirect needed (would cause redirect loop anyway)
            :else         (handler request)))
        (handler request)))))

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
            ;; files as array from fileupload-service /api/raw/upload-file
            (doseq [{file :tempfile} tempfile
                    ;; The actual file might be missing if an earlier file id is used.
                    :when            file]
              (fs/delete-if-exists file))
            (fs/delete-if-exists tempfile)))))))

;;
;; Server is alive
;;

(defjson "/api/alive" []
  (let [req (request/ring-request)]
    (cond
      (lockdown/lockdown?)            (fail :error.service-lockdown)
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
  (try+
    (let [{:keys [system] :as request} (request/ring-request)
          params                       (from-json request)
          [token-status response]      (token/consume-token system token-id params :consume true)]
      (case token-status
        :usable (cond
                  (contains? response :status) response
                  (ok? response)               (resp/status 200 (resp/json response))
                  (fail? response)             (resp/status 404 (resp/json response))
                  :else                        (resp/status 404 (resp/json (fail :error.unknown))))
        :used   (resp/status 404 (resp/json (fail :error.token-used)))
        (resp/status 404 (resp/json (fail :error.token-not-found)))))
    (catch [:ok false] {text :text}
      (error "Exception during token processing:" text)
      (resp/status 404 (resp/json (fail text))))))

;;
;; Cross-site request forgery protection
;;


(defn verbose-csrf-block [req]
   (let [ip (http/client-ip req)
         nothing "(not there)"
         cookie-csrf (get-in req [:cookies "anti-csrf-token" :value])
         ring-session-full (or (get-in req [:cookies (or (env/value :cookie-name) "ring-session") :value]) nothing)
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
    (warn (verbose-csrf-block request))
    (let [response (fail :error.invalid-csrf-token)]
      (if (env/feature? :reitit-api)
        {:status 403 :body response}
        (->> response (resp/json) (resp/status 403))))))

(defn tokenless-request? [request]
   (re-matches #"^/proxy/.*" (:uri request)))

(defn neighbor-application-request? [request]
  (re-matches #"^/api/query/neighbor-application" (:uri request)))

(defn anti-csrf [handler]
  (fn [request]
    (if (env/feature? :disable-anti-csrf)
      (handler request)
      (let [cookie-attrs (dissoc (env/value :cookie) :http-only)]
        (cond
          (and (re-matches #"^/api/(command|query|datatables|upload).*" (:uri request))
               (not (logged-in-with-apikey-or-token? request))
               (not (security-headers/bulletin-request? request))
               (not (neighbor-application-request? request)))
          (anti-forgery/crosscheck-token handler request csrf/anti-csrf-cookie-name csrf-attack-handler)
          (tokenless-request? request)
          ;; cookies via /proxy may end up overwriting current valid ones otherwise
          (handler request)
          :else
          (anti-forgery/set-token-in-cookie request (handler request) csrf/anti-csrf-cookie-name cookie-attrs))))))

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

(def default-session-timeout (* 4 60 60 1000)) ; 4 hours in ms

(defn get-session-timeout [request]
  (get-in request [:session :user :session-timeout] default-session-timeout))

(defn- session-expired?
  "Session is considered expired when
  1) there is no session at all
  2) session does not have `:expires`
  3) `:expires` is older than the current time `now` (in milliseconds)"
  [request now]
  (-> request :session :expires (or 0) (< now)))

(defn- session-not-recently-refreshed?
  "Returns true if the session has not been refreshed within the last minute.
  Used to throttle session writes to the database: prevents setting a new
  expiration timestamp on every single request."
  [request now]
  (< (-> request :session :expires (or 0)) (- (+ (get-session-timeout request) now) 60000)))

(defn- api-request-requiring-session-refresh?
  "Check if the request is made to a path which will cause session refresh.
  Of course, it is also required that the request is made with valid session."
  [request now]
  (and (not (session-expired? request now))
       ;; To prevent writing the session to database on every single request, only set
       ;; new expiration timestamp when previous one was written more than a minute ago.
       (session-not-recently-refreshed? request now)
       (->> request
            :uri
            (re-find #"^/api/(command|query|raw|datatables|upload)/")
            some?)))

(defn- session-user-created?
  "Check if some handler created valid session user even if the request
  had no valid session. This happens when a user logs in, for example."
  [request response now]
  (and (session-expired? request now)
       (-> response :session :user :expires (or 0) (> now))))

(defn- session-refresh-required?
  [request response now]
  (or (api-request-requiring-session-refresh? request now)
      (session-user-created? request response now)))

(defn session-timeout-handler
  "This middleware takes care of
  1) removing user data from the request session so that subsequent handlers cannot
  access expired user data and hence return data or perform actions the user
  is not allowed to do anymore. We let the handler decide how to appropriately
  react to the expired session (e.g. return a 401 if the endpoint requires auth)
  instead of deciding it here.
  2) setting the expiration time for the session if the user just logged in,
  for example.
  3) refreshing the session when the request is made to a certain endpoint.
  This keeps active user logged in."
  [handler request]
  (let [now             (now)
        session-timeout (get-session-timeout request)
        request         (cond-> request
                          (and (:session request) (session-expired? request now)) (update :session #(dissoc % :user)))
        response        (handler request)]
    (if (session-refresh-required? request response now)
      (ssess/merge-to-session request response {:expires (+ now session-timeout)})
      response)))

(defn session-timeout [handler]
  (fn [request] (session-timeout-handler handler request)))

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
                                (assoc :user user
                                       :created (now)
                                       :system (noir-util/get-system)))]
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

  (defpage "/dev/error" _
    (throw (Exception.)))

  (defn- create-app-and-publish-bulletin []
    (let [request  (request/ring-request)
          params   (assoc (from-query request) :operation "lannan-varastointi-v3"
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
    ;; clj-kondo doesn't understand defpage (destructuring)
    #_{:clj-kondo/ignore [:type-mismatch]}
    (let [results (take (util/to-long count) (repeatedly create-app-and-publish-bulletin))]
      (if (every? true? results)
        (resp/status 200 "OK")
        (resp/status 400 "FAIL"))))

  (defpage [:post "/dev/set-application-timestamp/:application/:k/:v"] {:keys [application k v]}
    (if (zero? (mongo/update-by-query :applications
                                      {:_id application}
                                      {$set {(keyword k) (-> v ss/trim parse-long)}}))
      (resp/status 404 (resp/json {:ok false :text "not found" :application application :k k :v v}))
      (resp/status 200 (resp/json {:ok true}))))

  ;; send ascii over the wire with wrong encoding (case: Vetuma)
  ;; direct:    http --form POST http://localhost:8080/dev/ascii Content-Type:'application/x-www-form-urlencoded' < dev-resources/input.ascii.txt
  ;; via nginx: http --form POST http://localhost/dev/ascii Content-Type:'application/x-www-form-urlencoded' < dev-resources/input.ascii.txt
  (defpage [:post "/dev/ascii"] {:keys [a]}
    (str a))

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
          (swap! lupapisteKeys assoc keyId (select-keys (:params req-map) [:applicationId :apikey :neighborId :token]))
          (resp/redirect (str "/dev/show-3dmap?lupapisteKey=" keyId) :see-other))
        (resp/status 401 "Unauthorized"))))

  (defpage [:get "/dev/show-3dmap"] {:keys [lupapisteKey]}
    (let [{:keys [applicationId apikey neighborId token]} (get @lupapisteKeys lupapisteKey)
          banner                                          " $$$$$$\\  $$$$$$$\\        $$\\      $$\\  $$$$$$\\  $$$$$$$\\        $$\\    $$\\ $$$$$$\\ $$$$$$$$\\ $$\\      $$\\ \n$$ ___$$\\ $$  __$$\\       $$$\\    $$$ |$$  __$$\\ $$  __$$\\       $$ |   $$ |\\_$$  _|$$  _____|$$ | $\\  $$ |\n\\_/   $$ |$$ |  $$ |      $$$$\\  $$$$ |$$ /  $$ |$$ |  $$ |      $$ |   $$ |  $$ |  $$ |      $$ |$$$\\ $$ |\n  $$$$$ / $$ |  $$ |      $$\\$$\\$$ $$ |$$$$$$$$ |$$$$$$$  |      \\$$\\  $$  |  $$ |  $$$$$\\    $$ $$ $$\\$$ |\n  \\___$$\\ $$ |  $$ |      $$ \\$$$  $$ |$$  __$$ |$$  ____/        \\$$\\$$  /   $$ |  $$  __|   $$$$  _$$$$ |\n$$\\   $$ |$$ |  $$ |      $$ |\\$  /$$ |$$ |  $$ |$$ |              \\$$$  /    $$ |  $$ |      $$$  / \\$$$ |\n\\$$$$$$  |$$$$$$$  |      $$ | \\_/ $$ |$$ |  $$ |$$ |               \\$  /   $$$$$$\\ $$$$$$$$\\ $$  /   \\$$ |\n \\______/ \\_______/       \\__|     \\__|\\__|  \\__|\\__|                \\_/    \\______|\\________|\\__/     \\__|\n"
          apikey-token                                    (token/get-usable-token apikey :consume false)
          user                                            (usr/get-user-with-token apikey-token)
          application                                     (mongo/select-one :applications {:_id applicationId})
          neighbor                                        (->> application
                                                               :neighbors
                                                               (util/find-by-id neighborId)
                                                               :owner)]
      (hiccup/html [:html
                    [:head [:title "3D Map View"]]
                    [:body {:style "background-color: #008b00; color: white; padding: 4em"}
                     [:pre banner]
                     [:ul
                      [:li (format "Application ID: %s (%s)" applicationId (:address application))]
                      [:li (format "User token id: %s" (if apikey-token
                                                         (:id apikey-token)
                                                         "-"))]
                      [:li (format "User: %s" (if user
                                                (format "%s %s" (:firstName user) (:lastName user))
                                                "-"))]
                      [:li (format "Neighbor token: %s" (or token "-"))]
                      [:li (format "Neighbor: %s" (if neighbor
                                                    (format "%s (%s)" (:name neighbor) (:email neighbor))
                                                    "-"))]]]])))

  ;; Reads and processes jatkoaika-ya.xml
  ;; Since the the xml is static, this is useful only in robots.
  (defpage "/dev/mock-ya-extension" []
    (-> "krysp/dev/jatkoaika-ya.xml"
        io/resource
        slurp
        (ss/replace "[YEAR]" (str (.getYear (date/now))))
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
                                "fi"
                                (noir-util/get-system))
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
                     (ah-reader/process-message (noir-util/get-system)
                                                zip-file
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
                         (ah-reader/process-message (noir-util/get-system)
                                                    zip-file
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
            (let [request (request/ring-request)
                  wait-ms (some-> request :params :delay util/->long (* 1000))]
              (when wait-ms
                (Thread/sleep ^long wait-ms))
              (when consume?
                (ring-request/body-string request))
              (resp/status (edn/read-string status)
                           (format "<FOO>Echo %s status</FOO>" status))))]
    (defpage [:post "/dev/statusecho/:status"] {status :status}
      (response status true))
    (defpage [:post "/dev/statusecho/:status/:any"] {status :status}
      (response status true))
    (defpage [:get "/dev/statusecho/:status"] {status :status}
      (response status false)))

  ;; Checks the verdict attachments for the verdicts created within the last hour.
  ;; Used in `lupapalvelu.autom-check-verdict-attachments-itest`.
  (defpage [:get "/dev/check-verdict-attachments"] {}
    (ok (resp/json (fetch-verdict/fetch-verdict-attachments
                     (noir-util/get-system)
                     {:start (date/timestamp (date/minus (date/now) :hour))
                      :end   (date/timestamp (date/now))}))))

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

  (defpage [:post "/dev/make-apikey/:username"] {:keys [username]}
    (let [apikey (mongo/create-id)]
      (mongo/update-by-query :users
                             {:username username}
                             {$set {:private.apikey apikey}})
      (ok (resp/json {:apikey apikey}))))

  ;; OAuth test endpoints

  (letfn [(make-link [path args]
            [:a
             {:href (str path "?" (clj-http/generate-query-string args))}
             "Start"])
          (html [status code]
            (hiccup/html
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
                                   (if registration? "enabled" "disabled"))}})))

  (defpage [:get "/dev/proxy-headers"] {}
    (-> (request/ring-request)
        :headers
        (select-keys ["host" "x-real-ip" "x-forwarded-for" "x-forwarded-proto"])
        resp/json
        ok))

  ;; AsiointiAPI local mock endpoint
  ;; `http://localhost:8000/dev/asiointi/notifications?failure-rate=25`
  ;; => artificially fail 25% of the time
  (defpage [:post "/dev/asiointi/notifications"] {}
    (let [{:keys [json query-params]} (request/ring-request)
          failure-rate                (some-> (get query-params "failure-rate")
                                              parse-long
                                              (/ 100.0))]
      (infof "Received AsiointiAPI notification: %s" json)
      (ring-response/status (if failure-rate
                              (if (< (rand) failure-rate)
                                400
                                200)
                              200)))))

(defn legacy-compatibility-json-middleware
  "The query body has been parsed by Muuntaja and included already as a
  map under the :parameters key. Here we copy the parsed body into a
  separate value under the :json key which is expected in some legacy
  Noir style functions."
  [handler]
  (fn [req]
    (handler (assoc req :json (get-in req [:parameters :body])))))

;; Reitit commands and queries

(defn ensure-response [data]
  (if (and (:body data)
           (:status data))
    data
    (ring-resp/ok data)))

(defn reitit-command-handler [request]
  (let [command-name (-> request :parameters :path :name)
        result (execute-command command-name (:json request) request)]
    (-> (ensure-response result)
        (assoc :headers http/no-cache-headers))))

(defn reitit-query-handler [request]
  (let [query-name (-> request :parameters :path :name)
        result (execute-query query-name (get-in request [:parameters :query]) request)]
    (-> (ensure-response result)
        (assoc :headers http/no-cache-headers))))

(defn reitit-raw-handler [request]
  (let [name (-> request :parameters :path :name)
        data (if (= :post (:request-method request))
               (get-in request [:parameters :body])
               (get-in request [:parameters :query]))
        response (execute (enriched (action/make-raw name data) request))]
    (cond
      (= response core/unauthorized) (resp/status 401 "unauthorized")
      (false? (:ok response)) (ring-resp/not-found response)
      :else response)))

(declare ^{:dynamic true} *request*)

(defn legacy-compatibility-request-map-dynamic-binding
  "Bind the current request to *request* which is used in the handlers
  by calling (noir.request/ring-request)."
  [handler]
  (fn [request]
    (binding [*request* request]
      (handler request))))

(def api-routes [["/api" {:middleware [cookie-monster
                                       control/lockdown-middleware
                                       security-headers/sanitize-header-values
                                       security-headers/add-security-headers
                                       autologin/catch-autologin-failure
                                       session-timeout
                                       wrap-authentication
                                       anti-csrf
                                       security-headers/session-id-to-mdc
                                       remove-_-param
                                       legacy-compatibility-json-middleware
                                       legacy-compatibility-request-map-dynamic-binding
                                       ;; Obsolete: Reitit MW config deals with this already and the legacy-json-middleware
                                       ;; adds the :info key to the request for compatibility with the existing stuff.
                                       ;; parse-json-body-middleware
                                       i18n/lang-middleware
                                       tempfile-cleanup]}
                  #_                  ["/raw/:name" {:get {:summary "Handler for raw"
                                                           :parameters {:path [:map [:name :string]]
                                                                        :query :any
                                                                        :body :any}
                                                           :responses {200 {:body :any}}
                                                           :handler #'reitit-raw-handler}
                                                     :post {:summary "Handler for raw"
                                                            :parameters {:path [:map [:name :string]]
                                                                         :query :any
                                                                         :body :any}
                                                            :responses {200 {:body :any}}
                                                            :handler #'reitit-raw-handler}}]
                  ["/command/:name" {:post {:summary "Handler for commands"
                                            :parameters {:path [:map [:name :string]]
                                                         :query :any
                                                         :body :any}
                                            :responses {200 {:body :any}}
                                            :handler #'reitit-command-handler}}]
                  ["/query/:name" {:get {:summary "Handler for queries"
                                         :parameters {:path [:map [:name :string]]
                                                      :query :any
                                                      :body :any}
                                         :responses {200 {:body :any}}
                                         :handler #'reitit-query-handler}}]]])

;; WEBHOOKS

(defn execute-check-model-webhook-command []
  (let [request (request/ring-request)
        ;; Merge query params since token is given in URL and rest are POST JSON
        params  (merge (-> request :query-params keywordize-keys)
                       (from-json request))]
    (execute-command "check-model-webhook" params request)))

;; Return BIM results via separate URL outside anti-csrf
(if (env/dev-mode?)
  ;; In dev mode, preflight (OPTIONS) request must be allowed for webhook.site to be able to forward the message
  (defjson [:any "/api/check-model-webhook"] [] (execute-check-model-webhook-command))
  (defjson [:post "/api/check-model-webhook"] [] (execute-check-model-webhook-command)))
