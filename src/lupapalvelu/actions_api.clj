(ns lupapalvelu.actions-api
  (:require [sade.env :as env]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery] :as action]))

;;
;; Default actions
;;

(defn foreach-action [user data application]
  (map
    #(assoc (action/make-command % data) :user user :application application)
    (keys (action/get-actions))))

(defn- validated [command]
  {(:action command) (action/validate command)})

(defquery actions
 {:user-roles #{:admin}
  :description "List of all actions and their meta-data."} [_]
 (ok :actions (action/serializable-actions)))

(defquery "allowed-actions"
 {:user-roles #{:anonymous}
  :extra-auth-roles [:any]}
 [{:keys [data user application]}]
 (let [results  (map validated (foreach-action user data application))
       filtered (if (env/dev-mode?)
                  results
                  (filter (comp :ok first vals) results))
       actions  (into {} filtered)]
   (ok :actions actions)))
