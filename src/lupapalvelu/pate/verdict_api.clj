(ns lupapalvelu.pate.verdict-api
  "Pate verdict API supports both 'modern' Pate verdicts with templates
  and 'legacy' verdicts that are old-school verdicts presented with
  Pate schemas."
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand defraw notify some-pre-check
                                        and-pre-check not-pre-check] :as action]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.backing-system.allu.contract :as allu-contract]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.verdict :refer [pate-enabled verdict-exists
                                              backing-system-verdict] :as verdict]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.ya-extension :refer [ya-extension-app?]]
            [sade.core :refer :all]
            [sade.util :as util]))


;; ------------------------------------------
;; Verdict API
;; ------------------------------------------

;; TODO: Make sure that the functionality (including notifications)
;; and constraints are in sync with the legacy verdict API.

(defn- verdicts-supported
  "Precheck that fails if the verdict functionality is not supported for
  the application"
  [{:keys [application]}]
  (when (ya-extension-app? application)
    (fail :ya-extension-application)))

(defn- replacement-check
  "Fails if the target verdict is a) already replaced, b) already being
  replaced (by another draft), c) not published, d) legacy verdict, d)
  missing, e) contract."
  [verdict-key]
  (fn [{:keys [application data]}]
    (when-let [verdict-id (verdict-key data)]
      (when-not (verdict/can-verdict-be-replaced? application verdict-id)
        (fail :error.verdict-cannot-be-replaced)))))

(defn- verdict-filled
  "Precheck that fails if any of the required fields is empty."
  [{data :data :as command}]
  (when (:verdict-id data)
    (when-not (verdict/verdict-filled? command)
      (fail :pate.required-fields))))

(defn- proposal-filled
  "Precheck that fails if any of the required fields for proposal is empty."
  [{data :data :as command}]
  (when (:verdict-id data)
    (when-not (verdict/proposal-filled? command)
      (fail :pate.required-fields))))

(defn- contractual-application
  "Precheck that fails if the application category IS NOT :contract or :allu-contract."
  [command]
  (let [category (verdict/command->category command)]
    (when-not (or (= category :contract) (= category :allu-contract))
      (fail :error.verdict.not-contract))))

(defn- state-in
  "Precheck that fails if the application state is not included in the
  given states."
  [states]
  (fn [{application :application}]
    (when-not (util/includes-as-kw? states (:state application))
      (fail :error.command-illegal-state))))

(defn- can-sign
  "Precheck that fails if the user cannot sign the
  contract. User/company can sign only once."
  [{:keys [application] :as command}]
  (when (and application
             (not (verdict/user-can-sign? command)))
    (fail :error.already-signed)))

(defn- password-matches
  "Precheck that fails if the given password does not match for the
  current user."
  [{:keys [user data]}]
  (when-let [password (:password data)]
    (when-not (usr/get-user-with-password (:username user)
                                          password)
      ;; Two-second delay to discourage brute forcing.
      (Thread/sleep 2000)
      (fail :error.password))))

(defn- legacy-category
  "Precheck that fails if the application category is not (always) legacy."
  [{:keys [application]}]
  (when (some-> application
                schema-util/application->category
                schema-util/legacy-category?
                false?)
    (fail :error.invalid-category)))

(defn- pate-supported-permit-type
  "Precheck that fails if the application does not have a category."
  [{:keys [application]}]
  (when-let [permit-type (:permitType application)]
    (when-not (schema-util/permit-type->categories permit-type)
      (fail :error.unsupported-permit-type))))

(defmethod action/allowed-actions-for-category :pate-verdicts
  [{:keys [application] :as command}]
  (if-let [verdict-id (get-in command [:data :verdict-id])]
    {verdict-id (action/allowed-category-actions-for-command :pate-verdicts
                                                             command)}
    (let [all-verdicts (concat (:verdicts application)
                               (:pate-verdicts application))]
      (->> all-verdicts
           (map (fn [verdict]
                 (assoc command
                        :data {:id         (:id application)
                               :verdict-id (:id verdict)})))
           (map action/foreach-action)
           (map (partial action/filter-actions-by-category :pate-verdicts))
           (map action/validate-actions)
           (zipmap (map :id all-verdicts))))))

;; ------------------------------------------
;; Actions common with modern and legacy
;; ------------------------------------------

