(ns lupapalvelu.ui.suomifi-login.root
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.misc]
            [lupapalvelu.ui.suomifi-login.change-email :as change-email]
            [lupapalvelu.ui.suomifi-login.change-password :as change-pw]
            [lupapalvelu.ui.suomifi-login.error :as error]
            [lupapalvelu.ui.suomifi-login.registration :as registration]
            [lupapalvelu.ui.suomifi-login.subs :as subs]
            [lupapalvelu.ui.suomifi-login.success :as success]))

(defn view []
  (if (<sub [::subs/loading-users?])
    [:div (common/loc :loading)]
    [:<>
     [success/view]
     [error/view]
     [registration/view]
     [change-pw/view]
     [change-email/view]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _]
  (ui-dom/mount! mounter dom-id))
