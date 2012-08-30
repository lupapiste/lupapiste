(ns lupapalvelu.web
  (:use noir.core
        noir.request
        [noir.response :only [json redirect]]
        [clojure.java.io :only [file]]
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

(defn current-party []
  "fetches the current party from 1) http-session 2) apikey from headers"
  (or (session/get :party) ((ring-request) :party)))

(defn logged-in? []
  (not (nil? (current-party))))

(defmacro secured [path params & content]
  `(defpage ~path ~params
     (if (logged-in?)
       (do ~@content)
       (json {:ok false :text "party not logged in"})))) ; should return 401?

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


(secured "/rest/application" []
  (json {:ok true :applications (mongo/all mongo/applications)}))

(secured "/rest/application/:id" {id :id}
  (json {:ok true :application (mongo/by-id mongo/applications id)}))

(defpage "/rest/party" []
  (json
    (if-let [party (current-party)]
      {:ok true :party party}
      {:ok false :message "No session"})))

(secured [:post "/rest/command"] []
  (let [data (from-json)]
    (json (command/execute {:command (:command data)
                            :party (current-party)
                            :created (System/currentTimeMillis) 
                            :data (dissoc data :command) }))))

(defpage "/rest/email-available" {email :email}
   (Thread/sleep 1000)
   (json {:ok (not= "bad" email)}))

;;
;; Web UI:
;;

(defpage "/" []
  (resp/redirect "/lupapalvelu#!/login"))

(defpage "/lupapalvelu" []
  (singlepage/compose-singlepage-html))

(defpage "/js/lupapalvelu.js" []
  (singlepage/compose-singlepage-js))

(defpage "/css/lupapalvelu.css" []
  (singlepage/compose-singlepage-css))

;;
;; Login/logout:
;;

(defpage [:post "/rest/login"] {:keys [username password]}
  (json
    (if-let [party (security/login username password)] 
      (do
        (info "login: successful: username=%s" username)
        (session/put! :party party)
        {:ok true :party party})
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
  "Reads apikeyfrom 'Auhtorization' headers, pushed it to :party request header
   'curl -H \"Authorization: apikey APIKEY\" http://localhost:8000/rest/application"
  [handler]
  (fn [request]
    (let [authorization (get-in request [:headers "authorization"])
          apikey        (parse "apikey" authorization)]
      (handler (assoc request :party (security/login-with-apikey apikey))))))

(server/add-middleware apikey-authentication)

;;
;; File upload/download:
;;

(defpage [:post "/rest/upload"] {applicationId :applicationId {:keys [size tempfile content-type filename]} :upload}
  (debug "file upload: uploading: applicationId=%s, filename=%s, tempfile=%s" applicationId filename tempfile)
  (let [attachment (mongo/upload filename content-type tempfile)
        attachment-id (:id attachment)]
    (mongo/update mongo/applications applicationId {:$push {:attachments {:attachmentId attachment-id :fileName filename :contentType content-type :size size}}})
    (.delete (file tempfile))
    (json {:ok true :attachmentId attachment-id})))

(defpage "/rest/download/:attachmentId" {attachmentId :attachmentId}
  (debug "file download: attachmentId=%s" attachmentId)
  (if-let [attachment (mongo/download attachmentId)]
    {:status 200
     :body ((:content attachment))
     :headers {"Content-Type" (:content-type attachment)
               "Content-Length" (str (:content-length attachment))}}))
;; Force automatic saving: "Content-Disposition" (str "attachment; filename=\"" (:file-name attachment) "\"")

;;
;; Initializing fixtures
;;

(env/in-dev
  (defpage "/fixture/:type" {type :type}
    (case type
      "minimal" (mongo/init-minimal)
      "full" (mongo/init-full)
      "nothing was done")))