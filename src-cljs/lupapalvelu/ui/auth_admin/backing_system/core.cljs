(ns lupapalvelu.ui.auth-admin.backing-system.core
  (:require [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.auth-admin.backing-system.common :as common]
            [lupapalvelu.ui.auth-admin.backing-system.sftp]
            [lupapalvelu.ui.dom :as ui-dom]))

(defonce mounter (ui-dom/init :reagent (fn [] common/view)))

(defn ^:export start
  [dom-id]
  (ui-dom/mount! mounter dom-id)
  (>evt [::common/fetch-configs]))
