(ns lupapalvelu.action
  (:use [monger.operators]
        [clojure.string :only [join]]
        [lupapalvelu.core]
        [lupapalvelu.log]
        [lupapalvelu.domain]
        [clojure.java.io :only [file]]
        [clojure.set :only [difference]])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]))

(defquery "ping" {} [q] (ok "pong"))

(defquery "user" {} [{user :user}]
  (if (not (nil? user))
    (ok :user user)
    (fail "user not logged in")))

(defquery "applications" {} [{user :user}]
  (case (keyword (:role user))
    :applicant (ok :applications (mongo/select mongo/applications {:roles.applicant.userId (:id user)}))
    :authority (ok :applications (mongo/select mongo/applications {:authority (:authority user)}))
    (fail "invalid role to get applications")))

(defquery "application" {:parameters [:id]} [{{id :id} :data user :user}]
  (case (keyword (:role user))
    :applicant (ok :applications (mongo/select mongo/applications {$and [{:_id id} {:roles.applicant.userId (:id user)}]}))
    :authority (ok :applications (mongo/select mongo/applications {$and [{:_id id} {:authority (:authority user)}]}))
    (fail :text "invalid role to get application")))

(defcommand "give-application-verdict"
  {:parameters [:id :ok :text]
   :roles      [:admin]
   :states     [:sent]}
  [command]
  (with-application command
    (fn [application]
      (mongo/update
        mongo/applications {:_id (:id application) :state :sent}
        {$set {:modified (:created command)
               :state :verdictGiven
               :verdict {:text (-> command :data :text)}}}))))

(defcommand "rh1-demo"
  {:parameters [:id :data]
   :roles      [:applicant]
   :states     [:open]}
  [command]
  (with-application command
    (fn [application]
      (mongo/update
        mongo/applications {:_id (:id application)}
        {$set {:modified (:created command)
               :rh1 (-> command :data :data)}}))))

(defcommand "create-apikey"
  {:parameters [:username :password]}
  [command]
  (if-let [user (security/login (-> command :data :username) (-> command :data :password))]
    (let [apikey (security/create-apikey)]
      (mongo/update 
        mongo/users
        {:username (:username user)}
        {$set {"private.apikey" apikey}})
      (ok :apikey apikey))
    (fail "unauthorized")))