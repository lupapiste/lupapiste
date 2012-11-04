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

(defpage "/welcome" []                         (resp/content-type (:html content-type) (singlepage/compose :html :welcome)))
(defpage "/welcome.js" []                      (resp/content-type (:js content-type)   (singlepage/compose :js :welcome)))
(defpage "/welcome.css" []                     (resp/content-type (:css content-type)  (singlepage/compose :css :welcome)))
(defpage "/iframe.css" []                      (resp/content-type (:css content-type)  (singlepage/compose :css :iframe)))

(defpage "/applicant" []      (if (logged-in?) (resp/content-type (:html content-type) (singlepage/compose :html :applicant)) (resp/redirect "/welcome#")))
(defpage "/applicant.js" []   (if (logged-in?) (resp/content-type (:js content-type)   (singlepage/compose :js   :applicant)) (resp/status 401 "Unauthorized\r\n")))
(defpage "/applicant.css" []  (if (logged-in?) (resp/content-type (:css content-type)  (singlepage/compose :css  :applicant)) (resp/status 401 "Unauthorized\r\n")))

(defpage "/authority" []      (if (authority?) (resp/content-type (:html content-type) (singlepage/compose :html :authority)) (resp/redirect "/welcome#")))
(defpage "/authority.js" []   (if (authority?) (resp/content-type (:js content-type)   (singlepage/compose :js   :authority)) (resp/status 401 "Unauthorized\r\n")))
(defpage "/authority.css" []  (if (authority?) (resp/content-type (:css content-type)  (singlepage/compose :css  :authority)) (resp/status 401 "Unauthorized\r\n")))

(defpage "/admin" []          (if (admin?)     (resp/content-type (:html content-type) (singlepage/compose :html :admin)) (resp/redirect "/welcome#")))
(defpage "/admin.js" []       (if (admin?)     (resp/content-type (:js content-type)   (singlepage/compose :js   :admin)) (resp/status 401 "Unauthorized\r\n")))
(defpage "/admin.css" []      (if (admin?)     (resp/content-type (:css content-type)  (singlepage/compose :css  :admin)) (resp/status 401 "Unauthorized\r\n")))

;
; Oskari:
;

(defpage "/oskarimap.js" []
  (->> (clojure.lang.RT/resourceAsStream nil "private/oskari/oskarimap.js")
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
      )
    ))

(defn- output-attachment [attachment-id download?]
  (if (logged-in?)
    (attachment/output-attachment attachment-id (current-user) download?)
    {:status 403
     :headers {"Content-Type" "text/plain"}
     :body "403"}))

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