(ns lupapalvelu.guest-authority
  (:require [monger.operators :refer :all]
            [sade.core :refer [fail ok]]
            [lupapalvelu.user :as usr]  ;; usr works better with code completion.
            [lupapalvelu.organization :as org]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.action :as action]))

(defn resolve-guest-authority-candidate
  "Namesake query implementation."
  [admin email]
  (let [candidate          (-> email
                               usr/canonize-email
                               usr/get-user-by-email
                               usr/with-org-auth)
        admin-org-id       (usr/authority-admins-organization-id admin)
        reader-roles       (set (remove #{:guestAuthority} org/authority-roles))
        candidate-org-ids  (usr/organization-ids-by-roles candidate reader-roles)
        already-has-access (->> candidate-org-ids
                                (filter #(= admin-org-id %))
                                not-empty
                                boolean)]
    (assoc (select-keys candidate [:firstName :lastName])
           :hasAccess already-has-access)))

(defn organization-guest-authorities
  "Guest auhtorities for the given organisation."
  ([org-id]
   (->> org-id
        org/get-organization
        :guestAuthorities)))

(defn update-guest-authority-organization
  "Namesake command implementation."
  [admin email name role]
  (let [email (usr/canonize-email email)
        org-id (usr/authority-admins-organization-id admin)
        guests (->> org-id
                    organization-guest-authorities
                    (remove #(= email (:email %)))
                    (concat [{:email email
                              :name name
                              :role role}]))]
    (org/update-organization org-id {$set {:guestAuthorities guests}})))

(defn remove-guest-authority-organization
  "Namesake command implementation."
  [admin email]
  (let [email (usr/canonize-email email)
        org-id (usr/authority-admins-organization-id admin)]
    (org/update-organization org-id {$pull {:guestAuthorities {:email email}}})))

(defn no-duplicate-guests
  "Pre check for avoiding duplicate guests or unnecessary access.
  Note: only application-defined access is checked."
  [{{email :email} :data} application]
  (when email
   (let [guest (usr/get-user-by-email email)]
     (when (auth/user-authz? auth/all-authz-roles application guest)
       (fail :error.already-has-access)))))

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
  (let [email (usr/canonize-email email)
        existing-user (usr/get-user-by-email email)]
    (if (or (domain/invite application email)
            (auth/has-auth? application (:id existing-user)))
      (fail :error.already-has-access)
      (let [guest (usr/get-or-create-user-by-email email user)
            auth (usr/user-in-role guest (keyword role))]
        (action/update-application command
                                   {$push {:auth     (assoc auth :inviter (:id user))}
                                    $set  {:modified timestamp}})
        (notifications/notify! :invite (assoc command :recipients [guest]))
        (ok)))))

(defn- guest-authority-role-map
  "email role map"
  [org-id]
  (reduce (fn [acc {:keys [email role]}]
            (assoc acc email role)) {}
          (organization-guest-authorities org-id)))

(defn- usercatname [{:keys [firstName lastName]}]
  (str firstName " " lastName))

(defn- auth-info [ga-role-map {:keys [id role unsubscribed username inviter] :as auth}]
  (let [role (keyword role)
        {:keys [email] :as user} (usr/get-user-by-id id)
        inviter (usr/get-user-by-id inviter)]
    ;; We only add the role information if the guest has been added by
    ;; authority.
    {:authorityRole (when (= role :guestAuthority)
                      (get ga-role-map email))
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
  [{:keys [application user]}]
  (let [ga-roles (guest-authority-role-map (:organization application))]
    (map (partial auth-info ga-roles) (application-guest-auths application))))

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
  [{{:keys [username]} :data user :user} application]
  (when username
    (let [role (username-auth-role application username)]
      (when-not (or (= role :guest)
                    (some (partial = (:organization application))
                          (usr/organization-ids-by-roles user #{:authority})))
        (fail :error.unauthorized)))))


(defn toggle-guest-subscription
  "Namesake command implementation.
  Organization authorities can un/subscribe anybody.
  Owner/writer can un/subscribe guests.
  Guests can un/subscribe themselves."
  [{{:keys [username unsubscribe]} :data
    application :application
    user :user
    :as command}]
  (let [authority? (auth/has-organization-authz-roles? #{:authority} application user)
        writer?    (auth/user-authz? #{:owner :writer} application user)
        guest?     (-> (username-auth application username) :role (= "guest"))
        own?       (= (:username user) username)]
    (println "authority?" authority? "writer?" writer? "guest?" guest? "own?" own?)
    (if (or authority? (and writer? guest?) own?)
      (action/update-application command
                                 {:auth {$elemMatch {:username username}}}
                                 {$set {:auth.$.unsubscribed unsubscribe}})
      (fail :error.unauthorized))))

(defn delete-guest-application
  "Namesake command implementation."
  [{{:keys [username]} :data
    application :application
    :as command}]
  (action/update-application command
                             {$pull {:auth {:username username}}}))


(defn guest-authorities-application-organization
  "Namesake query implementation."
  [{:keys [application]}]
  (organization-guest-authorities (:organization application)))
