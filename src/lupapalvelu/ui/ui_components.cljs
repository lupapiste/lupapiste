(ns lupapalvelu.ui.ui-components
  (:require [lupapalvelu.ui.inspection-summaries :as inspection-summaries]))

(defn reload-hook []
  (inspection-summaries/mount-component))
