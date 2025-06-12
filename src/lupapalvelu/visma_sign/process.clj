(ns lupapalvelu.visma-sign.process
  (:require [lupapalvelu.company :as c]
            [lupapalvelu.company-contract :as cc]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :refer [debug errorf infof warnf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.security :refer [random-password]]
            [lupapalvelu.storage.file-storage :as storage]
            [monger.operators :refer :all]
            [pandect.core :as pandect]
            [sade.env :as env]
            [sade.schemas :refer [max-length-string]]
            [sade.util :as util]
            [sade.validators :refer [valid-email? valid-hetu?]]
            [schema.core :as sc]
            [slingshot.slingshot :refer [throw+]]))

(set! *warn-on-reflection* true)

; Key is process state, value is allowed next states:
(def process-state {:created #{:new :cancelled :fail}
                    :new     #{:cancelled :new :pending :fail}
                    :pending #{:pending :cancelled :fail :done}})

(def Signer {(sc/optional-key :currentUser) (sc/pred mongo/valid-key? "valid key")
             :firstName (max-length-string 255)
             :lastName  (max-length-string 255)
             :email     (sc/pred valid-email? "valid email")
             :personId  (sc/pred valid-hetu? "valid hetu")
             (sc/optional-key :language)  i18n/EnumSupportedLanguages})
;
; Utils:

(defn- fail! [error]
  (throw+ {:error error}))

(defn find-sign-process [id]
  (mongo/by-id :sign-processes id))

(defn find-sign-process! [id]
  (or (find-sign-process id) (fail! :not-found)))

(defn- validate-process-update! [process status]
  (when-not (-> process :status keyword process-state status)
    (errorf "sign:illegal-state-transfer:%s: from [%s] to [%s]" (:stamp process) (:status process) (name status))
    (fail! :bad-request))
  process)

(defn- process-update! [process status ts & updates]
  (validate-process-update! process status)
  (mongo/update :sign-processes
                {:_id (:id process)}
                {$set  (assoc (apply hash-map updates) :status status)
                 $push {:progress {:status   status
                                   :ts       ts}}})
  process)

;
; Init sign process:
;

(defn init-sign-process [^java.util.Date current-date company signer lang]
  (let [process-id   (random-password 40)
        stamp        (random-password 40)
        company-name (:name company)
        company-y    (:y company)]
    (infof "sign:init-sign-process:%s: company-name [%s], y [%s], email [%s]" process-id company-name company-y (:email signer))
    (mongo/insert :sign-processes {:_id       process-id
                                   :stamp     stamp
                                   :company   company
                                   :signer    signer
                                   :lang      lang
                                   :status    :created
                                   :created   current-date
                                   :progress  [{:status :created, :ts (.getTime current-date)}]})
    {:process-id process-id}))

(defn set-doc-id [process-id doc-id ts]
  (let [process (find-sign-process process-id)]
    (process-update! process :new ts :doc-id doc-id)))

(defn update-status [process-id status ts]
  (let [process (find-sign-process process-id)]
    (process-update! process status ts)))

(defn cancel-sign-process! [process-id ts]
  (infof "sign:cancel-sign-process:%s:" process-id)
  (-> process-id
      (find-sign-process!)
      (process-update! :cancelled ts))
  (storage/delete-process-file process-id)
  nil)

(defn fetch-document
  "Either fetches the existing document (by process id) or uploads a new contract to storage and returns it."
  [process-id ts system]
  {:pre [(map? system)]}
  (infof "sign:fetch-document:%s" process-id)
  (let [{lang :lang filename :filename :as process} (find-sign-process! process-id)
        content-type             "application/pdf"]
    (if (= (:status process) "cancelled")
      (fail! :error.process-cancelled)
      (if-let [pdf (storage/download-process-file process-id)]
        (do
          (debug "sign:fetch-document:download-from-storage")
          [content-type ((:content pdf)) filename])
        (let [filename         (i18n/localize-and-fill lang :company-contract.filename
                                                       (-> process :company :name))
              account-type     (keyword (get-in process [:company :accountType]))
              billing-type     (keyword (get-in process [:company :billingType]))
              selected-account (util/find-by-key :name account-type c/account-types)
              account          {:type        account-type
                                :price       (get-in selected-account [:price billing-type])
                                :billingType billing-type}
              pdf              (cc/generate-company-contract lang
                                                             (:company process)
                                                             (:signer process)
                                                             account
                                                             system)
              sha256           (pandect/sha256 pdf)]
          (process-update! process :new ts :filename filename)
          (debug "sign:fetch-document:upload-to-storage")
          (.reset pdf) ; Hashing read the whole stream
          (storage/upload-process-file (:id process) filename content-type pdf {:sha256 sha256})
          (.reset pdf) ; Again, the whole stream was read
          [content-type pdf filename])))))
;
; Success:
;

(defn- visma-sign-recipients []
  (->> (concat [(env/value :visma-sign :success :email)]
               (when (env/feature? :company-change-notifications)
                 (c/company-subscribers-emails)))
       distinct
       (mapv (fn [email] {:email email}))))

(notifications/defemail :visma-sign-success
  {:model-fn      (fn [command _ _] command)
   :recipients-fn (fn [_cmd] (visma-sign-recipients))})

(defn upload-signed-contract [company filename pdf]
  (let [content-type "application/pdf"
        sha256       (pandect/sha256 pdf)]
    (debug "sign:signed-company-contract:upload-to-storage")
    (storage/upload-signed-company-contract (:id company ) filename content-type pdf {:sha256 sha256})))

(defn success! [system process-id response signed-pdf ts]
  (let [process      (find-sign-process! process-id)
        signer       (assoc (:signer process) :personIdSource :identification-service)
        company      (-> process :company :name)
        uuid         (:uuid response)
        signer-name  (-> response :invitations first :signer_name)
        document     (-> response :files first :filename)
        signature    {:name signer-name :identifier (-> process :signer :personId)}
        resp-assert! (fn [result expected message]
                       (when-not (= result expected)
                         (errorf "sign:success:%s: %s: expected '%s', got '%s'" process-id message result expected)
                         (process-update! process :error ts)
                         (fail! :bad-request)))]

    (resp-assert! (:doc-id process)               uuid "wrong document id")
    (resp-assert! (count (:invitations response)) 1    "number of signatures")
    ;; TODO: we might consider marking the process done after all the side-effects are done
    ;; this would at least simplify the tests. But we would also take care to handle any errors
    ;; from the side-effects, to make sure the process is always marked as done (or failed).
    (process-update! process :done ts :document document)

    (infof "sign:success:%s: OK: signer [%s], company: [%s], document: [%s]"
           process-id
           signer-name
           company
           document)
    (let [company    (c/create-company (merge (:company process) {:process-id process-id
                                                                  :document   document}))
          token-id   (when (nil? (:currentUser signer))
                       (c/add-user-after-company-creation! system signer company :admin true))
          mail-model (-> process
                         (select-keys [:company :signer])
                         (assoc :document document
                                :signature signature
                                :system system))]
      (upload-signed-contract company document signed-pdf)
      (infof "sign:success:%s: company-created: y [%s], company: [%s], id: [%s], token: [%s]"
             process-id
             (:y company)
             (:name company)
             (:id company)
             token-id)
      (when (:currentUser signer)
        (c/link-user-to-company! system (:currentUser signer) (:id company) :admin true)
        (infof "added current user to created-company: company [%s], user [%s]"
               (:id company)
               (:currentUser signer)))
      (notifications/notify! :visma-sign-success
                             mail-model))
    process))

(defn failed! [process-id error message ts]
  (warnf "sign:fail:%s: signing failed: error [%s], message [%s]" process-id error message)
  (-> process-id
      (find-sign-process!)
      (process-update! :fail ts :error {:code error :message message})))
