(ns lupapalvelu.pate.verdict-api
  "Pate verdict API supports both 'modern' Pate verdicts with templates
  and 'legacy' verdicts that are old-school verdicts presented with
  Pate schemas."
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand defraw notify some-pre-check
                                        and-pre-check not-pre-check] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.backing-system.allu.contract :as allu-contract]
            [lupapalvelu.invoices :as invoices]
            [lupapalvelu.notice-forms :as forms]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.tasks :as tasks]
            [lupapalvelu.pate.verdict :refer [pate-enabled verdict-exists
                                              backing-system-verdict] :as verdict]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-date :as verdict-date]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.suomifi-messages :refer [suomifi-enabled]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-robot.core :as robot]
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
  (cond
    (ya-extension-app? application)          (fail :ya-extension-application)
    (app/ymp-clarification-app? application) (fail :ymp-clarification-application)))

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

(defn- proposal-finalized
  "Precheck that fails if the verdict is proposal (draft or not)
  AND :verdict-flag value is false."
  [{:keys [data application] :as command}]
  (when-let [verdict (some-> data :verdict-id
                             (util/find-by-id (:pate-verdicts application)))]
    (when (and (vc/board-verdict? verdict)
               (-> verdict :data :verdict-flag metadata/unwrap not))
      (fail :error.proposal-not-finalized))))

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
  [{:keys [application data] :as command}]
  (when (and application
             (:verdict-id data)
             (not (verdict/user-can-sign? command)))
    (fail :error.already-signed)))

(defn- good-signature-request
  "Pre-check that succeeds only if the signature request target is suitable."
  [{:keys [data] :as command}]
  (when-let  [user-id (:signer-id data)]
    (when-not (util/find-by-key :value user-id
                                (verdict/signature-request-parties command))
      (fail :error.bad-signature-request))))

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
    (-> (:verdicts application)
        (concat (:pate-verdicts application))
        (action/allowed-actions-for-collection :pate-verdicts
                                               (fn [application verdict]
                                                 {:id         (:id application)
                                                  :verdict-id (:id verdict)})
                                               command))))

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
                       state:     Verdict state
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
  id, published (timestamp) and tags."
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

