(ns lupapalvelu.smoketest.user-smoke-tests
  (:require [schema.core :as sc]
            [lupapalvelu.smoketest.core :refer [defmonster]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

(def users (delay (mongo/select :users)))


(defmonster valid-users
  (let [results (seq (remove nil? (map
                                    #(when-let [res (sc/check user/User %)]
                                       (assoc (select-keys % [:id :username]) :errors res))
                                    @users)))]
    (if results
      {:ok false :results results}
      {:ok true})))

(defmonster disabled-dummy-users-no-password
 (let [results (seq (remove nil? (map
                                   #(when (and (= "dummy" (:role %))
                                               (not (:enabled %))
                                               (-> % :private :password))
                                      %)
                                   @users)))]
   (if (seq results)
     {:ok false :results results}
     {:ok true})))