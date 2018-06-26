(ns lupapalvelu.pate.verdict-api
  "Pate verdict API supports both 'modern' Pate verdicts with templates
  and 'legacy' verdicts that are old-school verdicts presented with
  Pate schemas."
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand defraw notify] :as action]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.verdict :as verdict]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.state-machine :as sm]
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
  "Pre-checker that fails if Pate is not enabled in the application organization scope."
  [{:keys [organization application]}]
  (when (and organization
             (not (-> (org/resolve-organization-scope (:municipality application) (:permitType application) @organization)
                      :pate-enabled)))
    (fail :error.pate-disabled)))

(defn- verdict-exists
  "Returns pre-checker that fails if the verdict does not exist.
  Additional conditions:
    :editable? fails if the verdict has been published.
    :published? fails if the verdict has NOT been published
    :legacy? fails if the verdict is a 'modern' Pate verdict
    :modern? fails if the verdict is a legacy verdict
    :contract? fails if the verdict is not a contract
    :verdict? fails for contracts
    :html? fails if the html version of the verdict attachment is not
           available."
  [& conditions]
  (let [{:keys [editable? published?
                legacy? modern?
                contract? verdict?
                html?]} (zipmap conditions
                                (repeat true))]
    (fn [{:keys [data application]}]
      (when-let [verdict-id (:verdict-id data)]
        (let [verdict (util/find-by-id verdict-id
                                       (:pate-verdicts application))]
          (util/pcond-> (cond
                          (not verdict)
                          :error.verdict-not-found

                          (util/not=as-kw (schema-util/application->category application)
                                          (:category verdict))
                          :error.invalid-category

                          (and editable? (:published verdict))
                          :error.verdict.not-draft

                          (and published? (not (:published verdict)))
                          :error.verdict.not-published

                          (and legacy? (not (:legacy? verdict)))
                          :error.verdict.not-legacy

                          (and modern? (:legacy? verdict))
                          :error.verdict.legacy

                          (and contract? (util/not=as-kw (:category verdict)
                                                         :contract))
                          :error.verdict.not-contract

                          (and verdict? (util/=as-kw (:category verdict)
                                                     :contract))
                          :error.verdict.contract

                          (and html? (not (some-> verdict :verdict-attachment :html)))
                          :error.verdict.no-html)
                        identity fail))))))

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

(defn- contractual-application
  "Precheck that fails if the application category IS NOT :contract."
  [command]
  (when-not (= (verdict/command->category command) :contract)
    (fail :error.verdict.not-contract)))

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

(defmethod action/allowed-actions-for-category :pate-verdicts
  [command]
  (if-let [verdict-id (get-in command [:data :verdict-id])]
    {verdict-id (action/allowed-category-actions-for-command :pate-verdicts
                                                             command)}
    (action/allowed-actions-for-collection :pate-verdicts
                                           (fn [application verdict]
                                             {:id         (:id application)
                                              :verdict-id (:id verdict)})
                                           command)))

;; ------------------------------------------
;; Actions common with modern and legacy
;; ------------------------------------------

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
(defquery pate-verdicts
  {:description "List of verdicts. Item properties:

                       id:        Verdict id
                       published: (optional) timestamp
                       modified:  timestamp
                       category:  Verdict category
                       legacy?:   (optional) true for legacy verdicts.
                       giver:     Either verdict handler or boardname.
                       verdict-date: (optional) timestamp

                       replaced?  (optional) true if the verdict is
                       replaced.

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
   :feature          :pate
   :user-roles       #{:authority :applicant}
   :org-authz-roles  roles/reader-org-authz-roles
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           states/post-submitted-states}
  [command]
  (ok :verdicts (verdict/verdict-list command)))

(defquery pate-verdict
  {:description      "Verdict and its settings."
   :feature          :pate
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

(defcommand edit-pate-verdict
  {:description "Updates verdict data. Returns changes and errors
  lists (items are path-vector value pairs)"
   :feature          :pate
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
   :feature          :pate
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :editable?)
                      verdict-filled]
   :states           states/post-submitted-states}
  [command]
  (verdict/preview-verdict command))

(defquery pate-verdict-tab
  {:description     "Pseudo-query that fails if the Pate verdicts tab
  should not be shown on the UI."
   :feature         :pate
   :parameters      [:id]
   :user-roles      #{:applicant :authority}
   :org-authz-roles roles/reader-org-authz-roles
   :states          states/post-submitted-states}
  [_])

