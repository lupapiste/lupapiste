(ns lupapalvelu.copy-application-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.company :as company]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.ryhti.permanent-id :as ryhti-id]
            [lupapalvelu.user :as usr]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.env :as env]))

(defn- validate-is-authority [{user :user}]
  (when-not (usr/authority? user)
    unauthorized))

(defcommand copy-application
  {:description      "Create a new application where various fields are copied from the source application"
   :parameters       [:source-application-id :x :y :address :propertyId :auth-invites]
   :user-roles       #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:address :propertyId :source-application-id])
                      (partial action/property-id-parameters [:propertyId])
                      (partial action/vector-parameters [:auth-invites])
                      coord/validate-x coord/validate-y]
   :pre-checks       [(action/some-pre-check validate-is-authority
                                             (company/validate-has-company-role :any))]
   :on-success       [ryhti-id/trigger-ryhti-id-fetching]}
  [{:keys [created user] :as command}]
  (let [copy-app-resp (copy-app/copy-application command)]
    (if (fail? copy-app-resp)
      copy-app-resp
      (let [{source-application           :source-application
             {copy-id :id
              :as     copied-application} :copy-application} copy-app-resp]
        (log/with-logging-context {:applicationId copy-id
                                   :userId        (:id user)}
          (app/insert-application copied-application)
          (let [copied-application (copy-app/copy-application->ryhti! command copy-id)]
            (copy-app/store-source-application source-application copy-id created)
            (app/try-autofill-rakennuspaikka copied-application created)
            (copy-app/send-invite-notifications! copied-application command)
            (ok :id copy-id)))))))

(defquery copy-application-invite-candidates
  {:description      "Possible parties to invite from the source application to the copied application"
   :parameters       [:source-application-id]
   :user-roles       #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:source-application-id])]
   :pre-checks       [(action/some-pre-check validate-is-authority
                                             (company/validate-has-company-role :any))]}
  [{{:keys [source-application-id]} :data user :user}]
  (let [candidates (copy-app/copy-application-invite-candidates user source-application-id)]
    (if (fail? candidates)
      candidates
      (ok :candidates candidates))))

(defquery application-copyable-to-location
  {:description      "Is it possible to copy the application to the given location"
   :parameters       [:source-application-id :x :y :address :propertyId]
   :user-roles       #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:address :propertyId :source-application-id])
                      (partial action/property-id-parameters [:propertyId])
                      coord/validate-x coord/validate-y]
   :pre-checks       [(action/some-pre-check validate-is-authority
                                       (company/validate-has-company-role :any))]}
  [command]
  (if-let [check-fail (copy-app/check-application-copyable-to-organization command)]
    check-fail
    (ok :result true)))

(defquery application-copyable
  {:description      "Pseudo-query for checking if it is possible to copy the application at all"
   :parameters       [:id]
   :user-roles       #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [(action/some-pre-check validate-is-authority
                                             (company/validate-has-company-role :any))
                      copy-app/check-application-copyable]}
  [_]
  (ok))

(env/in-dev
  (defquery source-application
    {:description      "Return a source application entry from source-applications. This is only
                        only for testing purposes and called from lupapalvelu.copy-application-itest"
     :parameters       [copy-application-id]
     :feature          :dev-mode-supported
     :input-validators [(partial action/non-blank-parameters [:copy-application-id])]
     :user-roles       #{:authority}}
    [_]
    (ok (mongo/by-id :source-applications copy-application-id))))
