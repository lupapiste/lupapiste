(ns lupapalvelu.ui.entries.auth-admin
  "Views used exclusively by the authority admin user"
  (:require [lupapalvelu.ui.attachment.admin.attachment-settings-view :as attachment-settings-view]
            [lupapalvelu.ui.auth-admin.ad-login.ad-login-settings :as ad-login]
            [lupapalvelu.ui.auth-admin.automatic-assignments.view :as automatic-assignments]
            [lupapalvelu.ui.auth-admin.automatic-emails.editor :as automatic-emails]
            [lupapalvelu.ui.auth-admin.application-submit-assurances.editor :as application-submit-assurances]
            [lupapalvelu.ui.auth-admin.backing-system.core :as backing-system]
            [lupapalvelu.ui.auth-admin.bim.checking-plan-defaults :as bim-checking-plan-defaults]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-view :as edit-authority-view]
            [lupapalvelu.ui.auth-admin.foreman-settings.submit :as foreman-settings-submit]
            [lupapalvelu.ui.auth-admin.imports.view :as imports]
            [lupapalvelu.ui.auth-admin.imports.config :as import-config]
            [lupapalvelu.ui.auth-admin.inspection-summary.view :as inspection-summaries]
            [lupapalvelu.ui.auth-admin.invoicing.backend-id :as backend-id]
            [lupapalvelu.ui.auth-admin.oauth.oauth-users :as oauth-users]
            [lupapalvelu.ui.auth-admin.prices.catalogue :as price-catalogue]
            [lupapalvelu.ui.auth-admin.reviews.configuration :as review-config]
            [lupapalvelu.ui.auth-admin.selected-operations.view :as selected-operations]
            [lupapalvelu.ui.auth-admin.stamp.editor :as stamp-editor]
            [lupapalvelu.ui.auth-admin.store.view :as store]
            [lupapalvelu.ui.dmcity.authority-admin :as dm-auth-admin]
            [lupapalvelu.ui.entries.dev-reload :as reload]
            [lupapalvelu.ui.pate.verdict-templates :as verdict-templates]))

(reload/register! :auth-admin
                  [ad-login/mounter
                   application-submit-assurances/mounter
                   attachment-settings-view/mounter
                   automatic-assignments/mounter
                   automatic-emails/mounter
                   backend-id/mounter
                   backing-system/mounter
                   bim-checking-plan-defaults/mounter
                   dm-auth-admin/mounter
                   edit-authority-view/mounter
                   foreman-settings-submit/mounter
                   import-config/mounter
                   imports/mounter
                   inspection-summaries/mounter
                   oauth-users/mounter
                   price-catalogue/mounter
                   review-config/mounter
                   selected-operations/mounter
                   stamp-editor/mounter
                   store/mounter
                   verdict-templates/mounter])
