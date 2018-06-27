(ns lupapalvelu.user-utils
  "Users are created from different namespaces (statements, user,
  guest). The commonly shared code is stored here outside of user
  namespace in order to avoid cyclic dependencies."
  (:require [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.token :as token]
            [lupapalvelu.user :as usr]
            [sade.core :refer [ok fail!]]
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

(defn create-and-notify-authority-user
  "Used by authorityAdmin. Creates authority user, notifies and returns new user."
  [org-id caller user-data]
  {:pre [(= "authority" (:role user-data)) (nil? (:organization user-data))]}
  (let [user (usr/create-new-user caller user-data)]
    (notify-new-authority user caller org-id)
    user))

(defn authority-by-email
  "Gets, creates or promotes authority. Returns user or nil if the
  user already exists and promotion is not possible (role is not
  dummy). If an authority is created, the email is also used as last
  name. Does not add any orgAuthz for possible new user."
  [org-id caller email]
  (let [user (usr/get-user-by-email email)]
    (cond
      (and (usr/authority? user)
           (not (usr/authority-admin? user))) user
      ;; Use existing user data if available
      (or (nil? user) (usr/dummy? user)) (->> (merge {:email email}
                                                     (dissoc user :id)
                                                     {:role "authority"})
                                              (create-and-notify-authority-user org-id caller)))))

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
