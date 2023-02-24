(ns lupapalvelu.open-inforequest-api
  (:require [monger.operators :refer :all]
            [noir.response :as resp]
            [sade.core :refer [ok fail fail!]]
            [sade.env :as env]
            [sade.session :as ssess]
            [lupapalvelu.action :refer [defraw defcommand] :as action]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.mongo :as mongo]))

(defn- make-user [token]
  (let [organization-id (:organization-id token)
        email (:email token)]
    {:id (str "oir-" organization-id "-" email)
     :email email
     :enabled true
     :role "oirAuthority"
     :orgAuthz {(keyword organization-id) #{:authority}}
     :oir true
     :firstName ""
     :lastName email
     :phone ""
     :username email}))

(defraw openinforequest
  {:permissions [{:required []}]}
  [{{token-id :token-id} :data lang :lang created :created :as command}]
  (let [token (mongo/by-id :open-inforequest-token token-id)
        url   (str (env/value :host) "/app/" (name lang) "/oir#!/inforequest/" (:application-id token))]
    (if token
      (do
        (mongo/update-by-id :open-inforequest-token token-id {$set {:last-used created}})
        (ssess/merge-to-session command (resp/redirect url) {:user (make-user token)}))
      (resp/status 404 "Not found"))))

(defcommand convert-to-normal-inforequests
  {:parameters [organizationId]
   :description "Converts organizations inforequests to normal inforequests and deletes all organizations inforequest tokens."
   :permissions [{:required [:organization/convert-inforequests]}]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (fn [{data :data}]
                        (if-let [organization (organization/get-organization (:organizationId data))]
                          (when (some :open-inforequest (:scope organization))
                            (fail :error.command-illegal-state)) ; too lazy to come up with new error msg...
                          (fail :error.unknown-organization)))]}
  [_]
  (let [n (mongo/update-by-query :applications
            {:organization organizationId
             :openInfoRequest true}
            {$set {:openInfoRequest false}})]
    (mongo/remove-many :open-inforequest-token {:organization-id organizationId})
    (ok :n n)))
