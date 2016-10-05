(ns lupapalvelu.web
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug info infof warn warnf error errorf fatal spy]]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [cheshire.core :as json]
            [me.raynes.fs :as fs]
            [ring.util.response :refer [resource-response]]
            [ring.util.io :as ring-io]
            [ring.middleware.content-type :refer [content-type-response]]
            [ring.middleware.anti-forgery :as anti-forgery]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [noir.session :as session]
            [noir.cookies :as cookies]
            [monger.operators :refer [$set $push]]
            [sade.core :refer [ok fail ok? fail? now def-] :as core]
            [sade.env :as env]
            [sade.http :as http]
            [sade.util :as util]
            [sade.property :as p]
            [sade.status :as status]
            [sade.strings :as ss]
            [sade.session :as ssess]
            [lupapalvelu.control-api :as control]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-search-api]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.features-api]
            [lupapalvelu.i18n :refer [*lang*] :as i18n]
            [lupapalvelu.singlepage :as singlepage]
            [lupapalvelu.user :as usr]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.proxy-services :as proxy-services]
            [lupapalvelu.organization-api]
            [lupapalvelu.application-api :as application]
            [lupapalvelu.foreman-api :as foreman-api]
            [lupapalvelu.open-inforequest-api]
            [lupapalvelu.pdf.pdf-export-api]
            [lupapalvelu.logging-api]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.token :as token]
            [lupapalvelu.activation :as activation]
            [lupapalvelu.logging :refer [with-logging-context]]
            [lupapalvelu.neighbors-api]
            [lupapalvelu.idf.idf-api :as idf-api]
            [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.calendars-api :as calendars]
            [lupapalvelu.ident.suomifi])
  (:import (java.io OutputStreamWriter BufferedWriter)))

;;
;; Helpers
;;

