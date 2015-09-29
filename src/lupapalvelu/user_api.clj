(ns lupapalvelu.user-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
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
            [lupapalvelu.states :as states]
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
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.password-reset :as pw-reset]
            ))

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

(defquery users-for-datatables
  {:user-roles #{:admin :authorityAdmin}}
  [{caller :user {params :params} :data}]
  (ok :data (user/users-for-datatables caller params)))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

;; Emails


(notifications/defemail :invite-authority
  (assoc pw-reset/base-email-conf :subject-key "authority-invite.title" :recipients-fn notifications/from-user))

(notifications/defemail :notify-authority-added
  {:model-fn (fn [{name :data} conf recipient] {:org-fi (:fi name), :org-sv (:sv name)}),
   :subject-key "authority-notification.title",
   :recipients-fn notifications/from-user})

(defn- notify-new-authority [new-user created-by]
  (let [token (token/make-token :authority-invitation created-by (merge new-user {:caller-email (:email created-by)}))]
    (notifications/notify! :invite-authority {:user new-user, :data {:token token}})))

(defn- notify-authority-added [email organization-id]
  (let [user (user/get-user-by-email email)
        org-name (:name (organization/get-organization organization-id))]
    (notifications/notify! :notify-authority-added {:user user, :data org-name})))

(defn- create-authority-user-with-organization [caller new-organization email firstName lastName roles]
  (let [org-authz {new-organization (into #{} roles)}
        user-data {:email email :orgAuthz org-authz :role :authority :enabled true :firstName firstName :lastName lastName}
        new-user (user/create-new-user caller user-data :send-email false)]
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
        user (user/create-new-user caller updated-user-data :send-email false)]
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
      (user/create-new-user current-user {:email email :role "dummy"}))))

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
  (let [caller-email    (:email caller)
        user-email      (:email user-data)]

    (if (user/admin? caller)
      (when (= user-email caller-email)    (fail! :error.unauthorized :desc "admin may not change his/her own data"))
      (when (not= user-email caller-email) (fail! :error.unauthorized :desc "can't edit others data")))

    true))

;; Define schema for update data
(def- UserUpdate (dissoc user/User :id :role :email :username :enabled))

(defn- validate-updatable-user [{user-data :data}]
  (when (sc/check UserUpdate user-data)
    (fail :error.invalid-user-data)))

