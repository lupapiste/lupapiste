(ns lupapalvelu.notice-api
  (:require [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [monger.operators :refer :all])
)

(defcommand toggle-urgent
  {:parameters [id urgent]
   :roles [:authority]
   :input-validators [(partial action/boolean-parameters [:urgent])]}
  [command]
  (update-application command {$set {:urgent urgent}}))

(defcommand add-authority-notice
  {:parameters [id authorityNotice]
   :roles [:authority]}
  [command]
  (update-application command {$set {:authorityNotice authorityNotice}}))
