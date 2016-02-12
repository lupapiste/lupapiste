(ns lupapalvelu.smoketest.user-smoke-tests
  (:require [lupapiste.mongocheck.core :refer [mongocheck]]
            [schema.core :as sc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]))

(def user-keys (map #(if (keyword? %) % (:k %)) (keys usr/User)))

(mongocheck :users
  #(when-let [res (sc/check usr/User (mongo/with-id %))]
     (assoc (select-keys % [:username]) :errors res))
  user-keys)

(mongocheck :users
  #(when (and (= "dummy" (:role %)) (not (:enabled %)) (-> % :private :password))
     (format "Dummy user %s has password" (:username %)))
  :role :private)