(defquery published-verdict-attachment-ids
  {:description      "Verdict attachments (either via target or source) for
  the given published verdict. In addition to :attachment-ids
  returns :attachment-missing? if the verdict attachment is not
  generated yet."
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
  (ok (verdict/published-verdict-attachment-ids command)))

(defquery signature-request-parties
  {:description      "Basic information (value, text) on each
  application party that can be sent signature request. The list includes only non-company
  users with write access."
   :user-roles       #{:authority :applicant}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           states/post-submitted-states}
  [command]
  (ok :parties (verdict/signature-request-parties command)))

(defcommand send-signature-request
  {:description       "Send request to sign contract"
   :user-roles        #{:authority}
   :parameters        [id verdict-id signer-id]
   :categories        #{:pate-verdicts}
   :input-validators  [(partial action/non-blank-parameters [:id :verdict-id :signer-id])]
   :pre-checks        [(verdict-exists :published? :contract?)
                       good-signature-request]
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
  {:description      "Generates preview version of the verdict PDF."
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(some-pre-check
                        (verdict-exists :editable?)
                        (verdict-exists :scheduled?))
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

(defcommand publish-pate-verdict
  {:description      "Publishes verdict.
  When command is started, state of the verdict is set to 'publishing-verdict'
  If verdict has publishing-state at the end of the command previous state of the verdict is restored"
   :permissions      [{:required [:verdict/publish]}]
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           (set/difference states/post-submitted-states
                                     #{:finished})
   :pre-checks       [pate-enabled
                      (some-pre-check
                        (verdict-exists :editable? :modern?)
                        (and-pre-check
                          ; LPK-4709 publish scheduled verdict from batchrun
                          (verdict-exists :scheduled?)
                          (fn [{:keys [user]}]
                            (when-not (usr/batchrun-user? user)
                              (fail :error.verdict.not-editable)))))
                      verdict-filled
                      proposal-finalized
                      (some-pre-check
                        (and-pre-check (verdict-exists :contract?)
                                       (state-in states/post-submitted-states))
                        ;; As KuntaGML message is generated the
                        ;; application state must be at least :sent
                        (state-in (set/difference states/post-submitted-states
                                                  #{:complementNeeded})))]
   :notified         true
   :on-success       [(notify :application-state-change)
                      invoices/new-verdict-invoice
                      verdict-date/update-verdict-date]}
  [command]
  (verdict/publish-verdict command))

(defcommand scheduled-verdict-publish
  {:description      "Sets verdict to be published on julkipano date."
   :parameters       [id verdict-id]
   :permissions      [{:required [:verdict/publish]}]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           (set/difference states/post-submitted-states #{:finished})
   :pre-checks       [pate-enabled
                      (verdict-exists :editable? :modern? :valid-julkipano?)
                      verdict-filled]}
  [command]
  (verdict/scheduled-publish command))

(defcommand rollback-scheduled-publish
  {:description      "Rollbacks scheduled publish back to draft"
   :parameters       [id verdict-id]
   :permissions      [{:required [:verdict/publish]}]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           (set/difference states/post-submitted-states #{:finished})
   :pre-checks       [pate-enabled
                      (verdict-exists :modern? :scheduled?)]}
  [command]
  (verdict/update-verdict-state command verdict-id (verdict/wrapped-state command :draft))
  (ok :state :draft))

(defcommand delete-pate-verdict
  {:description      "Deletes verdict, its tasks and
  attachments. Rewinds application state if needed."
   :user-roles       #{:authority}
   :parameters       [:id :verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :not-replaced? :not-scheduled?)
                      (some-pre-check (verdict-exists :editable? :not-publishing?)
                                      (state-in states/give-verdict-states))]
   :states           states/post-submitted-states
   :notified         true
   :on-success       [verdict-date/update-verdict-date
                      forms/cleanup-notice-forms
                      robot/remove-verdict]}
  [command]
  (verdict/delete-verdict command)
  (ok))

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

(defcommand publish-legacy-verdict
  {:description      "Publishes legacy verdict.
  When command is started, state of the verdict is set to 'publishing-verdict'
  If verdict has publishing-state at the end of the command previous state of the verdict is restored"
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
                      invoices/new-verdict-invoice
                      verdict-date/update-verdict-date]}
  [command]
  (verdict/publish-verdict command))

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
                      (verdict-exists :editable? :board?)
                      proposal-filled]}
  [command]
  (verdict/publish-verdict-proposal command))

(defcommand revert-verdict-proposal
  {:description      "Removes published proposal and reverts the verdict
  into draft."
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :states           (set/difference states/post-submitted-states
                                     #{:finished})
   :pre-checks       [pate-enabled
                      (verdict-exists :editable? :proposal?)]}
  [command]
  (ok (verdict/revert-verdict-proposal command)))

;; ------------------------------------------
;; Suomi.fi
;; ------------------------------------------

(defquery can-send-via-suomifi
  {:description      "Pseudo-query for enabling Send via Suomi.fi button."
   :user-roles       #{:authority}
   :org-authz-roles  #{:approver}
   :parameters       [:id :verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :feature          :suomifi-messages
   :categories       #{:pate-verdicts}
   :pre-checks       [suomifi-enabled
                      (verdict-exists :published?)]}
  [_])

;; ------------------------------------------
;; Task order
;; ------------------------------------------

(defquery task-order
  {:description      "Returns a map of ordered tasks. Keys are `:foremen`, `:reviews` and `:plans`.
Values are list of the corresponding task ids in the correct order. The query is in this
namespace since the Pate ordering is the most significant. The idea is that the tasks are in
the same order as in the verdict. Also, subreviews (osittainen) are grouped together."
   :user-roles       #{:authority :applicant}
   :org-authz-roles  roles/reader-org-authz-roles
   :user-authz-roles roles/all-authz-roles
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           states/post-verdict-states}
  [{:keys [application] :as command}]
  (ok (tasks/task-order application (vif/latest-published-pate-verdict command))))
