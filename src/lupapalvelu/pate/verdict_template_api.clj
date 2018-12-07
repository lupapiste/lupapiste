(ns lupapalvelu.pate.verdict-template-api
  "Verdict templates and category-wide settings."
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.pate.verdict-template :as template]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.pate.metadata :as metadata]
            [sade.util :as util]))

;; ----------------------------------
;; Pre-checks
;; ----------------------------------

(defn- pate-enabled
  "Pre-checker that fails if Pate is not enabled in any organization
  scope."
  [command]
  (when (some-> command :data :org-id)
    (if-let [organization (template/command->organization command)]
      (when-not (util/find-by-key :pate-enabled true (:scope organization))
        (fail :error.pate-disabled))
      (fail :error.invalid-organization))))

(defn- pate-enabled-user-org
  "Pre-checker that fails if Pate is not enabled in any organization
  scope."
  [command]
  (when (empty? (->> (mapv :scope (:user-organizations command))
                     (map #(util/find-by-key :pate-enabled true %))
                     (remove nil?)))
    (fail :error.pate-disabled)))

(defn- valid-category [{{category :category} :data :as command}]
  (when category
    (when-not (util/includes-as-kw? (template/organization-categories (template/command->organization command))
                                    category)
      (fail :error.invalid-category))))

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

;; ----------------------------------
;; Verdict template API
;; ----------------------------------

(defquery pate-enabled
  {:description      "Query that fails if Pate is not enabled in the
  organization. Thus, the result does not matter."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [_])

(defquery pate-enabled-user-org
  {:description "Pre-checker that fails if Pate is not enabled
  in any of user organization scopes."
   :permissions [{:required [:organization/admin]}]
   :pre-checks  [pate-enabled-user-org]}
  [_])

(defcommand new-verdict-template
  {:description      "Creates new empty template. Returns template id, name
  and draft."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id :category]
   :input-validators [(partial action/non-blank-parameters [:org-id :category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [command]
  (-> (template/command->options command)
      template/new-verdict-template
      (assoc :filled false)
      ok))

(defcommand set-verdict-template-name
  {:description      "Name cannot be empty."
   :parameters       [:org-id :template-id :name]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id :name])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]
   :permissions      [{:required [:organization/admin]}]}
  [{created :created :as command}]
  (template/set-name (template/command->options command))
  (ok :modified created))

(defcommand save-verdict-template-draft-value
  {:description      "Incremental save support for verdict template
  drafts. Returns modified timestamp."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id :template-id :path :value]
   ;; Value is validated upon saving according to the schema.
   :input-validators [(partial action/vector-parameters [:path])
                      (partial action/non-blank-parameters [:template-id :org-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]}
  [{:keys [created] :as command}]
  (let [{:keys [data category]
         :as   updated} (template/save-draft-value (template/command->options command))]
    (if data
      (ok (template/changes-response
            {:modified created
             :filled   (template/template-filled? {:category category
                                                   :data     data})}
            updated))
      (template/error-response updated))))

(defcommand publish-verdict-template
  {:description      "Publish creates a frozen snapshot of the current
  template draft. The snapshot includes also the current settings."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id :template-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable :named :filled)]}
  [{:keys [created] :as command}]
  (template/publish-verdict-template (template/command->options command))
  (ok :published created))

(defquery verdict-templates
  {:description      "Id, name, modified, published and deleted maps for
  every verdict template."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [command]
  (let [{:keys [organization]} (template/command->options command)]
    (ok :verdict-templates (->> organization
                                :verdict-templates
                                :templates
                                (map (util/fn->> metadata/unwrap-all
                                                 template/verdict-template-summary))))))

(defquery verdict-template-categories
  {:description      "Categories for the user's organization. Does not
   include legacy-only categories."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [command]
  (ok :categories (template/organization-categories (template/command->organization command))))

(defquery verdict-template
  {:description      "Verdict template summary plus draft data. The
  template must be editable."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id :template-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]}
  [command]
  (ok (template/verdict-template-response-data (template/command->options command))))

(defcommand update-and-open-verdict-template
  {:description      "Like verdict-template but also updates the template's
  settings dependencies."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id :template-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]}
  [command]
  (ok (template/verdict-template-update-and-open (template/command->options command))))

(defcommand toggle-delete-verdict-template
  {:description      "Toggle template's deletion status"
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id :template-id :delete]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])
                      (partial action/boolean-parameters [:delete])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check)]}
  [command]
  (template/set-deleted (template/command->options command)))

(defcommand copy-verdict-template
  {:description      "Makes copy of the template. The new template does not
  have any published versions. In other words, the draft of the
  original is copied."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [org-id template-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check)]}
  [command]
  (let [options (template/command->options command)]
    (ok (assoc (template/copy-verdict-template options)
               :filled (template/template-filled? options)))))

;; ----------------------------------
;; Verdict template settings API
;; ----------------------------------

(defquery verdict-template-settings
  {:description      "Settings matching the category or empty response."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id :category]
   :input-validators [(partial action/non-blank-parameters [:org-id :category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [command]
  (let [options (template/command->options command)]
    (let [settings (template/settings options)]
      (ok :settings settings
          :filled (template/settings-filled? (assoc options
                                                    :settings settings))))))

(defcommand save-verdict-template-settings-value
  {:description      "Incremental save support for verdict template
  settings. Returns modified timestamp. Creates settings if needed."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id :category :path :value]
   ;; Value is validated against schema on saving.
   :input-validators [(partial action/non-blank-parameters [:org-id :category])
                      (partial action/vector-parameters [:path])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [{created :created :as command}]
  (let [options (template/command->options command)]
    (let [{data :data :as updated} (template/save-settings-value options)]
      (if data
        (ok (template/changes-response
              {:modified created
               :filled   (template/settings-filled? (assoc options
                                                           :data data))}
              updated))
        (template/error-response updated)))))


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
  [command]
  (let [{:keys [template-id operation]
         :as   options} (template/command->options command)]
    (when (and operation (not (ss/blank? template-id)))
      (let [{category :category} (template/verdict-template options)]
       (when-not (and category
                      (util/=as-kw (template/operation->category operation)
                                   category))
         (fail :error.invalid-category))))))

(defquery default-operation-verdict-templates
  {:description      "Map where keys are operations and values template
  ids. Deleted templates are filtered out."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [command]
  (ok :templates (template/operation-verdict-templates (template/command->options command))))

(defcommand set-default-operation-verdict-template
  {:description         "Set default verdict template for a selected operation
  in the organization. If template-id is not given, the default is
  cleared."
   :permissions         [{:required [:organization/admin]}]
   :parameters          [org-id operation]
   :optional-parameters [template-id]
   :input-validators    [(partial action/non-blank-parameters [:org-id :operation])]
   :pre-checks          [pate-enabled
                         (template/verdict-template-check :published :editable :blank)
                         organization-operation
                         operation-vs-template-category]}
  [_]
  (template/set-operation-verdict-template org-id operation template-id))

(defquery selectable-verdict-templates
  {:description      "Returns a map of where keys are permit types or
  operation names and templates (id, name pairs) are values. Permit
  types that are missing are not supported by Pate verdict template
  mechanism."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [command]
  (ok :items (template/selectable-verdict-templates (template/command->options command))))
