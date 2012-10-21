(ns lupapalvelu.action
  (:use [monger.operators]
        [clojure.string :only [join]]
        [lupapalvelu.core]
        [lupapalvelu.log]
        [lupapalvelu.domain]
        [clojure.java.io :only [file]]
        [clojure.set :only [difference]])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]))

(defquery "ping" {} [q] (ok :text "pong"))

(defquery "user" {:authenticated true} [{user :user}]
  (ok :user user))

(defcommand "create-id" {:authenticated true} [command]
  (ok :id (mongo/create-id)))

(defquery "applications" {} [{user :user}]
  (case (keyword (:role user))
    :applicant (ok :applications (mongo/select mongo/applications {:roles.applicant.id (:id user)}))
    :authority (ok :applications (mongo/select mongo/applications {:authority (:authority user)}))
    (fail "invalid role to get applications")))

(defquery "application" {:parameters [:id]} [{{id :id} :data user :user}]
  (case (keyword (:role user))
    :applicant (ok :applications (mongo/select mongo/applications {$and [{:_id id} {:roles.applicant.id (:id user)}]}))
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

(defcommand "ask-for-planner"
  {:parameters [:id :email]
   :roles      [:applicant]}
  [command]
  (with-application command
    (fn [{application-id :id}]
      (with-user (-> command :data :email)
        (fn [planner]
          (if (= (:role planner) "authority")
            (fail "can't ask authority to be a planner")
            ;; TODO: check for duplicates
            (do
              (mongo/update-by-id mongo/users (:id planner)
                {$push {:tasks {:type        :invitation_planner
                                :application application-id
                                :created     (-> command :created)
                                :user        (security/summary (-> command :user))}}})
              (mongo/update-by-id mongo/applications application-id 
                {$push {:planners {:state :pending
                                   :user  (security/summary planner)}}}))))))))

(defcommand "approve-as-planner"
  {:parameters [:id]
   :roles      [:applicant]}
  [{user :user :as command}]
  (with-application command
    (fn [{id :id}]
      (mongo/update-by-id 
        mongo/applications id
        {$set {"roles.planner" (security/summary user)}}))))

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
        {$set {"roles.authority" (security/summary user)}}))))

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

(defcommand "create-application"
  {:create-application {:parameters [:lat :lon :street :zip :city :categories]
                       :roles      [:applicant]}}
  [{user :user data :data created :created :as command}]
  (let [id  (mongo/create-id)]
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
       :roles {:applicant (security/summary user)}
       :documents {:hakija {:id (mongo/create-id)
                            :nimi (str (:firstName user) " " (:lastName user))
                            :address {:street (:street user)
                                      :zip (:zip user)
                                      :city (:city user)}
                            :puhelinnumero (:phone user)
                            :sahkopostiosoite (:email user)}
                   :toimenpide  {:id (mongo/create-id)
                                 :type (:categories data)
                                 :otsikko (str (:lastName user) ", " (:street data))}}})
    (ok :id id)))

(defn create-attachment [{{application-id :id} :data created :created}]
  (let [attachment-id (mongo/create-id)]
    (mongo/update-by-id
      mongo/applications
      application-id
      {$set {:modified created
             (str "attachments." attachment-id) {:id attachment-id
                                                 :name nil
                                                 :filename nil
                                                 :contentType nil
                                                 :size nil}}})
    (ok :applicationId application-id :attachmentId attachment-id)))

(defn set-attachment-name [{{:keys [id attachmentId name]} :data created :created}]
  (mongo/update-by-id
    mongo/applications id
    {$set {:modified created
           (str "attachments." attachmentId ".name") name}}))

(defn upload-attachment [{created :created {:keys [id attachmentId name filename tempfile content-type size]} :data}]
  (debug "Create GridFS file: %s %s %s %s %s %s %d" id attachmentId name filename tempfile content-type size)
  (mongo/upload id attachmentId filename content-type tempfile created)
  (mongo/update-by-id
    mongo/applications id
    {$set {:modified created
           (str "attachments." attachmentId) {:id attachmentId
                                              :name name
                                              ; File name will be presented in ASCII when the file is downloaded.
                                              ; Conversion could be done here as well, but we don't want to lose information.
                                              :filename filename
                                              :contentType content-type
                                              :size size}}})
  (.delete (file tempfile)))

(defn get-attachment [attachmentId]
  ;; FIXME access rights
  (mongo/download attachmentId))
