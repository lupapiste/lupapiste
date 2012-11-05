(ns lupapalvelu.action
  (:use [monger.operators]
        [clojure.string :only [join]]
        [lupapalvelu.core]
        [lupapalvelu.env]
        [lupapalvelu.log]
        [lupapalvelu.domain]
        [clojure.set :only [difference]])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]))

(defquery "user" {:authenticated true} [{user :user}] (ok :user user))

(defcommand "create-id" {:authenticated true} [command] (ok :id (mongo/create-id)))

(defn- application-query-for [user]
  (case (keyword (:role user))
    :applicant {:auth.id (:id user)}
    :authority {:authority (:authority user)}
    :admin     {}
    (do
      (warn "invalid role to get applications")
      {:_id "-1"} ))) ; should not yield any results

(defn get-application-as [application-id user]
  (mongo/select mongo/applications {$and [{:_id application-id} (application-query-for user)]}))

(defquery "applications" {:authenticated true} [{user :user}]
  (ok :applications (mongo/select mongo/applications (application-query-for user))))

(defquery "application" {:authenticated true, :parameters [:id]} [{{id :id} :data user :user}]
  (ok :applications (get-application-as id user)))

(defcommand "open-application"
  {:parameters [:id]
   :roles      [:applicant]
   :states     [:draft]}
  [command]
  (with-application command
    (fn [{id :id}]
      (mongo/update-by-id mongo/applications id
        {$set {:modified (:created command)
               :state :open}}))))

(defquery "invites"
  {:authenticated true}
  [{{id :id} :user}]
  (let [filter     {:invites {$elemMatch {:user.id id}}}
        projection (assoc filter :_id 0)
        data       (mongo/select mongo/applications filter projection)
        invites    (flatten (map (comp :invites) data))]
    (ok :invites invites)))

(defcommand "invite"
  {:parameters [:id :email :title :text]
   :roles      [:applicant]}
  [{created :created
    user    :user
    {:keys [id email title text]} :data :as command}]
  (with-application command
    (fn [{application-id :id}]
      (with-user email ;; allows invites only existing users
        (fn [invited]
          (mongo/update mongo/applications
            {:_id application-id
             :invites {$not {$elemMatch {:user.username email}}}}
            {$push {:invites {:title       title
                              :application application-id
                              :text        text
                              :created     created
                              :inviter     (security/summary user)
                                :user  (security/summary invited)}
                      :auth (role invited :reader)}}))))))

(defcommand "approve-invite"
  {:parameters [:id]
   :roles      [:applicant]}
  [{user :user :as command}]
  (with-application command
    (fn [{application-id :id}]
      (mongo/update mongo/applications {:_id application-id :invites {$elemMatch {:user.id (:id user)}}}
        {$push {:auth         (role user :writer)}
         $pull {:invites      {:user.id (:id user)}}}))))

(defcommand "remove-invite"
  {:parameters [:id :email]
   :roles      [:applicant]}
  [{{:keys [id email]} :data :as command}]
  (with-application command
    (fn [{application-id :id}]
      (with-user email
        (fn [invited]
          (mongo/update-by-id mongo/applications application-id
            {$pull {:invites      {:user.username email}
                    :auth         {$and [{:username email}
                                         {:type {$ne :owner}}]}}}))))))

;; TODO: we need a) custom validator to tell weathet this is ok and/or b) return effected rows (0 if owner)
(defcommand "remove-auth"
  {:parameters [:id :email]
   :roles      [:applicant]}
  [{{:keys [id email]} :data :as command}]
  (with-application command
    (fn [{application-id :id}]
      (mongo/update-by-id mongo/applications application-id
        {$pull {:auth {$and [{:username email}
                             {:type {$ne :owner}}]}}}))))


(defcommand "rh1-demo"
  {:parameters [:id :data]
   :roles      [:applicant]
   :states     [:open :draft]}
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

(defcommand "register-user"
  {:parameters [:stamp :email :password :street :zip :city :phone]}
  [command]
  (let [password (-> command :data :password)
        data     (dissoc (:data command) :password)
        salt     (security/dispense-salt)
        user     (client/json-get (str "/vetuma/stamp/" (-> command :data :stamp)))] ;; loose coupling
    (info "Registering new user: %s - details from vetuma: %s" (str data) (str user))
    (mongo/insert mongo/users
      (assoc data
             :id (mongo/create-id)
             :username      (:email data)
             :role          :applicant
             :personId      (:userid user)
             :firstName     (:firstName user)
             :lastName      (:lastName user)
             :email         (:email data)
             :address      {
                            :street (:street data)
                            :zip    (:zip data)
                            :city   (:city data)
                            }
             :phone         (:phone data)
             :private       {:salt salt
                             :password (security/get-hash password salt)}))))

;;
;; Command functions
;;

(defn test-command [command])

(defn pong [command] (ok :text "ping"))

(defn add-comment [command]
  (with-application command
    (fn [application]
      (if (= "draft" (:state application))
        (executed "open-application" command))
      (let [user (:user command)]
        (mongo/update-by-id
          mongo/applications (:id application)
          {$set {:modified (:created command)}
           $push {:comments {:text    (-> command :data :text)
                             :created (-> command :created)
                             :user    (security/summary user)}}})))))

(defn assign-to-me [{{id :id} :data user :user :as command}]
  (with-application command
    (fn [application]
      (mongo/update-by-id
        mongo/applications (:id application)
        {$set {:roles.authority (security/summary user)}}))))

(defn approve-application [command]
  (with-application command
    (fn [application]
      (if (nil? (-> application :roles :authority))
        (assign-to-me command))
      (mongo/update
        mongo/applications {:_id (:id application) :state :submitted}
        {$set {:state :sent}}))))

(defn submit-application [command]
  (with-application command
    (fn [application]
      (mongo/update
        mongo/applications {:_id (:id application)}
          {$set {:state :submitted, :submitted (:created command) }}))))

(defn create-document [schema-name]
  (let [schema (mongo/by-id mongo/document-schemas schema-name)]
    (if (nil? schema) (throw (Exception. (str "Unknown schema ID: [" schema-name "]"))))
    {:id (mongo/create-id)
     :created (now)
     :schema schema
     :body {}}))

(defn to-map-by-id [docs] 
  (into {} (for [doc docs] [(:id doc) doc])))

(defcommand "create-application"
  {:parameters [:lat :lon :street :zip :city :schemas]
   :roles      [:applicant]}
  [command]
  (let [{:keys [user created data]} command
        id    (mongo/create-id)
        owner (role user :owner :type :owner)]
    (mongo/insert mongo/applications
      {:id id
       :created created
       :modified created
       :state :draft
       :permitType :buildingPermit
       :location {:lat (:lat data)
                  :lon (:lon data)}
       :address {:street (:street data)
                 :zip (:zip data)
                 :city (:city data)}
       :title (:street data)
       :authority (:city data)
       :roles {:applicant owner}
       :auth [owner]
       :documents (to-map-by-id (map create-document (:schemas data)))})
    (ok :id id)))
