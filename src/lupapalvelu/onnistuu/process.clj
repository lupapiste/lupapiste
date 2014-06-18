(ns lupapalvelu.onnistuu.process
  (:require [taoensso.timbre :as timbre :refer [infof warnf errorf]]
            [clojure.walk :as walk]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [cheshire.core :as json]
            [cheshire.generate :as cheshire]
            [slingshot.slingshot :refer [throw+]]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :refer [random-password]]
            [lupapalvelu.onnistuu.crypt :as c]))

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

(defn- str->bytes ^bytes [^String s]
  (.getBytes s "UTF-8"))

(defn- bytes->str ^String [^bytes b]
  (String. b "UTF-8"))

(defn find-sign-process [id]
  (mongo/by-id :sign-processes id))

(defn find-sign-process! [id]
  (or (find-sign-process id) (fail! :not-found)))

(defn- validate-process-update! [process status]
  (if-not process
    (fail! :not-found))
  (if-not (-> process :status keyword process-state status)
    (fail! :bad-request))
  process)

(defn- process-update! [process status ts]
  (validate-process-update! process status)
  (mongo/update :sign-processes
                {:_id (:id process)}
                {$set  {:status status}
                 $push {:progress {:status   status
                                   :ts       ts}}})
  process)

;
; Init sign process:
;

(defn- jump-data [{:keys [process success-url document-url crypto-iv crypto-key customer-id post-to]}]
  (assert (and process success-url document-url crypto-iv crypto-key customer-id post-to))
  )

(defn init-sign-process [ts crypto-key success-url document-url company-name y first-name last-name email lang]
  (let [{:keys [crypto-key success-url document-url]} config  
        crypto-iv  (c/make-iv)
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
                      (str->bytes)
                      (c/encrypt (-> crypto-key (str->bytes) (c/base64-decode)) crypto-iv)
                      (c/base64-encode)
                      (bytes->str))
     :iv         (-> crypto-iv (c/base64-encode) (bytes->str))}))

;
; Cancel sign:
;

(defn cancel-sign-process! [process-id ts]
  (infof "sign:cancel-sign-process:%s:" process-id)
  (process-update! (find-sign-process! process-id) :cancelled ts)
  nil)

;
; Process start:
;



(def ^:private config (env/value :onnistuu))

(defn start-process [id user-id ts success-url document-url]
  (infof "sign:start-process:%s" id)
  (let [process (find-sign-process! id)]
    (process-update! process :start ts user-id)
    (jump-data (assoc config
                      :process      process
                      :success-url  success-url
                      :document-url document-url
                      :crypto-iv    (c/make-iv)))))

;
; Onnistuu.fi loads the document:
;

(defn fetch-document [id ts]
  (infof "sign:fetch-document:%s" id)
  (-> (find-sign-process! id)
      (process-update! :started ts))
  ; FIXME: where we get the actual document?
  ["text/plain" "da pdf"])

;
; Success:
;

(defmacro resp-assert! [message result expected]
  `(when-not (= ~result ~expected)
     (errorf "sing:success:%s: %s: expected '%s', got '%s'" ~'id ~message ~result ~expected)
     (process-update! ~'process :error ~'ts)
     (fail! :bad-request)))

(defn success [id data iv ts]
  (let [process    (find-sign-process! id)
        crypto-key (-> config :crypto-key (str->bytes) (c/base64-decode))
        crypto-iv  (-> iv (str->bytes) (c/base64-decode))
        resp       (->> data
                        (str->bytes)
                        (c/base64-decode)
                        (c/decrypt crypto-key crypto-iv)
                        (json/decode)
                        (walk/keywordize-keys))
        signatures (:signatures resp)
        {:keys [type identifier name timestamp uuid]} (first signatures)]
    (resp-assert! "wrong stamp"           (:stamp process) (:stamp resp))
    (resp-assert! "number of signatures"  (count signatures) 1)
    (resp-assert! "wrong signature type"  type "company")
    (resp-assert! "wrong Y"               (-> process :company :y) identifier)
    (process-update! process :done ts)
    ; FIXME: Create compary account
    (infof "sign:success:%s: OK: y [%s], company: [%s], timestamp: [%s], uuid: [%s]" id identifier name timestamp uuid)
    process))

;
; Fail:
;

(defn fail! [id ts]
  (warnf "sign:fail:%s: signing failed" id)
  (-> (find-sign-process! id)
      (process-update! :fail ts)))
