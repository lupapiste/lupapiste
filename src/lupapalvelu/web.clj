(ns lupapalvelu.web
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug info infof warn warnf error errorf fatal spy]]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [cheshire.core :as json]
            [me.raynes.fs :as fs]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.content-type :refer [content-type-response]]
            [ring.middleware.anti-forgery :as anti-forgery]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [noir.session :as session]
            [noir.cookies :as cookies]
            [sade.env :as env]
            [sade.util :as util]
            [sade.status :as status]
            [sade.strings :as ss]
            [lupapalvelu.core :refer [ok fail now] :as core]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.i18n :refer [*lang*]]
            [lupapalvelu.user :as user]
            [lupapalvelu.singlepage :as singlepage]
            [lupapalvelu.user :as user]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.proxy-services :as proxy-services]
            [lupapalvelu.organization]
            [lupapalvelu.application :as application]
            [lupapalvelu.ke6666 :as ke6666]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.token :as token]
            [lupapalvelu.activation :as activation]
            [lupapalvelu.logging :refer [with-logging-context]]
            [lupapalvelu.neighbors]
            [lupapalvelu.idf.idf-server :as idf-server]))

;;
;; Helpers
;;

(defonce apis (atom #{}))

(defmacro defjson [path params & content]
  `(let [[m# p#] (if (string? ~path) [:get ~path] ~path)]
     (swap! apis conj {(keyword m#) p#})
     (defpage ~path ~params
       (resp/json (do ~@content)))))

(defjson "/system/apis" [] @apis)

(defn parse-json-body [request]
  (let [json-body (if (ss/starts-with (:content-type request) "application/json")
                    (if-let [body (:body request)]
                      (-> body
                        (io/reader :encoding (or (:character-encoding request) "utf-8"))
                        json/parse-stream
                        keywordize-keys)
                      {}))]
    (if json-body
      (assoc request :json json-body :params json-body)
      (assoc request :json nil))))

(defn parse-json-body-middleware [handler]
  (fn [request]
    (handler (parse-json-body request))))

(defn from-json [request]
  (:json request))

(defn from-query []
  (keywordize-keys (:query-params (request/ring-request))))

(defn host [request]
  (str (name (:scheme request)) "://" (get-in request [:headers "host"])))

(defn user-agent [request]
  (str (get-in request [:headers "user-agent"])))

(defn sessionId [request]
  (get-in request [:cookies "ring-session" :value]))

(defn client-ip [request]
  (or (get-in request [:headers "x-real-ip"]) (get-in request [:remote-addr])))

(defn- web-stuff [request]
  {:user-agent (user-agent request)
   :client-ip  (client-ip request)
   :sessionId  (sessionId request)
   :host       (host request)})

(defn- logged-in?
  ([] (logged-in? (request/ring-request)))
  ([request]
    (not (nil? (:id (user/current-user request))))))

(defn- in-role? [role]
  (= role (keyword (:role (user/current-user (request/ring-request))))))

(defn- authority? [] (in-role? :authority))
(defn- authority-admin? [] (in-role? :authorityAdmin))
(defn- admin? [] (in-role? :admin))
(defn- anyone [] true)
(defn- nobody [] false)

;;
;; Status
;;

(defn remove-sensitive-keys [m]
  (util/postwalk-map
    (partial filter (fn [[k v]] (if (or (string? k) (keyword? k)) (not (re-matches #"(?i).*(password.*|key)$" (name k))) true)))
    m))

(status/defstatus :build (assoc env/buildinfo :server-mode env/mode))
(status/defstatus :time  (. (new org.joda.time.DateTime) toString "dd.MM.yyyy HH:mm:ss"))
(status/defstatus :mode  env/mode)
(status/defstatus :system-env (remove-sensitive-keys (System/getenv)))
(status/defstatus :system-properties (remove-sensitive-keys (System/getProperties)))
(status/defstatus :sade-env (remove-sensitive-keys (env/get-config)))

;;
;; Commands
;;

(defn enriched [m request]
  (merge m {:user (user/current-user request)
            :lang *lang*
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
  (execute-query name (from-query) (request/ring-request)))

(defjson [:post "/api/datatables/:name"] {name :name}
  (let [request (request/ring-request)]
    (execute-query name (:params request) request)))

(defjson [:get "/data-api/:name"] {name :name}
  (execute-query name (from-query) (request/ring-request)))

(defpage "/api/raw/:name" {name :name}
  (let [response (execute (enriched (action/make-raw name (from-query)) (request/ring-request)))]
    (if-not (= (:ok response) false)
      response
      (resp/status 404 (resp/json response)))))

;;
;; Web UI:
;;

(def ^:private build-number (:build-number env/buildinfo))

(def etag (str "\"" build-number "\""))

(def content-type {:html "text/html; charset=utf-8"
                   :js   "application/javascript; charset=utf-8"
                   :css  "text/css; charset=utf-8"})

(def auth-methods {:init anyone
                   :cdn-fallback anyone
                   :hashbang anyone
                   :upload logged-in?
                   :applicant logged-in?
                   :authority authority?
                   :oir authority?
                   :authority-admin authority-admin?
                   :admin admin?
                   :login-frame anyone
                   :welcome anyone
                   :oskari anyone
                   :neighbor anyone})

(defn cache-headers [resource-type]
  (if (env/dev-mode?)
    {"Cache-Control" "no-cache"}
    (if (= :html resource-type)
      {"Cache-Control" "no-cache"
       "ETag"          etag}
      {"Cache-Control" "public, max-age=864000"
       "Vary"          "Accept-Encoding"
       "ETag"          etag})))

(def ^:private never-cache #{:hashbang})

(def default-lang "fi")

(def ^:private compose
  (if (env/dev-mode?)
    singlepage/compose
    (memoize (fn [resource-type app] (singlepage/compose resource-type app)))))

(defn- single-resource [resource-type app failure]
  (if ((auth-methods app nobody))
    ; Check If-None-Match header, see cache-headers above
    (if (or (never-cache app) (s/blank? build-number) (not= (get-in (request/ring-request) [:headers "if-none-match"]) etag))
      (->>
        (java.io.ByteArrayInputStream. (compose resource-type app))
        (resp/content-type (resource-type content-type))
        (resp/set-headers (cache-headers resource-type)))
      {:status 304})
    failure))

(def ^:private unauthorized (resp/status 401 "Unauthorized\r\n"))

;; CSS & JS
(defpage [:get ["/app/:app.:res-type" :res-type #"(css|js)"]] {app :app res-type :res-type}
  (single-resource (keyword res-type) (keyword app) unauthorized))

;; Single Page App HTML
(def apps-pattern
  (re-pattern (str "(" (clojure.string/join "|" (map name (keys auth-methods))) ")")))

(defn redirect [lang page]
  (resp/redirect (str "/app/" (name lang) "/" page)))

(defn redirect-to-server-root []
  (resp/redirect (str (env/value :host) "/")))

(defn redirect-to-frontpage [lang]
  (redirect lang "welcome"))

(defn- landing-page
  ([]
    (landing-page default-lang))
  ([lang]
    (if-let [application-page (and (logged-in?) (user/applicationpage-for (:role (user/current-user (request/ring-request)))))]
      (redirect lang application-page)
      (redirect-to-frontpage lang))))

(defn- ->hashbang [v]
  (when (and v (= -1 (.indexOf v ":")))
    (second (re-matches #"^[#!/]{0,3}(.*)" v))))

(defn- save-hashbang-on-client []
  (resp/set-headers {"Cache-Control" "no-cache", "ETag" "\"none\""}
    (single-resource :html :hashbang unauthorized)))

(defn serve-app [app hashbang lang]
  ; hashbangs are not sent to server, query-parameter hashbang used to store where the user wanted to go, stored on server, reapplied on login
  (if-let [hashbang (->hashbang hashbang)]
    (do
      (session/put! :hashbang hashbang)
      (single-resource :html (keyword app) (redirect-to-frontpage lang)))
    ; If current user has no access to the app, save hashbang using JS on client side.
    ; The next call will then be handled by the "true branch" above.
    (single-resource :html (keyword app) (save-hashbang-on-client))))

(defpage [:get ["/app/:lang/:app" :lang #"[a-z]{2}" :app apps-pattern]] {app :app hashbang :hashbang lang :lang}
  (serve-app app hashbang lang))

; Same as above, but with an extra path.
(defpage [:get ["/app/:lang/:app/*" :lang #"[a-z]{2}" :app apps-pattern]] {app :app hashbang :hashbang lang :lang}
  (serve-app app hashbang lang))

(defjson "/api/hashbang" []
  (ok :bang (session/get! :hashbang "")))

(defcommand "frontend-error" {}
  [{{:keys [page message]} :data {:keys [email]} :user {:keys [user-agent]} :web}]
  (let [limit    1000
        sanitize (partial lupapalvelu.logging/sanitize limit)
        sanitized-page (sanitize (or page "(unknown)"))
        user           (or (ss/lower-case email) "(anonymous)")
        sanitized-ua   (sanitize user-agent)
        sanitized-msg  (sanitize (str message))]
    (errorf "FRONTEND: %s [%s] got an error on page %s: %s"
            user sanitized-ua sanitized-page sanitized-msg)))

;;
;; Login/logout:
;;

(defn- logout! []
  (session/clear!)
  (cookies/put! :anti-csrf-token {:value "delete" :path "/" :expires "Thu, 01-Jan-1970 00:00:01 GMT"}))

(defjson [:post "/api/logout"] []
  (logout!)
  (ok))

(defpage "/logout" []
  (logout!)
  (redirect-to-server-root))

(defpage [:get ["/app/:lang/logout" :lang #"[a-z]{2}"]] {lang :lang}
  (logout!)
  (redirect-to-server-root))

;; Login via saparate URL outside anti-csrf
(defjson [:post "/api/login"] {username :username :as params}
  (let [request (request/ring-request)]
    (if username
     (execute-command "login" params request) ; Handles form POST (Nessus)
     (execute-command "login" (from-json request) request))))

;; Reset password via saparate URL outside anti-csrf
(defjson [:post "/api/reset-password"] []
  (let [request (request/ring-request)]
    (execute-command "reset-password" (from-json request) request)))

;;
;; Redirects
;;

(defpage "/" [] (landing-page))
(defpage "/app/" [] (landing-page))
(defpage [:get ["/app/:lang"  :lang #"[a-z]{2}"]] {lang :lang} (landing-page lang))
(defpage [:get ["/app/:lang/" :lang #"[a-z]{2}"]] {lang :lang} (landing-page lang))


;;
;; FROM SADE
;;

(defjson "/system/ping" [] {:ok true})
(defjson "/system/status" [] (status/status))

(def activation-route (str (env/value :activation :path) ":activation-key"))
(defpage activation-route {key :activation-key}
  (if-let [user (activation/activate-account key)]
    (do
      (infof "User account '%s' activated, auto-logging in the user" (:username user))
      (session/put! :user user)
      (let [application-page (user/applicationpage-for (:role user))]
        (redirect default-lang application-page)))
    (do
      (warnf "Invalid user account activation attempt with key '%s', possible hacking attempt?" key)
      (landing-page))))

;;
;; Apikey-authentication
;;

(defn- parse [required-key header-value]
  (when (and required-key header-value)
    (if-let [[_ k v] (re-find #"(\w+)\s*[ :=]\s*(\w+)" header-value)]
      (if (= k required-key) v))))

(defn- get-apikey [request]
  (let [authorization (get-in request [:headers "authorization"])]
    (parse "apikey" authorization)))

(defn authentication
  "Middleware that adds :user to request. If request has apikey authentication header then
   that is used for authentication. If not, then use user information from session."
  [handler]
  (fn [request]
    (let [api-key (get-apikey request)
          api-key-auth (when-not (ss/blank? api-key) (user/get-user-with-apikey api-key))
          session-user (session/get :user)]
      (handler (assoc request :user (or api-key-auth session-user))))))

(defn- logged-in-with-apikey? [request]
  (and (get-apikey request) (logged-in? request)))

;;
;; File upload
;;

(defpage [:post "/api/upload/attachment"]
  {:keys [applicationId attachmentId attachmentType text upload typeSelector targetId targetType locked authority] :as data}
  (infof "upload: %s: %s type=[%s] selector=[%s], locked=%s, authority=%s" data upload attachmentType typeSelector locked authority)
  (let [target (when-not (every? s/blank? [targetId targetType])
                 (if (s/blank? targetId)
                   {:type targetType}
                   {:type targetType :id targetId}))
        upload-data (assoc upload
                      :id applicationId
                      :attachmentId attachmentId
                      :target target
                      :locked (java.lang.Boolean/parseBoolean locked)
                      :authority (java.lang.Boolean/parseBoolean authority)
                      :text text)
        attachment-type (attachment/parse-attachment-type attachmentType)
        upload-data (if attachment-type
                      (assoc upload-data :attachmentType attachment-type)
                      upload-data)
        result (execute (enriched (action/make-command "upload-attachment" upload-data) (request/ring-request)))]
    (if (core/ok? result)
      (resp/redirect "/html/pages/upload-ok.html")
      (resp/redirect (str (hiccup.util/url "/html/pages/upload-1.13.html"
                                        (-> (:params (request/ring-request))
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
        (when-let [tempfile (get-in request [:params :upload :tempfile])]
          (fs/delete tempfile))))))

;;
;; Server is alive
;;

(defjson "/api/alive" [] {:ok (if (user/current-user (request/ring-request)) true false)})

;;
;; Proxy
;;

(defpage [:any "/proxy/:srv"] {srv :srv}
  (if @env/proxy-off
    {:status 503}
    ((proxy-services/services srv (constantly {:status 404})) (request/ring-request))))

;;
;; Token consuming:
;;

(defpage [:get "/api/token/:token-id"] {token-id :token-id}
  (let [params (:params (request/ring-request))
        response (token/consume-token token-id params)]
    (or response {:status 404})))

(defpage [:post "/api/token/:token-id"] {token-id :token-id}
  (let [params (from-json (request/ring-request))
        response (token/consume-token token-id params)]
    (or response (resp/status 404 (resp/json {:ok false})))))

;;
;; Cross-site request forgery protection
;;

(defn- csrf-attack-hander [request]
  (with-logging-context
    {:applicationId (or (get-in request [:params :id]) (:id (from-json request)))
     :userId        (:id (user/current-user request) "???")}
    (warnf "CSRF attempt blocked. Client IP: %s, Referer: %s" (client-ip request) (get-in request [:headers "referer"]))
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
  (get-in request [:session :noir :user :session-timeout] (.toMillis java.util.concurrent.TimeUnit/HOURS 4)))

(defn session-timeout-handler [handler request]
  (let [now (now)
        expires (session/get :expires now)
        expired? (< expires now)]
    (if expired?
      (session/clear!)
      (if (re-find #"^/api/(command|query|raw|datatables|upload)/" (:uri request))
        (session/put! :expires (+ now (get-session-timeout request)))))
    (handler request)))

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
  (idf-server/handle-create-user-request etunimi sukunimi
          email puhelin katuosoite postinumero postitoimipaikka
          suoramarkkinointilupa ammattilainen
          app id ts mac))

;;
;; dev utils:
;;

;; Static error responses for testing
(defpage [:get ["/dev/:status"  :status #"[45]0\d"]] {status :status} (resp/status (util/->int status) status))

(when (env/feature? :dummy-krysp)
  (defpage "/dev/krysp" {typeName :typeName r :request}
    (if-not (s/blank? typeName)
      (let [xmls {"rakval:ValmisRakennus"       "krysp/sample/building.xml"
                  "rakval:RakennusvalvontaAsia" "krysp/sample/verdict.xml"
                  "ymy:Ymparistolupa"           "krysp/sample/verdict-yl.xml"
                  "ymm:MaaAineslupaAsia"        "krysp/sample/verdict-mal.xml"
                  "ymv:Vapautus"                "krysp/sample/verdict-vvvl.xml"
                  "ppst:Poikkeamisasia,ppst:Suunnittelutarveasia" "krysp/sample/poikkari-verdict-cgi.xml"}]
        (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource (xmls typeName)))))
      (when (= r "GetCapabilities")
        (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/sample/capabilities.xml"))))))

  (defpage [:post "/dev/krysp"] {}
    (let [xml (sade.xml/parse (slurp (:body (request/ring-request))))
          xml-no-ns (sade.common-reader/strip-xml-namespaces xml)
          typeName (sade.xml/select1-attribute-value xml-no-ns [:Query] :typeName)]
      (when (= typeName "yak:Sijoituslupa,yak:Kayttolupa,yak:Liikennejarjestelylupa,yak:Tyolupa")
        (resp/content-type "application/xml; charset=utf-8" (slurp (io/resource "krysp/sample/yleiset alueet/ya-verdict.xml")))))))

(env/in-dev
  (defjson [:any "/dev/spy"] []
    (dissoc (request/ring-request) :body))

  (defpage "/dev/fixture/:name" {:keys [name]}
    (let [request (request/ring-request)
          response (execute-query "apply-fixture" {:name name} request)]
      (if (seq (re-matches #"(.*)MSIE [\.\d]+; Windows(.*)" (get-in (request/ring-request) [:headers "user-agent"])))
        (resp/status 200 (str response))
        (resp/json response))))

  (defpage "/dev/create" {:keys [infoRequest propertyId message]}
    (let [request (request/ring-request)
          property (util/to-property-id propertyId)
          params (assoc (from-query) :propertyId property :messages (if message [message] []))
          response (execute-command "create-application" params request)]
      (if (core/ok? response)
        (redirect "fi" (str (user/applicationpage-for (:role (user/current-user (request/ring-request))))
                            "#!/" (if infoRequest "inforequest" "application") "/" (:id response)))
        (resp/status 400 (str response)))))

  ;; send ascii over the wire with wrong encofing (case: Vetuma)
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

  (defjson "/dev/hgnotes" [] (env/hgnotes))

  (defpage "/dev/by-id/:collection/:id" {:keys [collection id]}
    (if-let [r (mongo/by-id collection id)]
      (resp/status 200 (resp/json {:ok true  :data r}))
      (resp/status 404 (resp/json {:ok false :text "not found"}))))

  (defpage "/dev/public/:collection/:id" {:keys [collection id]}
    (if-let [r (mongo/by-id collection id)]
      (resp/status 200 (resp/json {:ok true  :data (lupapalvelu.neighbors/->public r)}))
      (resp/status 404 (resp/json {:ok false :text "not found"}))))

  (defpage [:get "/api/proxy-ctrl"] []
    (resp/json {:ok true :data (not @env/proxy-off)}))

  (defpage [:post "/api/proxy-ctrl/:value"] {value :value}
    (let [on (condp = value
               true   true
               "true" true
               "on"   true
               false)]
      (resp/json {:ok true :data (swap! env/proxy-off (constantly (not on)))}))))
