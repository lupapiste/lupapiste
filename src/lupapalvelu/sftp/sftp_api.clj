(ns lupapalvelu.sftp.sftp-api
  "Organization SFTP configuration."
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.sftp.schemas :refer [SftpOrganizationConfiguration]]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

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
   :pre-checks       [org/organization-exists]}
  [_]
  (ok :configuration (sftp/get-organization-configuration organizationId)))

(defcommand update-sftp-organization-configuration
  {:description      "Update the SFTP user configuration for the given organization."
   :parameters       [organizationId sftpType users]
   :user-roles       #{:admin}
   :input-validators [(merge SftpOrganizationConfiguration org-id-param)
                      valid-configuration]
   :pre-checks       [org/organization-exists]}
  [_]
  (or (sftp/update-organization-configuration organizationId
                                              {:sftpType sftpType
                                               :users    users})
      (ok)))