(defquery application-verdict-templates
  {:description "List of id, name, default? maps for suitable
  application verdict templates."
   :user-roles #{:authority}
   :parameters [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks [pate-enabled
                verdicts-supported]
   :states states/post-submitted-states}
  [{:keys [application] :as command}]
  (ok :templates (template/application-verdict-templates (template/command->options command)
                                                         application)))
(defquery pate-verdicts
  {:description "List of verdicts. Item properties:

                       id:        Verdict id
                       published: (optional) timestamp
                       modified:  timestamp
                       category:  Verdict category
                       legacy?:   true for legacy verdicts.
                       giver:     Either verdict handler or boardname.
                       verdict-date: (optional) timestamp

                       replaced?  true if the verdict iso replaced.

                       title: Friendly title for the verdict. The
                       format depends on the verdict state and
                       category.

                       signatures: (optional) list of maps:
                           name: signer
                           date timestamp. The list is ordered by
                           dates.

                      If the user is applicant, only published
                      verdicts are returned. Note that verdicts can be
                      contracts."
   :user-roles       #{:authority :applicant}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [verdicts-supported]
   :states           states/post-submitted-states}
  [command]
  (ok :verdicts (vc/verdict-list command)))

(defquery pate-verdict
  {:description      "Verdict and its settings."
   :user-roles       #{:authority :applicant}
   :org-authz-roles  roles/reader-org-authz-roles
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists)]
   :states           states/post-submitted-states}
  [command]
  (ok (assoc (verdict/open-verdict command)
             :filled (verdict/verdict-filled? command))))

(defquery published-pate-verdict
  {:description      "Published tags for the verdict. The response includes
  id, published (timestamp), tags and attachment-ids (of both source
  and target relations)."
   :user-roles       #{:authority :applicant}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(some-pre-check (verdict-exists :published?)
                                      backing-system-verdict)]
   :states           states/post-verdict-states}
  [command]
  (ok :verdict (verdict/published-verdict-details command)))

(defquery pate-parties
  {:description       "Application parties"
   :user-roles        #{:authority :applicant}
   :parameters        [id verdict-id]
   :categories        #{:pate-verdicts}
   :input-validators  [(partial action/non-blank-parameters [:id :verdict-id])]
   :states            states/post-submitted-states}
  [command]
  (ok :parties (verdict/parties command)))

(defcommand send-signature-request
  {:description       "Send request to sign contract"
   :user-roles        #{:authority}
   :parameters        [id verdict-id signer-id]
   :categories        #{:pate-verdicts}
   :input-validators  [(partial action/non-blank-parameters [:id :verdict-id :signer-id])]
   :pre-checks        [(verdict-exists :published? :contract?)]
   :states            states/post-submitted-states
   :notified          true
   :on-success        (notify :pate-signature-request)}
  [command]
  (verdict/add-signature-request command)
  (ok))

(defcommand edit-pate-verdict
  {:description      "Updates verdict data. Returns changes and errors
  lists (items are path-vector value pairs)"
   :user-roles       #{:authority}
   :parameters       [id verdict-id path value]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])
                      (partial action/vector-parameters [:path])]
   :pre-checks       [(verdict-exists :editable?)]
   :states           states/post-submitted-states}
  [command]
  (let [result (verdict/edit-verdict command)]
    (if (:modified result)
      (ok (assoc result
            :filled (verdict/verdict-filled? command true)))
      (template/error-response result))))

(defraw preview-pate-verdict
  {:description      "Generate preview version of the verdict PDF."
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :editable?)
                      (some-pre-check
                        verdict-filled
                        proposal-filled)]
   :states           states/post-submitted-states}
  [command]
  (verdict/preview-verdict command))

(defquery pate-verdict-tab
  {:description      "Pseudo-query that fails if the Pate verdicts tab
  should not be shown on the UI."
   :parameters       [:id]
   :user-roles       #{:applicant :authority}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :pre-checks       [verdicts-supported]
   :states           states/post-submitted-states}
  [_])

(defquery pate-contract-tab
  {:description      "Pseudo-query that fails if the Pate contracts tab
  should not be shown on the UI. Note that pate-contract-tab always
  implies pate-verdict-tab, too."
   :parameters       [:id]
   :user-roles       #{:applicant :authority}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :pre-checks       [contractual-application
                      verdicts-supported]
   :states           states/post-submitted-states}
  [_])

(defcommand sign-pate-contract
  {:description "Adds the user as a signatory to a published Pate
  contract if the password matches. the same name (e.g., the user can resign if the initial handler name
  is different from the user name or if the user later changes her
  name)."
   :categories       #{:pate-verdicts}
   :parameters       [:id :verdict-id :password]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id :password])]
   :pre-checks       [(verdict-exists :published? :contract?)
                      can-sign
                      password-matches]
   :states           states/post-verdict-states
   :user-roles       #{:applicant :authority}
   :user-authz-roles roles/writer-roles-with-foreman}
  [command]
  (verdict/sign-contract command)
  (ok))

(defcommand sign-allu-contract
  {:description "Adds the user as a signatory to a published Pate contract"
   :categories       #{:pate-verdicts}
   :parameters       [:id :verdict-id :password]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id :password])]
   :pre-checks       [(verdict-exists :allu-contract?)
                      can-sign
                      password-matches]
   :states           states/post-verdict-states
   :user-roles       #{:applicant}
   :user-authz-roles roles/default-authz-writer-roles}
  [command]
  (allu-contract/sign-allu-contract command)
  (ok))

