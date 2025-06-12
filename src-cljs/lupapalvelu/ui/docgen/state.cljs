(ns lupapalvelu.ui.docgen.state
  "Handle state for docgen views
  
  Separate ns makes it easier to write tests for state handling,
  without loading the React components. I.e. map-component used by
  field.property-list can't be loaded on Node."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.docgen.component.accordion-assignments :as accordion-assignments]
            [lupapalvelu.ui.docgen.component.accordion-state :as accordion-state]
            [lupapalvelu.ui.docgen.component.reject-note :as reject-note]
            [lupapalvelu.ui.docgen.feature.allu-application-kind :as allu-application-kind]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.approvals :as approvals]
            [lupapalvelu.ui.docgen.feature.assignments :as assignments]
            [lupapalvelu.ui.docgen.feature.attachments :as attachments]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.buildings :as buildings]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.demolition-notice :as demolition-notice]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.edit :as edit]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.feature.filter-by-code :as filter-by-code]
            [lupapalvelu.ui.docgen.feature.flags :as flags]
            [lupapalvelu.ui.docgen.feature.foreman-history :as foreman-history]
            [lupapalvelu.ui.docgen.feature.identifiers :as identifiers]
            [lupapalvelu.ui.docgen.feature.maaraala :as maaraala]
            [lupapalvelu.ui.docgen.feature.operation :as operation]
            [lupapalvelu.ui.docgen.feature.partial-review-buildings :as partial-review-buildings]
            [lupapalvelu.ui.docgen.feature.parties :as parties]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.docgen-allu-drawings :as field-docgen-allu-drawings]
            [lupapalvelu.ui.docgen.field.docgen-fill-my-info-button :as fill-my-info]
            [lupapalvelu.ui.docgen.field.docgen-huoneistot-table :as field-docgen-huoneistot]
            [lupapalvelu.ui.docgen.field.link-permit-selector :as field-link-permit-selector]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]))

;;;
;;; Events

(defn- register-auth-model-listener [_ _]
  {:fx [[:dispatch [:auth/refresh]]]})

(rf/reg-event-fx ::init
  (fn [cofx [_ visible?]]
    (if visible?
      (nutil/merge-every-fn cofx [[hub/subscribe-fx
                                   "cljs-app-loaded"
                                   (fn [{:keys [app applicationDetails]}]
                                     (rf/dispatch [::app-loaded {:app     app
                                                                 :details applicationDetails}]))
                                   ::hub-subscription]
                                  [register-auth-model-listener]])
      (nutil/merge-every-fn cofx [[hub/unsubscribe-fx
                                   ::hub-subscription]
                                  [accordion-state/on-unload]
                                  [auth/on-unload]
                                  [allu-application-kind/on-unload]
                                  [assignments/on-unload]
                                  [app/on-unload]
                                  [attachments/on-unload]
                                  [approvals/on-unload]
                                  [buildings/on-unload]
                                  [committed/on-unload]
                                  [docs/on-unload]
                                  [edit/on-unload]
                                  [field-schemas/on-unload]
                                  [fill-my-info/on-unload]
                                  [field-docgen-huoneistot/on-unload]
                                  [field-docgen-allu-drawings/on-unload]
                                  [filter-by-code/on-unload]
                                  [flags/on-unload]
                                  [identifiers/on-unload]
                                  [maaraala/on-unload]
                                  [operation/on-unload]
                                  [parties/on-unload]
                                  [reject-note/on-unload]
                                  [validation/on-unload]
                                  [value/on-unload]
                                  [foreman-history/on-unload]
                                  [field-link-permit-selector/on-unload]
                                  [partial-review-buildings/on-unload]
                                  [accordion-assignments/on-unload]]))))

(rf/reg-event-fx ::app-loaded
  (fn [cofx [_ {:keys [app details] :as args}]]
    {:pre [(map? app) (map? details)]}
    (nutil/merge-every-fn cofx [[accordion-state/on-app-loaded args]
                                [auth/on-app-loaded args]
                                [assignments/on-app-loaded args]
                                [app/on-app-loaded args]
                                [attachments/on-app-loaded args]
                                [approvals/on-app-loaded args]
                                [buildings/on-app-loaded args]
                                [committed/on-app-loaded args]
                                [docs/on-app-loaded args]
                                [edit/on-app-loaded args]
                                [field-schemas/on-app-loaded args]
                                [filter-by-code/on-app-loaded args]
                                [flags/on-app-loaded args]
                                [identifiers/on-app-loaded args]
                                [maaraala/on-app-loaded args]
                                [operation/on-app-loaded args]
                                [parties/on-app-loaded args]
                                [reject-note/on-app-loaded args]
                                [validation/on-app-loaded args]
                                [value/on-app-loaded args]
                                [allu-application-kind/on-app-loaded args]
                                [foreman-history/on-app-loaded args]
                                [demolition-notice/on-app-loaded args]
                                [field-link-permit-selector/on-app-loaded args]])))
