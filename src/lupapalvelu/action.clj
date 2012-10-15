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
        (fn [{user-id :id}]
          (mongo/update-by-id mongo/users user-id 
            {$push {:tasks {:type        :invitation_planner
                            :application application-id
                            :created     (-> command :created)
                            :user        (security/summary (-> command :user))}}}))))))
  
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
             :streetAddress (:street data)
             :postalCode    (:zip data)
             :postalPlace   (:city data)
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

(defn add-application [command]
  (mongo/insert
    mongo/applications
    {:name (-> command :data :name)
     :position {:lon (-> command :data :lon)
                :lat (-> command :data :lat)}}))

(defn create-application [{user :user data :data created :created :as command}]
  (let [id  (mongo/create-id)
        applicant-document-id (mongo/create-id)
        operation-id (mongo/create-id)]
    (mongo/insert mongo/applications
                  {:id id
                   :created created
                   :modified created
                   :state :draft
                   :permitType :buildingPermit
                   :location {:lat (:lat data) :lon (:lon data)}
                   :title (:streetAddress data)
                   :streetAddress (:streetAddress data)
                   :postalCode (:postalCode data)
                   :postalPlace (:postalPlace data)
                   :authority (:postalPlace data)
                   :roles {:applicant (security/summary user)}
                   :documents {applicant-document-id {:documentType :hakijaTieto
                                                      :content {:nimi (str (:firstName user) " " (:lastName user))
                                                                :katuosoite (:streetAddress user)
                                                                :postinumero (:postalCode user)
                                                                :postitoimipaikka (:postalPlace user)
                                                                :puhelinnumero (:phone user)
                                                                :sahkopostiosoite (:email user)}}
                               operation-id {:documentType :toimenpide
                                             :type (:categories data)
                                             :content {:otsikko (str (:lastName user) ", " (:streetAddress data))}}}})
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
