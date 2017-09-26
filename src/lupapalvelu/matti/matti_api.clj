(ns lupapalvelu.matti.matti-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.matti.matti :as matti]
            [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

;; ----------------------------------
;; Pre-checks
;; ----------------------------------

(defn- verdict-template-check
  "Returns prechecker for template-id parameters.
   Condition parameters:
     :editable    Template must be editable (not deleted)
     :published   Template must hav been published
     :blank       Template-id can be empty. Note: this does not
                  replace input-validator.
     :named       Template name cannot be empty
     :application Template must belong to the same category as the
                  application

   Template's existence is always checked unless :blank matches."
  [& conditions]
  (let [{:keys [editable published blank named application]} (zipmap conditions
                                                                     (repeat true))]
    (fn [{{template-id :template-id} :data :as command}]
      (when template-id
        (if (ss/blank? template-id)
          (when-not blank
            (fail :error.missing-parameters))
          (let [template (some-> (matti/command->organization command)
                                 (matti/verdict-template template-id)
                                 matti/verdict-template-summary)]
            (when-not template
              (fail! :error.verdict-template-not-found))
            (when (and editable (:deleted template))
              (fail! :error.verdict-template-deleted))
            (when (and published (-> template :published not))
              (fail! :error.verdict-template-not-published))
            (when (and named (-> template :name ss/blank?))
              (fail! :error.verdict-template-name-missing))
            (when (and application
                       (util/not=as-kw (:category template)
                                       (-> command :application :permitType
                                           shared/permit-type->category)))
              (fail! :error.invalid-category))))))))

(defn- valid-category [{{category :category} :data :as command}]
  (when category
    (when-not (util/includes-as-kw? (matti/organization-categories (matti/command->organization command))
                                    category)
      (fail :error.invalid-category))))

(defn settings-generic-editable
  "Generic (a subcollection item) exists and is not deleted. Even if
  the generic is deleted, it is editable if the update includes
  deleted parameter with false value."
  [subcollection]
  (fn [{:keys [data user] :as command}]
    (when-let [gen-id (case subcollection
                   :reviews (:review-id data)
                   :plans   (:plan-id data)
                   false)]
      (if-let [generic (matti/generic (matti/command->organization command)
                                      gen-id
                                      subcollection)]
        (when (and (:deleted generic) (-> data :deleted false? not))
         (fail :error.settings-item-deleted))
        (fail :error.settings-item-not-found)))))

;; ----------------------------------
;; Input-validators
;; ----------------------------------

(defn supported-parameters
  "Returns checker that fails on unknown parameters. Params must be
  keywords."
  [& params]
  #(when-not (set/subset? (->> % :data keys (map keyword) set)
                          (set params))
     (fail :error.unsupported-parameters)))

