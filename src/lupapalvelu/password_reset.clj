(ns lupapalvelu.password-reset
  (:require [lupapalvelu.notifications :as notifications]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [sade.env :as env]
            [taoensso.timbre :refer [infof warnf]]))

(defn reset-link [lang token]
  (str (env/value :host) "/app/" lang "/welcome#!/setpw/" token))

(def base-email-conf
  {:model-fn (fn [{token :token} _ _]
               {:link #(reset-link (name %) token)})
   :recipients-fn notifications/from-user})

(notifications/defemail :reset-password
  (assoc base-email-conf :subject-key "reset.email.title"))

(defn reset-password [{:keys [email] :as user}]
  {:pre [email (not (usr/dummy? user))]}
  (let [token (token/make-token :password-reset nil {:email email} :ttl ttl/reset-password-token-ttl)]
    (infof "password reset request: email=%s, token=%s" email token)
    (notifications/notify! :reset-password {:user user :token token})
    token))

(defn reset-password-by-email
  "Creates the password reset token and sends the corresponding email. Returns the sent
  token if successful otherwise nil."
  [email]
  (let [user (usr/get-user-by-email email)]
    (if (and user (not (usr/dummy? user)) (:enabled user))
      (reset-password user)
      (warnf "password reset request: unknown or disabled email: email=%s" email))))
