(ns lupapalvelu.onnistuu.process
  (:require [taoensso.timbre :refer [debug infof warnf errorf]]
            [clojure.walk :as walk]
            [pandect.core :as pandect]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [lupapalvelu.json :as json]
            [slingshot.slingshot :refer [throw+]]
            [sade.env :as env]
            [sade.schemas :refer [max-length-string]]
            [sade.core :refer [ok]]
            [sade.crypt :as crypt]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :refer [valid-email? valid-hetu?]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :refer [random-password]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.company :as c]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.docx :as docx]
            [lupapalvelu.storage.file-storage :as storage]))

(set! *warn-on-reflection* true)

;;
;; Onnistuu.fi integration: Process handling
;;

;
; Process FSM:
;

; Key is process state, value is allowed next states:
(def process-state {:created  #{:started :cancelled}
                    :started  #{:done :cancelled :error :fail}})

(def Signer {(sc/optional-key :currentUser) (sc/pred mongo/valid-key? "valid key")
             :firstName (max-length-string 64)
             :lastName  (max-length-string 64)
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

(defn init-sign-process [^java.util.Date current-date crypto-key success-url document-url company signer lang]
  (let [crypto-iv    (crypt/make-iv)
        process-id   (random-password 40)
        stamp        (random-password 40)
        company-name (:name company)
        company-y    (:y company)
        hetu         (:personId signer)]
    (infof "sign:init-sign-process:%s: company-name [%s], y [%s], email [%s]" process-id company-name company-y (:email signer))
    (mongo/insert :sign-processes {:_id       process-id
                                   :stamp     stamp
                                   :company   company
                                   :signer    signer
                                   :lang      lang
                                   :status    :created
                                   :created   current-date
                                   :progress  [{:status :created, :ts (.getTime current-date)}]})
    {:process-id process-id
     :data       (->> {:stamp           stamp
                       :return_success  (str success-url "/" process-id)
                       :document        (str document-url "/" process-id)
                       :requirements    [{:type :person, :identifier hetu}]}
                      (json/encode)
                      (crypt/str->bytes)
                      (crypt/encrypt (-> crypto-key (crypt/str->bytes) (crypt/base64-decode)) crypto-iv :rijndael)
                      (crypt/base64-encode)
                      (crypt/bytes->str))
     :iv         (-> crypto-iv (crypt/base64-encode) (crypt/bytes->str))}))

;
; Cancel sign:
;

(defn cancel-sign-process! [process-id ts]
  (infof "sign:cancel-sign-process:%s:" process-id)
  (-> process-id
      (find-sign-process!)
      (process-update! :cancelled ts))
  (storage/delete-process-file process-id)
  nil)

;
; Onnistuu.fi loads the document:
;

(defn fetch-document
  "Either fetches the existing document (by process id) or uploads a new contract to storage and returns it."
  [process-id ts]
  (infof "sign:fetch-document:%s" process-id)
  (let [process (find-sign-process! process-id)
        content-type "application/pdf"]
    (when (not= (:status process) "started")
      (process-update! process :started ts))

    (if-let [pdf (storage/download-process-file process-id)]
      (do
        (debug "sign:fetch-document:download-from-storage")
        [content-type ((:content pdf))])
      (let [filename (str "yritystilisopimus-" (-> process :company :name) ".pdf")
            account-type (keyword (get-in process [:company :accountType]))
            billing-type (keyword (get-in process [:company :billingType]))
            selected-account (util/find-by-key :name account-type c/account-types)
            account {:type  (i18n/localize "fi" :register :company account-type :title)
                     :price (i18n/localize-and-fill "fi"
                                                    [:register :company :billing billing-type :price]
                                                    (get-in selected-account [:price billing-type]))
                     :billingType (ss/lower-case (i18n/localize "fi" :register :company :billing billing-type :title))}
            pdf (docx/yritystilisopimus (:company process) (:signer process) account ts)
            sha256 (pandect/sha256 pdf)]
        (debug "sign:fetch-document:upload-to-storage")
        (.reset pdf) ; Hashing read the whole stream
        (storage/upload-process-file (:id process) filename content-type pdf {:sha256 sha256})
        (.reset pdf) ; Again, the whole stream was read
        [content-type pdf]))))
;
; Success:
;

(notifications/defemail :onnistuu-success
  {:model-fn  (fn [command _ _] command)
   :recipients-fn (constantly [{:email (env/value :onnistuu :success :email)}])})

(notifications/defemail :onnistuu-success-campaign
  {:model-fn  (fn [command _ _] command)
   :recipients-fn (constantly [{:email (env/value :onnistuu :success :email)}])})

(defn success! [process-id data iv ts]
  (let [process    (find-sign-process! process-id)
        signer     (assoc (:signer process) :personIdSource :identification-service)
        crypto-key (-> (env/value :onnistuu :crypto-key) (crypt/str->bytes) (crypt/base64-decode))
        crypto-iv  (-> iv (crypt/str->bytes) (crypt/base64-decode))
        resp       (->> data
                        (crypt/str->bytes)
                        (crypt/base64-decode)
                        (crypt/decrypt crypto-key crypto-iv :rijndael)
                        (crypt/bytes->str)
                        (json/decode)
                        walk/keywordize-keys)
        {:keys [signatures stamp document]} resp
        {:keys [type identifier name]} (first signatures)
        resp-assert! (fn [result expected message]
                      (when-not (= result expected)
                        (errorf "sign:success:%s: %s: expected '%s', got '%s'" process-id message result expected)
                        (process-update! process :error ts)
                        (fail! :bad-request)))]
    (resp-assert! (:stamp process)          stamp              "wrong stamp")
    (resp-assert! (count signatures)        1                  "number of signatures")
    (resp-assert! type                      "person"           "wrong signature type")
    (resp-assert! identifier                (:personId signer) "returned personId does not match original")
    (process-update! process :done ts :document document)

    (infof "sign:success:%s: OK: identifier [%s], company: [%s], document: [%s]"
           process-id
           identifier
           name
           document)
    (let [company  (c/create-company (merge (:company process) {:process-id process-id, :document document}))
          token-id (if (nil? (:currentUser signer)) (c/add-user-after-company-creation! signer company :admin true))
          mail-model (assoc (select-keys process [:company :signer]) :document document :signature (first signatures))]
      (infof "sign:success:%s: company-created: y [%s], company: [%s], id: [%s], token: [%s]"
             process-id
             (:y company)
             (:name company)
             (:id company)
             token-id)
      (when (:currentUser signer)
        (c/link-user-to-company! (:currentUser signer) (:id company) :admin true)
        (infof "added current user to created-company: company [%s], user [%s]"
               (:id company)
               (:currentUser signer)))
      (notifications/notify! (if (-> mail-model :company :campaign ss/not-blank?)
                               :onnistuu-success-campaign
                               :onnistuu-success)
                             mail-model))
    process))

;
; Fail:
;

(defn failed! [process-id error message ts]
  (warnf "sign:fail:%s: signing failed: error [%s], message [%s]" process-id error message)
  (-> process-id
      (find-sign-process!)
      (process-update! :fail ts :error {:code error :message message})))
