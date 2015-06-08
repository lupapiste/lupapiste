(ns lupapalvelu.user-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [swiss.arrows :refer [-<>]]
            [clojure.set :as set]
            [noir.request :as request]
            [noir.response :as resp]
            [noir.core :refer [defpage]]
            [slingshot.slingshot :refer [throw+]]
            [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.util :refer [future*]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer :all]
            [sade.session :as ssess]
            [lupapalvelu.action :refer [defquery defcommand defraw email-validator] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.activation :as activation]
            [lupapalvelu.security :as security]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.user :as user]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.idf.idf-client :as idf]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.attachment :as attachment]))

;;
;; ==============================================================================
;; Getting user and users:
;; ==============================================================================
;;

(defquery user
  {:user-roles action/all-authenticated-user-roles}
  [{user :user}]
  (if (user/virtual-user? user)
    (ok :user user)
    (if-let [full-user (user/get-user-by-id (:id user))]
      (ok :user (dissoc full-user :private :personId))
     (fail))))

(defquery users
  {:user-roles #{:admin}}
  [{{:keys [role]} :user data :data}]
  (let [base-query (-> data
                     (set/rename-keys {:userId :id})
                     (select-keys [:id :role :email :username :firstName :lastName :enabled :allowDirectMarketing]))
        org-ids (cond
                  (:organization data) [(:organization data)]
                  (:organizations data) (:organizations data))
        query (if (seq org-ids)
                {$and [base-query, (user/org-authz-match org-ids)]}
                base-query)
        users (user/find-users query)]
    (ok :users (map (comp user/with-org-auth user/non-private) users))))

(defquery users-in-same-organizations
  {:user-roles #{:authority}}
  [{user :user}]
  (if-let [organization-ids (seq (user/organization-ids user))]
    (let [query {$and [{:role "authority"}, (user/org-authz-match organization-ids)]}
          users (user/find-users query)]
      (ok :users (map user/summary users)))
    (ok :users [])))

(env/in-dev
  (defquery user-by-email
    {:parameters [email]
     :user-roles #{:admin}}
    [_]
    (ok :user (user/get-user-by-email email))))

(defcommand users-for-datatables
  {:user-roles #{:admin :authorityAdmin}}
  [{caller :user {params :params} :data}]
  (ok :data (user/users-for-datatables caller params)))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

(defn- reset-link [lang token]
  (str (env/value :host) "/app/" lang "/welcome#!/setpw/" token))

;; Emails
(def- base-email-conf
  {:model-fn      (fn [{{token :token} :data} conf recipient]
                {:link-fi (reset-link "fi" token), :link-sv (reset-link "sv" token)})})

(notifications/defemail :invite-authority
  (assoc base-email-conf :subject-key "authority-invite.title" :recipients-fn notifications/from-user))

(notifications/defemail :reset-password
  (assoc base-email-conf :subject-key "reset.email.title" :recipients-fn notifications/from-data))

(defn- notify-new-authority [new-user created-by]
  (let [token (token/make-token :authority-invitation created-by (merge new-user {:caller-email (:email created-by)}))]
    (notifications/notify! :invite-authority {:user new-user, :data {:token token}})))

(defn- validate-create-new-user! [caller user-data]
  (when-let [missing (util/missing-keys user-data [:email :role])]
    (fail! :error.missing-parameters :parameters missing))

  (let [password         (:password user-data)
        user-role        (keyword (:role user-data))
        caller-role      (keyword (:role caller))
        org-authz        (:orgAuthz user-data)
        organization-id  (when (map? org-authz)
                           (name (first (keys org-authz))))
        admin?           (= caller-role :admin)
        authorityAdmin?  (= caller-role :authorityAdmin)]

    (when (and org-authz (not (every? coll? (vals org-authz))))
      (fail! :error.invalid-role :desc "new user has unsupported organization roles"))

    (when-not (#{:authority :authorityAdmin :applicant :dummy} user-role)
      (fail! :error.invalid-role :desc "new user has unsupported role" :user-role user-role))

    (when (and (= user-role :applicant) caller)
      (fail! :error.unauthorized :desc "applicants are born via registration"))

    (when (and (= user-role :authorityAdmin) (not admin?))
      (fail! :error.unauthorized :desc "only admin can create authorityAdmin users"))

    (when (and (= user-role :authority) (not authorityAdmin?))
      (fail! :error.unauthorized :desc "only authorityAdmin can create authority users" :user-role user-role :caller-role caller-role))

    (when (and (= user-role :authorityAdmin) (not organization-id))
      (fail! :error.missing-parameters :desc "new authorityAdmin user must have organization" :parameters [:organization]))

    (when (and (= user-role :authority) (and organization-id (not ((user/organization-ids caller) organization-id))))
      (fail! :error.unauthorized :desc "authorityAdmin can create users into his/her own organization only, or statement givers without any organization at all"))

    (when (and (= user-role :dummy) organization-id)
      (fail! :error.unauthorized :desc "dummy user may not have an organization" :missing :organization))

    (when (and password (not (security/valid-password? password)))
      (fail! :password-too-short :desc "password specified, but it's not valid"))

    (when (and organization-id (not (organization/get-organization organization-id)))
      (fail! :error.organization-not-found))

    (when (and (:apikey user-data) (not admin?))
      (fail! :error.unauthorized :desc "only admin can create create users with apikey")))

  true)

(defn- create-new-user-entity [{:keys [enabled password] :as user-data}]
  (let [email (user/canonize-email (:email user-data))]
    (-<> user-data
      (select-keys [:email :username :role :firstName :lastName :personId
                    :phone :city :street :zip :enabled :orgAuthz
                    :allowDirectMarketing :architect :company])
      (merge {:firstName "" :lastName "" :username email} <>)
      (assoc
        :email email
        :enabled (= "true" (str enabled))
        :private (if password {:password (security/get-hash password)} {})))))

;;
;; TODO: Ylimaaraisen "send-email"-parametrin sijaan siirra mailin lahetys pois
;;       activation/send-activation-mail-for -funktiosta kutsuviin funktioihin.
;;
(defn create-new-user
  "Insert new user to database, returns new user data without private information. If user
   exists and has role \"dummy\", overwrites users information. If users exists with any other
   role, throws exception."
  [caller user-data & {:keys [send-email] :or {send-email true}}]
  (validate-create-new-user! caller user-data)
  (let [user-entry  (create-new-user-entity user-data)
        old-user    (user/get-user-by-email (:email user-entry))
        new-user    (if old-user
                      (assoc user-entry :id (:id old-user))
                      (assoc user-entry :id (mongo/create-id)))
        email       (:email new-user)
        {old-id :id old-role :role}  old-user
        notification {:titleI18nkey "user.notification.firstLogin.title"
                      :messageI18nkey "user.notification.firstLogin.message"}
        new-user   (if (user/applicant? user-data)
                     (assoc new-user :notification notification)
                     new-user)]
    (try
      (condp = old-role
        nil     (do
                  (info "creating new user" (dissoc new-user :private))
                  (mongo/insert :users new-user))
        "dummy" (do
                  (info "rewriting over dummy user:" old-id (dissoc new-user :private :id))
                  (mongo/update-by-id :users old-id (dissoc new-user :id)))
        ; LUPA-1146
        "applicant" (if (and (= (:personId old-user) (:personId new-user)) (not (:enabled old-user)))
                      (do
                        (info "rewriting over inactive applicant user:" old-id (dissoc new-user :private :id))
                        (mongo/update-by-id :users old-id (dissoc new-user :id)))
                      (fail! :error.duplicate-email))
        (fail! :error.duplicate-email))

      (when (and send-email (not= "dummy" (name (:role new-user))))
        (activation/send-activation-mail-for new-user))

      (user/get-user-by-email email)

      (catch com.mongodb.MongoException$DuplicateKey e
        (if-let [field (second (re-find #"E11000 duplicate key error index: lupapiste\.users\.\$([^\s._]+)" (.getMessage e)))]
          (do
            (warnf "Duplicate key detected when inserting new user: field=%s" field)
            (fail! :duplicate-key :field field))
          (do
            (warn e "Inserting new user failed")
            (fail! :cant-insert)))))))

(defn- create-authority-user-with-organization [caller new-organization email firstName lastName roles]
  (let [org-authz {new-organization (into #{} roles)}
        new-user (create-new-user
                   caller
                   {:email email :orgAuthz org-authz :role :authority :enabled true
                    :firstName firstName :lastName lastName}
                   :send-email false)]
    (infof "invitation for new authority user: email=%s, organization=%s" email new-organization)
    (notify-new-authority new-user caller)
    (ok :operation "invited")))

(defcommand create-user
  {:parameters [:email role]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :user-roles #{:admin :authorityAdmin}}
  [{user-data :data caller :user}]
  (let [updated-user-data (if (:organization user-data) (assoc user-data :orgAuthz {(:organization user-data) [(:role user-data)]}) user-data)
        user (create-new-user caller updated-user-data :send-email false)]
    (infof "Added a new user: role=%s, email=%s, orgAuthz=%s" (:role user) (:email user) (:orgAuthz user))
    (if (user/authority? user)
      (do
        (notify-new-authority user caller)
        (ok :id (:id user) :user user))
      (let [token (token/make-token :password-reset caller {:email (:email user)} :ttl ttl/create-user-token-ttl)]
        (ok :id (:id user)
          :user user
          :linkFi (str (env/value :host) "/app/fi/welcome#!/setpw/" token)
          :linkSv (str (env/value :host) "/app/sv/welcome#!/setpw/" token))))))

(defn get-or-create-user-by-email [email current-user]
  (let [email (user/canonize-email email)]
    (or
      (user/get-user-by-email email)
      (create-new-user current-user {:email email :role "dummy"}))))

;;
;; ==============================================================================
;; Updating user data:
;; ==============================================================================
;;

;;
;; General changes:
;;

(def- user-data-editable-fields [:firstName :lastName :street :city :zip :phone
                                 :architect :degree :graduatingYear :fise
                                 :companyName :companyId :allowDirectMarketing])

(defn- validate-update-user! [caller user-data]
  (let [admin?          (= (-> caller :role keyword) :admin)
        caller-email    (:email caller)
        user-email      (:email user-data)]

    (if admin?
      (when (= user-email caller-email)    (fail! :error.unauthorized :desc "admin may not change his/her own data"))
      (when (not= user-email caller-email) (fail! :error.unauthorized :desc "can't edit others data")))

    true))

;; Define schema for update data
(def ^:private UserUpdate (dissoc user/User :id :role :email :username :enabled))

(defn- validate-updatable-user [{user-data :data}]
  (when (sc/check UserUpdate user-data)
    (fail :error.invalid-user-data)))

(defcommand update-user
  {:user-roles #{:applicant :authority :authorityAdmin :admin}
   :input-validators [validate-updatable-user]}
  [{caller :user user-data :data :as command}]
  (let [email     (user/canonize-email (or (:email user-data) (:email caller)))
        user-data (assoc user-data :email email)]
    (validate-update-user! caller user-data)
    (if (= 1 (mongo/update-n :users {:email email} {$set (select-keys user-data user-data-editable-fields)}))
      (if (= email (:email caller))
        (ssess/merge-to-session command (ok) {:user (user/session-summary (user/get-user-by-id (:id caller)))})
        (ok))
      (fail :not-found :email email))))

(defcommand applicant-to-authority
  {:parameters [email]
   :user-roles #{:admin}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :description "Changes applicant or dummy account into authority"}
  [_]
  (let [user (user/get-user-by-email email)]
    (if (#{"dummy" "applicant"} (:role user))
      (mongo/update :users {:email email} {$set {:role "authority"}})
      (fail :error.user-not-found))))

;;
;; Change organization data:
;;

(defn- valid-organization-operation? [{data :data}]
  (when-not (#{"add" "remove"} (:operation data))
    (fail :bad-request :desc (str "illegal organization operation: '" (:operation data) "'"))))

(defcommand update-user-organization
  {:parameters       [email firstName lastName roles]
   :input-validators [(partial action/non-blank-parameters [:email :firstName :lastName])
                      (partial action/vector-parameters-with-at-least-n-non-blank-items 1 [:roles])
                      action/email-validator]
   :user-roles #{:authorityAdmin}}
  [{caller :user}]
  (let [new-organization (user/authority-admins-organization-id caller)
        has-archive?     (:permanent-archive-enabled (organization/get-organization new-organization))
        actual-roles     (if (and (env/feature? :tiedonohjaus) has-archive?) roles ["authority"])
        email            (user/canonize-email email)
        query            {:email email, :role "authority"}
        update-count     (mongo/update-n :users query {$set {(str "orgAuthz." new-organization) actual-roles}})]
    (debug "update user" email)
    (if (pos? update-count)
      (ok :operation "add")
      (if-not (user/get-user-by-email email)
        (create-authority-user-with-organization caller new-organization email firstName lastName actual-roles)
        (fail :error.user-not-found)))))

(defcommand remove-user-organization
  {:parameters       [email]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :user-roles       #{:authorityAdmin}}
  [{caller :user}]
  (let [email            (user/canonize-email email)
        organization     (user/authority-admins-organization-id caller)
        query            {:email email, :role "authority"}
        update-count     (mongo/update-n :users query {$unset {(str "orgAuthz." organization) ""}})]
    (if (pos? update-count)
      (ok :operation "remove")
      (fail :error.user-not-found))))

(defn allowed-roles [allowed-roles command]
  (let [roles (get-in command [:data :roles])
        filtered-roles (->> roles (map keyword) (filter (into #{} allowed-roles)))
        ok (= (count roles)
              (count filtered-roles))]
    (when-not ok
      (fail :invalid.roles))))

(defcommand update-user-roles
  {:parameters [email roles organization]
   :input-validators [(partial action/non-blank-parameters [:email :organization])
                      (partial action/vector-parameters-with-at-least-n-non-blank-items 1 [:roles])
                      action/email-validator
                      (partial allowed-roles action/authority-roles)]
   :user-roles #{:authorityAdmin}}
  (let [update-count (mongo/update-n :users {:email (user/canonize-email email)} {$set {(str "orgAuthz." organization) roles}})]
    (if (= 1 update-count)
      (ok)
      (fail :error.user-not-found))))

(defmethod token/handle-token :authority-invitation [{{:keys [email organization caller-email]} :data} {password :password}]
  (infof "invitation for new authority: email=%s: processing..." email)
  (let [caller (user/get-user-by-email caller-email)]
    (when-not caller (fail! :not-found :desc (format "can't process invitation token for email %s, authority admin (%s) no longer exists" email caller-email)))
    (user/change-password email password)
    (infof "invitation was accepted: email=%s, organization=%s" email organization)
    (ok)))

;;
;; Change and reset password:
;;

(defcommand change-passwd
  {:parameters [oldPassword newPassword]
   :user-roles #{:applicant :authority :authorityAdmin :admin}}
  [{{user-id :id :as user} :user}]
  (let [user-data (mongo/by-id :users user-id)]
    (if (security/check-password oldPassword (-> user-data :private :password))
      (do
        (debug "Password change: user-id:" user-id)
        (user/change-password (:email user) newPassword)
        (ok))
      (do
        (warn "Password change: failed: old password does not match, user-id:" user-id)
        ; Throttle giving information about incorrect password
        (Thread/sleep 2000)
        (fail :mypage.old-password-does-not-match)))))

(defn reset-password [{:keys [email role] :as user}]
  (assert (and email role (not= "dummy" role)) "Can't reset dummy user's password")

  (let [token (token/make-token :password-reset nil {:email email} :ttl ttl/reset-password-token-ttl)]
    (infof "password reset request: email=%s, token=%s" email token)
    (notifications/notify! :reset-password {:data {:email email :token token}})
    token))

(defcommand reset-password
  {:parameters    [email]
   :user-roles #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :notified      true}
  [_]
  (let [user (user/get-user-by-email email) ]
      (if (and user (not= "dummy" (:role user)))
      (do
        (reset-password user)
         (ok))
       (do
         (warnf "password reset request: unknown email: email=%s" email)
        (fail :error.email-not-found)))))

(defcommand admin-reset-password
  {:parameters    [email]
   :user-roles #{:admin}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :notified      true}
  [_]
  (let [user (user/get-user-by-email email) ]
    (if (and user (not= "dummy" (:role user)))
      (ok :link (reset-link "fi" (reset-password user)))
      (fail :error.email-not-found))))

(defmethod token/handle-token :password-reset [{data :data} {password :password}]
  (let [email (user/canonize-email (:email data))]
    (user/change-password email password)
    (infof "password reset performed: email=%s" email)
    (ok)))

;;
;; enable/disable:
;;

(defcommand set-user-enabled
  {:parameters    [email enabled]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :user-roles #{:admin}}
  [_]
  (let [email (user/canonize-email email)
       enabled (contains? #{true "true"} enabled)]
   (infof "%s user: email=%s" (if enabled "enable" "disable") email)
   (if (= 1 (mongo/update-n :users {:email email} {$set {:enabled enabled}}))
     (ok)
     (fail :not-found))))

;;
;; ==============================================================================
;; Login:
;; ==============================================================================
;;


(defcommand login
  {:parameters [username password]
   :user-roles #{:anonymous}}
  [command]
  (if (user/throttle-login? username)
    (do
      (info "login throttled, username:" username)
      (fail :error.login-trottle))
    (if-let [user (user/get-user-with-password username password)]
      (do
        (info "login successful, username:" username)
        (user/clear-logins username)
        (if-let [application-page (user/applicationpage-for (:role user))]
          (ssess/merge-to-session
            command
            (ok :user (-> user user/with-org-auth user/non-private) :applicationpage application-page)
            {:user (user/session-summary user)})
          (do
            (error "Unknown user role:" (:role user))
            (fail :error.login))))
      (do
        (info "login failed, username:" username)
        (user/login-failed username)
        (fail :error.login)))))

(defcommand impersonate-authority
  {:parameters [organizationId password]
   :user-roles #{:admin}
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :description "Changes admin session into authority session with access to given organization"}
  [{user :user :as command}]
  (if (user/get-user-with-password (:username user) password)
    (let [imposter (assoc user :impersonating true :role "authority" :orgAuthz {(keyword organizationId) #{:authority}})]
      (ssess/merge-to-session command (ok) {:user imposter}))
    (fail :error.login)))

;;
;; ==============================================================================
;; Registering:
;; ==============================================================================
;;

(defcommand register-user
  {:parameters [stamp email password street zip city phone allowDirectMarketing rakentajafi]
   :user-roles #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:email :password :stamp :street :zip :city :phone])
                      (partial action/boolean-parameters [:allowDirectMarketing :rakentajafi])
                      action/email-validator]}
  [{data :data}]
  (let [vetuma-data (vetuma/get-user stamp)
        email (user/canonize-email email)]
    (when-not vetuma-data (fail! :error.create-user))
    (try
      (infof "Registering new user: %s - details from vetuma: %s" (dissoc data :password) vetuma-data)
      (if-let [user (create-new-user nil (merge
                                           (set/rename-keys vetuma-data {:userid :personId})
                                           (select-keys data [:password :street :zip :city :phone :allowDirectMarketing])
                                           {:email email :role "applicant" :enabled false}))]
        (do
          (vetuma/consume-user stamp)
          (when rakentajafi
            (util/future* (idf/send-user-data user "rakentaja.fi")))
          (ok :id (:id user)))
        (fail :error.create-user))
      (catch IllegalArgumentException e
        (fail (keyword (.getMessage e)))))))

(defcommand confirm-account-link
  {:parameters [stamp tokenId email password street zip city phone]
   :user-roles #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:tokenId :password])
                      action/email-validator]}
  [{data :data}]
  (let [vetuma-data (vetuma/get-user stamp)
        email (user/canonize-email email)
        token (token/get-token tokenId)]
    (when-not (and vetuma-data
                (= (:token-type token) :activate-linked-account)
                (= email (get-in token [:data :email])))
      (fail! :error.create-user))
    (try
      (infof "Confirm linked account: %s - details from vetuma: %s" (dissoc data :password) vetuma-data)
      (if-let [user (create-new-user nil (merge data vetuma-data {:email email :role "applicant" :enabled true}) :send-email false)]
        (do
          (vetuma/consume-user stamp)
          (token/get-token tokenId :consume true)
          (ok :id (:id user)))
        (fail :error.create-user))
      (catch IllegalArgumentException e
        (fail (keyword (.getMessage e)))))))

(defcommand retry-rakentajafi
  {:parameters [email]
   :user-roles #{:admin}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :description "Admin can retry sending data to rakentaja.fi, if account is not linked"}
  [_]
  (if-let [user (user/get-user-by-email email)]
    (when-not (get-in user [:partnerApplications :rakentajafi])
      (if (idf/send-user-data user "rakentaja.fi")
        (ok)
        (fail :error.unknown)))
    (fail :error.user-not-found)))

;;
;; ==============================================================================
;; User attachments:
;; ==============================================================================
;;

(defquery user-attachments
  {:user-roles #{:applicant :authority :authorityAdmin :admin}}
  [{user :user}]
  (if-let [current-user (user/get-user-by-id (:id user))]
    (ok :attachments (:attachments current-user))
    (fail :error.user-not-found)))

(defn- add-user-attachment-allowed? [user] (user/applicant? user))

(defquery add-user-attachment-allowed
  {:description "Dummy command for UI logic: returns falsey if current user is not allowed to add \"user attachments\"."
   :pre-checks [(fn [command _]
                  (when-not (add-user-attachment-allowed? (:user command))
                    unauthorized))]
   :user-roles #{:anonymous}})

(defpage [:post "/api/upload/user-attachment"] {[{:keys [tempfile filename content-type size]}] :files attachmentType :attachmentType}
  (let [user              (user/current-user (request/ring-request))
        filename          (mime/sanitize-filename filename)
        attachment-type   (attachment/parse-attachment-type attachmentType)
        attachment-id     (mongo/create-id)
        file-info         {:attachment-type  attachment-type
                           :attachment-id    attachment-id
                           :file-name        filename
                           :content-type     content-type
                           :size             size
                           :created          (now)}]

    (when-not (add-user-attachment-allowed? user) (throw+ {:status 401 :body "forbidden"}))

    (info "upload/user-attachment" (:username user) ":" attachment-type "/" filename content-type size "id=" attachment-id)
    (when-not ((set attachment/attachment-types-osapuoli) (:type-id attachment-type)) (fail! :error.illegal-attachment-type))
    (when-not (mime/allowed-file? filename) (fail :error.illegal-file-type))

    (mongo/upload attachment-id filename content-type tempfile :user-id (:id user))
    (mongo/update-by-id :users (:id user) {$push {:attachments file-info}})

    (->> (assoc file-info :ok true)
      (resp/json)
      (resp/content-type "text/plain") ; IE is fucking stupid: must use content type text/plain, or else IE prompts to download response.
      (resp/status 200))))

(defraw download-user-attachment
  {:parameters [attachment-id]
   :user-roles #{:applicant}}
  [{user :user}]
  (when-not user (throw+ {:status 401 :body "forbidden"}))
  (if-let [attachment (mongo/download-find {:id attachment-id :metadata.user-id (:id user)})]
    {:status 200
     :body ((:content attachment))
     :headers {"Content-Type" (:content-type attachment)
               "Content-Length" (str (:content-length attachment))
               "Content-Disposition" (format "attachment;filename=\"%s\"" (ss/encode-filename (:file-name attachment)))}}
    {:status 404
     :body (str "can't file attachment: id=" attachment-id)}))

(defcommand remove-user-attachment
  {:parameters [attachment-id]
   :user-roles #{:applicant}}
  [{user :user}]
  (info "Removing user attachment: attachment-id:" attachment-id)
  (mongo/update-by-id :users (:id user) {$pull {:attachments {:attachment-id attachment-id}}})
  (mongo/delete-file {:id attachment-id :metadata.user-id (:id user)})
  (ok))

(defcommand copy-user-attachments-to-application
  {:parameters [id]
   :user-roles #{:applicant}
   :states     [:draft :open :submitted :complement-needed]
   :pre-checks [(fn [command application] (not (-> command :user :architect)))]}  ;;TODO: lisaa architect? check
  [{application :application user :user}]
  (doseq [attachment (:attachments (mongo/by-id :users (:id user)))]
    (let [application-id id
          user-id (:id user)
          {:keys [attachment-type attachment-id file-name content-type size created]} attachment
          attachment (mongo/download-find {:id attachment-id :metadata.user-id user-id})
          attachment-id (str application-id "." user-id "." attachment-id)]
      (when (zero? (mongo/count :applications {:_id application-id :attachments.id attachment-id}))
        (attachment/attach-file! {:application application
                                  :attachment-id attachment-id
                                  :attachment-type attachment-type
                                  :content ((:content attachment))
                                  :filename file-name
                                  :content-type content-type
                                  :size size
                                  :created created
                                  :user user
                                  :required false
                                  :locked false}))))
  (ok))

(defquery email-in-use
  {:parameters       [email]
   :input-validators [email-validator]
   :user-roles       #{:anonymous}}
  [_]
  (let [user (user/get-user-by-email email)]
    (if user
      (ok)
      (fail :email-not-in-use))))

(defcommand remove-user-notification
  {:user-roles #{:applicant}}
  [{{id :id} :user}]
  (mongo/update-by-id :users id {$unset {:notification 1}}))
