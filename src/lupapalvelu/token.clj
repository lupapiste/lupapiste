(ns lupapalvelu.token
  (:use [monger.operators]
        [clojure.tools.logging])
  (:require [lupapalvelu.core :as core]
            [lupapalvelu.mongo :as mongo]
            [noir.request :as request]))

(defmulti handle-token :token-type)

(defmethod handle-token :default [token]
  (errorf "consumed token: token-type %s does not have handler: id=%s" (:token-type token) (:_id token)))

(def ^:private default-ttl (* 24 60 60 1000))

(def ^:private token-chars (concat (range (int \0) (inc (int \9)))
                                   (range (int \A) (inc (int \Z)))
                                   (range (int \a) (inc (int \z)))))
    
(defn- make-token-id []
  (apply str (repeatedly 48 (comp char (partial rand-nth token-chars)))))

(defn save-token [token-type data & {:keys [ttl] :or {ttl default-ttl}}]
  (let [id (make-token-id)
        now (core/now)]
    (mongo/insert :token {:_id id
                          :token-type token-type
                          :created now
                          :expires (+ now ttl)
                          :ip (:remote-addr (request/ring-request))
                          :used false
                          :data data})
    id))

(defn- get-and-use-token [id]
  (when-let [token (mongo/update-one-and-return :token
                                                {:_id id
                                                 :used false
                                                 :expires {$gt (core/now)}}
                                                {$set {:used true}})]
    (assoc token :token-type (keyword (:token-type token)))))

(defn consume-token [id]
  (if-let [token (get-and-use-token id)]
    (handle-token token)))
