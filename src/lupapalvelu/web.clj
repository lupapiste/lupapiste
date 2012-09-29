(ns lupapalvelu.web
  (:use noir.core
        noir.request
        [noir.response :only [json redirect content-type]]
        [lupapalvelu.command :only [ok fail]]
        lupapalvelu.log
        [clojure.walk :only [keywordize-keys]]
        monger.operators)
  (:require [noir.response :as resp]
            [noir.session :as session]
            [noir.server :as server]
            [cheshire.core :as json]
            [lupapalvelu.env :as env] 
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture :as fixture]
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
       (json (fail "user not logged in"))))) ; should return 401?

(defmacro defjson [path params & content]
  `(defpage ~path ~params
     (json (do ~@content))))

;;
;; REST API:
;;

(defjson "/rest/buildinfo" []
  (ok (read-string (slurp (.getResourceAsStream (clojure.lang.RT/baseLoader) "buildinfo.clj")))))

(defjson "/rest/ping" [] (ok))

;;
;; Commands
;;

(defn create-action [name & args]
  (apply command/create-action name (into args [(current-user) :user])))
 
(defn- foreach-action []
  (let [json (from-json)]
    (map 
      #(create-action % :data json)
      (keys (command/get-actions)))))

(defn- validated [command]
  {(:action command) (command/validate command)})

(env/in-dev 
  (defjson "/rest/actions" []
    (ok :commands (command/get-actions))))

  (defjson [:post "/rest/actions/valid"] []
    (ok :commands (into {} (map validated (foreach-action)))))

(defjson [:post "/rest/command/:name"] {name :name}
  (command/execute 
    (create-action 
      name
      :data (from-json))))

(defjson "/rest/query/:name" {name :name}
  (command/execute 
    (create-action
      name
      :type :query
      :data (keywordize-keys (:query-params (ring-request))))))

; TODO: make command out of this, needs :loggedin true -kinda validation. 
(secured "/rest/genid" []
  (json (ok :id (mongo/create-id))))

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

(defjson [:post "/rest/upload"] {applicationId :applicationId attachmentId :attachmentId name :name upload :upload}
  (debug "upload: %s: %s" name (str upload))
  (command/execute
    (create-action (assoc upload :action "upload-attachment" 
                                  :id applicationId
                                  :attachmentId attachmentId
                                  :name (or name "")))))

(defpage "/rest/download/:attachmentId" {attachmentId :attachmentId}
  (debug "file download: attachmentId=%s" attachmentId)
  (if-let [attachment (mongo/download attachmentId)]
    {:status 200
     :body ((:content attachment))
     :headers {"Content-Type" (:content-type attachment)
               "Content-Length" (str (:content-length attachment))}}))

;;
;; Development thingies
;;

(env/in-dev

  (defpage "/fixture/:name" {name :name}
    (fixture/apply-fixture name)
    (format "fixture applied: %s" name))

  (defjson "/fixture" []
    (keys @fixture/fixtures))

  (defpage "/verdict" {:keys [id ok text]}
    (command/execute
      (command/create-action
        "give-application-verdict" 
        :user (security/login-with-apikey "505718b0aa24a1c901e6ba24")
        :data {:id id :ok ok :text text}))
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