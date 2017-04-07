(ns lupapalvelu.ui.ui-components
  (:require [lupapalvelu.ui.inspection-summaries :as inspection-summaries]
            [lupapalvelu.ui.auth-admin.stamp-editor :as stamp-editor]))

(defn reload-hook []
  (inspection-summaries/mount-component)
  (stamp-editor/mount-component))
