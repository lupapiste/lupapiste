(ns lupapalvelu.ui.ui-components
  "Note: some namespaces (lupapalvelu.ui.components, etc) required so they are included
  in the compiling step"
  (:require [lupapalvelu.ui.admin.users.change-user-email]
            [lupapalvelu.ui.admin.users.create-authority-user]
            [lupapalvelu.ui.attachment.admin.attachment-settings-view :as attachment-settings-view]
            [lupapalvelu.ui.attachment.attachments-view :as attachment-view]
            [lupapalvelu.ui.attachment.transfer :as transfer]
            [lupapalvelu.ui.auth-admin.ad-login.ad-login-settings :as ad-login]
            [lupapalvelu.ui.auth-admin.automatic-assignments.view :as automatic-assignments]
            [lupapalvelu.ui.auth-admin.automatic-emails.editor :as automatic-emails]
            [lupapalvelu.ui.auth-admin.edit-authority.edit-view :as edit-authority-view]
            [lupapalvelu.ui.auth-admin.invoicing.backend-id :as backend-id]
            [lupapalvelu.ui.auth-admin.prices.catalogue :as price-catalogue]
            [lupapalvelu.ui.auth-admin.reviews.configuration :as review-config]
            [lupapalvelu.ui.auth-admin.stamp.editor :as stamp-editor]
            [lupapalvelu.ui.bulletins.bulletin-preamble :as bulletin-preamble]
            [lupapalvelu.ui.bulletins.local-bulletins :as local-bulletins]
            [lupapalvelu.ui.company.reports :as company-reports]
            [lupapalvelu.ui.components]
            [lupapalvelu.ui.filebank.view :as filebank]
            [lupapalvelu.ui.inspection-summaries :as inspection-summaries]
            [lupapalvelu.ui.invoices.invoices :as invoices]
            [lupapalvelu.ui.location.locations-operations :as locations-operations]
            [lupapalvelu.ui.pate.verdict :as verdict]
            [lupapalvelu.ui.pate.verdict-templates :as verdict-templates]
            [lupapalvelu.ui.pate.verdicts :as verdicts]
            [lupapalvelu.ui.printing-order.composer :as printing-order-composer]
            [lupapalvelu.ui.property-list.view]
            [lupapalvelu.ui.search.dashboard :as dashboard]
            [lupapalvelu.ui.sftp.configuration :as sftp]
            [re-frame.subs :as rf-subs]
            [lupapalvelu.ui.admin.screenmessage :as screenmessage]
            [lupapalvelu.ui.auth-admin.store.view :as store]))

(defn ^:dev/after-load reload-hook []
  (rf-subs/clear-subscription-cache!)
  (->> [inspection-summaries/mount-component
        stamp-editor/mount-component
        automatic-emails/mount-component
        price-catalogue/mount-component
        verdict-templates/mount-component
        verdicts/mount-component
        invoices/mount-component
        verdict/mount-component
        printing-order-composer/mount-component
        local-bulletins/mount-component
        bulletin-preamble/mount-component
        company-reports/mount-component
        edit-authority-view/mount-component
        ad-login/mount-component
        filebank/mount-component
        review-config/mount-component
        automatic-assignments/mount-component
        attachment-view/mount-component
        dashboard/mount-component
        backend-id/mount-component
        sftp/mount-component
        transfer/mount-component
        locations-operations/mount-component
        attachment-settings-view/mount-component
        screenmessage/mount-component
        store/mount-component]
       (run! (fn [mount-fn]
               (try (mount-fn)
                    (catch js/Error e
                      (when-not (re-find #"(?i)Target container is not a DOM element" (aget e "message"))
                        (throw e))))))))
