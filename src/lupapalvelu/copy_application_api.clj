(ns lupapalvelu.copy-application-api
  (:require [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :refer [warn warnf]]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.company :as company]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.user :as usr]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.env :as env])
  (:import (java.net SocketTimeoutException)))

(defn- validate-is-authority [{user :user}]
  (when-not (usr/authority? user)
    unauthorized))

(defn- try-autofill-rakennuspaikka [application timestamp]
  (try+
   (app/autofill-rakennuspaikka application timestamp true)
   (catch [:sade.core/type :sade.core/fail] {:keys [cause text] :as exp}
     (warnf "Could not get KTJ data for the new application, cause: %s, text: %s. From %s:%s"
            cause
            text
            (:sade.core/file exp)
            (:sade.core/line exp)))
   (catch SocketTimeoutException _
     (warn "Socket timeout from KTJ when creating application"))))

(defcommand copy-application
  {:description "Create a new application where various fields are copied from the source application"
   :parameters [:source-application-id :x :y :address :propertyId :auth-invites]
   :feature :copy-applications
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:address :propertyId :source-application-id])
                      (partial action/property-id-parameters [:propertyId])
                      (partial action/vector-parameters [:auth-invites])
                      coord/validate-x coord/validate-y]
   :pre-checks [(action/some-pre-check validate-is-authority
                                       (company/validate-has-company-role :any))]}
  [{:keys [created] :as command}]
  (let [copy-app-resp (copy-app/copy-application command)]
    (if (fail? copy-app-resp)
      copy-app-resp
      (let [{source-application :source-application
             copied-application :copy-application}  copy-app-resp]
        (logging/with-logging-context {:applicationId (:id copied-application)}
          (app/insert-application copied-application)
          (copy-app/store-source-application source-application (:id copied-application) created)
          (try-autofill-rakennuspaikka copied-application created)
          (copy-app/send-invite-notifications! copied-application command)
          (ok :id (:id copied-application)))))))

(defquery copy-application-invite-candidates
  {:description "Possible parties to invite from the source application to the copied application"
   :parameters [:source-application-id]
   :feature :copy-applications
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:source-application-id])]
   :pre-checks [(action/some-pre-check validate-is-authority
                                       (company/validate-has-company-role :any))]}
  [{{:keys [source-application-id]} :data user :user}]
  (let [candidates (copy-app/copy-application-invite-candidates user source-application-id)]
    (if (fail? candidates)
      candidates
      (ok :candidates candidates))))

(defquery application-copyable-to-location
  {:description "Is it possible to copy the application to the given location"
   :parameters [:source-application-id :x :y :address :propertyId]
   :feature :copy-applications
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:address :propertyId :source-application-id])
                      (partial action/property-id-parameters [:propertyId])
                      coord/validate-x coord/validate-y]
   :pre-checks [(action/some-pre-check validate-is-authority
                                       (company/validate-has-company-role :any))]}
  [command]
  (if-let [check-fail (copy-app/check-application-copyable-to-organization command)]
    check-fail
    (ok :result true)))

(defquery application-copyable
  {:description "Is it possible to copy the application at all"
   :parameters [:source-application-id]
   :feature :copy-applications
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:source-application-id])]
   :pre-checks [(action/some-pre-check validate-is-authority
                                       (company/validate-has-company-role :any))]}
  [command]
  (if-let [check-fail (copy-app/check-application-copyable command)]
    check-fail
    (ok :result true)))


(env/in-dev
  (defquery source-application
    {:description "Return a source application entry from source-applications"
     :feature :copy-applications
     :parameters [:copy-application-id]
     :input-validators [(partial action/non-blank-parameters [:copy-application-id])]
     :user-roles #{:authority}}
    [{{:keys [copy-application-id]} :data}]
    (merge (ok) (copy-app/get-source-application copy-application-id))))
