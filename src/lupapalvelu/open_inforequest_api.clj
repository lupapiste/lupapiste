(ns lupapalvelu.open-inforequest-api
  (:require [lupapalvelu.action :refer [defcommand defraw] :as action]
            [lupapalvelu.http.csrf :as csrf]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.web :as web]
            [monger.operators :refer :all]
            [noir.response :as resp]
            [sade.core :refer [fail ok]]
            [sade.session :as ssess]))

(defn- make-user [token]
  (let [organization-id (:organization-id token)
        email           (:email token)]
    {:id        (str "oir-" organization-id "-" email)
     :email     email
     :enabled   true
     :role      "oirAuthority"
     :orgAuthz  {(keyword organization-id) #{:authority}}
     :oir       true
     :firstName ""
     :lastName  email
     :phone     ""
     :username  email}))

(defraw openinforequest
  {:permissions [{:required []}]}
  [{{token-id :token-id} :data
    :keys                [lang created system]
    :as                  command}]
  (let [token (mongo/by-id :open-inforequest-token token-id)
        url   (str (components/system->host system) "/app/" (name lang) "/oir#!/inforequest/" (:application-id token))]
    (if token
      (do
        (mongo/update-by-id :open-inforequest-token token-id {$set {:last-used created}})
        (-> (ssess/merge-to-session command (resp/redirect url)
                                    {:user    (make-user token)
                                     :expires (+ created web/default-session-timeout)}
                                    {:rotate-session? true})
            (update :cookies merge (csrf/new-csrf-cookie))))
      (resp/status 404 "Not found"))))

(defcommand convert-to-normal-inforequests
  {:parameters       [organizationId]
   :description      "Converts organizations inforequests to normal inforequests and deletes all organizations inforequest tokens."
   :permissions      [{:required [:organization/convert-inforequests]}]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (fn [{data :data}]
                        (if-let [organization (organization/get-organization (:organizationId data))]
                          (when (some :open-inforequest (:scope organization))
                            (fail :error.command-illegal-state)) ; too lazy to come up with new error msg...
                          (fail :error.unknown-organization)))]}
  [_]
  (let [n (mongo/update-by-query :applications
                                 {:organization    organizationId
                                  :openInfoRequest true}
                                 {$set {:openInfoRequest false}})]
    (mongo/remove-many :open-inforequest-token {:organization-id organizationId})
    (ok :n n)))
