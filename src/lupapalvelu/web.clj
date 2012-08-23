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
            [lupapalvelu.data :as data]
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

;;
;; Alive?
;;

(defpage "/ping" []
  "pong\r\n")

;;
;; REST API:
;;

(defpage "/rest/project" []
  (json {:ok true :projects (mongo/all mongo/projects)}))

(defpage "/rest/project/:id" {:keys [id]}
  (json {:ok true :project (mongo/by-id mongo/projects id)}))

(defpage "/rest/user" []
  (json
    (if-let [user (current-user)]
      {:ok true :user user}
      {:ok false :message "No session"})))

(defpage [:post "/rest/command"] []
  (let [data (from-json)]
    (if-let [user (current-user)]
      (json (command/execute {:command (:command data)
                              :user user
                              :created (System/currentTimeMillis) 
                              :data (dissoc data :command) }))
      (json {:ok false :text "user not logged in"}))))

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
    (if-let [user (security/login username password)] 
      (do
        (info "login: successful: username=%s" username)
        (session/put! :user user)
        {:ok true :user user})
      (do
        (info "login: failed: username=%s" username)
        {:ok false :message "gtfo!"}))))

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
  "Reads apikeyfrom 'Auhtorization' headers, pushed it to :user request header
   'curl -H \"Authorization: apikey APIKEY\" http://localhost:8000/rest/project"
  [handler]
  (fn [request]
    (let [authorization (get-in request [:headers "authorization"])
          apikey        (parse "apikey" authorization)]
      (handler (assoc request :user (security/login-with-apikey apikey))))))

(server/add-middleware apikey-authentication)

;;
;; File upload/download:
;;

(defpage [:post "/rest/upload"] {projectId :projectId {:keys [size tempfile content-type filename]} :upload}
  (debug "file upload: uploading: projectId=%s, filename=%s, tempfile=%s" projectId filename tempfile)
  (let [attachment (mongo/upload filename content-type tempfile)
        attachment-id (:id attachment)]
    (mongo/update mongo/projects projectId {:$push {:attachments {:attachmentId attachment-id :fileName filename :contentType content-type :size size}}})
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

