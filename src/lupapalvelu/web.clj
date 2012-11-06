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
            [clj-http.client :as client]))

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

(defn logged-in? []
  (not (nil? (current-user))))

(defn has-role [role]
  (= role (keyword (:role (current-user)))))

(defn with-user [m]
  (merge m {:user (current-user)}))

(defn authority? []
  (and logged-in? (has-role :authority)))

(defn admin? []
  (and logged-in? (has-role :admin)))

(defn anyone [] true)

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
  (core/execute (with-user (core/command name (from-json)))))

(defjson "/rest/query/:name" {name :name}
  (core/execute (with-user (core/query name (from-query)))))

;;
;; Web UI:
;;

(defpage "/" [] (resp/redirect "/welcome#"))

(def content-type {:html "text/html; charset=utf-8"
                   :js   "application/javascript"
                   :css  "text/css"})

(def authz-methods {:welcome anyone
                    :upload logged-in?
                    :applicant logged-in?
                    :authority authority?
                    :admin admin?})

(defn- single-resource [resource-type app failure]
  (if (and (contains? authz-methods app) ((authz-methods app)) )
      (resp/content-type (resource-type content-type) (singlepage/compose resource-type app))
      failure))

;; CSS & JS
(defpage [:get ["/:app.:res-type" :res-type #"(css|js)"]] {app :app res-type :res-type}
  (single-resource (keyword res-type) (keyword app) (resp/status 401 "Unauthorized\r\n")))

;; Single Page App HTML
(def apps-pattern
  (re-pattern (str "(" (clojure.string/join "|" (map #(name %) (keys authz-methods))) ")")))

(defpage [:get ["/:app" :app apps-pattern]] {app :app}
  (single-resource :html (keyword app) (resp/redirect "/welcome#")))

;
; Oskari:
;

(def oskari (if (env/dev-mode?) "private/mockoskari/mockoskarimap.js" "private/oskari/oskarimap.js"))

(defpage "/js/oskarimap.js" []
  (->> (clojure.lang.RT/resourceAsStream nil oskari)
    (resp/set-headers {"Cache-Control" "public, max-age=86400"})
    (resp/content-type (:js content-type))))

(defpage "/js/hub.js" []
  (->> (clojure.lang.RT/resourceAsStream nil "private/common/hub.js")
    (resp/set-headers {"Cache-Control" "public, max-age=86400"})
    (resp/content-type (:js content-type))))

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
      (fail "Tunnus tai salasana on v\u00E4\u00E4rin."))))

(defjson [:post "/rest/logout"] []
  (session/clear!)
  (ok))

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

(defpage [:post "/rest/upload"] {applicationId :applicationId attachmentId :attachmentId type :type upload :upload :as data}
  (debug "upload: %s: %s" data (str upload))
  (let [upload-data (assoc upload :id applicationId, :attachmentId attachmentId, :type (or type ""))
        result (core/execute (with-user (core/command "upload-attachment" upload-data)))]
    (if (core/ok? result)
      (resp/redirect (str "/html/pages/upload-ok.html?applicationId=" applicationId "&attachmentId=" attachmentId))
      (json/generate-string result) ; TODO display error message
      )))

(defn- output-attachment [attachment-id download?]
  (if (logged-in?)
    (attachment/output-attachment attachment-id (current-user) download?)
    (resp/status 401 "Unauthorized\r\n")))

(defpage "/rest/view/:attachmentId" {attachment-id :attachmentId}
  (output-attachment attachment-id false))

(defpage "/rest/download/:attachmentId" {attachment-id :attachmentId}
  (output-attachment attachment-id true))

;;
;; Oskari map ajax request proxy
;;

(defpage [:post "/ajaxProxy/:srv"] {srv :srv}
  (let [request (ring-request)
        body (slurp (:body request))
        urls {"Kunta" "http://tepa.sito.fi/sade/lupapiste/karttaintegraatio/Kunta.asmx/Hae"}]
    (client/post (get urls srv)
       {:body body
        :content-type :json
        :accept :json})))

;;
;; Speed bump
;;

(env/in-dev

  (def speed-bump (atom 0))

  (server/add-middleware
    (fn [handler]
      (fn [request]
        (let [bump @speed-bump]
          (when (> bump 0)
            (warn "Hit speed bump %d ms: %s" bump (:uri request))
            (Thread/sleep bump)))
        (handler request)))))