(defn- settings-generic-details-valid
  "None of the details is mandatory."
  [{data :data}]
  (some (fn [[k v]]
          (let [k (keyword k)]
            (cond
              (k #{:fi :sv :en})
              (when (ss/blank? v)
                (fail :error.name-blank))
              (= k :deleted)
              (when-not (or (false? v) (true? v))
                (fail :error.deleted-not-boolean)))))
        data))

(defn- settings-review-details-valid
  "None of the details is mandatory."
  [{{type :type} :data}]
  (when (and type
             (not (util/includes-as-kw? (keys shared/review-type-map)
                                        type)))
    (fail :error.invalid-review-type)))

;; ----------------------------------
;; Verdict template API
;; ----------------------------------

(defcommand new-verdict-template
  {:description      "Creates new empty template. Returns template id, name
  and draft."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [{:keys [created user lang]}]
  (ok (matti/new-verdict-template (usr/authority-admins-organization-id user)
                                  created
                                  lang
                                  category)))

(defcommand set-verdict-template-name
  {:description "Name cannot be empty."
   :feature          :matti
   :parameters       [template-id name]
   :input-validators [(partial action/non-blank-parameters [:name])]
   :pre-checks       [(verdict-template-check :editable)]
   :user-roles       #{:authorityAdmin}}
  [{created :created :as command}]
  (matti/set-name (matti/command->organization command)
                  template-id
                  created
                  name)
  (ok :modified created))

(defcommand save-verdict-template-draft-value
  {:description      "Incremental save support for verdict template
  drafts. Returns modified timestamp."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id path value]
   ;; Value is validated upon saving according to the schema.
   :input-validators [(partial action/vector-parameters [:path])
                      (partial action/non-blank-parameters [:template-id])]
   :pre-checks       [(verdict-template-check :editable)]}
  [{:keys [created] :as command}]
  (if-let [error (matti/save-draft-value (matti/command->organization command)
                                         template-id
                                         created
                                         path
                                         value)]
    (fail error)
    (ok :modified created)))

(defcommand publish-verdict-template
  {:description      "Creates new verdict template version. The version
  includes also the current settings."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [(verdict-template-check :editable :named)]}
  [{:keys [created user user-organizations] :as command}]
  (matti/publish-verdict-template (matti/command->organization command)
                                  template-id
                                  created)
  (ok :published created))

(defquery verdict-templates
  {:description "Id, name, modified, published and deleted maps for
  every verdict template."
   :feature     :matti
   :user-roles  #{:authorityAdmin}}
  [command]
  (ok :verdict-templates (->> (matti/command->organization command)
                              :verdict-templates
                              :templates
                              (map matti/verdict-template-summary))))

(defquery verdict-template-categories
  {:description "Categories for the user's organization"
   :feature     :matti
   :user-roles  #{:authorityAdmin}}
  [command]
  (ok :categories (matti/organization-categories (matti/command->organization command))))

(defquery verdict-template
  {:description      "Verdict template summary plus draft data. The
  template must be editable."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [(verdict-template-check :editable)]}
  [command]
  (let [template (matti/verdict-template (matti/command->organization command)
                                         template-id)]
    (ok (assoc (matti/verdict-template-summary template)
               :draft (:draft template)))))

(defcommand toggle-delete-verdict-template
  {:description      "Toggle template's deletion status"
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id delete]
   :input-validators [(partial action/non-blank-parameters [:template-id])
                      (partial action/boolean-parameters [:delete])]
   :pre-checks       [(verdict-template-check)]}
  [command]
  (matti/set-deleted (matti/command->organization command)
                     template-id
                     delete))

(defcommand copy-verdict-template
  {:description      "Makes copy of the template. The new template does not
  have any published versions. In other words, the draft of the
  original is copied."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [(verdict-template-check)]}
  [{:keys [created lang] :as command}]
  (ok (matti/copy-verdict-template (matti/command->organization command)
                                    template-id
                                    created
                                    lang)))

;; ----------------------------------
;; Verdict template settings API
;; ----------------------------------

(defquery verdict-template-settings
  {:description      "Settings matching the category or empty response."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [command]
  (when-let [settings (matti/settings (matti/command->organization command)
                                      category)]
    (ok :settings settings)))

(defcommand save-verdict-template-settings-value
  {:description      "Incremental save support for verdict template
  settings. Returns modified timestamp. Creates settings if needed."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [category path value]
   ;; Value is validated against schema on saving.
   :input-validators [(partial action/non-blank-parameters [:category])
                      (partial action/vector-parameters [:path])]
   :pre-checks       [valid-category]}
  [{:keys [created] :as command}]
  (if-let [error (matti/save-settings-value (matti/command->organization command)
                                            category
                                            created
                                            path
                                            value)]
    (fail error)
    (ok :modified created)))

;; ----------------------------------
;; Verdict template reviews API
;; ----------------------------------

(defquery verdict-template-reviews
  {:description      "Reviews matching the category"
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [command]
  (ok :reviews (matti/generic-list (matti/command->organization command)
                                   category
                                   :reviews)))

(defcommand add-verdict-template-review
  {:description      "Creates empty review for the settings
  category. Returns review."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [{user :user}]
  (ok :review (matti/new-review (usr/authority-admins-organization-id user)
                                category)))

(defcommand update-verdict-template-review
  {:description         "Updates review details according to the
  parameters. Returns the updated review."
   :feature             :matti
   :user-roles          #{:authorityAdmin}
   :parameters          [review-id]
   :optional-parameters [fi sv en type deleted]
   :input-validators    [(partial action/non-blank-parameters [:review-id])
                         (supported-parameters :review-id :fi :sv :en
                                               :type :deleted)
                         settings-generic-details-valid
                         settings-review-details-valid]
   :pre-checks          [(settings-generic-editable :reviews)]}
  [{:keys [created user data] :as command}]
  (matti/set-review-details (matti/command->organization command)
                            created
                            review-id
                            data)
  (ok :review (matti/review (usr/authority-admins-organization user)
                            review-id)
      :modified created))

;; ----------------------------------
;; Verdict template plans API
;; ----------------------------------

(defquery verdict-template-plans
  {:description      "Plans matching the category"
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [command]
  (ok :plans (matti/generic-list (matti/command->organization command)
                                 category
                                 :plans)))

(defcommand add-verdict-template-plan
  {:description      "Creates empty plan for the settings
  category. Returns plan."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [{user :user}]
  (ok :plan (matti/new-plan (usr/authority-admins-organization-id user)
                            category)))

(defcommand update-verdict-template-plan
  {:description         "Updates plan details according to the
  parameters. Returns the updated plan."
   :feature             :matti
   :user-roles          #{:authorityAdmin}
   :parameters          [plan-id]
   :optional-parameters [fi sv en type deleted]
   :input-validators    [(partial action/non-blank-parameters [:plan-id])
                         (supported-parameters :plan-id :fi :sv :en :deleted)
                         settings-generic-details-valid]
   :pre-checks          [(settings-generic-editable :plans)]}
  [{:keys [created user data] :as command}]
  (matti/set-plan-details (matti/command->organization command)
                          created
                          plan-id
                          data)
  (ok :plan (matti/plan (usr/authority-admins-organization user)
                        plan-id)
      :modified created))

;; ------------------------------------------
;; Default verdict templates for operations
;; ------------------------------------------

;; Operation related pre-checkers

(defn- organization-operation
  "Fails if the operation parameter is not selected for the
  organization."
  [{{operation :operation} :data :as command}]
  (when operation
    (when-not (-> (matti/command->organization command)
                  :selected-operations
                  (util/includes-as-kw? operation))
      (fail :error.unknown-operation))))

(defn- operation-vs-template-category
  "Operation permit type must belong to the template category."
  [{{:keys [operation template-id]} :data :as command}]
  (when (and operation (not (ss/blank? template-id)))
    (let [{category :category} (matti/verdict-template (matti/command->organization command)
                                                       template-id)]
      (when-not (and category
                     (util/=as-kw (matti/operation->category operation)
                                  category))
        (fail :error.invalid-category)))))

(defquery default-operation-verdict-templates
  {:description "Map where keys are operations and values template
  ids. Deleted templates are filtered out."
   :feature     :matti
   :user-roles  #{:authorityAdmin}}
  [command]
  (ok :templates (matti/operation-verdict-templates (matti/command->organization command))))

(defcommand set-default-operation-verdict-template
  {:description      "Set default verdict template for a selected operation
  in the organization."
   :feature          :matti
   :user-roles       #{:authorityAdmin}
   :parameters       [operation template-id]
   :input-validators [(partial action/non-blank-parameters [:operation])]
   :pre-checks       [(verdict-template-check :published :editable :blank)
                      organization-operation
                      operation-vs-template-category]}
  [{user :user}]
  (matti/set-operation-verdict-template (usr/authority-admins-organization-id user)
                                        operation
                                        template-id))

;; ------------------------------------------
;; Verdict API
;; ------------------------------------------

;; TODO: Make sure that the functionality (including notifications)
;; and constraints are in sync with the legacy verdict API.

(defn- verdict-check
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

(defn- verdict-exists [{:keys [data application]}]
  (when-not (util/find-by-id (:verdict-id data) (:matti-verdicts application))
    (fail :error.verdict-not-found)))

(defquery application-verdict-templates
  {:description      "List of id, name, default? maps for suitable
  application verdict templates."
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           states/give-verdict-states}
  [{:keys [application organization]}]
  (ok :templates (matti/application-verdict-templates @organization
                                                      application)))

(defcommand new-matti-verdict-draft
  {:description      "Composes new verdict draft from the latest published
  template and its settings."
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id template-id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [(verdict-template-check :application :published)]
   :states           states/give-verdict-states}
  [command]
  (ok (matti/new-verdict-draft template-id command)))

(defquery matti-verdicts
  {:description      "List of verdicts. Item properties:
                       id:        Verdict id
                       published: timestamp (can be nil)
                       modified:  timestamp"
   :feature          :matti
   :user-roles       #{:authority :applicant}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           (states/all-states-but [:draft :open])}
  [{:keys [application]}]
  (ok :verdicts (map matti/verdict-summary
                     (:matti-verdicts application))))

(defquery matti-verdict
  {:description "Verdict and its settings."
   :feature :matti
   :user-roles       #{:authority :applicant}
   :parameters [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-check)]
   :states           states/give-verdict-states}
  [command]
  (ok (matti/open-verdict command)))

(defcommand delete-matti-verdict
  {:description      "Deletes verdict. Published verdicts cannot be
  deleted."
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id verdict-id]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])]
   :pre-checks       [(verdict-check :editable?)]
   :states           (states/all-states-but [:draft :open])}
  [command]
  (matti/delete-verdict verdict-id command)
  (ok))

(defcommand edit-matti-verdict
  {:description      "Updates verdict data. Returns changes and errors
  lists (items are path-vector value pairs)"
   :feature          :matti
   :user-roles       #{:authority}
   :parameters       [id verdict-id path value]
   :input-validators [(partial action/non-blank-parameters [:id :verdict-id])
                      (partial action/vector-parameters [:path])]
   :pre-checks       [(verdict-check :editable?)]
   :states           states/give-verdict-states}
  [command]
  (ok (matti/edit-verdict command)))
