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
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.core :as core]
            [lupapalvelu.action :as action]
            [lupapalvelu.singlepage :as singlepage]
            [lupapalvelu.security :as security]
            [lupapalvelu.strings :as strings]
            [clj-http.client :as client]))

;;
;; Helpers
;;

(defn from-json []
  (json/decode (slurp (:body (request/ring-request))) true))

(defn from-query []
  (keywordize-keys (:query-params (request/ring-request))))
                 
(defn current-user []
  "fetches the current user from 1) http-session 2) apikey from headers"
  (or (session/get :user) ((request/ring-request) :user)))

(defn logged-in? []
  (not (nil? (current-user))))

(defn has-role [role]
  (= role (keyword (:role (current-user)))))

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

(defn- with-user 
  ([m] (with-user m (current-user)))
  ([m user] (merge m {:user user})))

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

(defpage "/applicant" []      (if (logged-in?) (resp/content-type (:html content-type) (singlepage/compose :html :applicant)) (resp/redirect "/welcome#")))
(defpage "/applicant.js" []   (if (logged-in?) (resp/content-type (:js content-type)   (singlepage/compose :js   :applicant)) (resp/status 401 "Unauthorized\r\n")))
(defpage "/applicant.css" []  (if (logged-in?) (resp/content-type (:css content-type)  (singlepage/compose :css  :applicant)) (resp/status 401 "Unauthorized\r\n")))

(defpage "/authority" []      (if (authority?) (resp/content-type (:html content-type) (singlepage/compose :html :authority)) (resp/redirect "/welcome#")))
(defpage "/authority.js" []   (if (authority?) (resp/content-type (:js content-type)   (singlepage/compose :js   :authority)) (resp/status 401 "Unauthorized\r\n")))
(defpage "/authority.css" []  (if (authority?) (resp/content-type (:css content-type)  (singlepage/compose :css  :authority)) (resp/status 401 "Unauthorized\r\n")))

(defpage "/admin" []          (if (admin?)     (resp/content-type (:html content-type) (singlepage/compose :html :admin)) (resp/redirect "/welcome#")))
(defpage "/admin.js" []       (if (admin?)     (resp/content-type (:js content-type)   (singlepage/compose :js   :admin)) (resp/status 401 "Unauthorized\r\n")))
(defpage "/admin.css" []      (if (admin?)     (resp/content-type (:css content-type)  (singlepage/compose :css  :admin)) (resp/status 401 "Unauthorized\r\n")))

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

(defjson [:post "/rest/upload"] {applicationId :applicationId attachmentId :attachmentId type :type upload :upload}
  (debug "upload: %s: %s" name (str upload))
  (core/execute
    (with-user
      (core/command "upload-attachment" (assoc upload
                                               :id applicationId
                                               :attachmentId attachmentId
                                               :type (or type ""))))))

(def windows-filename-max-length 255)

(defn encode-filename
  "Replaces all non-ascii chars and other that the allowed punctuation with dash.
   UTF-8 support would have to be browser specific, see http://greenbytes.de/tech/tc2231/"
  [unencoded-filename]
  (when-let [de-accented (strings/de-accent unencoded-filename)]
      (clojure.string/replace
        (strings/last-n windows-filename-max-length de-accented)
        #"[^a-zA-Z0-9\.\-_ ]" "-")))

(defn output-attachment [attachmentId download]
  (debug "file download: attachmentId=%s" attachmentId)
  (if-let [attachment (action/get-attachment attachmentId)]
    (let [response
          {:status 200
           :body ((:content attachment))
           :headers {"Content-Type" (:content-type attachment)
                     "Content-Length" (str (:content-length attachment))}}]
        (if download
          (assoc-in response [:headers "Content-Disposition"]
            (format "attachment;filename=\"%s\"" (encode-filename (:file-name attachment))) )
          response))))

(defpage "/rest/view/:attachmentId" {attachmentId :attachmentId}
  (output-attachment attachmentId false))

(defpage "/rest/download/:attachmentId" {attachmentId :attachmentId}
  (output-attachment attachmentId true))

;;
;; Oskari map ajax request proxy
;;
(defpage [:post "/ajaxProxy/:srv"] {srv :srv}
  (let [request (ring-request) body (slurp(:body request)) urls {"Kunta" "http://tepa.sito.fi/sade/lupapiste/karttaintegraatio/Kunta.asmx/Hae"}]
    (client/post (get urls srv) {:body body
                                 :content-type :json
                                 :accept :json})))

;;
;; Development thingies.
;;

(env/in-dev

  (defpage "/fixture/:name" {name :name}
    (fixture/apply-fixture name)
    (format "fixture applied: %s" name))

  (defjson "/fixture" []
    (keys @fixture/fixtures))

  (defpage "/verdict" {:keys [id ok text]}
    (core/execute
      (with-user
        (core/command "give-application-verdict" {:id id :ok ok :text text})
        (security/login-with-apikey "505718b0aa24a1c901e6ba24")))
    (format "verdict is given for application %s" id))

  (def speed-bump (atom 0))

  (server/add-middleware
    (fn [handler]
      (fn [request]
        (let [bump @speed-bump]
          (when (> bump 0)
            (warn "Hit speed bump %d ms: %s" bump (:uri request))
            (Thread/sleep bump)))
        (handler request)))))