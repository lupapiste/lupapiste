(ns lupapalvelu.user-utils
  "Users are created from different namespaces (statements, user,
  guest). The commonly shared code is stored here outside of user
  namespace in order to avoid cyclic dependencies."
  (:require [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [sade.core :refer [ok fail!]]
            [sade.env :as env]
            [sade.strings :as ss]
            [taoensso.timbre :as timbre :refer [infof]]))

;; Emails

(notifications/defemail :invite-authority
  {:model-fn (fn [{token :token org-fn :org-fn} _ _]
               {:link #(pw-reset/reset-link (name %) token)
                :org org-fn})
   :recipients-fn notifications/from-user
   :subject-key "authority-invite.title"})

(notifications/defemail :notify-authority-added
  {:model-fn (fn [{org-fn :org-fn} _ _] {:org org-fn}),
   :subject-key "authority-notification.title",
   :recipients-fn notifications/from-user})

(defn notify-new-authority [new-user created-by organization-id]
  (let [token       (token/make-token :authority-invitation created-by (merge new-user {:caller-email (:email created-by)}))
        org-name-fn #(-> (org/get-organization organization-id [:name])
                         (get-in [:name (or (keyword %) :fi)]))]
    (notifications/notify! :invite-authority {:user new-user, :token token :org-fn org-name-fn})))

(defn notify-authority-added [email organization-id]
  (let [user (usr/get-user-by-email email)
        org-name-fn #(-> (org/get-organization organization-id [:name])
                         (get-in [:name (or (keyword %) :fi)]))]
    (notifications/notify! :notify-authority-added {:user user, :org-fn org-name-fn})))

(defn create-and-notify-user
  "Since create-user command is typically called by authority admin to
  create authorities, there are some peculiar details."
  [caller {:as user-data :keys [organization role]}]
  (let [user-data (if organization
                    (assoc user-data :orgAuthz {(keyword organization) [role]})
                    user-data)
        ; TODO: drop this when callers are updated
        user-data (if (= role "authorityAdmin")
                    (assoc user-data :role "authority")
                    user-data)
        user (usr/create-new-user caller user-data :send-email false)]
    (infof "Added a new user: role=%s, email=%s, orgAuthz=%s" (:role user) (:email user) (:orgAuthz user))
    (if (usr/authority? user)
      ; FIXME: user can have multiple orgz
      (do (notify-new-authority user caller (or (:organization user-data) (usr/authority-admins-organization-id caller)))
          (ok :id (:id user)
              :user user))
      (let [token (token/make-token :password-reset caller {:email (:email user)} :ttl ttl/create-user-token-ttl)]
        (ok :id (:id user)
            :user user
            :linkFi (str (env/value :host) "/app/fi/welcome#!/setpw/" token)
            :linkSv (str (env/value :host) "/app/sv/welcome#!/setpw/" token))))))

(defn authority-by-email
  "Gets, creates or promotes authority. Returns user or nil if the
  user already exists and promotion is not possible (role is not
  dummy). If an authority is created, the email is also used as last
  name."
  [caller email]
  (let [user (usr/get-user-by-email email)]
    (cond
      (usr/authority? user)              user
      ;; Use existing user data if available
      (or (nil? user) (usr/dummy? user)) (->> (merge
                                               {:email email
                                                :firstname ""
                                                :lastName email}
                                               user
                                               {:role "authority"})
                                              (create-and-notify-user caller)
                                              :user))))

(defn admin-and-user-have-same-email-domain? [authority auth-admin]
   (let [email-domain-picker (fn [email] (-> email (ss/split #"@") (last)))]
     (= (-> authority :email email-domain-picker)
        (-> auth-admin :email email-domain-picker))))

(defn auth-admin-can-view-authority? [authority auth-admin]
  (let [authority-orgs (-> authority :orgAuthz (keys) (set))]
    (-> auth-admin :orgAuthz (keys) (first) (authority-orgs) (nil?) (not))))

(defn authority-has-only-one-org? [authority]
  (let [authority-orgs (-> authority :orgAuthz (keys))]
    (= (count authority-orgs) 1)))
