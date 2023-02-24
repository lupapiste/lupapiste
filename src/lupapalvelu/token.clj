(ns lupapalvelu.token
  (:require [taoensso.timbre :refer [errorf]]
            [monger.operators :refer :all]
            [noir.request :as request]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.security :as security]
            [lupapalvelu.ttl :as ttl]
            [sade.core :refer :all]))

(defmulti handle-token (fn [token-data _params] (:token-type token-data)))

(defmethod handle-token :default [token-data _]
  (errorf "consumed token: token-type %s does not have handler: id=%s" (:token-type token-data) (:_id token-data)))

(def make-token-id (partial security/random-password 48))

(defn make-token [token-type user data & {:keys [ttl auto-consume] :or {ttl ttl/default-ttl auto-consume true}}]
  (let [token-id (make-token-id)
        now (now)
        request (request/ring-request)]
    (mongo/insert :token {:_id token-id
                          :token-type token-type
                          :data data
                          :used nil
                          :auto-consume auto-consume
                          :created now
                          :expires (+ now ttl)
                          :ip (get-in request [:headers "x-real-ip"] (:remote-addr request))
                          :user-id (:id (or user (user/current-user request)))})
    token-id))

(defn- keywordize-token-type [token]
  (update-in token [:token-type] keyword))

(defn get-token [id & {:keys [consume] :or {consume false}}]
  (if-let [token (mongo/select-one :token {:_id id})]
    (cond
      (:used token)              [:used (keywordize-token-type token)]
      (< (:expires token) (now)) [:expired (keywordize-token-type token)]
      :else (do
              (when (or consume (:auto-consume token))
                (mongo/update-by-id :token id {$set {:used (now)}}))
              [:usable (keywordize-token-type token)]))
    [:not-found]))

(defn get-usable-token [id & {:keys [consume] :or {consume false}}]
  (let [[status token] (get-token id :consume consume)]
    (when (= status :usable)
      token)))

(defn consume-token [id params & {:keys [consume] :or {consume false}}]
  (let [[status token] (get-token id :consume consume)]
    (case status
      :usable [:usable (handle-token token params)]
      [status])))
