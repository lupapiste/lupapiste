(ns lupapalvelu.web
  (:use noir.core
        noir.request
        [noir.response :only [json redirect]]
        lupapalvelu.log)
  (:require [noir.response :as resp]
            [noir.session :as session]
            [noir.server :as server]
            [cheshire.core :as json]
            [lupapalvelu.env :as env] 
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.command :as command]
            [lupapalvelu.singlepage :as singlepage]
            [lupapalvelu.security :as security]))

;;
;; Helpers
;;

(defn from-json []
  (json/parse-string (slurp (:body (ring-request))) true))

(defn current-user []
  "fetches the current user from 1) http-session 2) apikey from headers"
  (or (session/get :user) ((ring-request) :user)))

(defn logged-in? []
  (not (nil? (current-user))))

(defn logged-in-as-authority? []
  (and logged-in? (= :authority (keyword (:role (current-user))))))

(defmacro secured [path params & content]
  `(defpage ~path ~params
     (if (logged-in?)
       (do ~@content)
       (json {:ok false :text "user not logged in"})))) ; should return 401?

;;
;; Alive?
;;

(defpage "/ping" []
  "pong\r\n")

;;
;; REST API:
;;

(defpage "/rest/ping" []
  (json {:ok true}))


; TODO: for applicants, return only their own applications
(secured "/rest/application" []
  (let [user (current-user)]
    (json
      (case (keyword (:role user))
        :applicant {:ok true :applications (mongo/select mongo/applications) }
        :authority {:ok true :applications (mongo/select mongo/applications {:authority (:authority user)})}
        {:ok false :text "invalid role to load applications"}))))

(secured "/rest/application/:id" {id :id}
  (json {:ok true :application (mongo/by-id mongo/applications id)}))

(defpage "/rest/user" []
  (json
    (if-let [user (current-user)]
      {:ok true :user user}
      {:ok false :message "No session"})))

;;
;; Commands
;;

(defn create-command [data]
  {:command (:command data)
   :user (current-user)
   :created (System/currentTimeMillis) 
   :data (dissoc data :command) })

(defn- foreach-command []
  (map #(create-command (merge (from-json) {:command % })) (keys (command/get-commands))))

(defn- validated [command]
  (let [result (command/validate command)]
    {(:command command) (:ok result)}))

(env/in-dev 
  (defpage "/rest/commands" []
    (json {:ok true :commands (command/get-commands)})))

(env/in-dev
  (defpage "/rest/commands/valid" []
    (json {:ok true :commands (into {} (map #(validated %) (foreach-command)))})))

(defpage [:post "/rest/command"] []
  (json (command/execute (create-command (from-json)))))

(defpage [:get "/rest/command"] []
  (json (command/validate (create-command (from-json)))))

; way cool naming dudes! Love this.
(secured "/rest/genid" []
  (json {:ok true :id (mongo/make-objectid)}))

;;
;; Web UI:
;;

(defpage "/" [] (resp/redirect "/welcome#"))

(defpage "/welcome" [] (session/clear!) (singlepage/compose-singlepage-html "welcome"))
(defpage "/welcome.js" [] (singlepage/compose-singlepage-js "welcome"))
(defpage "/welcome.css" [] (singlepage/compose-singlepage-css "welcome"))

(defpage "/lupapiste" [] (if (logged-in?) (singlepage/compose-singlepage-html "lupapiste") (resp/redirect "/welcome#")))
(defpage "/lupapiste.js" [] (if (logged-in?) (singlepage/compose-singlepage-js "lupapiste") {:status 401}))
(defpage "/lupapiste.css" [] (if (logged-in?) (singlepage/compose-singlepage-css "lupapiste") {:status 401}))

(defpage "/authority" [] (if (logged-in-as-authority?) (singlepage/compose-singlepage-html "authority") (resp/redirect "/welcome#")))
(defpage "/authority.js" [] (if (logged-in-as-authority?) (singlepage/compose-singlepage-js "authority") {:status 401}))
(defpage "/authority.css" [] (if (logged-in-as-authority?) (singlepage/compose-singlepage-css "authority") {:status 401}))

;;
;; Login/logout:
;;

(def applicationpage-for {:applicant "/lupapiste"
                          :authority "/authority"})

(defpage [:post "/rest/login"] {:keys [username password]}
  (json
    (if-let [user (security/login username password)] 
      (do
        (info "login: successful: username=%s" username)
        (session/put! :user user)
        (let [userrole (keyword (:role user))]
          {:ok true :user user :applicationpage (userrole applicationpage-for) }))
      (do
        (info "login: failed: username=%s" username)
        {:ok false :message "Tunnus tai salasana on väärin."}))))

(defpage [:post "/rest/logout"] []
  (session/clear!)
  (json {:ok true}))

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

;;
;; File upload/download:
;;

(defpage [:post "/rest/upload"] {applicationId :applicationId attachmentId :attachmentId name :name upload :upload}
  (debug "upload: %s: %s" name (str upload))
  (json
    (command/execute
      (create-command (assoc upload :command "upload-attachment" 
                                    :id applicationId
                                    :attachmentId attachmentId
                                    :name (or name ""))))))

(defpage "/rest/download/:attachmentId" {attachmentId :attachmentId}
  (debug "file download: attachmentId=%s" attachmentId)
  (if-let [attachment (mongo/download attachmentId)]
    {:status 200
     :body ((:content attachment))
     :headers {"Content-Type" (:content-type attachment)
               "Content-Length" (str (:content-length attachment))}}))

;;
;; Initializing fixtures
;;

(env/in-dev
  (defpage "/fixture/:type" {type :type}
    (case type
      "minimal" (mongo/init-minimal!)
      "fixture not found")))
