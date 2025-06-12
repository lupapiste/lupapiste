(ns lupapalvelu.ui.entries.admin
  "Views used exclusively by the admin user"
  (:require [lupapalvelu.ui.admin.ad-login.ad-login-settings :as admin-ad-login]
            [lupapalvelu.ui.admin.arcgis.arcgis-settings :as admin-arcgis]
            [lupapalvelu.ui.admin.asiointi-api.settings :as asiointi-api-settings]
            [lupapalvelu.ui.admin.bim.checking-plan-config :as bim-checking-plan-config]
            [lupapalvelu.ui.admin.company.subscribers :as company-subscribers]
            [lupapalvelu.ui.admin.elasticsearch.core :as elasticsearch]
            [lupapalvelu.ui.admin.import-export.core :as import-export]
            [lupapalvelu.ui.admin.organization-settings.backend-systems :as organization-backend-systems]
            [lupapalvelu.ui.admin.organization-settings.common]
            [lupapalvelu.ui.admin.organization-settings.invoicing-config :as invoicing-config]
            [lupapalvelu.ui.admin.organization-settings.ryhti-config :as ryhti-config]
            [lupapalvelu.ui.admin.reports.core :as reports]
            [lupapalvelu.ui.admin.screenmessage :as screenmessage]
            [lupapalvelu.ui.admin.sso-keys.core :as sso]
            [lupapalvelu.ui.admin.users.change-user-email :as change-user-email]
            [lupapalvelu.ui.admin.users.create-authority-user :as create-auth-user]
            [lupapalvelu.ui.admin.users.edit-rest-api-user :as edit-rau]
            [lupapalvelu.ui.dmcity.admin :as dm-admin]
            [lupapalvelu.ui.entries.dev-reload :as reload]
            [lupapalvelu.ui.sftp.configuration :as sftp]))

(reload/register! :admin
                  [admin-ad-login/mounter
                   admin-arcgis/mounter
                   asiointi-api-settings/mounter
                   bim-checking-plan-config/mounter
                   change-user-email/mounter
                   company-subscribers/mounter
                   create-auth-user/mounter
                   dm-admin/mounter
                   edit-rau/mounter
                   import-export/mounter
                   organization-backend-systems/mounter
                   invoicing-config/mounter
                   reports/mounter
                   screenmessage/mounter
                   sftp/mounter
                   sso/mounter
                   elasticsearch/mounter
                   ryhti-config/mounter])
