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
  {:parameters [:source-application-id :x :y :address :propertyId :auth-invites]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:address :propertyId :source-application-id])
                      (partial action/property-id-parameters [:propertyId])
                      (partial action/vector-parameters [:auth-invites])
                      coord/validate-x coord/validate-y]
   :pre-checks [(action/some-pre-check validate-is-authority
                                       (company/validate-has-company-role :any))]}
  [{:keys [created] :as command}]
  (let [{source-application :source-application
         copied-application :copy-application} (copy-app/copy-application command)]
    (logging/with-logging-context {:applicationId (:id copied-application)}
      (app/insert-application copied-application)
      (copy-app/store-source-application source-application (:id copied-application) created)
      (when (copy-app/new-building-application? copied-application)
        (try-autofill-rakennuspaikka copied-application created))
      (copy-app/send-invite-notifications! copied-application command)
      (ok :id (:id copied-application)))))

(defquery copy-application-invite-candidates
  {:parameters [:source-application-id]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:source-application-id])]
   :pre-checks [(action/some-pre-check validate-is-authority
                                       (company/validate-has-company-role :any))]}
  [{{:keys [source-application-id]} :data user :user}]
  (ok :candidates (copy-app/copy-application-invite-candidates user source-application-id)))

(defquery application-copyable-to-location
  {:parameters [:source-application-id :x :y :address :propertyId]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/non-blank-parameters [:address :propertyId :source-application-id])
                      (partial action/property-id-parameters [:propertyId])
                      coord/validate-x coord/validate-y]
   :pre-checks [(action/some-pre-check validate-is-authority
                                       (company/validate-has-company-role :any))]}
  [command]
  (ok :result (copy-app/application-copyable-to-location command)))

(env/in-dev
  (defquery source-application
    {:description "Return a source application entry from source-applications"
     :parameters [:copy-application-id]
     :input-validators [(partial action/non-blank-parameters [:copy-application-id])]
     :user-roles #{:authority}}
    [{{:keys [copy-application-id]} :data}]
    (merge (ok) (copy-app/get-source-application copy-application-id))))
