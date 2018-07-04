(ns lupapalvelu.onnistuu-api
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.campaign :as camp]
            [lupapalvelu.company :as com]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.onnistuu.process :as p]
            [lupapalvelu.user :as usr]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [sade.core :refer [ok fail fail! now]]
            [sade.env :as env]
            [sade.session :as ssess]
            [sade.strings :as ss]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]))

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

(defn- get-signer [current-user signer]
  {:post [(sc/validate p/Signer %)]}
  (if (= (:email signer) (:email current-user))
    (merge
      (assoc signer :currentUser (:id current-user))
      (select-keys (usr/get-user-by-id (:id current-user)) [:firstName :lastName :personId]))
    signer))

(defn- validate-email-in-use
  "Signer email is OK if it is not in use or belongs to the current
  user whose role is non-company applicant."
  [{{signer :signer} :data user :user}]
  (let [signer-email                 (:email signer)
        {:keys [email role company]} user]
    (cond
      (not (usr/email-in-use? signer-email)) nil
      (not= email signer-email)              (fail :email-in-use)
      (not= role "applicant")                (fail :error.not-applicant)
      (-> company :role ss/not-blank?)       (fail :error.already-in-company))))

(defcommand init-sign
  {:parameters [company signer lang]
   :user-roles #{:anonymous}
   :input-validators [validate-email-in-use
                      (fn [{{lang :lang} :data}]
                        (when-not ((set (map name i18n/languages)) lang)
                          (fail :bad-lang)))
                      camp/campaign-is-active]}
  [{:keys [^Long created user]}]
  (let [company (merge com/company-skeleton
                       company
                       (when-let [campaign (:campaign company)]
                         {:campaign (camp/code->id campaign)}))
        signer (get-signer user signer)]
    (sc/validate com/Company company)
    (let [config       (env/value :onnistuu)
          base-url     (or (:return-base-url config) (env/value :host))
          document-url (str base-url "/api/sign/document")
          success-url  (str base-url "/api/sign/success")
          process-data (p/init-sign-process (java.util.Date. created) (:crypto-key config) success-url document-url company signer lang)
          failure-url  (str base-url "/api/sign/fail/" (:process-id process-data))]

      (ok (merge {:failure-url failure-url}
                 (select-keys config [:post-to
                                      :customer-id])
                 (select-keys process-data [:process-id
                                            :data
                                            :iv]))))))

; Cancel signing:

(defcommand cancel-sign
  {:parameters [processId]
   :input-validators [(partial action/non-blank-parameters [:processId])]
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
        (let [current-user (usr/current-user (request/ring-request))
              response     (ssess/merge-to-session
                             (request/ring-request)
                             (resp/redirect (str (env/value :host) "/app/" lang "/"
                                                 (usr/applicationpage-for current-user)
                                                 "#!/register-company-existing-user-success"))
                             {:user (usr/session-summary (usr/get-user-by-id (:id current-user)))})]
          response)))))

;
; Something went terribly wrong!
;

(defpage "/api/sign/fail/:id" {:keys [id onnistuu_error onnistuu_message]}
  (with-error-handling
    (let [process      (p/failed! id onnistuu_error onnistuu_message (now))
          lang         (-> process :lang)
          current-user (usr/current-user (request/ring-request))]
      (if (nil? (get-in process [:signer :currentUser]))
        (resp/redirect (str (env/value :host) "/app/" lang "/welcome#!/register-company-fail"))
        (resp/redirect (str (env/value :host) "/app/" lang "/" (usr/applicationpage-for current-user) "#!/register-company-fail"))))))

(when (env/feature? :dummy-onnistuu)

  (defquery find-sign-process
    {:parameters [processId]
     :input-validators [(partial action/non-blank-parameters [:processId])]
     :user-roles #{:anonymous}}
    [_]
    (ok :process (p/find-sign-process! processId)))

  ;
  ; Load dummy onnistuu.fi simulator:
  ;

  (require 'lupapalvelu.onnistuu.dummy-onnistuu-server))
