(ns lupapalvelu.smoketest.user-smoke-tests
  (:require [lupapiste.mongocheck.core :refer [mongocheck]]
            [schema.core :as sc]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [sade.schemas :as ssc])
  (:import [schema.utils ErrorContainer]))

(def user-keys (map #(if (keyword? %) % (:k %)) (keys usr/User)))

(def coerce-user (ssc/json-coercer usr/User))

(mongocheck :users
  #(let [res (->> (mongo/with-id %)
                  (coerce-user))]
     (when (instance? ErrorContainer res)
       (assoc (select-keys % [:username]) :errors res)))
  user-keys)

(mongocheck :users
  (fn [user]
    (when (and (usr/applicant? user)
               (contains? user :orgAuthz))
      (format "User %s is applicant, but has orgAuthz: %s" (:username user) (:orgAuthz user))))
  :role :orgAuthz)

(mongocheck :users
            (fn [{pid :personId source :personIdSource :as user}]
              (cond
                (and pid (sc/check usr/PersonIdSource source))
                (format "User %s has invalid person id source" (:username user))

                (and (usr/erased? user) pid)
                (format "User %s is erased, but has personId" (:username user))

                (and (usr/applicant? user)
                     (not (usr/erased? user))
                     (not (usr/company-user? user))
                     (not (usr/company-admin? user))
                     (not (contains? #{"AD" "foreign-id"} source))
                     (not (usr/verified-person-id? user)))
                (format "Applicant user %s has unverified person id" (:username user))))
            :personId :personIdSource :username :company :role)

(mongocheck :users
  #(when (and (= "dummy" (:role %)) (not (:enabled %)) (-> % :private :password))
     (format "Dummy user %s has password" (:username %)))
  :role :private :username :enabled)

(mongocheck :users
  (fn [{:keys [company username]}]
    (when (ss/not-blank? (:id company))
      (when-not (pos? (mongo/count :companies {:_id (:id company)}))
        (format "User %s has orphan company id: %s" username (:id company)))))
  :username :company)

(mongocheck :companies
            (fn [{company-id :_id}]
              (when (pos? (mongo/count :users {:_id company-id}))
                (format "Same id in users and companies collection: %s" company-id)))
            :id)
