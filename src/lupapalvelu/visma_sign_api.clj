(ns lupapalvelu.visma-sign-api
  "Functionality for creating and signing company contracts.
  Integrates with the Visma Sign API"
  (:require [hato.client :as http]
            [lupapalvelu.action :refer [defcommand defquery defraw] :as action]
            [lupapalvelu.company :as com]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.user :as usr]
            [lupapalvelu.visma-sign.client :as client]
            [lupapalvelu.visma-sign.process :as p]
            [noir.response :as resp]
            [sade.core :refer [fail now ok]]
            [sade.env :as env]
            [sade.strings :as ss]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]))

(def ^:private valid-signer! (sc/validator p/Signer))

(defn- get-signer [current-user signer]
  {:post [(valid-signer! %)]}
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
      (not signer)                           nil
      (not (usr/email-in-use? signer-email)) nil
      (not= email signer-email)              (fail :email-in-use)
      (not= role "applicant")                (fail :error.not-applicant)
      (-> company :role ss/not-blank?)       (fail :error.already-in-company))))

(def ^:private valid-company! (sc/validator com/Company))

(defcommand start-polling
  {:description         "Instructs the backend to start polling Visma Sign API for the state of some document"
   :parameters          [processId]
   :optional-parameters [attempts]
   :pre-checks          [client/validate-config]
   :input-validators    [(partial action/non-blank-parameters [:processId])]
   :user-roles          #{:anonymous}}
  [{:keys [system]}]
  (let [attempts         (min (or attempts 450) 600) ; How many attempts before timeout, max value of 20 minutes (with interval 2000)
        timeout-interval 2000]                      ; How long to wait between each attempt
    (when (p/find-sign-process! processId)
      (client/poll-visma-doc-state system processId attempts timeout-interval))
    (ok)))


(defquery is-signed
  {:description      "The frontend polls this endpoint for information if the document related to some process-id has been signed"
   :parameters       [processId]
   :input-validators [(partial action/non-blank-parameters [:processId])]
   :pre-checks       [client/validate-config]
   :user-roles       #{:anonymous}}
  [_]
  (let [process (p/find-sign-process! processId)]
    (case (:status process)
      "signed" {:ok     true
                :status (:status process)
                :url    (if (nil? (get-in process [:signer :currentuser]))
                          "register-company-success"
                          "register-company-existing-user-success")}
      "fail"   {:ok false :status (:status process)}
      {:ok true :status (:status process)})))

(defcommand sign-contract
  {:description      "Does all the neccessary communication with the Visma Sign API to sign a document
                      - Creates a document
                      - Uploads a pdf file to the document
                      - Creates an invitation to sign the document/file
                      - Returns the invitation url"
   :parameters       [processId]
   :input-validators [(partial action/non-blank-parameters [:processId])]
   :pre-checks       [client/validate-config]
   :user-roles       #{:anonymous}}
  [{:keys [system]}]
  (let [process                    (p/find-sign-process! processId)
        lang                       (:lang process)
        [_ct pdf filename]         (p/fetch-document processId (now) system)
        location                   (client/create-doc system filename)
        doc-id                     (-> location (ss/split #"/api/v1/document/") second)
        _                          (p/set-doc-id processId doc-id (now))
        _                          (client/add-file system doc-id doc-id pdf)
        [invitation-id invitation] (client/create-invite system doc-id lang)
        config                     (-> system :config :visma-sign)
        base-url                   (:base-url config)]
    {:ok true :id processId :invitation-url (str base-url invitation) :invitation-id invitation-id}))

(defcommand init-sign
  {:description      "Init the signing process, creates a company and process in database"
   :parameters       [company signer lang]
   :user-roles       #{:anonymous}
   :pre-checks       [validate-email-in-use]
   :input-validators [(fn [{{lang :lang} :data}]
                        (when-not ((set (map name i18n/languages)) lang)
                          (fail :bad-lang)))]}
  [{:keys [^Long created user]}]
  (let [company (merge com/company-skeleton
                       (ss/trimwalk company))
        signer  (get-signer user signer)]
    (valid-company! company)
    (let [process-data (p/init-sign-process (java.util.Date. created) company signer lang)]
      {:ok true :process-id (:process-id process-data)})))

(defn- user-company-admin? [{:keys [user]}]
  (when-not (usr/company-admin? user)
    (fail :error.user.not.company.admin)))

(defmacro with-error-handling [& body]
  `(try+
     ~@body
     (catch map? {error# :error}
       (resp/status (get {:not-found 404 :bad-request 400 :error.process-cancelled 400} error# 500)
                    (name error#)))))

(defraw download-signed-company-contract
  {:parameters       [companyId]
   :user-roles       #{:authority :applicant}
   :pre-checks       [user-company-admin?]
   :input-validators [(partial action/non-blank-parameters [:companyId])]}
  [_]
  (with-error-handling
    (if-let [pdf (storage/download-contract companyId)]
      (->> ((:content pdf))
           (resp/status 200)
           (resp/content-type "application/pdf"))
      (fail :error.contract.not.found))))

(defraw admin-download-signed-company-contract
  {:parameters       [companyId]
   :user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:companyId])]}
  [_]
  (with-error-handling
    (if-let [pdf (storage/download-contract companyId)]
      (->> ((:content pdf))
           (resp/status 200)
           (resp/content-type "application/pdf"))
      (fail :error.contract.not.found))))

(defraw download-contract
  {:parameters       [processId]
   :user-roles       #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:processId])]}
  [{:keys [system]}]
  (with-error-handling
    (if-let [[content-type document] (p/fetch-document processId (now) system)]
      (->> document
           (resp/status 200)
           (resp/content-type content-type))
      (fail :error.contract.not.found))))

(defcommand cancel-sign
  {:parameters       [processId]
   :input-validators [(partial action/non-blank-parameters [:processId])]
   :user-roles       #{:anonymous}}
  [{:keys [created]}]
  (p/cancel-sign-process! processId created)
  (ok))

(defn wrap-body-for-visma-sign-dev-dummy
  "When running the dev dummy server for Visma sign we need to add the body to params for Noir to be able to accept the pdf body"
  [handler]
  (fn [req]
    (if (and (env/feature? :dummy-visma-sign)
             (= (:uri req) (str "/dev/dummy-visma-sign/api/v1/document/" (-> req :query-params (get "filename")) "/files"))
             (-> req :headers (get "content-type") (= "application/pdf")))
      (let [body (slurp (:body req))]
        (handler (assoc-in req [:params :body] body)))
      (handler req))))

(when (env/feature? :dummy-visma-sign)

  (defquery dummy-find-sign-process
    {:parameters       [processId]
     :input-validators [(partial action/non-blank-parameters [:processId])]
     :user-roles       #{:anonymous}}
    [_]
    (ok :process (p/find-sign-process! processId)))

  (defcommand dummy-sign-document
    {:parameters       [processId]
     :input-validators [(partial action/non-blank-parameters [:processId])]
     :user-roles       #{:anonymous}}
    [{:keys [system]}]
    (let [process (p/find-sign-process processId)
          req     (client/create-visma-request system (str "/document/" (:doc-id process) "/sign") {:method :post})
          _       (http/request req)]
      (ok)))

  (defcommand dummy-cancel-sign-document
    {:parameters       [processId]
     :input-validators [(partial action/non-blank-parameters [:processId])]
     :user-roles       #{:anonymous}}
    [{:keys [system]}]
    (let [process (p/find-sign-process processId)
          req     (client/create-visma-request system (str "/document/" (:doc-id process) "/cancel") {:method :post})
          _       (http/request req)]
      (ok)))

  ;;
  ;; Load dummy Visma Sign simulator:
  ;;

  (require 'lupapalvelu.visma-sign.dummy-visma-sign-server))
