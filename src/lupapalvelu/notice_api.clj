(ns lupapalvelu.notice-api
  (:require [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [monger.operators :refer :all])
)

(defn validate-urgency [{{urgency :urgency} :data}]
  (when-not (#{"normal" "urgent" "pending"} urgency)
    (fail :error.unknown-urgency-state)))

(defcommand change-urgency
  {:parameters [id urgency]
   :roles [:authority]
   :states action/all-states
   :input-validators [validate-urgency]}
  [command]
  (update-application command {$set {:urgency urgency}}))

(defcommand add-authority-notice
  {:parameters [id authorityNotice]
   :states action/all-states
   :roles [:authority]}
  [command]
  (update-application command {$set {:authorityNotice authorityNotice}}))
