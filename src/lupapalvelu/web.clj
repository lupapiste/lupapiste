(ns lupapalvelu.web
  (:use [noir.core :only [defpage]]
        [noir.request]
        [lupapalvelu.core :only [ok fail]]
        [lupapalvelu.log]
        [clojure.walk :only [keywordize-keys]])
  (:require [noir.request :as request]
            [noir.response :as resp]
            [noir.session :as session]
            [noir.server :as server]
            [cheshire.core :as json]
            [lupapalvelu.env :as env]
            [lupapalvelu.core :as core]
            [lupapalvelu.action :as action]
            [lupapalvelu.singlepage :as singlepage]
            [lupapalvelu.security :as security]
            [lupapalvelu.attachment :as attachment]
            [clj-http.client :as client]
            [lupapalvelu.proxy-services :as proxy-services]))

;;
;; Helpers
;;

(defn from-json []
  (json/decode (slurp (:body (request/ring-request))) true))

(defn from-query []
  (keywordize-keys (:query-params (request/ring-request))))

(defn current-user
  "fetches the current user from 1) http-session 2) apikey from headers"
  []
  (or (session/get :user) ((request/ring-request) :user)))

(defn host []
  (let [request (ring-request)]
    (str (name (:scheme request)) "://" (get-in request [:headers "host"]) "/")))

(defn enriched [m]
  (merge m {:user    (current-user)
            :host    (host)}))

(defn logged-in? []
  (not (nil? (current-user))))

(defn has-role? [role]
  (= role (keyword (:role (current-user)))))

(defn authority? [] (has-role? :authority))
(defn admin? [] (has-role? :admin))
(defn anyone [] true)
(defn nobody [] false)

(defmacro defjson [path params & content]
  `(defpage ~path ~params
     (resp/json (do ~@content))))

;;
;; REST API:
;;

(defjson "/rest/buildinfo" []
  (ok :data (assoc (read-string (slurp (.getResourceAsStream (clojure.lang.RT/baseLoader) "buildinfo.clj"))) :server-mode env/mode)))

(defjson "/rest/ping" [] (ok))

;;
;; Commands
;;

(defjson [:post "/rest/command/:name"] {name :name}
  (core/execute (enriched (core/command name (from-json)))))

(defjson "/rest/query/:name" {name :name}
  (core/execute (enriched (core/query name (from-query)))))

;;
;; Web UI:
;;

(defpage "/" [] (resp/redirect "/welcome#"))

(def content-type {:html "text/html; charset=utf-8"
                   :js   "application/javascript"
                   :css  "text/css"})

(def authz-methods {:init anyone
                    :welcome anyone
                    :upload logged-in?
                    :applicant logged-in?
                    :authority authority?
                    :admin admin?})

(def headers
  (if (env/dev-mode?)
    {"Cache-Control" "no-cache"}
    {"Cache-Control" "public, max-age=86400"}))

(defn- single-resource [resource-type app failure]
  (if ((authz-methods app nobody))
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
  (re-pattern (str "(" (clojure.string/join "|" (map #(name %) (keys authz-methods))) ")")))

(defpage [:get ["/:app" :app apps-pattern]] {app :app}
  (single-resource :html (keyword app) (resp/redirect "/welcome#")))

;;
;; Login/logout:
;;

(def applicationpage-for {:applicant "/applicant"
                          :authority "/authority"
                          :admin "/admin"})

(defjson [:post "/rest/login"] {:keys [username password]}
  (if-let [user (security/login username password)]
    (do
      (info "login: successful: username=%s" username)
      (session/put! :user user)
      (let [userrole (keyword (:role user))]
        (ok :user user :applicationpage (userrole applicationpage-for))))
    (do
      (info "login: failed: username=%s" username)
      (fail :error.login))))

(defjson [:post "/rest/logout"] []
  (session/clear!)
  (ok))

(defpage "/logout" []
  (session/clear!)
  (resp/redirect "/"))

;;
;; Apikey-authentication
;;

(defn- parse [key value]
  (let [value-string (str value)]
    (if (.startsWith value-string key)
      (.trim (.substring value-string (.length key))))))

(defn apikey-authentication
  "Reads apikey from 'Auhtorization' headers, pushed it to :user request header
   'curl -H \"Authorization: apikey APIKEY\" http://localhost:8000/rest/application"
  [handler]
  (fn [request]
    (let [authorization (get-in request [:headers "authorization"])
          apikey        (parse "apikey" authorization)]
      (handler (assoc request :user (security/login-with-apikey apikey))))))

(server/add-middleware apikey-authentication)

;;
;; File upload/download:
;;

(defpage [:post "/rest/upload"]
  {applicationId :applicationId attachmentId :attachmentId type :type text :text upload :upload :as data}
  (debug "upload: %s: %s" data (str upload))
  (let [upload-data (assoc upload :id applicationId, :attachmentId attachmentId, :type (or type ""), :text text)
        result (core/execute (enriched (core/command "upload-attachment" upload-data)))]
    (if (core/ok? result)
      (resp/redirect "/html/pages/upload-ok.html")
      (resp/redirect (str (hiccup.util/url "/html/pages/upload.html"
                                           {:applicationId applicationId
                                            :attachmentId attachmentId
                                            :type type
                                            :defaultType type
                                            :errorMessage (result :text)}))))))

(defn- output-attachment [attachment-id download?]
  (if (logged-in?)
    (attachment/output-attachment attachment-id (current-user) download?)
    (resp/status 401 "Unauthorized\r\n")))

(defpage "/rest/view/:attachmentId" {attachment-id :attachmentId}
  (output-attachment attachment-id false))

(defpage "/rest/download/:attachmentId" {attachment-id :attachmentId}
  (output-attachment attachment-id true))

;;
;; Proxy
;;

(defpage [:any "/proxy/:srv"] {srv :srv}
  (if (logged-in?)
    ((proxy-services/services srv (constantly {:status 404})) (ring-request))
    {:status 401}))

;;
;; dev utils:
;;

(env/in-dev
  (defjson "/rest/spy" []
    (dissoc (ring-request) :body)))
