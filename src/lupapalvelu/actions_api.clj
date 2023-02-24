(ns lupapalvelu.actions-api
  (:require [clojure.set :refer [difference]]
            [sade.core :refer :all]
            [sade.util :refer [fn-> fn->>]]
            [lupapalvelu.action :refer [defquery] :as action]))

;;
;; Default actions
;;

(defquery actions
  {:permissions [{:required [:global/get-actions]}]
   :description "List of all actions and their meta-data."} [_]
  (ok :actions (action/serializable-actions)))

(defquery allowed-actions
  {:permissions [{:required []}]}
  [command]
  (ok :actions (->> (action/foreach-action command)
                    (action/validate-actions))))

(defquery allowed-actions-for-category
  {:description      "Returns map of allowed actions for a category (attachments, tasks, etc.)"
   :permissions      [{:required []}]}
  [command]
  (if-let [actions-by-id (action/allowed-actions-for-category command)]
    (ok :actionsById actions-by-id)
    (fail :error.invalid-category)))
