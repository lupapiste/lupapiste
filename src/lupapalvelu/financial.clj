(ns lupapalvelu.financial
  (:require
    [monger.operators :refer :all]
    [sade.core :refer :all]
    [lupapalvelu.user :as usr]
    [lupapalvelu.authorization-api :as auth]
    [lupapalvelu.action :as action]
    [lupapalvelu.notifications :as notifications]
    [sade.util :as util]))

(defn get-financial-user []
  (usr/get-user {:role "financialAuthority"}))

(defn invite-financial-handler [command]
  (let [financial-authority (get-financial-user)
        updated-data (assoc (:data command) :email (:email financial-authority))
        updated-data (assoc updated-data :text "")
        updated-data (assoc updated-data :documentName "")
        updated-data (assoc updated-data :role "financialAuthority")
        updated-data (assoc updated-data :notification "invite-financial-authority")
        command (assoc command :data updated-data)
        caller (:user command)]
    (auth/send-invite! command)
    (action/update-application command
                        {:auth {$elemMatch {:invite.user.id (:id financial-authority)}}}
                               {$set {:modified (now)
                                      :auth.$   (util/assoc-when-pred (usr/summary financial-authority) util/not-empty-or-nil? :inviter (usr/summary caller))}})))

(defn remove-financial-handler-invitation [command]
  (let [financial-authority (get-financial-user)
        username (:username financial-authority)]
    (auth/do-remove-auth command username)
    (notifications/notify! :remove-financial-authority-invitation (assoc command :recipients [financial-authority]))))

(defn notify-housing-office [command]
  (notifications/notify! :organization-housing-office command))
