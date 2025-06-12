(ns lupapalvelu.actions-api
  (:require [lupapalvelu.action :refer [defquery] :as action]
            [sade.core :refer [fail ok Response]]
            [schema.core :as sc]))

;;
;; Default actions
;;

(defquery allowed-actions
  {:permissions         [{:required []}]
   :response-schema     {:actions {sc/Keyword Response}}
   :allowed-token-types :all}
  [command]
  ;; `allowed-actions` might be called with a `:id` parameter,
  ;; which means that the `command` already contains the application
  (if-let [[_ token] (action/command-token command)]
    ;; Token used when external integrations invoke `allowed-actions`
    (ok :actions (->> (action/actions-by-token command (:token-type token))
                      (action/validate-actions)))
    (ok :actions (->> (action/foreach-action command)
                      (action/validate-actions)))))

(defquery allowed-action
  {:description    "Like `allowed-actions`, but checks a single action."
   :permissions    [{:required []}]
   :request-schema {:action                 sc/Keyword
                    (sc/optional-key :args) {sc/Keyword sc/Any}}}
  [{{:keys [action args]} :data :as command}]
  (let [;; Make sure we clear any `:application nil` entries
        ;; in the `command` so that we will fetch the app if `args` contains an `:id`.
        skeleton    (-> command
                        (select-keys #{:user :created :lang :web :session})
                        (assoc :data (or args {})))
        action-meta (get (action/get-actions) action)
        command     (action/merge-action-definition-and-data
                      [action action-meta] skeleton)]
    (if command
      (action/validate command)
      (fail :error.command-not-found))))

(defquery allowed-actions-for-category
  {:description      "Returns map of allowed actions for a category (attachments, tasks, etc.)"
   :permissions      [{:required []}]
   :input-validators [(fn [{data :data}]
                        (when-not (action/action-category? (:category data))
                          (fail :error.invalid-category)))]}
  [command]
  (ok :actionsById (or (action/allowed-actions-for-category command) {})))
