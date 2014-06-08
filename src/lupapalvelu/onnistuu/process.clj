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
(def process-state {:created  #{:start}
                    :start    #{:document :fail}
                    :document #{:done :error :fail}})

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

(defn- process-update! [process status ts & user-id]
  (validate-process-update! process status)
  (mongo/update :sign-processes
                {:_id (:id process)}
                {$set  {:status status}
                 $push {:progress {:status   status
                                   :ts       ts
                                   :user-id  user-id}}}))

;
; Init sign process:
;

(defn init-sign-process [company-name y user-id ts]
  (let [id     (random-password 40)
        stamp  (random-password 40)]
    (infof "sign:init-sign-process:%s: company-name [%s], y [%s], user-id [%s]" id company-name y user-id)
    (mongo/insert :sign-processes {:id        id
                                   :stamp     stamp
                                   :company   {:name  company-name
                                               :y     y}
                                   :status    :created
                                   :created   ts
                                   :progress  [{:ts       ts
                                                :user-id  user-id
                                                :status   :created}]})
    id))

;
; Process start:
;

(defn- jump-data [{:keys [process success-url document-url crypto-iv crypto-key customer-id post-to]}]
  (assert (and process success-url document-url crypto-iv crypto-key customer-id post-to))
  {:data      (->> {:stamp           (-> process :stamp)
                    :return_success  success-url
                    :document        document-url
                    :requirements    [{:type       :company
                                       :identifier (-> process :company :y)}]}
                   (json/encode)
                   (str->bytes)
                   (c/encrypt (-> crypto-key (str->bytes) (c/base64-decode)) crypto-iv)
                   (c/base64-encode)
                   (bytes->str))
   :iv        (-> crypto-iv
                  (c/base64-encode)
                  (bytes->str))
   :customer  customer-id
   :post-to   post-to})

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
      (process-update! :document ts))
  ; FIXME: where we get the actual document?
  ["text/plain" "da pdf"])

;
; Success:
;

(defn success [id data iv ts]
  (let [process (find-sign-process! id)
        data    (->> data
                     (str->bytes)
                     (c/base64-decode)
                     (c/decrypt (-> (:crypto-key config)
                                    (str->bytes)
                                    (c/base64-decode))
                                (-> iv
                                    (str->bytes)
                                    (c/base64-decode)))
                     (json/decode)
                     (walk/keywordize-keys))]
    (when-not (= (:stamp process) (:stamp data))
      (errorf "sing:success:%s: stamp fail: process stamp: [%s], data stamp: [%s]" id (:stamp process) (:stamp data))
      (process-update! process :error ts)
      (fail! :bad-request))
    (let [signatures (:signatures data)
          {:keys [type identifier name timestamp uuid]} (first signatures)]
      (when-not (= (count signatures) 1)
        (errorf "sing:success:%s: wrong number of signatures: %d" id (count signatures))
        (process-update! process :error ts)
        (fail! :bad-request))
      (when-not (= type "company")
        (errorf "sing:success:%s: wrong type: [%s]" id type)
        (process-update! process :error ts)
        (fail! :bad-request))
      (when-not (= (-> process :company :y) identifier)
        (errorf "sing:success:%s: wrong Y: expected [%s], response [%s]" id (-> process :company :y) identifier)
        (process-update! process :error ts)
        (fail! :bad-request))
      (process-update! process :done ts)
      ; FIXME: Create compary account
      (infof "sign:success:%s: OK: y [%s], company: [%s], timestamp: [%s], uuid: [%s]" id identifier name timestamp uuid))))

;
; Fail:
;

(defn fail [id ts]
  (warnf "sign:fail:%s: signing failed" id)
  (-> (find-sign-process! id)
      (process-update! :fail ts)))
