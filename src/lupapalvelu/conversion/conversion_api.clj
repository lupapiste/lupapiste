(ns lupapalvelu.conversion.conversion-api
  (:require [sade.core :refer :all]
            [lupapalvelu.action :as action :refer [defcommand execute]]
            [lupapalvelu.conversion.kuntagml-converter :as converter]))

(defcommand convert-application
  {:parameters       [kuntalupatunnus]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus])]
   :feature :conversion-debug
   :user-roles   #{:authority}
   :permissions [{:required []}]}
  [command]
  (converter/debug command))
