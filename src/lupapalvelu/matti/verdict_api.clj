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
            [sade.util :as util]
            [lupapalvelu.application-bulletins :as bulletins]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))


;; ------------------------------------------
;; Verdict API
;; ------------------------------------------

;; TODO: Make sure that the functionality (including notifications)
;; and constraints are in sync with the legacy verdict API.

(defn- matti-enabled
  "Pre-checker that fails if Matti is not enabled in the application
  organization."
  [{:keys [organization]}]
  (when (and organization
             (not (:matti-enabled @organization)))
    (fail :error.matti-disabled)))

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
   :pre-checks       [matti-enabled]
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
   :pre-checks       [matti-enabled
                      (template/verdict-template-check :application :published)]
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
   :pre-checks       [matti-enabled]
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
   :pre-checks       [matti-enabled
                      (verdict-exists)]
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
   :pre-checks       [matti-enabled
                      (verdict-exists :editable?)]
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
   :pre-checks       [matti-enabled
                      (verdict-exists :editable?)]
   :states           states/give-verdict-states}
  [command]
  (ok (verdict/edit-verdict command)))

(defquery matti-verdict-tab
  {:description     "Pseudo-query that fails if the Matti verdicts tab
  should not be shown on the UI."
   :feature         :matti
   :parameters      [:id]
   :user-roles      #{:applicant :authority}
   :org-authz-roles roles/reader-org-authz-roles
   :states          (states/all-states-but [:draft :open])
   :pre-checks      [matti-enabled]}
  [_])

(defn- get-search-fields [fields app]
  (into {} (map #(hash-map % (% app)) fields)))

(defn- create-bulletin [application created verdict-id & [updates]]
  (let [verdict (util/find-by-id verdict-id (:matti-verdicts application))
        app-snapshot (-> (bulletins/create-bulletin-snapshot application)
                         (dissoc :verdicts :matti-verdicts)
                         (merge
                           updates
                           {:application-id (:id application)
                            :matti-verdict verdict
                            :bulletin-op-description (-> verdict :data :bulletin-op-description)}))
        search-fields [:municipality :address :matti-verdict :_applicantIndex
                       :application-id
                       :bulletinState :applicant :organization :bulletin-op-description]
        search-updates (get-search-fields search-fields app-snapshot)]
    (bulletins/snapshot-updates app-snapshot search-updates created)))

(defcommand upsert-matti-verdict-bulletin
  {:description      ""
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :editable?)]
   :states           states/give-verdict-states}
  [{application :application created :created}]
  (let [today-long (tc/to-long (t/today-at-midnight))
        updates (create-bulletin application created verdict-id
                                 {:bulletinState :verdictGiven
                                  :verdictGivenAt       today-long
                                  :appealPeriodStartsAt today-long
                                  :appealPeriodEndsAt   (tc/to-long (t/plus (t/today-at-midnight) (t/days 14)))  ;; TODO!!!
                                  :verdictGivenText ""})]
    (bulletins/upsert-bulletin-by-id (str id "_" verdict-id) updates)
    (ok)))