(ns lupapalvelu.activation
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.security :as security]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]))

(notifications/defemail :account-activation
  {:recipients-fn notifications/from-user
   :model-fn      (fn [{:keys [data system]} _ recipient]
                    {:link (str (components/system->host system) (env/value :activation :path) (:key data))
                     :name (:firstName recipient)})})

(defn send-activation-mail-for [system user]
  (let [userid (or (:_id user) (:id user))
        email  (:email user)
        key    (if-let [old-activation (mongo/select-one :activation {:user-id userid :email email})]
                 (:activation-key old-activation)
                 (let [new-key (security/random-password)]
                   (mongo/insert :activation {:user-id userid :email email :activation-key new-key :_created (now)})
                   new-key))]
    (notifications/notify! :account-activation
                           {:system system
                            :user   (dissoc user :id) ;; user is not yet enabled, so avoid checking it by removing the id
                            :data   {:key key}})))

(defn activate-account [activation-key]
  (let [act     (mongo/select-one :activation {:activation-key activation-key})
        userid  (:user-id act)
        updated-user (mongo/update-one-and-return :users {:_id userid} {$set {:enabled true :firstLogin true}})]
    (when updated-user
      (user/clear-logins (:username updated-user))
      (mongo/remove :activation (:id act))
      (merge (user/non-private (mongo/select-one :users {:_id userid})) {:id userid}))))
