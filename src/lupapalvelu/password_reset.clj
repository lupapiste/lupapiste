(ns lupapalvelu.password-reset
  (:require [taoensso.timbre :as timbre :refer [debug info infof warn warnf error]]
            [sade.env :as env]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as user]
            ))

(defn reset-link [lang token]
  (str (env/value :host) "/app/" lang "/welcome#!/setpw/" token))

(def base-email-conf
  {:model-fn (fn [{{token :token} :data} conf recipient]
               {:link-fi (reset-link "fi" token), :link-sv (reset-link "sv" token)})})

(notifications/defemail :reset-password
  (assoc base-email-conf :subject-key "reset.email.title" :recipients-fn notifications/from-data))

(defn reset-password [{:keys [email] :as user}]
  {:pre [email (not (user/dummy? user))]}
  (let [token (token/make-token :password-reset nil {:email email} :ttl ttl/reset-password-token-ttl)]
    (infof "password reset request: email=%s, token=%s" email token)
    (notifications/notify! :reset-password {:data {:email email :token token}})
    token))
