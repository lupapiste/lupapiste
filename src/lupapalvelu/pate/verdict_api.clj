(ns lupapalvelu.pate.verdict-api
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand defraw notify] :as action]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.pate.verdict :as verdict]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))


;; ------------------------------------------
;; Verdict API
;; ------------------------------------------

;; TODO: Make sure that the functionality (including notifications)
;; and constraints are in sync with the legacy verdict API.

(defn- pate-enabled
  "Pre-checker that fails if Pate is not enabled in the application
  organization."
  [{:keys [organization]}]
  (when (and organization
             (not (:pate-enabled @organization)))
    (fail :error.pate-disabled)))

(defn- verdict-exists
  "Returns pre-checker that fails if the verdict does not exist.
  Additional conditions:
    :editable? fails if the verdict has been published.
    :published? fails if the verdict has NOT been published"
  [& conditions]
  (let [{:keys [editable? published?]} (zipmap conditions (repeat true))]
    (fn [{:keys [data application]}]
      (when-let [verdict-id (:verdict-id data)]
        (let [verdict (util/find-by-id verdict-id
                                       (:pate-verdicts application))]
          (when-not verdict
            (fail! :error.verdict-not-found))
          (when (and editable? (:published verdict))
            (fail! :error.verdict.not-draft))
          (when (and published? (not (:published verdict)))
            (fail! :error.verdict.not-published)))))))

(defn- verdict-filled
  "Precheck that fails if any of the required fields is empty."
  [{data :data :as command}]
  (when (:verdict-id data)
    (when-not (verdict/verdict-filled? command)
      (fail :pate.required-fields))))

(defquery application-verdict-templates
  {:description "List of id, name, default? maps for suitable
  application verdict templates."
   :feature :pate
   :user-roles #{:authority}
   :parameters [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks [pate-enabled]
   :states states/post-submitted-states}
  [{:keys [application organization]}]
  (ok :templates (template/application-verdict-templates @organization
                                                         application)))

(defcommand new-pate-verdict-draft
  {:description "Composes new verdict draft from the latest published
  template and its settings. Returns the verdict-id."
   :feature :pate
   :user-roles #{:authority}
   :parameters [id template-id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks [pate-enabled
                (template/verdict-template-check :application :published)]
   :states states/post-submitted-states}
  [command]
  (ok :verdict-id (verdict/new-verdict-draft template-id command)))

(defquery pate-verdicts
  {:description "List of verdicts. Item properties:

                       id:        Verdict id
                       published: timestamp (can be nil)
                       modified:  timestamp

                      If the user is applicant, only published
                      verdicts are returned."
   :feature :pate
   :user-roles #{:authority :applicant}
   :org-authz-roles roles/reader-org-authz-roles
   :parameters [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks [pate-enabled]
   :states states/post-submitted-states}
  [{:keys [application]}]
  (ok :verdicts (map verdict/verdict-summary
                     (:pate-verdicts application))))

(defquery pate-verdict
  {:description "Verdict and its settings."
   :feature :pate
   :user-roles #{:authority :applicant}
   :org-authz-roles roles/reader-org-authz-roles
   :parameters [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks [pate-enabled
                (verdict-exists)]
   :states states/post-submitted-states}
  [command]
  (ok (assoc (verdict/open-verdict command)
        :filled (verdict/verdict-filled? command))))

(defcommand delete-pate-verdict
  {:description "Deletes verdict. Published verdicts cannot be
  deleted."
   :feature :pate
   :user-roles #{:authority}
   :parameters [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks [pate-enabled
                (verdict-exists :editable?)]
   :states states/post-submitted-states}
  [command]
  (verdict/delete-verdict verdict-id command)
  (ok))

(defcommand edit-pate-verdict
  {:description "Updates verdict data. Returns changes and errors
  lists (items are path-vector value pairs)"
   :feature :pate
   :user-roles #{:authority}
   :parameters [id verdict-id path value]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])
                      (partial action/vector-parameters [:path])]
   :pre-checks [pate-enabled
                (verdict-exists :editable?)]
   :states states/post-submitted-states}
  [command]
  (let [result (verdict/edit-verdict command)]
    (if (:modified result)
      (ok (assoc result
            :filled (verdict/verdict-filled? command true)))
      (template/error-response result))))

(defcommand publish-pate-verdict
  {:description "Publishes verdict."
   :feature :pate
   :user-roles #{:authority}
   :parameters [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks [pate-enabled
                (verdict-exists :editable?)
                verdict-filled]
   ;; As KuntaGML message is generated the application state must be
   ;; at least :sent
   :states (set/difference states/post-sent-states #{:complementNeeded})
   :notified true
   :on-success (notify :application-state-change)}
  [command]
  (ok (verdict/publish-verdict command)))

(defraw preview-pate-verdict
  {:description "Generate preview version of the verdict PDF."
   :feature :pate
   :user-roles #{:authority}
   :parameters [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks [pate-enabled
                (verdict-exists :editable?)
                verdict-filled]
   :states states/post-submitted-states}
  [command]
  (verdict/preview-verdict command))

(defquery pate-verdict-tab
  {:description "Pseudo-query that fails if the Pate verdicts tab
  should not be shown on the UI."
   :feature :pate
   :parameters [:id]
   :user-roles #{:applicant :authority}
   :org-authz-roles roles/reader-org-authz-roles
   :states states/post-submitted-states
   :pre-checks [pate-enabled]}
  [_])

(defn- get-search-fields [fields app]
  (into {} (map #(hash-map % (% app)) fields)))

(defn- create-bulletin [application created verdict-id & [updates]]
  (let [verdict (util/find-by-id verdict-id (:pate-verdicts application))
        app-snapshot (-> (bulletins/create-bulletin-snapshot application)
                         (dissoc :verdicts :pate-verdicts)
                         (merge
                           updates
                           {:application-id (:id application)
                            :pate-verdict verdict
                            :bulletin-op-description (-> verdict :data :bulletin-op-description)}))
        search-fields [:municipality :address :pate-verdict :_applicantIndex
                       :application-id
                       :bulletinState :applicant :organization :bulletin-op-description]
        search-updates (get-search-fields search-fields app-snapshot)]
    (bulletins/snapshot-updates app-snapshot search-updates created)))

(defcommand upsert-pate-verdict-bulletin
  {:description ""
   :feature :pate
   :user-roles #{:authority}
   :parameters [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks [pate-enabled
                (verdict-exists :editable?)]
   :states states/post-submitted-states}
  [{application :application created :created}]
  (let [today-long (tc/to-long (t/today-at-midnight))
        updates (create-bulletin application created verdict-id
                                 {:bulletinState :verdictGiven
                                  :verdictGivenAt today-long
                                  :appealPeriodStartsAt today-long
                                  :appealPeriodEndsAt (tc/to-long (t/plus (t/today-at-midnight) (t/days 14))) ;; TODO!!!
                                  :verdictGivenText ""})]
    (bulletins/upsert-bulletin-by-id (str id "_" verdict-id) updates)
    (ok)))
