(ns lupapalvelu.pate.verdict-template-api
  "Verdict templates and category-wide settings."
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

;; ----------------------------------
;; Pre-checks
;; ----------------------------------

(defn- pate-enabled
  "Pre-checker that fails if Pate is not enabled in the organization."
  [cmd]
  (when-not (:pate-enabled (template/command->organization cmd))
    (fail :error.pate-disabled)))

(defn- valid-category [{{category :category} :data :as command}]
  (when category
    (when-not (util/includes-as-kw? (template/organization-categories (template/command->organization command))
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
      (if-let [generic (template/generic (template/command->organization command)
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

(defquery pate-enabled
  {:description "Pseudo-query that fails if Pate is not enabled in
  the organization"
   :feature     :pate
   :user-roles  #{:authorityAdmin}
   :pre-checks  [pate-enabled]}
  [_])


(defcommand new-verdict-template
  {:description      "Creates new empty template. Returns template id, name
  and draft."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [{:keys [created user lang]}]
  (ok (assoc (template/new-verdict-template (usr/authority-admins-organization-id user)
                                            created
                                            lang
                                            category)
             :filled false)))

(defcommand set-verdict-template-name
  {:description "Name cannot be empty."
   :feature          :pate
   :parameters       [template-id name]
   :input-validators [(partial action/non-blank-parameters [:name])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]
   :user-roles       #{:authorityAdmin}}
  [{created :created :as command}]
  (template/set-name (template/command->organization command)
                     template-id
                     created
                     name)
  (ok :modified created))

(defcommand save-verdict-template-draft-value
  {:description      "Incremental save support for verdict template
  drafts. Returns modified timestamp."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id path value]
   ;; Value is validated upon saving according to the schema.
   :input-validators [(partial action/vector-parameters [:path])
                      (partial action/non-blank-parameters [:template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]}
  [{:keys [created] :as command}]
  (let [organization   (template/command->organization command)
        {data :data
         :as  updated} (template/save-draft-value organization
                                                  template-id
                                                  created
                                                  path
                                                  value)]
    (if data
      (ok  (template/changes-response
            {:modified created
             :filled   (template/template-filled? {:data data})}
            updated))
      (template/error-response updated))))

(defcommand publish-verdict-template
  {:description      "Publish creates a frozen snapshot of the current
  template draft. The snapshot includes also the current settings."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable :named :filled)]}
  [{:keys [created user user-organizations] :as command}]
  (template/publish-verdict-template (template/command->organization command)
                                     template-id
                                     created)
  (ok :published created))

(defquery verdict-templates
  {:description "Id, name, modified, published and deleted maps for
  every verdict template."
   :feature     :pate
   :user-roles  #{:authorityAdmin}
   :pre-checks  [pate-enabled]}
  [command]
  (ok :verdict-templates (->> (template/command->organization command)
                              :verdict-templates
                              :templates
                              (map template/verdict-template-summary))))

(defquery verdict-template-categories
  {:description "Categories for the user's organization"
   :feature     :pate
   :user-roles  #{:authorityAdmin}
   :pre-checks  [pate-enabled]}
  [command]
  (ok :categories (template/organization-categories (template/command->organization command))))

(defquery verdict-template
  {:description      "Verdict template summary plus draft data. The
  template must be editable."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]}
  [command]
  (let [organization (template/command->organization command)
        template     (template/verdict-template organization template-id)]
    (ok (assoc (template/verdict-template-summary template)
               :draft (:draft template)
               :filled (template/template-filled? {:org-id   (:id organization)
                                                   :template template})))))

(defcommand toggle-delete-verdict-template
  {:description      "Toggle template's deletion status"
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id delete]
   :input-validators [(partial action/non-blank-parameters [:template-id])
                      (partial action/boolean-parameters [:delete])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check)]}
  [command]
  (template/set-deleted (template/command->organization command)
                        template-id
                        delete))

(defcommand copy-verdict-template
  {:description      "Makes copy of the template. The new template does not
  have any published versions. In other words, the draft of the
  original is copied."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check)]}
  [{:keys [created lang] :as command}]
  (let [organization (template/command->organization command)]
    (ok (assoc (template/copy-verdict-template organization
                                               template-id
                                               created
                                               lang)
               :filled (template/template-filled? {:org-id      (:id organization)
                                                   :template-id template-id})))))

;; ----------------------------------
;; Verdict template settings API
;; ----------------------------------

(defquery verdict-template-settings
  {:description      "Settings matching the category or empty response."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [command]
  (when-let [settings (template/settings (template/command->organization command)
                                         category)]
    (ok :settings settings
        :filled (template/settings-filled? {:settings settings} category))))

(defcommand save-verdict-template-settings-value
  {:description      "Incremental save support for verdict template
  settings. Returns modified timestamp. Creates settings if needed."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [category path value]
   ;; Value is validated against schema on saving.
   :input-validators [(partial action/non-blank-parameters [:category])
                      (partial action/vector-parameters [:path])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [{:keys [created] :as command}]
  (let [organization (template/command->organization command)]
    (let [{data :data :as updated} (template/save-settings-value organization
                                                                 category
                                                                 created
                                                                 path
                                                                 value)]
      (if data
        (ok (template/changes-response
             {:modified created
              :filled (template/settings-filled? {:data data}
                                                 category)}
             updated))
        (template/error-response updated)))))

;; ----------------------------------
;; Verdict template reviews API
;; ----------------------------------

(defquery verdict-template-reviews
  {:description      "Reviews matching the category"
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [command]
  (ok :reviews (template/generic-list (template/command->organization command)
                                      category
                                      :reviews)))

(defcommand add-verdict-template-review
  {:description      "Creates empty review for the settings
  category. Returns review."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [{user :user}]
  (ok :review (template/new-review (usr/authority-admins-organization-id user)
                                   category)))

(defcommand update-verdict-template-review
  {:description         "Updates review details according to the
  parameters. Returns the updated review."
   :feature             :pate
   :user-roles          #{:authorityAdmin}
   :parameters          [review-id]
   :optional-parameters [fi sv en type deleted]
   :input-validators    [(partial action/non-blank-parameters [:review-id])
                         (supported-parameters :review-id :fi :sv :en
                                               :type :deleted)
                         settings-generic-details-valid
                         settings-review-details-valid]
   :pre-checks          [pate-enabled
                         (settings-generic-editable :reviews)]}
  [{:keys [created user data] :as command}]
  (template/set-review-details (template/command->organization command)
                               created
                               review-id
                               data)
  ;; The organization has changed.
  (ok :review (template/review (usr/authority-admins-organization user)
                               review-id)
      :modified created))

;; ----------------------------------
;; Verdict template plans API
;; ----------------------------------

(defquery verdict-template-plans
  {:description      "Plans matching the category"
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [command]
  (ok :plans (template/generic-list (template/command->organization command)
                                    category
                                    :plans)))

(defcommand add-verdict-template-plan
  {:description      "Creates empty plan for the settings
  category. Returns plan."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [{user :user}]
  (ok :plan (template/new-plan (usr/authority-admins-organization-id user)
                               category)))

(defcommand update-verdict-template-plan
  {:description         "Updates plan details according to the
  parameters. Returns the updated plan."
   :feature             :pate
   :user-roles          #{:authorityAdmin}
   :parameters          [plan-id]
   :optional-parameters [fi sv en type deleted]
   :input-validators    [(partial action/non-blank-parameters [:plan-id])
                         (supported-parameters :plan-id :fi :sv :en :deleted)
                         settings-generic-details-valid]
   :pre-checks          [pate-enabled
                         (settings-generic-editable :plans)]}
  [{:keys [created user data] :as command}]
  (template/set-plan-details (template/command->organization command)
                             created
                             plan-id
                             data)
  ;; The organization has changed.
  (ok :plan (template/plan (usr/authority-admins-organization user)
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
    (when-not (-> (template/command->organization command)
                  :selected-operations
                  (util/includes-as-kw? operation))
      (fail :error.unknown-operation))))

(defn- operation-vs-template-category
  "Operation permit type must belong to the template category."
  [{{:keys [operation template-id]} :data :as command}]
  (when (and operation (not (ss/blank? template-id)))
    (let [{category :category} (template/verdict-template (template/command->organization command)
                                                          template-id)]
      (when-not (and category
                     (util/=as-kw (template/operation->category operation)
                                  category))
        (fail :error.invalid-category)))))

(defquery default-operation-verdict-templates
  {:description "Map where keys are operations and values template
  ids. Deleted templates are filtered out."
   :feature     :pate
   :user-roles  #{:authorityAdmin}
   :pre-checks  [pate-enabled]}
  [command]
  (ok :templates (template/operation-verdict-templates (template/command->organization command))))

(defcommand set-default-operation-verdict-template
  {:description      "Set default verdict template for a selected operation
  in the organization."
   :feature          :pate
   :user-roles       #{:authorityAdmin}
   :parameters       [operation template-id]
   :input-validators [(partial action/non-blank-parameters [:operation])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :published :editable :blank)
                      organization-operation
                      operation-vs-template-category]}
  [{user :user}]
  (template/set-operation-verdict-template (usr/authority-admins-organization-id user)
                                           operation
                                           template-id))