(defn- validate-registrable-user [{user-data :data}]
  (when (sc/check user/RegisterUser user-data)
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
;; Saved search filters
;;

(defn- validate-filter-type [{{filter-type :filterType} :data}]
  (when-not (#{"application" "foreman"} filter-type)
    (fail :error.invalid-type)))

(def filter-storage-key
  {"application" :applicationFilters
   "foreman" :foremanFilters})

(def default-filter-storage-key
  {"application" :id
   "foreman" :foremanFilterId})

(defcommand update-default-application-filter
  {:parameters       [filterId filterType]
   :user-roles       #{:authority}
   :input-validators [validate-filter-type
                      (fn [{{filter-id :filterId filter-type :filterType} :data {user-id :id} :user}]
                        (let [user (user/get-user-by-id user-id)
                              filters (get user (filter-storage-key filter-type))]
                          (when-not (or (nil? filter-id) (util/find-by-id filter-id filters))
                            (fail :error.filter-not-found))))]
   :description      "Adds/Updates users default filter for the application search"}
  [{{user-id :id} :user}]

  (mongo/update-by-id :users user-id {$set {:defaultFilter {:id filterId}}}))

(defcommand save-application-filter
  {:parameters       [title :filter sort filterType]
   :user-roles       #{:authority}
   :input-validators [validate-filter-type
                      (partial action/non-blank-parameters [:title :filter :sort])
                      (fn [{{filter-id :filterId} :data}]
                        (when (and filter-id (not (mongo/valid-key? filter-id)))
                          (fail :error.illegal-key)))]
   :description      "Adds/updates application filter for the user"}
  [{{user-id :id} :user {filter-data :filter filter-id :filterId} :data}]
  (let [filter-id        (or filter-id (mongo/create-id))
        storage-key      (filter-storage-key filterType)
        id-key           (default-filter-storage-key filterType)
        user             (user/get-user-by-id user-id)
        filters          (get user storage-key)
        title-collision? (->> filters
                              (filter #(= (:title %) title))
                              (map :id)
                              (some (partial not= filter-id)))
        search-filter    {:id filter-id :title title :filter filter-data :sort sort}
        ; enable editing existing filter
        updated-filters  (if (empty? filters)
                           [search-filter]
                           (as-> filters $
                                (zipmap (map :id $) (range))
                                (get $ filter-id (count $))
                                (assoc-in filters [$] search-filter)))
        update {$set (merge {storage-key updated-filters}
                       (when (empty? filters)
                         {:defaultFilter {id-key filter-id}}))}]

    (when title-collision?
      (fail! :error.filter-title-collision))

    (doseq [filter updated-filters]
      ; Should always  pass, but if not, throws exception which will be caught
      ; by our action framework. User gets an unexpected error.
      (sc/validate user/SearchFilter filter))

    (mongo/update-by-id :users user-id update)
    (ok :filter search-filter)))

(defcommand remove-application-filter
  {:parameters [filterId filterType]
   :user-roles #{:authority}
   :input-validators [validate-filter-type
                      (partial action/non-blank-parameters [:filterId])]
   :description "Removes users application filter"}
  [{{user-id :id} :user}]
  (let [user (user/get-user-by-id user-id)
        storage-key (filter-storage-key filterType)
        id-key (default-filter-storage-key filterType)
        update (merge {$pull {storage-key {:id filterId}}}
                      (when (= (get-in user [:defaultFilter id-key]) filterId)
                        {$set {:defaultFilter {id-key nil}}}))]
    (mongo/update-by-id :users user-id update)))

;;
;; Change organization data:
;;

(defn- valid-organization-operation? [{data :data}]
  (when-not (#{"add" "remove"} (:operation data))
    (fail :bad-request :desc (str "illegal organization operation: '" (:operation data) "'"))))

(defn- allowed-roles [allowed-roles command]
  (let [roles (get-in command [:data :roles])
        pred (set (map name allowed-roles))]
    (when-not (every? pred roles)
      (fail :invalid.roles))))

(defcommand update-user-organization
  {:parameters       [email firstName lastName roles]
   :input-validators [(partial action/non-blank-parameters [:email :firstName :lastName])
                      (partial action/vector-parameters-with-at-least-n-non-blank-items 1 [:roles])
                      action/email-validator
                      (partial allowed-roles organization/authority-roles)]
   :user-roles #{:authorityAdmin}}
  [{caller :user}]
  (let [organization-id (user/authority-admins-organization-id caller)
        actual-roles    (organization/filter-valid-user-roles-in-organization organization-id roles)
        email           (user/canonize-email email)
        result          (user/update-user-by-email email {:role "authority"} {$set {(str "orgAuthz." organization-id) actual-roles}})]
    (if (ok? result)
      (do
        (notify-authority-added email organization-id)
        (ok :operation "add"))
      (if-not (user/get-user-by-email email)
        (create-authority-user-with-organization caller organization-id email firstName lastName actual-roles)
        (fail :error.user-not-found)))))

(defcommand remove-user-organization
  {:parameters       [email]
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :user-roles       #{:authorityAdmin}}
  [{caller :user}]
  (let [organization-id (user/authority-admins-organization-id caller)]
    (user/update-user-by-email email {:role "authority"} {$unset {(str "orgAuthz." organization-id) ""}})))

(defcommand update-user-roles
  {:parameters [email roles]
   :input-validators [(partial action/non-blank-parameters [:email])
                      (partial action/vector-parameters-with-at-least-n-non-blank-items 1 [:roles])
                      action/email-validator
                      (partial allowed-roles organization/authority-roles)]
   :user-roles #{:authorityAdmin}}
  [{caller :user}]
  (let [organization-id (user/authority-admins-organization-id caller)
        actual-roles    (organization/filter-valid-user-roles-in-organization organization-id roles)]
    (user/update-user-by-email email {:role "authority"} {$set {(str "orgAuthz." organization-id) actual-roles}})))

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

(defcommand reset-password
  {:parameters    [email]
   :user-roles #{:anonymous}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :notified      true}
  [_]
  (let [user (user/get-user-by-email email) ]
    (if (and user (not (user/dummy? user)))
      (do
        (pw-reset/reset-password user)
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
    (if (and user (not (user/dummy? user)))
      (ok :link (pw-reset/reset-link "fi" (pw-reset/reset-password user)))
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

(defquery redirect-after-login
  {:user-roles action/all-authenticated-user-roles}
  [{session :session}]
  (ok :url (get session :redirect-after-login "")))

(defcommand impersonate-authority
  {:parameters [organizationId role password]
   :user-roles #{:admin}
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (fn [{data :data}] (when-not (#{"authority" "authorityAdmin"} (:role data)) (fail :error.invalid-role)))]
   :description "Changes admin session into authority session with access to given organization"}
  [{user :user :as command}]
  (if (user/get-user-with-password (:username user) password)
    (let [imposter (assoc user :impersonating true :role role :orgAuthz {(keyword organizationId) #{(keyword role)}})]
      (ssess/merge-to-session command (ok) {:user imposter}))
    (fail :error.login)))

;;
;; ==============================================================================
;; Registering:
;; ==============================================================================
;;

(defcommand register-user
  {:parameters       [stamp email password street zip city phone allowDirectMarketing rakentajafi]
   :user-roles       #{:anonymous}
   :input-validators [action/email-validator validate-registrable-user]}
  [{data :data}]
  (let [vetuma-data (vetuma/get-user stamp)
        email (user/canonize-email email)]
    (when-not vetuma-data (fail! :error.create-user))
    (try
      (infof "Registering new user: %s - details from vetuma: %s" (dissoc data :password) vetuma-data)
      (if-let [user (user/create-new-user
                      nil
                      (merge
                        (set/rename-keys vetuma-data {:userid :personId})
                        (select-keys data [:password :street :zip :city :phone :allowDirectMarketing])
                        (when (:architect data)
                          (select-keys data [:architect :degree :graduatingYear :fise]))
                        {:email email :role "applicant" :enabled false}))]
        (do
          (activation/send-activation-mail-for user)
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
      (if-let [user (user/create-new-user
                      nil
                      (merge data vetuma-data {:email email :role "applicant" :enabled true})
                      :send-email false)]
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
    (when-not (mime/allowed-file? filename) (fail! :error.illegal-file-type))

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
   :states     #{:draft :open :submitted :complement-needed}
   :pre-checks [(fn [command _]
                  (when-not (-> command :user :architect)
                    unauthorized))]}
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
  (if (user/email-in-use? email)
    (ok)
    (fail :email-not-in-use)))

(defcommand remove-user-notification
  {:user-roles #{:applicant :authority}}
  [{{id :id} :user}]
  (mongo/update-by-id :users id {$unset {:notification 1}}))
