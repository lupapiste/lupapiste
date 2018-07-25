(ns lupapalvelu.fixture.core
  (:require [taoensso.timbre :refer [debug info warnf error]]
            [lupapalvelu.mongo :as mongo]))

(defonce fixtures (atom {}))
(defonce exec-lock (Object.))

(defn exists? [name]
  (contains? @fixtures (keyword name)))

(defn apply-fixture [name]
  (if-let [fixture (@fixtures (keyword name))]
    (locking exec-lock
      (info "applying fixture:" name)
      ((:handler fixture)) [])
    (error "fixture not found:" name)))

(defmacro deffixture [name params & body]
  `(swap! fixtures assoc
     (keyword ~name)
     (merge ~params {:handler (fn [] ~@body)})))

;; Helpers

(defn insert-user [user]
  (try
    (mongo/insert :users user)
    (debug "Created user" (:email user))
    (catch com.mongodb.DuplicateKeyException e
      (debug "Duplicate user:" (:email user)))))

(defn insert-organization [organization]
  (try
    (mongo/insert :organizations organization)
    (debug "Created organization" (-> organization :name :fi) (:id organization))
    (catch com.mongodb.DuplicateKeyException e
      (debug "Duplicate organization:" (:id organization)))))