(defonce apis (atom #{}))

(defmacro defjson [path params & content]
  `(let [[m# p#] (if (string? ~path) [:get ~path] ~path)]
     (swap! apis conj {(keyword m#) p#})
     (defpage ~path ~params
       (let [response-data# (do ~@content)
             response-session# (:session response-data#)]
         (resp/set-headers
           http/no-cache-headers
           (if (contains? response-data# :session)
             (-> response-data#
               (dissoc :session)
               resp/json
               (assoc :session response-session#))
             (resp/json response-data#)))))))

(defjson "/system/apis" [] @apis)

(defn parse-json-body [{:keys [content-type character-encoding body] :as request}]
  (let [json-body (if (or (ss/starts-with content-type "application/json")
                          (ss/starts-with content-type "application/csp-report"))
                    (if body
                      (-> body
                        (io/reader :encoding (or character-encoding "utf-8"))
                        (json/parse-stream (comp keyword ss/strip-non-printables)))
                      {}))]
    (if json-body
      (assoc request :json json-body :params json-body)
      (assoc request :json nil))))

(defn parse-json-body-middleware [handler]
  (fn [request]
    (handler (parse-json-body request))))

(defn from-json [request]
  (:json request))

(defn from-query [request]
  (keywordize-keys (:query-params request)))

(defn host [request]
  (str (name (:scheme request)) "://" (get-in request [:headers "host"])))

(defn user-agent [request]
  (str (get-in request [:headers "user-agent"])))

(defn- web-stuff [request]
  {:user-agent (user-agent request)
   :client-ip  (http/client-ip request)
   :host       (host request)})

(defn- logged-in? [request]
  (not (nil? (:id (usr/current-user request)))))

(defn- in-role? [role request]
  (= role (keyword (:role (usr/current-user request)))))

(def applicant? (partial in-role? :applicant))
(def authority? (partial in-role? :authority))
(def oir? (partial in-role? :oirAuthority))
(def authority-admin? (partial in-role? :authorityAdmin))
(def admin? (partial in-role? :admin))
(defn- anyone [_] true)
(defn- nobody [_] false)

;;
;; Status
;;

(defn remove-sensitive-keys [m]
  (util/postwalk-map
    (partial filter (fn [[k v]] (if (or (string? k) (keyword? k)) (not (re-matches #"(?i).*(passw(or)?d.*|key)$" (name k))) true)))
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

(defn enriched [m request]
  (merge m {:user (usr/current-user request)
            :lang *lang*
            :session (:session request)
            :web  (web-stuff request)}))

(defn execute [action]
  (with-logging-context
    {:applicationId (get-in action [:data :id])
     :userId        (get-in action [:user :id])}
    (action/execute action)))

(defn execute-command [name params request]
  (execute (enriched (action/make-command name params) request)))

(defjson [:post "/api/command/:name"] {name :name}
  (let [request (request/ring-request)]
    (execute-command name (from-json request) request)))

(defn execute-query [name params request]
  (execute (enriched (action/make-query name params) request)))

(defjson "/api/query/:name" {name :name}
  (let [request (request/ring-request)]
    (execute-query name (from-query request) request)))

(defjson [:post "/api/datatables/:name"] {name :name}
  (let [request (request/ring-request)]
    (execute-query name (:params request) request)))

(defn basic-authentication
  "Returns a user map or nil if authentication fails"
  [request]
  (let [[u p] (http/decode-basic-auth request)]
    (when (and u p)
      (:user (execute-command "login" {:username u :password p} request)))))

(def basic-401
  (assoc-in (resp/status 401 "Unauthorized") [:headers "WWW-Authenticate"] "Basic realm=\"Lupapiste\""))

(defn execute-export [name params request]
  (execute (enriched (action/make-export name params) request)))

(defpage [:get "/rest/:name"] {name :name}
  (let [request (request/ring-request)
        user    (basic-authentication request)]
    (if user
      (let [response (execute (assoc (action/make-raw name (from-query request)) :user user))]
        (if (action/response? response)
          response
          (if (:ok response)
            (resp/status 200 (resp/json response))
            (resp/status 404 (resp/json response)))))
      basic-401)))

(defn json-data-as-stream [data]
  (ring-io/piped-input-stream
    #(json/generate-stream data (BufferedWriter. (OutputStreamWriter. % "utf-8")))))

(defn json-stream-response [data]
  (resp/content-type
    "application/json; charset=utf-8"
    (json-data-as-stream data)))

(defpage [:get "/data-api/json/:name"] {name :name}
  (let [request (request/ring-request)
        user (basic-authentication request)]
    (if user
      (json-stream-response (execute-export name (from-query request) (assoc request :user user)))
      basic-401)))

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

(def last-modified (util/to-RFC1123-datetime build-ts))

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
                   :oskari anyone
                   :neighbor anyone
                   :bulletins anyone})

(defn cache-headers [resource-type]
  (if (env/feature? :no-cache)
    {"Cache-Control" "no-cache"}
    (if (= :html resource-type)
      {"Cache-Control" "no-cache"
       "Last-Modified" last-modified}
      {"Cache-Control" "public, max-age=864000"
       "Vary"          "Accept-Encoding"
       "Last-Modified" last-modified})))

(def- never-cache #{:hashbang})

(def default-lang (name i18n/default-lang))

(def- compose
  (if (env/feature? :no-cache)
    singlepage/compose
    (memoize singlepage/compose)))

(defn- single-resource [resource-type app theme failure]
  (let [request (request/ring-request)
        lang    *lang*]
    (if ((auth-methods app nobody) request)
     ; Check If-Modified-Since header, see cache-headers above
     (if (or (never-cache app) (env/feature? :no-cache) (not= (get-in request [:headers "if-modified-since"]) last-modified))
       (->>
         (java.io.ByteArrayInputStream. (compose resource-type app lang theme))
         (resp/content-type (resource-type content-type))
         (resp/set-headers (cache-headers resource-type)))
       {:status 304})
     failure)))

(def- unauthorized (resp/status 401 "Unauthorized\r\n"))

;; CSS & JS
(defpage [:get ["/app/:build/:app.:res-type" :res-type #"(css|js)"]] {build :build app :app res-type :res-type}
  (let [build-number (:build-number env/buildinfo)]
    (if (= build build-number)
     (single-resource (keyword res-type) (keyword app) nil unauthorized)
     (resp/redirect (str "/app/" build-number "/" app "." res-type "?lang=" (name *lang*))))))

;; Single Page App HTML
(def apps-pattern
  (re-pattern (str "(" (s/join "|" (map name (keys auth-methods))) ")")))

(defn redirect [lang page]
  (resp/redirect (str "/app/" (name lang) "/" page)))

(defn redirect-after-logout [lang]
  (resp/redirect (str (env/value :host) (or (env/value :redirect-after-logout (keyword lang)) "/"))))

(defn redirect-to-frontpage [lang]
  (resp/redirect (str (env/value :host) (or (env/value :frontpage (keyword lang)) "/"))))

(defn- landing-page
  ([]     (landing-page default-lang (usr/current-user (request/ring-request))))
  ([lang] (landing-page lang         (usr/current-user (request/ring-request))) )
  ([lang user]
   (let [lang (get user :language lang)]
     (if-let [application-page (and (:id user) (usr/applicationpage-for (:role user)))]
       (redirect lang application-page)
       (redirect-to-frontpage lang)))))

(defn- ->hashbang [s]
  (let [hash (cond
               (string? s) s
               (sequential? s) (last s))
        sanitized-hash (some-> hash
                               (s/replace-first "%21" "!")
                               (ss/replace #"\.+/" ""))]
    (when (util/relative-local-url? sanitized-hash)
      (second (re-matches #"^[#!/]{0,3}(.*)" sanitized-hash)))))

(defn- save-hashbang-on-client [theme]
  (resp/set-headers {"Cache-Control" "no-cache", "Last-Modified" (util/to-RFC1123-datetime 0)}
    (single-resource :html :hashbang theme unauthorized)))

(defn serve-app [app hashbang theme]
  ; hashbangs are not sent to server, query-parameter hashbang used to store where the user wanted to go, stored on server, reapplied on login
  (if-let [hashbang (->hashbang hashbang)]
    (ssess/merge-to-session
      (request/ring-request) (single-resource :html (keyword app) theme (redirect-to-frontpage *lang*))
      {:redirect-after-login hashbang})
    ; If current user has no access to the app, save hashbang using JS on client side.
    ; The next call will then be handled by the "true branch" above.
    (single-resource :html (keyword app) theme (save-hashbang-on-client theme))))

(defpage [:get ["/app/:lang/:app" :lang #"[a-z]{2}" :app apps-pattern]] {app :app hashbang :redirect-after-login lang :lang theme :theme}
  (i18n/with-lang lang
    (serve-app app hashbang theme)))

; Same as above, but with an extra path.
(defpage [:get ["/app/:lang/:app/*" :lang #"[a-z]{2}" :app apps-pattern]] {app :app hashbang :redirect-after-login lang :lang theme :theme}
  (i18n/with-lang lang
    (serve-app app hashbang theme)))

;;
;; Login/logout:
;;

(defn- user-to-application-page [user lang]
  (ssess/merge-to-session (request/ring-request) (landing-page lang user) {:user (usr/session-summary user)}))

(defn- logout! []
  (cookies/put! :anti-csrf-token {:value "delete" :path "/" :expires "Thu, 01-Jan-1970 00:00:01 GMT"})
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
    (select-keys response [:ok :text :session :applicationpage :lang])))

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

;;
;; File upload
;;

(defpage [:post "/api/upload/attachment"]
  {:keys [applicationId attachmentId attachmentType operationId text upload typeSelector targetId targetType locked] :as data}
  (infof "upload: %s: %s type=[%s] op=[%s] selector=[%s], locked=%s" data upload attachmentType operationId typeSelector locked)
  (let [request (request/ring-request)
        target (when-not (every? s/blank? [targetId targetType])
                 (if (s/blank? targetId)
                   {:type targetType}
                   {:type targetType :id targetId}))
        attachment-type (att-type/parse-attachment-type attachmentType)
        group (cond
                (ss/blank? operationId) nil
                ((set att-tags/attachment-groups) (keyword operationId)) {:groupType (keyword operationId)}
                :else {:id operationId})
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
      (resp/redirect (str "/lp-static/html/upload-success.html#attachmentId=" (:attachmentId result)))
      (resp/redirect (str (hiccup.util/url "/lp-static/html/upload-1.127.html"
                                           (-> (:params request)
                                               (dissoc :upload)
                                               (dissoc ring.middleware.anti-forgery/token-key)
                                               (assoc  :errorMessage (:text result)))))))))

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
  (cond
    (control/lockdown?) (fail :error.service-lockdown)
    (usr/current-user (request/ring-request)) (ok)
    :else (fail :error.unauthorized)))

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
  (if-let [token (token/get-token token-id :consume false)]
    (resp/status 200 (resp/json (ok :token token)))
    (resp/status 404 (resp/json (fail :error.token-not-found)))))

(defpage [:post "/api/token/:token-id"] {token-id :token-id}
  (let [params (from-json (request/ring-request))
        response (token/consume-token token-id params :consume true)]
    (cond
      (contains? response :status) response
      (ok? response)   (resp/status 200 (resp/json response))
      (fail? response) (resp/status 404 (resp/json response))
      :else (resp/status 404 (resp/json (fail :error.unknown))))))

;;
;; Cross-site request forgery protection
;;

(defn- csrf-attack-hander [request]
  (with-logging-context
    {:applicationId (or (get-in request [:params :id]) (:id (from-json request)))
     :userId        (:id (usr/current-user request) "???")}
    (warnf "CSRF attempt blocked. Client IP: %s, Referer: %s" (http/client-ip request) (get-in request [:headers "referer"]))
    (->> (fail :error.invalid-csrf-token) (resp/json) (resp/status 403))))

(defn anti-csrf
  [handler]
  (fn [request]
    (if (env/feature? :disable-anti-csrf)
      (handler request)
      (let [cookie-name "anti-csrf-token"
            cookie-attrs (dissoc (env/value :cookie) :http-only)]
        (if (and (re-matches #"^/api/(command|query|datatables|upload).*" (:uri request))
                 (not (logged-in-with-apikey? request)))
          (anti-forgery/crosscheck-token handler request cookie-name csrf-attack-hander)
          (anti-forgery/set-token-in-cookie request (handler request) cookie-name cookie-attrs))))))

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
        response (handler request)]
    (if expired?
      (assoc response :session nil)
      (if (re-find #"^/api/(command|query|raw|datatables|upload)/" (:uri request))
        (ssess/merge-to-session request response {:expires (+ now (get-session-timeout request))})
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

;; Static error responses for testing
(defpage [:get ["/dev/:status"  :status #"[45]0\d"]] {status :status} (resp/status (util/->int status) status))

(when (env/feature? :dummy-krysp)
  (defn override-xml [xml-file overrides]
    (let [xml            (enlive/xml-resource xml-file)
          overridden-xml (reduce (fn [nodes override]
                                   (enlive/transform nodes
                                                     (->> (:selector override)
                                                          (map keyword))
                                                     (enlive/content (:value override))))
                                 xml overrides)]
      (apply str (enlive/emit* overridden-xml))))

  (defpage "/dev/krysp" {typeName :typeName r :request filter :filter overrides :overrides}
    (if-not (s/blank? typeName)
      (let [filter-type-name (-> filter sade.xml/parse (sade.common-reader/all-of [:PropertyIsEqualTo :PropertyName]))
            typeName (if (ss/starts-with typeName "kiito:") "kiito:every-type" typeName)
            xmls {"rakval:ValmisRakennus"                         "krysp/dev/building.xml"
                  "rakval:RakennusvalvontaAsia"                   "krysp/dev/verdict.xml"
                  "ymy:Ymparistolupa"                             "krysp/dev/verdict-yl.xml"
                  "ymm:MaaAineslupaAsia"                          "krysp/dev/verdict-mal.xml"
                  "ymv:Vapautus"                                  "krysp/dev/verdict-vvvl.xml"
                  "ppst:Poikkeamisasia,ppst:Suunnittelutarveasia" "krysp/dev/verdict-p.xml"
                  "kiito:every-type"                              "krysp/dev/verdict-kt.xml"}
            overrides (-> (json/decode overrides)
                          (clojure.walk/keywordize-keys))]
        ;; Use different xml for rakval query with kuntalupatunnus type of filter.
        (cond
          (and (= "rakval:RakennusvalvontaAsia" typeName)
               (= "rakval:luvanTunnisteTiedot/yht:LupaTunnus/yht:kuntalupatunnus" filter-type-name)) (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml")))
          (not-empty overrides) (resp/content-type "application/xml; charset=utf-8" (override-xml (io/resource (xmls typeName)) overrides))
          :else (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource (xmls typeName))))))
      (when (= r "GetCapabilities")
        (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/dev/capabilities.xml"))))))

  (defpage [:post "/dev/krysp"] {}
    (let [xml (sade.xml/parse (slurp (:body (request/ring-request))))
          xml-no-ns (sade.common-reader/strip-xml-namespaces xml)
          typeName (sade.xml/select1-attribute-value xml-no-ns [:Query] :typeName)]
      (when (= typeName "yak:Sijoituslupa,yak:Kayttolupa,yak:Liikennejarjestelylupa,yak:Tyolupa")
        (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/dev/verdict-ya.xml"))))))

  (defpage [:get "/dev/private-krysp"] []
    (let [request (request/ring-request)
          [u p] (http/decode-basic-auth request)]
      (if (= u p "pena")
        (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/dev/capabilities.xml")))
        basic-401))))

(env/in-dev
  (defjson [:any "/dev/spy"] []
    (dissoc (request/ring-request) :body))

  (defpage "/dev/header-echo" []
    (resp/status 200 (resp/set-headers (:headers (request/ring-request)) "OK")))

  (defpage "/dev/fixture/:name" {:keys [name]}
    (let [request (request/ring-request)
          response (execute-query "apply-fixture" {:name name} request)]
      (if (seq (re-matches #"(.*)MSIE [\.\d]+; Windows(.*)" (get-in request [:headers "user-agent"])))
        (resp/status 200 (str response))
        (resp/json response))))

  (defpage "/dev/create" {:keys [infoRequest propertyId message redirect state] :as query-params}
    (let [request (request/ring-request)
          property (p/to-property-id propertyId)
          params (assoc (from-query request) :propertyId property :messages (if message [message] []))
          {application-id :id :as response} (execute-command "create-application" params request)
          user (usr/current-user request)]
      (if (core/ok? response)
        (let [command (-> (domain/get-application-no-access-checking application-id)
                          action/application->command
                          (assoc :user user, :created (now)))]
          (cond
            (= state "submitted")
            (application/do-submit command)

            (and (ss/not-blank? state) (not= state (get-in command [:application :state])))
            (action/update-application command {$set {:state state}, $push {:history (app/history-entry state (:created command) user)}}))

          (if redirect
            (resp/redirect (str "/app/fi/" (str (usr/applicationpage-for (:role user))
                                                "#!/" (if infoRequest "inforequest" "application") "/" application-id)))
            (resp/status 200 application-id)))
        (resp/status 400 (str response)))))

  (defn- create-app-and-publish-bulletin []
    (let [request (request/ring-request)
          params (assoc (from-query request) :operation "lannan-varastointi"
                                             :address "Vaalantie 540"
                                             :propertyId (p/to-property-id "564-404-26-102")
                                             :x 430109.3125 :y 7210461.375)
          {id :id} (execute-command "create-application" params request)
          _        (mongo/update-by-id :applications id {$set {:state "sent"}})
          now     (sade.core/now)
          params  (-> (assoc (from-query request) :id id)
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

  ;; send ascii over the wire with wrong encoding (case: Vetuma)
  ;; direct:    http --form POST http://localhost:8080/dev/ascii Content-Type:'application/x-www-form-urlencoded' < dev-resources/input.ascii.txt
  ;; via nginx: http --form POST http://localhost/dev/ascii Content-Type:'application/x-www-form-urlencoded' < dev-resources/input.ascii.txt
  (defpage [:post "/dev/ascii"] {:keys [a]}
    (str a))

  (defpage [:get "/dev-pages/:file"] {:keys [file]}
    (->
      (resource-response (str "dev-pages/" file))
      (content-type-response {:uri file})))

  (defjson "/dev/fileinfo/:id" {:keys [id]}
    (dissoc (mongo/download id) :content))

  (defpage "/dev/by-id/:collection/:id" {:keys [collection id]}
    (if-let [r (mongo/by-id collection id)]
      (resp/status 200 (resp/json {:ok true  :data r}))
      (resp/status 404 (resp/json {:ok false :text "not found"}))))

  (defpage "/dev/public/:collection/:id" {:keys [collection id]}
    (if-let [r (mongo/by-id collection id)]
      (resp/status 200 (resp/json {:ok true  :data (lupapalvelu.neighbors-api/->public r)}))
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
       :empty (json/generate-string {})
       :auth (let [[username password] (http/decode-basic-auth (request/ring-request))]
               (if (and (= username "suti") (= password "secret"))
                 (json/generate-string {:productlist [{:name "Four" :expired true :expirydate "\\/Date(1467883327899)\\/" :downloaded "\\/Date(1467019327022)\\/" }
                                                      {:name "Five" :expired true :expirydate "\\/Date(1468056127124)\\/" :downloaded nil}
                                                      {:name "Six" :expired false :expirydate nil :downloaded nil}]})
                 (resp/status 401 "Unauthorized")))
       (json/generate-string {:productlist [{:name "One" :expired false :expirydate nil :downloaded nil}
                                            {:name "Two" :expired true :expirydate "\\/Date(1467710527123)\\/"
                                             :downloaded "\\/Date(1467364927456)\\/"}
                                            {:name "Three" :expired false :expirydate nil :downloaded nil}]}))))

  ;; Development (mockup) functionality of 3D map server, both backend and frontend
  ;; Username / password: 3dmap / 3dmap
  (defonce lupapisteKeys (atom {}))

  (defpage [:post "/dev/3dmap"] []
    (let [req-map (request/ring-request)
          [username password] (http/decode-basic-auth req-map)]
      (if (= username password "3dmap")
        (let [keyId (mongo/create-id)]
          (swap! lupapisteKeys assoc keyId (select-keys (:params req-map) [:applicationId :apikey]))
          (resp/redirect (str "/dev/show-3dmap?lupapisteKey=" keyId) :see-other))
        (resp/status 401 "Unauthorized"))))

  (defpage [:get "/dev/show-3dmap"] {:keys [lupapisteKey]}
    (let [{:keys [applicationId apikey]} (get @lupapisteKeys lupapisteKey)
          banner " $$$$$$\\  $$$$$$$\\        $$\\      $$\\  $$$$$$\\  $$$$$$$\\        $$\\    $$\\ $$$$$$\\ $$$$$$$$\\ $$\\      $$\\ \n$$ ___$$\\ $$  __$$\\       $$$\\    $$$ |$$  __$$\\ $$  __$$\\       $$ |   $$ |\\_$$  _|$$  _____|$$ | $\\  $$ |\n\\_/   $$ |$$ |  $$ |      $$$$\\  $$$$ |$$ /  $$ |$$ |  $$ |      $$ |   $$ |  $$ |  $$ |      $$ |$$$\\ $$ |\n  $$$$$ / $$ |  $$ |      $$\\$$\\$$ $$ |$$$$$$$$ |$$$$$$$  |      \\$$\\  $$  |  $$ |  $$$$$\\    $$ $$ $$\\$$ |\n  \\___$$\\ $$ |  $$ |      $$ \\$$$  $$ |$$  __$$ |$$  ____/        \\$$\\$$  /   $$ |  $$  __|   $$$$  _$$$$ |\n$$\\   $$ |$$ |  $$ |      $$ |\\$  /$$ |$$ |  $$ |$$ |              \\$$$  /    $$ |  $$ |      $$$  / \\$$$ |\n\\$$$$$$  |$$$$$$$  |      $$ | \\_/ $$ |$$ |  $$ |$$ |               \\$  /   $$$$$$\\ $$$$$$$$\\ $$  /   \\$$ |\n \\______/ \\_______/       \\__|     \\__|\\__|  \\__|\\__|                \\_/    \\______|\\________|\\__/     \\__|\n"
          address ((mongo/select-one :applications {:_id applicationId}) :address)
          {:keys [firstName lastName]} (usr/get-user-with-apikey apikey)]
      (hiccup.core/html [:html
                         [:head [:title "3D Map View"]]
                         [:body {:style "background-color: #008b00; color: white; padding: 4em"} [:pre banner]
                          [:ul
                           [:li (format "Application ID: %s (%s)" applicationId address)]
                           [:li (format "User: %s %s" firstName lastName)]]]])))
  )
