(ns lupapalvelu.sftp.sftp-api
  "Organization SFTP configuration."
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.sftp.schemas :refer [SftpOrganizationConfiguration]]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]))

(defn organization-exists
  [command]
  (when-let [org-id (-> command :data :organizationId)]
    (when-not (mongo/any? :organizations {:_id org-id})
      (fail :error.organization-not-found))))

(defn valid-configuration
  [command]
  (when-let [cfg (some-> command :data (select-keys [:users :sftpType]) not-empty)]
    (when-not (sftp/valid-configuration? cfg)
      (fail :error.invalid-configuration))))

(sc/defschema org-id-param
  {:organizationId      ssc/NonBlankStr
   (sc/optional-key :_) sc/Any})

(defquery sftp-organization-configuration
  {:description      "Current SFTP user configuration for the given organization."
   :parameters       [organizationId]
   :user-roles       #{:admin}
   :input-validators [org-id-param]
   :pre-checks       [organization-exists]}
  [_]
  (ok :configuration (sftp/get-organization-configuration organizationId)))

(defcommand update-sftp-organization-configuration
  {:description      "Update the SFTP user configuration for the given organization."
   :parameters       [organizationId sftpType users]
   :user-roles       #{:admin}
   :input-validators [(merge SftpOrganizationConfiguration org-id-param)
                      valid-configuration]
   :pre-checks       [organization-exists]}
  [_]
  (or (sftp/update-organization-configuration organizationId
                                              {:sftpType sftpType
                                               :users    users})
      (ok)))
