(ns lupapalvelu.open-inforequest-api
  (:require [monger.operators :refer :all]
            [noir.session :as session]
            [noir.response :as resp]
            [sade.core :refer [ok fail fail!]]
            [sade.env :as env]
            [lupapalvelu.action :refer [defraw defcommand] :as action]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.mongo :as mongo]))

(defn- make-user [token]
  (let [organization-id (:organization-id token)
        email (:email token)]
    {:id (str "oir-" organization-id "-" email)
     :email email
     :enabled true
     :role "authority"
     :oir true
     :organizations [organization-id]
     :firstName ""
     :lastName email
     :phone ""
     :username email}))

(defraw openinforequest
  {:user-roles #{:anonymous}}
  [{{token-id :token-id} :data lang :lang created :created}]
  (assert created)
  (let [token (mongo/by-id :open-inforequest-token token-id)
        url   (str (env/value :host) "/app/" (name lang) "/oir#!/inforequest/" (:application-id token))]
    (if token
      (do
        (mongo/update-by-id :open-inforequest-token token-id {$set {:last-used created}})
        (session/put! :user (make-user token))
        (resp/redirect url))
      (resp/status 404 "Not found"))))

(defcommand convert-to-normal-inforequests
  {:parameters [organizationId]
   :description "Converts organizations inforequests to normal inforequests and deletes all organizations inforequest tokens."
   :user-roles #{:admin}
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (fn [{data :data}]
                        (if-let [organization (organization/get-organization (:organizationId data))]
                          (when (some :open-inforequest (:scope organization))
                            (fail :error.command-illegal-state)) ; too lazy to come up with new error msg...
                          (fail :error.unknown-organization)))]}
  [_]
  (println "dp.applications.update(" {:orgazation organizationId
             :infoRequest true} ", " {$set {:openInfoRequest false}} ")")
  (let [n (mongo/update-by-query :applications
            {:organization organizationId
             :infoRequest true}
            {$set {:openInfoRequest false}})]
    (mongo/remove-many :open-inforequest-token {:organization-id organizationId})
    (ok :n n)))
