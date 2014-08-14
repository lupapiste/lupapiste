(ns lupapalvelu.onnistuu.process
  (:require [taoensso.timbre :as timbre :refer [infof warnf errorf]]
            [clojure.walk :as walk]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [cheshire.core :as json]
            [cheshire.generate :as cheshire]
            [slingshot.slingshot :refer [throw+]]
            [noir.response :as resp]
            [sade.env :as env]
            [sade.util :refer [fn->]]
            [lupapalvelu.core :refer [ok]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :refer [random-password]]
            [lupapalvelu.onnistuu.crypt :as crypt]
            [lupapalvelu.company :as c]
            [lupapalvelu.token :as token]
            [lupapalvelu.notifications :as notif]
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

(defn init-sign-process [ts crypto-key success-url document-url company-name y first-name last-name email lang]
  (let [crypto-iv  (crypt/make-iv)
        process-id (random-password 40)
        stamp      (random-password 40)]
    (infof "sign:init-sign-process:%s: company-name [%s], y [%s], email [%s]" process-id company-name y email)
    (mongo/insert :sign-processes {:_id       process-id
                                   :stamp     stamp
                                   :company   {:name company-name, :y y}
                                   :signer    {:first-name first-name, :last-name last-name, :email email, :lang lang}
                                   :status    :created
                                   :created   ts
                                   :progress  [{:status :created, :ts ts}]})
    {:process-id process-id
     :data       (->> {:stamp           stamp
                       :return_success  (str success-url "/" process-id)
                       :document        (str document-url "/" process-id)
                       :requirements    [{:type :company, :identifier y}]}
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
  ["text/plain" "da pdf"])

;
; Success:
;

(defmacro resp-assert! [message result expected]
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
    (resp-assert! "wrong stamp"           (:stamp process) stamp)
    (resp-assert! "number of signatures"  (count signatures) 1)
    (resp-assert! "wrong signature type"  type "company")
    (resp-assert! "wrong Y"               (-> process :company :y) identifier)
    (process-update! process :done ts)
    (infof "sign:success:%s: OK: y [%s], company: [%s]"
             process-id
             identifier
             name)
    (let [company  (c/create-company {:name name, :y identifier, :process-id process-id})
          token-id (token/make-token :new-company-user nil {:user signer, :company company} :auto-consume false)]
      (notif/notify! :new-company-user {:user       signer
                                        :company    company
                                        :link-fi    (str (env/value :host) "/app/fi/welcome#!/new-company-user/" token-id)
                                        :link-sv    (str (env/value :host) "/app/sv/welcome#!/new-company-user/" token-id)})
      (infof "sign:success:%s: company-created: y [%s], company: [%s], company-id: [%s], token: [%s]"
             process-id
             (:y company)
             (:name company)
             (:id company)
             token-id))
    process))

(notif/defemail :new-company-user {:subject-key   "new-company-user.subject"
                                   :recipients-fn (fn-> :user :email vector)
                                   :model-fn      (fn [model _] model)})

(defmethod token/handle-token :new-company-user [{{:keys [user company]} :data} {password :password}]
  (println "handle:" company (:id company))
  (c/find-company! {:_id (:id company)}) ; make sure company still exists
  (u/create-new-user nil
                     {:email       (:email user)
                      :username    (:email user)
                      :firstName   (:first-name user)
                      :lastName    (:last-name user)
                      :company     (:id company)
                      :password    password
                      :role        :applicant
                      :architect   true
                      :enabled     true}
                     :send-email false)
  (ok))

;
; Fail:
;

(defn failed! [process-id error message ts]
  (warnf "sign:fail:%s: signing failed: error [%s], message [%s]" process-id error message)
  (-> process-id
      (find-sign-process!)
      (process-update! :fail ts :error {:code error :message message})))
