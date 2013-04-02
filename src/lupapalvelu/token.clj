(ns lupapalvelu.token
  (:use [monger.operators]
        [clojure.tools.logging])
  (:require [lupapalvelu.core :as core]
            [lupapalvelu.mongo :as mongo]
            [noir.request :as request]))

(def ^:private default-ttl (* 24 60 60 1000))

(def ^:private token-chars (concat (range (int \0) (inc (int \9)))
                                   (range (int \A) (inc (int \Z)))
                                   (range (int \a) (inc (int \z)))))
    
(defn- make-token-id []
  (apply str (repeatedly 48 (comp char (partial rand-nth token-chars)))))

(defn save-token [data & {:keys [ttl] :or {ttl default-ttl}}]
  (let [now (core/now)
        id (make-token-id)]
    (mongo/insert :token {:_id id
                          :created now
                          :expires (+ now ttl)
                          :ip (:remote-addr (request/ring-request))
                          :used false
                          :data data})
    id))

(defn consume-token [id]
  (mongo/update-one-and-return :token
                               {:_id id
                                :used false
                                :expires {$gt (core/now)}}
                               {$set {:used true}}))
