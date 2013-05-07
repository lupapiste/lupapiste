(ns lupapalvelu.token
  (:use [monger.operators]
        [clojure.tools.logging]
        [sade.security :only [random-password]])
  (:require [lupapalvelu.core :as core]
            [lupapalvelu.mongo :as mongo]
            [noir.request :as request]))

(defmulti handle-token (fn [token-data params] (:token-type token-data)))

(defmethod handle-token :default [token-data params]
  (errorf "consumed token: token-type %s does not have handler: id=%s" (:token-type token-data) (:_id token-data)))

(def ^:private default-ttl (* 24 60 60 1000))
    
(def ^:private make-token-id (partial random-password 48))

(defn make-token [token-type data & {:keys [ttl] :or {ttl default-ttl}}]
  (let [id (make-token-id)
        now (core/now)
        request (request/ring-request)]
    (mongo/insert :token {:_id id
                          :token-type token-type
                          :data data
                          :used false
                          :created now
                          :expires (+ now ttl)
                          :ip (:remote-addr request)
                          :user-id (get-in request [:session "noir" "user" "id"])})
    id))

(defn get-token [id consume]
  (let [query {:_id id
               :used false
               :expires {$gt (core/now)}}]
    (when-let [token (if consume
                       (mongo/update-one-and-return :token query {$set {:used true}})
                       (mongo/select-one :token query))]
      (assoc token :token-type (keyword (:token-type token))))))

(defn get-and-consume-token [id]
  (get-token id true))

(defn consume-token [id params]
  (when-let [token-data (get-and-consume-token id)]
    (handle-token token-data params)))
