(ns lupapalvelu.token
  (:use [monger.operators]
        [clojure.tools.logging]
        [sade.security :only [random-password]])
  (:require [lupapalvelu.core :as core]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [noir.request :as request]))

(defmulti handle-token (fn [token-data params] (:token-type token-data)))

(defmethod handle-token :default [token-data params]
  (errorf "consumed token: token-type %s does not have handler: id=%s" (:token-type token-data) (:_id token-data)))

(def ^:private default-ttl (* 24 60 60 1000))

(def make-token-id (partial random-password 48))

(defn make-token [token-type data & {:keys [ttl auto-consume] :or {ttl default-ttl auto-consume true}}]
  (let [token-id (make-token-id)
        now (core/now)
        request (request/ring-request)]
    (mongo/insert :token {:_id token-id
                          :token-type token-type
                          :data data
                          :used nil
                          :auto-consume auto-consume
                          :created now
                          :expires (+ now ttl)
                          :ip (:remote-addr request)
                          :user-id (:id (security/current-user))})
    token-id))

(defn get-token [id & {:keys [consume] :or {consume false}}]
  (when-let [token (mongo/select-one :token {:_id id
                                             :used nil
                                             :expires {$gt (core/now)}})]
    (when (or consume (:auto-consume token))
      (mongo/update-by-id :token id {$set {:used (core/now)}}))
    (assoc token :token-type (keyword (:token-type token)))))

(defn consume-token [id params & {:keys [consume] :or {consume false}}]
  (when-let [token-data (get-token id :consume consume)]
    (handle-token token-data params)))
