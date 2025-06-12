(ns lupapalvelu.password-reset
  (:require [lupapalvelu.logging :refer [infof warnf]]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr])
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

(defn reset-link [system lang token]
  {:pre [(map? system)]}
  (str (components/system->host system) "/app/" lang "/welcome#!/setpw/" token))

(def base-email-conf
  {:model-fn (fn [{:keys [token system]} _ _]
               {:link #(reset-link system (name %) token)})
   :recipients-fn notifications/from-user})

(notifications/defemail :reset-password
  (assoc base-email-conf :subject-key "reset.email.title"))

(defn make-reset-token [email]
  {:pre [email]}
  ;; Don't auto-consume; allows reading token for password quality estimation
  (token/make-token :password-reset nil {:email email}
                    :ttl ttl/reset-password-token-ttl
                    :auto-consume false))

(defn reset-password
  ([system user]
   (reset-password system user {}))
  ([system {:keys [email] :as user} {:keys [token-only?]}]
   {:pre [(map? system) email (not (usr/dummy? user))]}
   (let [token (make-reset-token email)]
     (infof "password reset request: email=%s, token=%s" email token)
     (when-not token-only?
       (notifications/notify! :reset-password {:system system
                                               :user   user
                                               :token  token}))
     token)))

(def max-allowed-recent-tokens 5)
(def token-age-window (.toMillis (Duration/ofMinutes 1)))

(defn password-reset-tokens-available? [email]
  (let [recent-token-count (token/count-password-reset-tokens-by-email email token-age-window)]
    (< recent-token-count max-allowed-recent-tokens)))

(defn reset-password-by-email
  "Creates the password reset token and sends the corresponding email. Returns the sent
  token if successful otherwise nil."
  [system email]
  {:pre [(map? system)]}
  (let [user (usr/get-user-by-email email)]
    (if (and user (not (usr/dummy? user)) (:enabled user))
      (if (password-reset-tokens-available? email)
        (reset-password system user)
        (warnf "password reset request: too many recent password request tokens for email address %s" email))
      (warnf "password reset request: unknown or disabled email: email=%s" email))))
