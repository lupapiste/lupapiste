(ns lupapalvelu.change-email-api
  (:require [taoensso.timbre :as timbre :refer [debug info infof warn warnf error]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.action :refer [defquery defcommand defraw email-validator] :as action]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [lupapalvelu.vetuma :as vetuma]))

(defn change-email-link [lang token]
  (str (env/value :host) "/app/" lang "/welcome#!/email/" token))

(notifications/defemail :change-email
  {:recipients-fn notifications/from-user
   :model-fn (fn [{{token :token} :data} conf recipient]
               {:link-fi (change-email-link "fi" token), :link-sv (change-email-link "sv" token)})})

(defn- init-email-change [user new-email]
  (let [token (token/make-token :change-email user {:new-email new-email} :auto-consume false :ttl ttl/change-email-token-ttl)]
    (notifications/notify! :change-email {:user (assoc user :email new-email), :data {:token token}})))

(defcommand change-email-init
  {:parameters [email]
   :user-roles #{:applicant}
   :input-validators [(partial action/non-blank-parameters [:email])
                      action/email-validator]
   :description "Starts the workflow for changing user password"}
  [{user :user}]
  (let [email (usr/canonize-email email)]
    (if-not (usr/get-user-by-email email)
      (init-email-change user email)
      (fail :error.duplicate-email))))

(defn- change-email [token stamp]
  {:pre [(map? token)]}

  (let [vetuma-data (vetuma/get-user stamp)
        new-email (get-in token [:data :new-email])
        {hetu :personId email :email} (usr/get-user-by-id! (:user-id token))]

    (cond
      (not= (:token-type token) :change-email) (fail! :error.unknown)
      (not= hetu (:userid vetuma-data)) (fail! :error.personid-mismatch)
      (usr/get-user-by-email new-email) (fail! :error.duplicate-email))

    (usr/update-user-by-email email {:personId hetu} {$set {:username new-email :email new-email}})

    ; Cleanup tokens
    (vetuma/consume-user stamp)
    (token/get-token (:id token) :consume true)

    (ok)))

(defcommand change-email
  {:parameters [tokenId stamp]
   :input-validators [(partial action/non-blank-parameters [:tokenId :stamp])]
   :user-roles #{:anonymous}}
  [_]
  (if-let [token (token/get-token tokenId)]
    (change-email token stamp)
    (fail :error.unknown)))