(defquery pate-contract-tab
  {:description     "Pseudo-query that fails if the Pate contracts tab
  should not be shown on the UI. Note that pate-contract-tab always
  implies pate-verdict-tab, too."
   :feature         :pate
   :parameters      [:id]
   :user-roles      #{:applicant :authority}
   :org-authz-roles roles/reader-org-authz-roles
   :pre-checks      [contractual-application]
   :states          states/post-submitted-states}
  [_])

(defcommand sign-pate-contract
  {:description      "Adds the user as signatory to a published Pate
  contract if the password matches."
   :feature          :pate
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

(defcommand generate-pate-pdf
  {:description      "Regenerates (or at least tries to) verdict pdf if the
  creation during publishing has failed."
   :feature          :pate
   :categories       #{:pate-verdicts}
   :parameters       [:id :verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :published? :html?)]
   :states           states/post-verdict-states
   :user-roles       #{:authority}}
  [command]
  (ok :attachment-id (verdict/verdict-html->pdf command
                                                (verdict/command->verdict command))))

;; ------------------------------------------
;; Modern actions
;; ------------------------------------------

(defquery replace-pate-verdict
  {:description      "Pseudo-query for checking whether a verdict can be
  replaced."
   :feature          :pate
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
   :feature             :pate
   :user-roles          #{:authority}
   :parameters          [id template-id]
   :optional-parameters [replacement-id]
   :input-validators    [(partial action/non-blank-parameters [:id])]
   :pre-checks          [pate-enabled
                         (action/not-pre-check legacy-category)
                         (template/verdict-template-check :application :published)
                         (replacement-check :replacement-id)]
   :states              states/post-submitted-states}
  [command]
  (ok :verdict-id (verdict/new-verdict-draft template-id command replacement-id)))

(defcommand delete-pate-verdict
  {:description      "Deletes verdict. Published verdicts cannot be
  deleted."
   :feature          :pate
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
   :feature          :pate
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [pate-enabled
                      (verdict-exists :editable? :modern?)
                      verdict-filled
                      (action/some-pre-check
                       (action/and-pre-check (verdict-exists :contract?)
                                             (state-in states/post-submitted-states))
                       ;; As KuntaGML message is generated the
                       ;; application state must be at least :sent
                       (state-in (set/difference states/post-sent-states
                                                 #{:complementNeeded})))]
   :notified         true
   :on-success       (notify :application-state-change)}
  [command]
  (ok (verdict/publish-verdict command)))

;; ------------------------------------------
;; Legacy actions
;; ------------------------------------------

(defcommand new-legacy-verdict-draft
  {:description "Composes new legacy verdict draft. Even if Pate is
  enabled, some categories are only supported by legacy
  verdicts. Returns the verdict-id."
   :feature          :pate
   :user-roles       #{:authority}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [(action/some-pre-check (action/not-pre-check pate-enabled)
                                             legacy-category)]
   :states           states/post-submitted-states}
  [command]
  (ok :verdict-id (verdict/new-legacy-verdict-draft command)))

(defcommand delete-legacy-verdict
  {:description      "Deletes legacy verdict, its tasks and
  attachments. Rewinds application state if needed."
   :feature          :pate
   :user-roles       #{:authority}
   :parameters       [:id :verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :legacy?)
                      (action/some-pre-check (verdict-exists :legacy? :editable?)
                                             (state-in states/give-verdict-states))]
   :states           states/post-submitted-states
   :notified         true}
  [command]
  (ok (verdict/delete-legacy-verdict command)))

(defcommand publish-legacy-verdict
  {:description      "Publishes legacy verdict."
   :feature          :pate
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-exists :editable? :legacy?)
                      verdict-filled]
   :states            states/post-submitted-states
   :notified         true
   :on-success       (notify :application-state-change)}
  [command]
  (ok (verdict/publish-verdict command)))

;; ------------------------------------------
;; Bulletin related actions
;; ------------------------------------------

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
  {:description      ""
   :feature          :pate
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :categories       #{:pate-verdicts}
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [pate-enabled
                      (verdict-exists :editable? :verdict?)]
   :states           states/post-submitted-states}
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