(defraw verdict-pdf
  {:description      "Endpoint for downloading the verdict attachment."
   :parameters       [:id :verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :user-roles       #{:applicant :authority :oirAuthority :financialAuthority}
   :user-authz-roles roles/all-authz-roles
   :pre-checks       [(verdict-exists :published?)]}
  [command]
  (verdict/download-verdict command))

(defraw proposal-pdf
  {:description      "Endpoint for downloading the verdict attachment."
   :parameters       [:id :verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :user-roles       #{:applicant :authority :oirAuthority :financialAuthority}
   :user-authz-roles roles/all-authz-roles
   :pre-checks       [(some-pre-check
                        (verdict-exists :published?)
                        (verdict-exists :proposal?))]}
  [command]
  (verdict/download-proposal command))

;; ------------------------------------------
;; Modern actions
;; ------------------------------------------

(defquery replace-pate-verdict
  {:description      "Pseudo-query for checking whether a verdict can be
  replaced."
   :user-roles       #{:authority}
   :parameters       [:id :verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [pate-enabled
                      (replacement-check :verdict-id)]
   :states           states/post-submitted-states}
  [_])

(defcommand new-pate-verdict-draft
  {:description         "Composes new verdict draft from the latest published
  template and its settings. Returns the verdict-id."
   :user-roles          #{:authority}
   :parameters          [:id :template-id]
   :optional-parameters [:replacement-id]
   :input-validators    [(partial action/non-blank-parameters [:id])]
   :pre-checks          [pate-enabled
                         verdicts-supported
                         pate-supported-permit-type
                         (not-pre-check legacy-category)
                         (template/verdict-template-check :application :published)
                         (replacement-check :replacement-id)]
   :states              states/post-submitted-states}
  [command]
  (ok :verdict-id (verdict/new-verdict-draft (template/command->options command))))

(defcommand copy-pate-verdict-draft
  {:description         "Composes new verdict draft from the latest published
  template and its settings. Returns the verdict-id."
   :user-roles          #{:authority}
   :parameters          [id replacement-id]
   :input-validators    [(partial action/non-blank-parameters [:id])]
   :pre-checks          [pate-enabled
                         (not-pre-check legacy-category)
                         (template/verdict-template-check :application :published)
                         (replacement-check :replacement-id)]
   :states              states/post-submitted-states}
  [command]
  (ok :verdict-id (verdict/copy-verdict-draft command replacement-id)))

(defcommand delete-pate-verdict
  {:description      "Deletes verdict. Published verdicts cannot be
  deleted."
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [pate-enabled
                      (verdict-exists :editable? :modern?)]
   :states           states/post-submitted-states}
  [command]
  (verdict/delete-verdict verdict-id command)
  (ok))

(defcommand publish-pate-verdict
  {:description      "Publishes verdict."
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           (set/difference states/post-submitted-states
                                     #{:finished})
   :pre-checks       [pate-enabled
                      (verdict-exists :editable? :modern?)
                      verdict-filled
                      (some-pre-check
                        (and-pre-check (verdict-exists :contract?)
                                              (state-in states/post-submitted-states))
                        ;; As KuntaGML message is generated the
                        ;; application state must be at least :sent
                        (state-in (set/difference states/post-submitted-states
                                                  #{:complementNeeded})))]
   :notified         true
   :on-success       [(notify :application-state-change)
                      invoices/new-verdict-invoice]}
  [command]
  (ok (verdict/publish-verdict command)))

;; ------------------------------------------
;; Legacy actions
;; ------------------------------------------

(defcommand new-legacy-verdict-draft
  {:description "Composes new legacy verdict draft. Even if Pate is
  enabled, some categories are only supported by legacy
  verdicts. Returns the verdict-id."
   :user-roles       #{:authority}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [verdicts-supported
                      pate-supported-permit-type
                      (some-pre-check (not-pre-check pate-enabled)
                                      legacy-category)]
   :states           states/post-submitted-states}
  [command]
  (ok :verdict-id (verdict/new-legacy-verdict-draft command)))

(defcommand delete-legacy-verdict
  {:description      "Deletes legacy verdict, its tasks and
  attachments. Rewinds application state if needed."
   :user-roles       #{:authority}
   :parameters       [:id :verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :legacy?)
                      (some-pre-check (verdict-exists :legacy? :editable?)
                                             (state-in states/give-verdict-states))]
   :states           states/post-submitted-states
   :notified         true}
  [command]
  (ok (verdict/delete-legacy-verdict command)))

(defcommand publish-legacy-verdict
  {:description      "Publishes legacy verdict."
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :editable? :legacy?)
                      verdict-filled]
   :states           (set/difference states/post-submitted-states
                                     #{:finished :complementNeeded})
   :notified         true
   :on-success       [(notify :application-state-change)
                      invoices/new-verdict-invoice]}
  [command]
  (ok (verdict/publish-verdict command)))

;; ------------------------------------------
;; Verdict proposal
;; ------------------------------------------

(defcommand publish-verdict-proposal
  {:description      "Publishes verdict proposal."
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           (set/difference states/post-submitted-states
                                     #{:finished})
   :pre-checks       [pate-enabled
                      (verdict-exists :editable?)
                      proposal-filled]}
  [command]
  (ok (verdict/publish-verdict-proposal command)))
