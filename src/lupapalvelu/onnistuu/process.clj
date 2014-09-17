(ns lupapalvelu.onnistuu.process
  (:require [taoensso.timbre :as timbre :refer [infof warnf errorf]]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [cheshire.core :as json]
            [cheshire.generate :as cheshire]
            [slingshot.slingshot :refer [throw+]]
            [noir.response :as resp]
            [sade.env :as env]
            [sade.util :refer [max-length-string valid-email?]]
            [lupapalvelu.core :refer [ok]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :refer [random-password]]
            [lupapalvelu.onnistuu.crypt :as crypt]
            [lupapalvelu.company :as c]
            [lupapalvelu.user-api :as u]))

(set! *warn-on-reflection* true)

;;
;; Onnistuu.fi integration: Process handling
;;

;
; Setup:
;

; Encode Java arrays to JSON just like any sequence.
(cheshire/add-encoder (Class/forName "[B") cheshire/encode-seq)

;
; Process FSM:
;

; Key is process state, value is allowed next states:
(def process-state {:created  #{:started :cancelled}
                    :started  #{:done :error :fail}})

(def Signer {:firstName (max-length-string 64)
             :lastName  (max-length-string 64)
             :email     (sc/pred valid-email? "valid email")})

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

(defn init-sign-process [ts crypto-key success-url document-url company signer lang]
  (let [crypto-iv    (crypt/make-iv)
        process-id   (random-password 40)
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
                                   :created   ts
                                   :progress  [{:status :created, :ts ts}]})
    {:process-id process-id
     :data       (->> {:stamp           stamp
                       :return_success  (str success-url "/" process-id)
                       :document        (str document-url "/" process-id)
                       :requirements    [{:type :company, :identifier company-y}]}
                      (json/encode)
                      (crypt/str->bytes)
                      (crypt/encrypt (-> crypto-key (crypt/str->bytes) (crypt/base64-decode)) crypto-iv)
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
  nil)

;
; Onnistuu.fi loads the document:
;

(defn fetch-document [process-id ts]
  (infof "sign:fetch-document:%s" process-id)
  (-> (find-sign-process! process-id)
      (process-update! :started ts))
  ; FIXME: where we get the actual document?
  ["application/pdf" (-> "hello.pdf" io/resource io/input-stream)])

;
; Success:
;

(defmacro resp-assert! [result expected message]
  `(when-not (= ~result ~expected)
     (errorf "sing:success:%s: %s: expected '%s', got '%s'" ~'process-id ~message ~result ~expected)
     (process-update! ~'process :error ~'ts)
     (fail! :bad-request)))

(defn success [process-id data iv ts]
  (let [process    (find-sign-process! process-id)
        signer     (:signer process)
        crypto-key (-> (env/get-config) :onnistuu :crypto-key (crypt/str->bytes) (crypt/base64-decode))
        crypto-iv  (-> iv (crypt/str->bytes) (crypt/base64-decode))
        resp       (->> data
                        (crypt/str->bytes)
                        (crypt/base64-decode)
                        (crypt/decrypt crypto-key crypto-iv)
                        (crypt/bytes->str)
                        (json/decode))
        {:strs [signatures stamp]} resp
        {:strs [type identifier name timestamp uuid]} (first signatures)]
    (resp-assert! (:stamp process)          stamp       "wrong stamp")
    (resp-assert! (count signatures)        1           "number of signatures")
    (resp-assert! type                      "company"   "wrong signature type")
    (resp-assert! (-> process :company :y)  identifier  "wrong Y")
    (process-update! process :done ts)
    (infof "sign:success:%s: OK: y [%s], company: [%s]"
             process-id
             identifier
             name)
    (let [company  (c/create-company (merge (:company process) {:name name, :process-id process-id}))
          token-id (c/add-user! signer company :admin)]
      (infof "sign:success:%s: company-created: y [%s], company: [%s], id: [%s], token: [%s]"
             process-id
             (:y company)
             (:name company)
             (:id company)
             token-id))
    process))

;
; Fail:
;

(defn failed! [process-id error message ts]
  (warnf "sign:fail:%s: signing failed: error [%s], message [%s]" process-id error message)
  (-> process-id
      (find-sign-process!)
      (process-update! :fail ts :error {:code error :message message})))
