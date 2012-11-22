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
            [lupapalvelu.client :as client]
            [lupapalvelu.tepa :as tepa]
            [lupapalvelu.email :as email]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]))

(defquery "user" {:authenticated true} [{user :user}] (ok :user user))

(defcommand "create-id" {:authenticated true} [command] (ok :id (mongo/create-id)))

(defn- application-query-for [user]
  (case (keyword (:role user))
    :applicant {:auth.id (:id user)}
    :authority {:authority (:authority user)
                :state {$ne "draft"}}
    :admin     {}
    (do
      (warn "invalid role to get applications")
      {:_id "-1"} ))) ; should not yield any results

(defquery "applications" {:authenticated true} [{user :user}]
  (ok :applications (mongo/select mongo/applications (application-query-for user))))

(defn get-application-as [application-id user]
  (mongo/select-one mongo/applications {$and [{:_id application-id} (application-query-for user)]}))

(defquery "application" {:authenticated true, :parameters [:id]} [{{id :id} :data user :user}]
  (if-let [app (get-application-as id user)]
    (ok :application app)
    (fail :error.not-found)))

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
  [{{:keys [id]} :user}]
  (let [filter     {:invites {$elemMatch {:user.id id}}}
        projection (assoc filter :_id 0)
        data       (mongo/select mongo/applications filter projection)
        invites    (flatten (map (comp :invites) data))]
    (ok :invites invites)))

(defn invite-body [user id host]
  (format
    (str
      "Tervehdys,\n\n%s %s lis\u00E4si teid\u00E4t suunnittelijaksi lupahakemukselleen.\n\n"
      "Hyv\u00E4ksy\u00E4ksesi rooli ja n\u00E4hd\u00E4ksesi hakemuksen tiedot avaa linkki %s/applicant#!/application/%s\n\n"
      "Yst\u00E4v\u00E4llisin terveisin,\n\n"
      "Lupapiste.fi")
    (:firstName user)
    (:lastName user)
    host
    id))

(defcommand "invite"
  {:parameters [:id :email :title :text]
   :roles      [:applicant]}
  [{created :created
    user    :user
    {:keys [id email title text]} :data {:keys [host]} :web :as command}]
  (with-application command
    (fn [{application-id :id :as application}]
      (let [invited (security/get-or-create-user-by-email email)]
        (mongo/update mongo/applications
          {:_id application-id
           :invites {$not {$elemMatch {:user.username email}}}}
          {$push {:invites {:title       title
                            :application application-id
                            :text        text
                            :created     created
                            :email       email
                            :user        (security/summary invited)
                            :inviter     (security/summary user)}
                  :auth (role invited :reader)}})
        (future
          (info "sending email to %s" email)
          (if (email/send-email email (:title application) (invite-body user application-id host))
            (info "email was sent successfully")
            (error "email could not be delivered.")))
        nil))))

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
    (fail :error.unauthorized)))

(defcommand "register-user"
  {:parameters [:stamp :email :password :street :zip :city :phone]}
  [{data :data}]
  (let [from-vetuma (client/json-get (str "/vetuma/stamp/" (:stamp data)))]
    (info "Registering new user: %s - details from vetuma: %s" (dissoc data :password) from-vetuma)
    (security/create-user (merge data from-vetuma))
    nil))

(defcommand "add-comment"
  {:parameters [:id :text :target :category]
   :roles      [:applicant :authority]}
  [{{:keys [text target category]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (when (= "draft" (:state application))
        (executed "open-application" command))
      (mongo/update-by-id
        mongo/applications
        (:id application)
        {$set {:modified (:created command)}
         $push {:comments {:text    text
                           :target  target
                           :category category
                           :created (-> command :created)
                           :user    (security/summary user)}}}))))

(defcommand "assign-to-me"
  {:parameters [:id]
   :roles      [:authority]}
  [{{id :id} :data user :user :as command}]
  (with-application command
    (fn [application]
      (mongo/update-by-id
        mongo/applications (:id application)
        {$set {:roles.authority (security/summary user)}}))))

(defcommand "approve-application"
  {:parameters [:id]
   :roles      [:authority]
   :authority  true
   :states     [:submitted]}
  [command]
  (with-application command
    (fn [application]
      (if (nil? (-> application :roles :authority))
        (executed "assign-to-me" command))
      (mongo/update
        mongo/applications {:_id (:id application) :state :submitted}
        {$set {:state :sent}}))))

(defcommand "submit-application"
  {:parameters [:id]
   :roles      [:applicant]
   :roles-in   [:applicant]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [application]
      (mongo/update
        mongo/applications {:_id (:id application)}
          {$set {:state :submitted
                 :submitted (:created command) }}))))

(defn create-document [schema-name]
  (let [schema (get schemas/schemas schema-name)]
    (if (nil? schema) (throw (Exception. (str "Unknown schema: [" schema-name "]"))))
    {:id (mongo/create-id)
     :created (now)
     :schema schema
     :body {}}))

(defcommand "create-application"
  {:parameters [:x :y :street :zip :city :schemas]
   :roles      [:applicant]}
  [command]
  (let [{:keys [user created data]} command
        id        (mongo/create-id)
        owner     (role user :owner :type :owner)
        documents (map create-document (:schemas data))
        municipality (:result (executed "municipality-by-location" command))]
    (mongo/insert mongo/applications
      {:id id
       :created created
       :modified created
       :state :draft
       :permitType :buildingPermit
       :municipality municipality
       :location {:x (:x data)
                  :y (:y data)}
       :address {:street (:street data)
                 :zip (:zip data)
                 :city (:city data)}
       :title (:street data)
       :authority (:city data)
       :roles {:applicant owner}
       :auth [owner]
       :documents documents})
    (ok :id id)))

; TODO: by-id or by-name (or both)
#_(defcommand "user-to-document"
  {:parameters [:id :document-id]
   :authenticated true}
  [{{:keys [document-id]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (let [document       (get-document application document-id)
            schema-name    (get-in document [:schema :info :name])
            schema         (get schemas/schemas schema-name)
            transformation {"etunimi" (:firstName user)}]
        (info "merging user %s with best effort into document %s" user document-id)
        (mongo/update
          mongo/applications
          {:_id (:id application)
           :documents {$elemMatch {:id document-id}}}
          {$set {:documents.$.body.etunimi  (:firstName user)
                 :documents.$.body.sukunimi (:lastName user)
                 :documents.$.body.email    (:email user)
                 :documents.$.body.puhelin  (:phone user)}})))))

(defcommand "user-to-document"
  {:parameters [:id :name]
   :authenticated true}
  [{{:keys [name]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (let [document       (get-document-by-name application name)
            schema-name    (get-in document [:schema :info :name])
            schema         (get schemas/schemas schema-name)]
        (if (nil? document)
          (fail :error.document-not-found)
          (do
            (info "merging user %s with best effort into document %s" user name)
            (mongo/update
              mongo/applications
              {:_id (:id application)
               :documents {$elemMatch {:schema.info.name name}}}
              {$set {:documents.$.body.etunimi  (:firstName user)
                     :documents.$.body.sukunimi (:lastName user)
                     :documents.$.body.email    (:email user)
                     :documents.$.body.puhelin  (:phone user)
                     :modified (:created command)}})))))))


#_ (let [result (model/apply-updates {} schema transformation)]
     {:user     user
      :trans    transformation
      :result   result
      :document document})
