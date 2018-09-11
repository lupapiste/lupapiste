(ns lupapalvelu.guest
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.authorization-messages] ; notification definitions
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.user :as usr]  ;; usr works better with code completion.
            [lupapalvelu.user-utils :as uu]
            [lupapalvelu.company :as company]
            [monger.operators :refer :all]
            [sade.core :refer [fail ok]]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn resolve-guest-authority-candidate
  "Namesake query implementation."
  [admin email]
  (let [candidate          (-> email
                               ss/canonize-email
                               usr/get-user-by-email
                               usr/with-org-auth)
        admin-org-id       (usr/authority-admins-organization-id admin)
        candidate-org-ids (usr/organization-ids-by-roles candidate roles/org-roles-without-admin)
        already-has-access (->> candidate-org-ids
                                (filter #(= admin-org-id %))
                                not-empty
                                boolean)]
    (assoc (select-keys candidate [:firstName :lastName])
           :hasAccess already-has-access :financialAuthority (usr/financial-authority? candidate))))

(defn organization-guest-authorities
  "Guest authorities for the given organisation. Fetches organization from db."
  ([org-id]
   (->> org-id
        org/get-organization
        :guestAuthorities)))

(defn update-guest-authority-organization
  "Namesake command implementation."
  [org-admin email first-name last-name description]
  (let [email (ss/canonize-email email)
        org-id (usr/authority-admins-organization-id org-admin)
        guests (->> org-id
                    organization-guest-authorities
                    (remove #(= email (:email %)))
                    (concat [{:email email
                              :name (str first-name " " last-name)
                              :description description}]))]
    (org/update-organization org-id {$set {:guestAuthorities guests}})
    (when-not (usr/get-user-by-email email)
      (let [new-user-data {:firstName first-name
                           :lastName  last-name
                           :email     email
                           :role      "authority"}
            user (usr/create-new-user org-admin new-user-data)]
        (uu/notify-new-authority user org-admin org-id)))))

(defn remove-guest-authority-organization
  "Namesake command implementation."
  [admin email]
  (let [email (ss/canonize-email email)
        {guest-id :id} (usr/get-user-by-email email)
        org-id (usr/authority-admins-organization-id admin)
        match {:id guest-id :role :guestAuthority}]
    ;; Remove guestAuthority from organization
    (org/update-organization org-id {$pull {:guestAuthorities {:email email}}})
    ;; Remove guestAuthority from every application within organization
    ;; Optimization: if the user does not have id, she has not been
    ;; actually created yet.
    (when guest-id
      (mongo/update-by-query :applications
                             {:organization org-id :auth {$elemMatch match}}
                             {$pull {:auth match}}))))

(defn no-duplicate-guests
  "Pre check for avoiding duplicate guests or unnecessary access.
  Note: only application-defined access is checked."
  [{{email :email} :data application :application}]
  (when email
   (let [guest (usr/get-user-by-email email)]
     (when (auth/user-authz? roles/all-authz-roles application guest)
       (fail :error.already-has-access)))))

(defn known-guest-authority
  "Pre check to make sure that guest authority is defined for the
  organization."
  [{{:keys [email role]} :data app :application org :organization}]
  (when (and (= role "guestAuthority")
             (not-any? #(= email (:email %))
                       (or (and org (:guestAuthorities @org))
                           (organization-guest-authorities (:organization app)))))
    (fail :error.not-guest-authority)))

(defn valid-guest-role [{{role :role} :data}]
  (when-not (#{:guest :guestAuthority} (keyword role))
    (fail :error.illegal-role :parameters role)))

(defn invite-guest
  "Namesake command implementation. Invites and grants access to
  application guest. Sends invitation email and updates application
  auth."
  [{{:keys [email role]} :data
    user                         :user
    timestamp                    :created
    application                  :application
    :as                          command}]
  (let [email (ss/canonize-email email)]
    (if (->> (get-in (usr/get-user-by-email email) [:company :id])
             (company/company-denies-invitations? application))
      (fail :invite.company-denies-invitation)
      (if (or (util/find-by-key :email email (domain/invites application))
              (auth/has-auth? application (:id (usr/get-user-by-email email))))
        (fail :error.already-has-access)
        (let [guest (usr/get-or-create-user-by-email email user)
              auth (usr/user-in-role guest (keyword role))
              err (action/update-application command
                                             {$push {:auth (assoc auth :inviter (:id user))}
                                              $set  {:modified timestamp}})]
          (when-not err
            (notifications/notify! :guest-invite (assoc command :recipients [guest])))
          (or err (ok)))))))

(defn- guest-authority-description-map
  "email description map"
  [guest-authorities]
  (reduce (fn [acc {:keys [email description]}]
            (assoc acc email description)) {}
          guest-authorities))

(defn- usercatname [{:keys [firstName lastName]}]
  (ss/trim (str firstName " " lastName)))

(defn- auth-info [ga-description-map {:keys [id role unsubscribed username inviter]}]
  (let [role (keyword role)
        {:keys [email] :as user} (usr/get-user-by-id id)
        inviter (usr/get-user-by-id inviter)]
    ;; We only add the description information if the guest has been added by
    ;; authority.
    {:description (when (= role :guestAuthority)
                      (get ga-description-map email))
     :name (usercatname user)
     :username username
     :email email
     :role role
     :unsubscribed unsubscribed
     :inviter (usercatname inviter)}))

(defn- application-guest-auths [app]
  (auth/get-auths-by-roles app [:guest :guestAuthority]))

(defn application-guests
  "Namesake query implementation."
  [{:keys [application organization]}]
  (let [ga-descriptions (guest-authority-description-map (:guestAuthorities @organization))]
    (map (partial auth-info ga-descriptions) (application-guest-auths application))))

(defn- username-auth-role
  "Given username's role in the application auth, if any"
  [application username]
  (->> application
       application-guest-auths
       (some #(and (= username (:username %)) (:role %)))
       keyword))

(defn- username-auth [application username]
  (some #(when (= username (:username %)) %) (:auth application)))

(defn auth-modification-check
  "User can manipulate every guest but only organization authority can
  modify guestAuthorities."
  [{{:keys [username]} :data user :user application :application}]
  (when username
    (let [role (username-auth-role application username)]
      (when-not (or (= role :guest)
                    (some (partial = (:organization application))
                          (usr/organization-ids-by-roles user #{:authority})))
        (fail :error.unauthorized)))))


(defn toggle-guest-subscription
  "Namesake command implementation.
  Organization authorities can un/subscribe anybody.
  Writer can un/subscribe guests.
  Guests can un/subscribe themselves."
  [{{:keys [username unsubscribe]} :data
    application :application
    user :user
    :as command}]
  (let [authority? (auth/application-authority? application user)
        writer?    (auth/user-authz? #{:writer} application user)
        guest?     (-> (username-auth application username) :role (= "guest"))
        own?       (= (:username user) username)]
    (if (or authority? (and writer? guest?) own?)
      (do (action/update-application command
                                  {:auth {$elemMatch {:username username}}}
                                  {$set {:auth.$.unsubscribed unsubscribe}})
          (ok))
      (fail :error.unauthorized))))

(defn delete-guest-application
  "Namesake command implementation."
  [{{:keys [username]} :data :as command}]
  (action/update-application command
                             {$pull {:auth {:username username
                                            :role #"guest(Authority)?"}}})
  (ok))


(defn guest-authorities-application-organization
  "Namesake query implementation."
  [{:keys [organization]}]
  (get @organization :guestAuthorities))
