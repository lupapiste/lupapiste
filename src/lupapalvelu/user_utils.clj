(ns lupapalvelu.user-utils
  "Users are created from different namespaces (statements, user,
  guest). The commonly shared code is stored here outside of user
  namespace in order to avoid cyclic dependencies."
  (:require [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.token :as token]
            [lupapalvelu.user :as usr]
            [sade.strings :as ss]))

;; Emails

(notifications/defemail :invite-authority
  {:model-fn (fn [{:keys [token org-fn system]} _ _]
               {:link #(pw-reset/reset-link system (name %) token)
                :org org-fn})
   :recipients-fn notifications/from-user
   :subject-key "authority-invite.title"})

(notifications/defemail :notify-authority-added
  {:model-fn (fn [{org-fn :org-fn} _ _] {:org org-fn}),
   :subject-key "authority-notification.title",
   :recipients-fn notifications/from-user})

(defn notify-new-authority [system new-user created-by organization-id]
  {:pre [(map? system)]}
  ;; Don't auto-consume; allows reading token for password quality estimation
  (let [token       (token/make-token :authority-invitation
                                      created-by
                                      (merge new-user {:caller-email (:email created-by)})
                                      :auto-consume false)
        org-name-fn #(-> (org/get-organization organization-id [:name])
                         (get-in [:name (or (keyword %) :fi)]))]
    (notifications/notify! :invite-authority {:system system
                                              :user   new-user
                                              :token  token
                                              :org-fn org-name-fn})))

(defn notify-authority-added [system email organization-id]
  {:pre [(map? system)]}
  (let [user        (usr/get-user-by-email email)
        org-name-fn #(-> (org/get-organization organization-id [:name])
                         (get-in [:name (or (keyword %) :fi)]))]
    (notifications/notify! :notify-authority-added {:system system
                                                    :user   user
                                                    :org-fn org-name-fn})))

(defn create-and-notify-authority-user
  "Used by authorityAdmin. Creates authority user, notifies and returns new user."
  [system org-id caller user-data]
  {:pre [(= "authority" (:role user-data))
         (nil? (:organization user-data))
         (nil? (:orgAuthz user-data))
         (map? system)]}
  (let [user (usr/create-new-user caller user-data)]
    (notify-new-authority system user caller org-id)
    user))

(defn authority-by-email
  "Gets, creates or promotes authority. Returns user or nil if the
  user already exists and promotion is not possible (role is not
  dummy). If an authority is created, the email is also used as last
  name. Does not add any orgAuthz for possible new user."
  [system org-id caller email]
  {:pre [(map? system)]}
  (let [user (usr/get-user-by-email email)]
    (cond
      (and (usr/authority? user)
           (not (usr/purely-authorityAdmin? user))) user
      ;; Use existing user data if available
      (or (nil? user) (usr/dummy? user)) (->> (merge {:email email}
                                                     (dissoc user :id)
                                                     {:role "authority"})
                                              (create-and-notify-authority-user system org-id caller)))))

(defn admin-and-user-have-same-email-domain? [authority auth-admin]
   (let [email-domain-picker (fn [email] (-> email (ss/split #"@") (last)))]
     (= (-> authority :email email-domain-picker)
        (-> auth-admin :email email-domain-picker))))

(defn authority-has-only-one-org? [authority]
  (let [authority-orgs (-> authority :orgAuthz (keys))]
    (= (count authority-orgs) 1)))
