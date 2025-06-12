(ns lupapalvelu.ui.entries.shared-auth-and-applicant
  "Views used by both the authority user and the applicant.
  Note that docgen related fields should be in `lupapalvelu.ui.entries.shared-docgen`."
  (:require [lupapalvelu.ui.application.apply-execution-permit :as application-apply-execution-permit]
            [lupapalvelu.ui.application.building-product-catalog-dialog :as building-product-catalog-dialog]
            [lupapalvelu.ui.application.designer-view :as designer-view]
            [lupapalvelu.ui.application.field-issue-list :as field-issue-list]
            [lupapalvelu.ui.application.foremen-view :as foremen-view]
            [lupapalvelu.ui.application.info]
            [lupapalvelu.ui.application.invite-foreman :as invite-foreman]
            [lupapalvelu.ui.application.low-carbon-assessment :as low-carbon-assessment]
            [lupapalvelu.ui.application.parties]
            [lupapalvelu.ui.assurances.view :as assurances-view]
            [lupapalvelu.ui.attachment.attachments-view :as attachment-view]
            [lupapalvelu.ui.attachment.transfer :as transfer]
            [lupapalvelu.ui.bulletins.comments :as bulletin-comments]
            [lupapalvelu.ui.company.reports :as company-reports]
            [lupapalvelu.ui.conversion.view :as conversion]
            [lupapalvelu.ui.dmcity.states-tab :as dm-states-tab]
            [lupapalvelu.ui.entries.dev-reload :as reload]
            [lupapalvelu.ui.filebank.view :as filebank]
            [lupapalvelu.ui.inspection-summaries :as inspection-summaries]
            [lupapalvelu.ui.location.bim-viewer.model-viewer-page :as model-viewer-page]
            [lupapalvelu.ui.location.locations-operations :as locations-operations]
            [lupapalvelu.ui.location.model-details-page :as model-details-page]
            [lupapalvelu.ui.notices.view :as notices]
            [lupapalvelu.ui.party.designers-warning :as party-designers]
            [lupapalvelu.ui.party.parties-as-krysp :as party-as-krysp]
            [lupapalvelu.ui.pate.verdict :as verdict]
            [lupapalvelu.ui.pate.verdicts :as verdicts]
            [lupapalvelu.ui.printing-order.composer :as printing-order-composer]
            [lupapalvelu.ui.process.process :as process]
            [lupapalvelu.ui.search.dashboard :as dashboard]
            [lupapalvelu.ui.statistics.entries :as stats-entries]
            [lupapalvelu.ui.statistics.entriestitle :as stats-entriestitle]
            [lupapalvelu.ui.statistics.summary :as stats-summary]
            [lupapalvelu.ui.task.task-controls :as task-controls]
            [lupapalvelu.ui.task.task-view :as docgen-tasks]))

(reload/register! :auth-and-app
                  [application-apply-execution-permit/mounter
                   assurances-view/mounter
                   attachment-view/mounter
                   building-product-catalog-dialog/mounter
                   bulletin-comments/mounter
                   company-reports/mounter
                   conversion/mounter
                   dashboard/mounter
                   designer-view/mounter
                   dm-states-tab/mounter
                   docgen-tasks/mounter
                   field-issue-list/mounter
                   filebank/mounter
                   foremen-view/mounter
                   inspection-summaries/mounter
                   invite-foreman/mounter
                   locations-operations/mounter
                   low-carbon-assessment/mounter
                   model-details-page/mounter
                   model-viewer-page/mounter
                   notices/mounter
                   party-as-krysp/mounter
                   party-designers/mounter
                   printing-order-composer/mounter
                   process/mounter
                   stats-entries/mounter
                   stats-entriestitle/mounter
                   stats-summary/mounter
                   task-controls/mounter
                   transfer/mounter
                   verdict/mounter
                   verdicts/mounter])
