(ns lupapalvelu.activation
  (:require [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :refer [lower-case]]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.security :as security]))

(notifications/defemail :account-activation
  {:recipients-fn  notifications/from-user
   :model-fn       (fn [{data :data} _ recipient]
                     {:link (str (env/value :host) (env/value :activation :path) (:key data))
                      :name (:firstName recipient)})})

(defn send-activation-mail-for [user]
  (let [userid  (or (:_id user) (:id user))
        email   (:email user)
        key     (if-let [old-activation (mongo/select-one :activation {:user-id userid :email email})]
                  (:activation-key old-activation)
                  (let [new-key (security/random-password)]
                    (mongo/insert :activation {:user-id userid :email email :activation-key new-key :_created (now)})
                    new-key))]
    (notifications/notify! :account-activation
       {:user (dissoc user :id) ;; user is not yet enabled, so avoid checking it by removing the id
        :data {:key key}})))

(defn activate-account [activation-key]
  (let [act     (mongo/select-one :activation {:activation-key activation-key})
        userid  (:user-id act)
        updated-user (mongo/update-one-and-return :users {:_id userid} {$set {:enabled true :firstLogin true}})]
    (when updated-user
      (user/clear-logins (:username updated-user))
      (mongo/remove :activation (:id act))
      (merge (user/non-private (mongo/select-one :users {:_id userid})) {:id userid}))))
