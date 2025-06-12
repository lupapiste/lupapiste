(ns lupapalvelu.logging-api
  (:require [lupapalvelu.action :refer [defquery] :as action]
            [sade.core :refer [fail ok]]
            [sade.env :as env]))

(defquery newest-version
  {:user-roles #{:anonymous}
   :parameters [frontendBuild]
   :input-validators [(partial action/non-blank-parameters [:frontendBuild])]}
  [_]
  (if (= frontendBuild env/build-number)
    (ok)
    (fail :frontend-too-old)))
