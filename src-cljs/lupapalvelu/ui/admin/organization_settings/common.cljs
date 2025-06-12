(ns lupapalvelu.ui.admin.organization-settings.common
  (:require [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.admin.organization-settings.backend-systems :as backend-system]
            [lupapalvelu.ui.admin.organization-settings.invoicing-config :as invoicing]
            [lupapalvelu.ui.admin.organization-settings.ryhti-config :as ryhti-config]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::init
  (fn [cofx [_ visible?]]
    (nutil/merge-every-fn cofx
                          (if visible?
                            [[backend-system/on-visible]
                             [invoicing/on-visible]
                             [ryhti-config/on-visible]]
                            [[backend-system/on-hidden]
                             [invoicing/on-hidden]
                             [ryhti-config/on-hidden]]))))
