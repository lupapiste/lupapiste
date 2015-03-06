(ns lupapalvelu.asianhallinta-config-api
  "Configuration API for asianhallinta"
  (:require [sade.core :refer :all]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.organization :as org]))


(defquery asianhallinta-config
  {:roles [:authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization-id (first organizations)]
    (if-let [{:keys [scope]} (org/get-organization organization-id)]
      (ok :scope scope)
      (fail :error.unknown-organization))))

(defcommand save-asianhallinta-config
  {:parameters [permitType municipality enabled version]
   :roles [:authorityAdmin]}
  [command]
  (ok))
