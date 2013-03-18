(ns lupapalvelu.web
  (:use [noir.core :only [defpage]]
        [lupapalvelu.core :only [ok fail]]
        [clojure.tools.logging]
        [clojure.tools.logging]
        [clj-logging-config.log4j :only [with-logging-context]]
        [clojure.walk :only [keywordize-keys]]
        [clojure.string :only [blank?]])
  (:require [noir.request :as request]
            [noir.response :as resp]
            [noir.session :as session]
            [noir.server :as server]
            [noir.cookies :as cookies]
            [lupapalvelu.env :as env]
            [lupapalvelu.core :as core]
            [lupapalvelu.action :as action]
            [lupapalvelu.singlepage :as singlepage]
            [lupapalvelu.security :as security]
            [lupapalvelu.user :as user]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.proxy-services :as proxy-services]
            [lupapalvelu.municipality]
            [lupapalvelu.application :as application]
            [lupapalvelu.ke6666 :as ke6666]
            [lupapalvelu.mongo :as mongo]
            [sade.security :as sadesecurity]
            [sade.status :as status]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [ring.middleware.anti-forgery :as anti-forgery]))

;;
;; Helpers
;;

(defonce apis (atom #{}))

(defmacro defjson [path params & content]
  `(do
     (swap! apis conj ~path)
     (defpage ~path ~params
       (resp/json (do ~@content)))))

(defn from-json [request]
  (json/decode (slurp (:body request)) true))

(defn from-query []
  (keywordize-keys (:query-params (request/ring-request))))

(defn current-user
  "fetches the current user from 1) http-session 2) apikey from headers"
  ([] (current-user (request/ring-request)))
  ([request] (or (session/get :user) (request :user))))

(defn host [request]
  (str (name (:scheme request)) "://" (get-in request [:headers "host"])))

(defn user-agent [request]
  (str (get-in request [:headers "user-agent"])))

(defn sessionId [request]
  (get-in request [:cookies "ring-session" :value]))

(defn client-ip [request]
  (or (get-in request [:headers "x-real-ip"]) (get-in request [:remote-addr])))

(defn web-stuff []
  (let [request (request/ring-request)]
    {:user-agent (user-agent request)
     :client-ip  (client-ip request)
     :sessionId  (sessionId request)
     :host       (host request)}))

(defn logged-in?
  ([] (logged-in? (request/ring-request)))
  ([request] (not (nil? (current-user request)))))

(defn in-role? [role]
  (= role (keyword (:role (current-user)))))

(defn authority? [] (in-role? :authority))
(defn authority-admin? [] (in-role? :authorityAdmin))
(defn admin? [] (in-role? :admin))
(defn anyone [] true)
(defn nobody [] false)

;;
;; Status
;;

(status/defstatus :build (assoc env/buildinfo :server-mode env/mode))
(status/defstatus :time  (. (new org.joda.time.DateTime) toString "dd.MM.yyyy HH:mm:ss"))
(status/defstatus :mode  env/mode)

(defjson "/api/buildinfo" []
  (ok :data (assoc env/buildinfo :server-mode env/mode)))

;;
;; Commands
;;

(defn enriched [m]
  (merge m {:user (current-user)
            :web  (web-stuff)}))

;; MDC will throw NPE on nil values. Fix sent to clj-logging-config.log4j (Tommi 17.2.2013)
(defn execute [action]
  (with-logging-context
    {:applicationId (get-in action [:data :id] "???")
     :userId        (get-in action [:user :id] "???")}
    (core/execute action)))

(defjson [:post "/api/command/:name"] {name :name}
  (execute (enriched (core/command name (from-json (request/ring-request))))))

(defjson "/api/query/:name" {name :name}
  (execute (enriched (core/query name (from-query)))))

;;
;; Web UI:
;;

(def content-type {:html "text/html; charset=utf-8"
                   :js   "application/javascript; charset=utf-8"
                   :css  "text/css; charset=utf-8"})

(def auth-methods {:init anyone
                   :cdn-fallback anyone
                   :welcome anyone
                   :upload logged-in?
                   :applicant logged-in?
                   :authority authority?
                   :authority-admin authority-admin?
                   :admin admin?})

(def headers
  (if (env/dev-mode?)
    {"Cache-Control" "no-cache"}
    {"Cache-Control" "public, max-age=86400"}))

(def default-lang "fi")

(defn- single-resource [resource-type app failure]
  (if ((auth-methods app nobody))
    (->>
      (singlepage/compose resource-type app)
      (resp/content-type (resource-type content-type))
      (resp/set-headers headers))
      failure))

;; CSS & JS
(defpage [:get ["/:app.:res-type" :res-type #"(css|js)"]] {app :app res-type :res-type}
  (single-resource (keyword res-type) (keyword app) (resp/status 401 "Unauthorized\r\n")))

;; Single Page App HTML
(def apps-pattern
  (re-pattern (str "(" (clojure.string/join "|" (map #(name %) (keys auth-methods))) ")")))

(defn- local? [uri] (and uri (= -1 (.indexOf uri ":"))))

(defjson "/api/hashbang" []
  (ok :bang (session/get! :hashbang "")))

(defn- redirect-to-frontpage [lang]
  (resp/redirect (str "/" (name lang) "/welcome#")))

(defpage [:get ["/:lang/:app" :lang #"[a-z]{2}" :app apps-pattern]] {app :app hashbang :hashbang}
  ;; hashbangs are not sent to server, query-parameter hashbang used to store where the user wanted to go, stored on server, reapplied on login
  (when (and hashbang (local? hashbang))
    (session/put! :hashbang hashbang))
  (single-resource :html (keyword app) (redirect-to-frontpage :fi)))

;;
;; Login/logout:
;;

(defn- redirect-to-frontpage [lang]
  (resp/redirect (str "/" (name lang) "/welcome")))

(defn- logout! []
  (session/clear!)
  (cookies/put! :lupapiste-token {:value "delete" :path "/" :expires "Thu, 01-Jan-1970 00:00:01 GMT"}))

(defjson [:post "/api/logout"] []
  (logout!)
  (ok))

(defpage "/logout" []
  (logout!)
  (resp/redirect "/"))

(defpage [:get ["/:lang/logout" :lang #"[a-z]{2}"]] {lang :lang}
  (logout!)
  (redirect-to-frontpage lang))

(defpage "/" []
  (if (logged-in?)
    (if-let [application-page (user/applicationpage-for (:role (current-user)))]
      (resp/redirect (str "/" default-lang application-page))
      (redirect-to-frontpage default-lang))
    (redirect-to-frontpage default-lang)))

;;
;; FROM SADE
;;

(defjson "/system/ping" [] {:ok true})
(defjson "/system/status" [] (status/status))

(defpage "/security/activate/:activation-key" {key :activation-key}
  (if-let [user (sadesecurity/activate-account key)]
    (do
      (infof "User account '%s' activated, auto-logging in the user" (:username user))
      (session/put! :user user)
      (resp/redirect "/"))
    (do
      (warn (format "Invalid user account activation attempt with key '%s', possible hacking attempt?" key))
      (resp/redirect "/"))))

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

(defn apikey-authentication
  "Reads apikey from 'Auhtorization' headers, pushed it to :user request attribute
   'curl -H \"Authorization: apikey APIKEY\" http://localhost:8000/api/application"
  [handler]
  (fn [request]
    (let [apikey (get-apikey request)]
      (handler (assoc request :user (security/login-with-apikey apikey))))))


(defn- logged-in-with-apikey? [request]
  (and (get-apikey request) (logged-in? request)))

;;
;; File upload/download:
;;

(defpage [:post "/api/upload"]
  {:keys [applicationId attachmentId attachmentType text upload typeSelector] :as data}
  (debugf "upload: %s: %s type=[%s] selector=[%s]" data upload attachmentType typeSelector)
  (let [upload-data (assoc upload
                           :id applicationId
                           :attachmentId attachmentId
                           :text text)
        attachment-type (attachment/parse-attachment-type attachmentType)
        upload-data (if attachment-type
                      (assoc upload-data :attachmentType attachment-type)
                      upload-data)
        result (core/execute (enriched (core/command "upload-attachment" upload-data)))]
    (if (core/ok? result)
      (resp/redirect "/html/pages/upload-ok.html")
      (resp/redirect (str (hiccup.util/url "/html/pages/upload.html"
                                           {:applicationId applicationId
                                            :attachmentId attachmentId
                                            :attachmentType (or attachmentType "")
                                            :typeSelector typeSelector
                                            :errorMessage (result :text)}))))))

(defn- output-attachment [attachment-id download?]
  (if (logged-in?)
    (attachment/output-attachment attachment-id (current-user) download?)
    (resp/status 401 "Unauthorized\r\n")))

(defpage "/api/view-attachment/:attachment-id" {attachment-id :attachment-id}
  (output-attachment attachment-id false))

(defpage "/api/download-attachment/:attachment-id" {attachment-id :attachment-id}
  (output-attachment attachment-id true))

(defpage "/api/download-all-attachments/:application-id" {application-id :application-id lang :lang :or {lang "fi"}}
  (attachment/output-all-attachments application-id (current-user) lang))

(defpage "/api/pdf-export/:application-id" {application-id :application-id lang :lang :or {lang "fi"}}
  (ke6666/export application-id (current-user) lang))

;;
;; Proxy
;;

(defpage [:any "/proxy/:srv"] {srv :srv}
  (if (logged-in?)
    (if env/proxy-off
      {:status 503}
      ((proxy-services/services srv (constantly {:status 404})) (request/ring-request)))
    {:status 401}))

;;
;; Cross-site request forgery protection
;;

(defn- csrf-attack-hander [request]
  (with-logging-context
    {:applicationId (or (get-in request [:params :id]) (:id (from-json request)) "???")
     :userId        (or (:id (current-user request)) "???")}
    (warn "CSRF attempt blocked."
          "Client IP:" (client-ip request)
          "Referer:" (get-in request [:headers "referer"]))
    (resp/json (fail :error.invalid-csrf-token))))

(defn anti-csrf
  [handler]
  (fn [request]
    (let [cookie-name "lupapiste-token"]
      (if (and (re-matches #"^/api/(command|query|upload).*" (:uri request))
               (not (logged-in-with-apikey? request)))
        (anti-forgery/crosscheck-token handler request cookie-name csrf-attack-hander)
        (anti-forgery/set-token-in-cookie request (handler request) cookie-name)))))

;;
;; dev utils:
;;

(env/in-dev
  (defjson "/api/spy" []
    (dissoc (request/ring-request) :body))

  (defpage "/api/by-id/:collection/:id" {:keys [collection id]}
    (if-let [r (mongo/by-id collection id)]
      (resp/status 200 (resp/json {:ok true  :data r}))
      (resp/status 404 (resp/json {:ok false :text "not found"})))))
