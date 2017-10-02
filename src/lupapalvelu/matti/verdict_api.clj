(ns lupapalvelu.matti.verdict-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.matti.verdict :as verdict]
            [lupapalvelu.matti.verdict-template :as template]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))


;; ------------------------------------------
;; Verdict API
;; ------------------------------------------

;; TODO: Make sure that the functionality (including notifications)
;; and constraints are in sync with the legacy verdict API.

(defn- verdict-exists
  "Returns pre-checker that fails if the verdict does not exist.
  Additional conditions:
    :editable? fails if the verdict has been published."
  [& conditions]
  (let [{:keys [editable?]} (zipmap conditions (repeat true))]
    (fn [{:keys [data application]}]
      (when-let [verdict-id (:verdict-id data)]
        (let [verdict (util/find-by-id verdict-id
                                       (:matti-verdicts application))]
          (when-not verdict
            (fail! :error.verdict-not-found))
          (when (and editable? (:published verdict))
            (fail! :error.verdict.not-draft)))))))

(defquery application-verdict-templates
  {:description      "List of id, name, default? maps for suitable
  application verdict templates."
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           states/give-verdict-states}
  [{:keys [application organization]}]
  (ok :templates (template/application-verdict-templates @organization
                                                         application)))

(defcommand new-matti-verdict-draft
  {:description      "Composes new verdict draft from the latest published
  template and its settings."
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id template-id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [(template/verdict-template-check :application :published)]
   :states           states/give-verdict-states}
  [command]
  (ok (verdict/new-verdict-draft template-id command)))

(defquery matti-verdicts
  {:description      "List of verdicts. Item properties:
                       id:        Verdict id
                       published: timestamp (can be nil)
                       modified:  timestamp"
   :feature          :matti
   :user-roles       #{:authority :applicant}
   :org-authz-roles  roles/reader-org-authz-roles
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           (states/all-states-but [:draft :open])}
  [{:keys [application]}]
  (ok :verdicts (map verdict/verdict-summary
                     (:matti-verdicts application))))

(defquery matti-verdict
  {:description      "Verdict and its settings."
   :feature          :matti
   :user-roles       #{:authority :applicant}
   :org-authz-roles  roles/reader-org-authz-roles
   :parameters       [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists)]
   :states           states/give-verdict-states}
  [command]
  (ok (verdict/open-verdict command)))

(defcommand delete-matti-verdict
  {:description      "Deletes verdict. Published verdicts cannot be
  deleted."
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :editable?)]
   :states           (states/all-states-but [:draft :open])}
  [command]
  (verdict/delete-verdict verdict-id command)
  (ok))

(defcommand edit-matti-verdict
  {:description      "Updates verdict data. Returns changes and errors
  lists (items are path-vector value pairs)"
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id verdict-id path value]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])
                      (partial action/vector-parameters [:path])]
   :pre-checks       [(verdict-exists :editable?)]
   :states           states/give-verdict-states}
  [command]
  (ok (verdict/edit-verdict command)))
