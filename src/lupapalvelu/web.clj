(ns lupapalvelu.web
  (:use [noir.core :only [defpage]]
        [lupapalvelu.core :only [ok fail]]
        [lupapalvelu.log]
        [clojure.walk :only [keywordize-keys]]
        [clojure.string :only [blank?]])
  (:require (noir  [request :as request]
                   [response :as resp]
                   [session :as session]
                   [server :as server])
            (lupapalvelu [env :as env]
                         [core :as core]
                         [action :as action]
                         [singlepage :as singlepage]
                         [security :as security]
                         [attachment :as attachment]
                         [proxy-services :as proxy-services])
            [sade.security :as sadesecurity]
            [cheshire.core :as json]
            [clj-http.client :as client]))

;;
;; Helpers
;;

(defmacro defjson [path params & content]
  `(defpage ~path ~params
     (resp/json (do ~@content))))

(defn from-json []
  (json/decode (slurp (:body (request/ring-request))) true))

(defn from-query []
  (keywordize-keys (:query-params (request/ring-request))))

(defn current-user
  "fetches the current user from 1) http-session 2) apikey from headers"
  []
  (or (session/get :user) ((request/ring-request) :user)))

(defn host [request]
  (str (name (:scheme request)) "://" (get-in request [:headers "host"])))

(defn user-agent [request]
  (str (get-in request [:headers "user-agent"])))

(defn client-ip [request]
  (or (get-in request [:headers "real-ip"]) (get-in request [:remote-addr])))

(defn web-stuff []
  (let [request (request/ring-request)]
    {:user-agent (user-agent request)
     :client-ip  (client-ip request)
     :host       (host request)}))

(defn enriched [m]
  (merge m {:user (current-user)
            :web  (web-stuff)}))

(defn logged-in? []
  (not (nil? (current-user))))

(defn has-role? [role]
  (= role (keyword (:role (current-user)))))

(defn authority? [] (has-role? :authority))
(defn authority-admin? [] (has-role? :authorityAdmin))
(defn admin? [] (has-role? :admin))
(defn anyone [] true)
(defn nobody [] false)

;;
;; API:
;;

(defjson "/api/buildinfo" []
  (ok :data (assoc (read-string (slurp (.getResourceAsStream (clojure.lang.RT/baseLoader) "buildinfo.clj"))) :server-mode env/mode)))

(defjson "/api/ping" [] (ok))

;;
;; Commands
;;

(defjson [:post "/api/command/:name"] {name :name}
  (core/execute (enriched (core/command name (from-json)))))

(defjson "/api/query/:name" {name :name}
  (core/execute (enriched (core/query name (from-query)))))

;;
;; Web UI:
;;

(def content-type {:html "text/html; charset=utf-8"
                   :js   "application/javascript"
                   :css  "text/css"})

(def auth-methods {:init anyone
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

(defpage [:get ["/:lang/:app" :lang #"[a-z]{2}" :app apps-pattern]] {app :app}
  (single-resource :html (keyword app) (resp/redirect "/fi/welcome#")))

;;
;; Login/logout:
;;

(def applicationpage-for {"applicant" "/applicant"
                          "authority" "/authority"
                          "authorityAdmin" "/authority-admin"
                          "admin" "/admin"})

(defjson [:post "/api/login"] {:keys [username password]}
  (if-let [user (security/login username password)]
    (do
      (info "login: successful: username=%s" username)
      (session/put! :user user)
      (if-let [application-page (applicationpage-for (:role user))]
        (ok :user user :applicationpage application-page)
        (do
          (error "Unknown user role: role=[%s]" (:role user))
          (fail :error.login))))
    (do
      (info "login: failed: username=%s" username)
      (fail :error.login))))

(defn- redirect-to-frontpage [lang]
  (resp/redirect (str "/" lang "/welcome")))

(defjson [:post "/api/logout"] []
  (session/clear!)
  (ok))

(defpage "/logout" []
  (session/clear!)
  (resp/redirect "/"))

(defpage [:get ["/:lang/logout" :lang #"[a-z]{2}"]] {lang :lang}
  (session/clear!)
  (redirect-to-frontpage lang))

(defpage "/" []
  (if (logged-in?)
    (if-let [application-page (applicationpage-for (:role (current-user)))]
      (resp/redirect (str "/" default-lang application-page))
      (redirect-to-frontpage default-lang))
    (redirect-to-frontpage default-lang)))

;;
;; FROM SADE
;;

(defpage "/security/activate/:activation-key" {key :activation-key}
  (if-let [user (sadesecurity/activate-account key)]
    (do
      (info "User account '%s' activated, auto-logging in the user" (:username user))
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

(defn apikey-authentication
  "Reads apikey from 'Auhtorization' headers, pushed it to :user request header
   'curl -H \"Authorization: apikey APIKEY\" http://localhost:8000/api/application"
  [handler]
  (fn [request]
    (let [authorization (get-in request [:headers "authorization"])
          apikey        (parse "apikey" authorization)]
      (handler (assoc request :user (security/login-with-apikey apikey))))))

(server/add-middleware apikey-authentication)

;;
;; File upload/download:
;;

(defpage [:post "/api/upload"]
  {:keys [applicationId attachmentId attachmentType text upload typeSelector] :as data}
  (debug "upload: %s: %s type=[%s] selector=[%s]" data upload attachmentType typeSelector)
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

(defpage "/api/view/:attachmentId" {attachment-id :attachmentId}
  (output-attachment attachment-id false))

(defpage "/api/download/:attachmentId" {attachment-id :attachmentId}
  (output-attachment attachment-id true))

;;
;; Proxy
;;

(defpage [:any "/proxy/:srv"] {srv :srv}
  (if (logged-in?)
    ((proxy-services/services srv (constantly {:status 404})) (request/ring-request))
    {:status 401}))

;;
;; dev utils:
;;

(env/in-dev
  (defjson "/api/spy" []
    (dissoc (request/ring-request) :body)))
