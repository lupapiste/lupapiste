(ns lupapalvelu.conversion.conversion-api
  (:require [sade.core :refer :all]
            [lupapalvelu.action :as action :refer [defcommand]]
            [lupapalvelu.conversion.kuntagml-converter :as converter]))

(defcommand convert-application
  {:parameters       [kuntalupatunnus]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus])]
   :feature :conversion-debug
   :permissions [{:required []}]}
  [{:keys [user] :as command}]
  (converter/debug command))

