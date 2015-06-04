(ns lupapalvelu.onnistuu-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [noir.request :as request]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]
            [sade.env :as env]
            [sade.core :refer [ok fail fail! now]]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand]]
            [lupapalvelu.onnistuu.process :as p]
            [lupapalvelu.company :as c]
            [lupapalvelu.user :as u]
            [lupapalvelu.i18n :as i18n]))

;;
;; Onnistuu.fi integration: Web API
;;

;
; Workflow:
;   - user decides to sign contract
;   - front -> init-sign command
;       * create uniq sign process ID
;       * save sign data to db with process ID as key
;       * sign process state is :created
;       * return a HTML form with sign data in hidden fields
;

(defcommand init-sign
  {:parameters [company signer lang]
   :user-roles #{:anonymous}}
  [{:keys [created user]}]
  (sc/validate c/Company company)
  (sc/validate p/Signer signer)
  (if-not ((set (map name i18n/languages)) lang) (fail! :bad-lang))
  (if (and (nil? (:currentUser signer)) (u/get-user-by-email (:email signer))) (fail! :email-in-use))
  (let [config       (env/value :onnistuu)
        base-url     (or (:return-base-url config) (env/value :host))
        document-url (str base-url "/api/sign/document")
        success-url  (str base-url "/api/sign/success")
        signer       (if (:currentUser signer) (-> signer
                                                   (assoc :email (:email user))
                                                   (assoc :currentUser (:id user)))
                                               signer)
        process-data (p/init-sign-process (java.util.Date. created) (:crypto-key config) success-url document-url company signer lang)
        failure-url  (str base-url "/api/sign/fail/" (:process-id process-data))]


    (ok (merge {:failure-url failure-url}
               (select-keys config [:post-to
                                    :customer-id])
               (select-keys process-data [:process-id
                                          :data
                                          :iv])))))

; Cancel signing:

(defcommand cancel-sign
  {:parameters [processId]
   :user-roles #{:anonymous}}
  [{:keys [created]}]
  (p/cancel-sign-process! processId created)
  (ok))

;
; Error handling util:
;

(defmacro with-error-handling [& body]
  `(try+
     ~@body
     (catch map? {error# :error}
       (resp/status (get {:not-found 404 :bad-request 400} error# 500)
                    (name error#)))))

;
; Workflow:
;  - Onnistuu.fi gets the document to sign:
;      * onnistuu.fi -> GET "/api/sign/document/" + process ID
;

(defpage "/api/sign/document/:id" {:keys [id]}
  (with-error-handling
    (let [[content-type document] (p/fetch-document id (now))]
      (->> document
           (resp/status 200)
           (resp/content-type content-type)))))

;
; Workflow:
;  - User signs document in onnistuu.fi, and gets a redirect back
;    to here:
;       browser -> GET "/api/sign/done/id?data=...&iv=..."
;         * mark process done, save status
;         * redirect to proper lupapiste.fi url
;

(defpage "/api/sign/success/:id" {:keys [id data iv]}
  (with-error-handling
    (let [process (p/success! id data iv (now))
          lang    (-> process :lang)]
      (if (nil? (get-in process [:signer :currentUser]))
        (resp/redirect (str (env/value :host) "/app/" lang "/welcome#!/register-company-success"))
        (let [current-user (u/current-user (request/ring-request))
              response     (ssess/merge-to-session (request/ring-request) (resp/redirect (str (env/value :host) "/app/" lang "/" (u/applicationpage-for (:role current-user)) "#!/register-company-existing-user-success"))
                                                   {:user (u/session-summary (u/get-user-by-id (:id current-user)))})]
          response)))))

;
; Something went terribly wrong!
;

(defpage "/api/sign/fail/:id" {:keys [id error message]}
  (with-error-handling
    (let [process      (p/failed! id error message (now))
          lang         (-> process :lang)
          current-user (u/current-user (request/ring-request))]
      (if (nil? (get-in process [:signer :currentUser]))
        (resp/redirect (str (env/value :host) "/app/" lang "/welcome#!/register-company-fail"))
        (resp/redirect (str (env/value :host) "/app/" lang "/" (u/applicationpage-for (:role current-user)) "#!/register-company-fail"))))))

(when (env/feature? :dummy-onnistuu)

  (defquery find-sign-process
    {:parameters [processId]
     :user-roles #{:anonymous}}
    [_]
    (ok :process (p/find-sign-process! processId)))

  ;
  ; Load dummy onnistuu.fi simulator:
  ;

  (require 'lupapalvelu.onnistuu.dummy-onnistuu-server))
