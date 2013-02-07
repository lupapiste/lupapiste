(ns lupapalvelu.action
  (:use [monger.operators]
        [lupapalvelu.log]
        [lupapalvelu.core])
  (:require [sade.security :as sadesecurity]
            [sade.client :as sadeclient]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]
            [lupapalvelu.email :as email]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(defquery "user" {:authenticated true} [{user :user}] (ok :user user))

(defcommand "create-id" {:authenticated true} [_] (ok :id (mongo/create-id)))

(defn application-query-for [user]
  (case (keyword (:role user))
    :applicant {:auth.id (:id user)
                :state {$ne "canceled"}}
    :authority {:municipality (:municipality user)
                $and [{:state {$ne "draft"}} {:state {$ne "canceled"}}]}
    :admin     {:state {$ne "canceled"}}
    (do
      (warn "invalid role to get applications")
      {:_id "-1"} ))) ; should not yield any results

(defn get-application-as [application-id user]
  (mongo/select-one :applications {$and [{:_id application-id} (application-query-for user)]}))

(defquery "invites"
  {:authenticated true}
  [{{:keys [id]} :user}]
  (let [filter     {:invites {$elemMatch {:user.id id}}}
        projection (assoc filter :_id 0)
        data       (mongo/select :applications filter projection)
        invites    (flatten (map :invites data))]
    (ok :invites invites)))

(defn invite-body [user id host]
  (format
    (str
      "Tervehdys,\n\n%s %s lis\u00E4si teid\u00E4t suunnittelijaksi lupahakemukselleen.\n\n"
      "Hyv\u00E4ksy\u00E4ksesi rooli ja n\u00E4hd\u00E4ksesi hakemuksen tiedot avaa linkki %s/fi/applicant#!/application/%s\n\n"
      "Yst\u00E4v\u00E4llisin terveisin,\n\n"
      "Lupapiste.fi")
    (:firstName user)
    (:lastName user)
    host
    id))

(defcommand "invite"
  {:parameters [:id :email :title :text :document]
   :roles      [:applicant]}
  [{created :created
    user    :user
    {:keys [id email title text document]} :data {:keys [host]} :web :as command}]
  (with-application command
    (fn [{application-id :id :as application}]
      (if (domain/invited? application email)
        (fail :already-invited)
        (let [invited (security/get-or-create-user-by-email email)]
          (if (domain/has-auth? application (:id invited))
            (fail :already-has-auth)
            (do
              (mongo/update
                :applications
                {:_id application-id
                 :invites {$not {$elemMatch {:user.username email}}}}
                {$push {:invites {:title       title
                                  :application application-id
                                  :text        text
                                  :document    document
                                  :created     created
                                  :email       email
                                  :user        (security/summary invited)
                                  :inviter     (security/summary user)}
                        :auth (role invited :writer)}})
              (future
                (info "sending email to %s" email)
                (if (email/send-email email (:title application) (invite-body user application-id host))
                  (info "email was sent successfully")
                  (error "email could not be delivered.")))
              nil)))))))

(defcommand "approve-invite"
  {:parameters [:id]
   :roles      [:applicant]}
  [{user :user :as command}]
  (with-application command
    (fn [{application-id :id invites :invites}]
      (when-let [my-invite (first (filter #(= (-> % :user :id) (:id user)) invites))]
        (executed "set-user-to-document" (assoc-in command [:data :name] (:document my-invite)))
        (mongo/update :applications
                      {:_id application-id :invites {$elemMatch {:user.id (:id user)}}}
                      ;; TODO: should refresh the data - for new invites to get full names
                      {$pull {:invites      {:user.id (:id user)}}})))))

(defcommand "remove-invite"
  {:parameters [:id :email]
   :roles      [:applicant]}
  [{{:keys [id email]} :data :as command}]
  (with-application command
    (fn [{application-id :id}]
      (with-user email
        (fn [_]
          (mongo/update-by-id :applications application-id
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
      (mongo/update-by-id :applications application-id
        {$pull {:auth {$and [{:username email}
                             {:type {$ne :owner}}]}}}))))

(defcommand "create-apikey"
  {:parameters [:username :password]}
  [command]
  (if-let [user (security/login (-> command :data :username) (-> command :data :password))]
    (let [apikey (security/create-apikey)]
      (mongo/update
        :users
        {:username (:username user)}
        {$set {"private.apikey" apikey}})
      (ok :apikey apikey))
    (fail :error.unauthorized)))

(defcommand "register-user"
  {:parameters [:stamp :email :password :street :zip :city :phone]}
  [{data :data}]
  (let [vetuma   (client/json-get (str "/vetuma/stamp/" (:stamp data)))
        userdata (merge data vetuma)]
    (println userdata)
    (info "Registering new user: %s - details from vetuma: %s" (dissoc data :password) vetuma)
    (if-let [user (security/create-user userdata)]
      (do
        (future
          (let [pimped_user (merge user {:_id (:id user)})] ;; FIXME
            (sadesecurity/send-activation-mail-for {:user pimped_user
                                                    :from "lupapiste@solita.fi"
                                                    :service-name "Lupapiste"
                                                    :host-url (sadeclient/uri)})))
        (ok :id (:_id user)))
      (fail :error.create_user))))

(defcommand "add-comment"
  {:parameters [:id :text :target]
   :roles      [:applicant :authority]}
  [{{:keys [text target]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (if (= "draft" (:state application))
        (executed "open-application" command))
      (mongo/update-by-id
        :applications
        (:id application)
        {$set {:modified (:created command)}
         $push {:comments {:text    text
                           :target  target
                           :created (:created command)
                           :user    (security/summary user)}}}))))

(defcommand "assign-to-me"
  {:parameters [:id]
   :roles      [:authority]}
  [{{id :id} :data user :user :as command}]
  (with-application command
    (fn [application]
      (mongo/update-by-id
        :applications (:id application)
        {$set {:roles.authority (security/summary user)}}))))

(defn user2paasuunnittelija [user]
  {:henkilotiedot {:etunimi       (:firstName user)
                   :sukunimi      (:lastName user)}
   :yhteystiedot {:email          (:email user)
                  :puhelin        (:phone user)}
   :osoite {:katu                 (:street user)
            :postinumero          (:zip user)
            :postitoimipaikannimi (:city user)}})


(defcommand "set-user-to-document"
  {:parameters [:id :name]
   :authenticated true}
  [{{:keys [name]} :data user :user :as command}]
  (println "!!!" command)
  (with-application command
    (fn [application]
      (let [document       (domain/get-document-by-name application name)
            schema-name    (get-in document [:schema :info :name])
            schema         (get schemas/schemas schema-name)]
        (if (nil? document)
          (fail :error.document-not-found)
          (do
            (info "merging user %s with best effort into document %s" user name)
            (mongo/update
              :applications
              {:_id (:id application)
               :documents {$elemMatch {:schema.info.name name}}}
              {$set {:documents.$.body (user2paasuunnittelija user)
                     :modified (:created command)}})))))))
