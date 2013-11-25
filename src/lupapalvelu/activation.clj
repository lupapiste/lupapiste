(ns lupapalvelu.activation
  (:require [monger.operators :refer :all]
            [hiccup.core :refer :all]
            [sade.env :as env]
            [sade.email :as email]
            [sade.strings :refer [lower-case]]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.security :as security]))

(notifications/defemail :account-activation
  {:recipients-fn  notifications/from-user
   :model-fn       (fn [{user :user data :data} _]
                     {:link (str (env/value :host) (env/value :activation :path) (:key data))
                      :name (:firstName user)})})

(defn send-activation-mail-for [user]
  (let [key     (security/random-password)
        userid  (or (:_id user) (:id user))
        email   (:email user)]
    (mongo/insert :activation {:user-id userid :email email :activation-key key})
    (notifications/notify! :account-activation {:user user :data {:key key}})))

(defn get-activation-key [userid]
  (mongo/select-one :activation {:user-id userid}))

(defn activate-account [activation-key]
  (let [act     (mongo/select-one :activation {:activation-key activation-key})
        userid  (:user-id act)
        updated-user (mongo/update-one-and-return :users {:_id userid} {$set {:enabled true}})]
    (when updated-user
      (mongo/remove :activation (:_id act))
      (merge (user/non-private (mongo/select-one :users {:_id userid})) {:id userid})
      (user/clear-logins (:username updated-user)))))

(defn activate-account-by-email [email]
  (let [act     (mongo/select-one :activation {:email (lower-case email)})
        userid  (:user-id act)
        success (mongo/update-by-id :users userid {$set {:enabled true}})]
    (when success
      (mongo/remove :activation (:_id act))
      (merge (user/non-private (mongo/select-one :users {:_id userid})) {:id userid}))))

(defn activations []
  (mongo/select :activation))
