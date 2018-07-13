(ns lupapalvelu.pate.verdict-template-api
  "Verdict templates and category-wide settings."
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.strings :as ss]
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
  {:description "Pseudo-query that fails if Pate is not enabled in
  the organization"
   :feature     :pate
   :permissions [{:required [:organization/admin]}]
   :parameters [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks  [pate-enabled]}
  [_])


(defcommand new-verdict-template
  {:description      "Creates new empty template. Returns template id, name
  and draft."
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [org-id category]
   :input-validators [(partial action/non-blank-parameters [:org-id :category])]
   :pre-checks       [pate-enabled
                      valid-category]}
  [{:keys [created lang] :as command}]
  (ok (assoc (template/new-verdict-template org-id
                                            created
                                            lang
                                            category)
        :filled false)))

(defcommand set-verdict-template-name
  {:description      "Name cannot be empty."
   :feature          :pate
   :parameters       [:org-id template-id name]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id :name])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]
   :permissions      [{:required [:organization/admin]}]}
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
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id template-id path value]
   ;; Value is validated upon saving according to the schema.
   :input-validators [(partial action/vector-parameters [:path])
                      (partial action/non-blank-parameters [:template-id :org-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]}
  [{:keys [created] :as command}]
  (let [organization (template/command->organization command)
        {:keys [data category]
         :as   updated} (template/save-draft-value organization
                                                  template-id
                                                  created
                                                  path
                                                  value)]
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
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id template-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable :named :filled)]}
  [{:keys [created user user-organizations] :as command}]
  (template/publish-verdict-template (template/command->organization command)
                                     template-id
                                     created)
  (ok :published created))

(defquery verdict-templates
  {:description      "Id, name, modified, published and deleted maps for
  every verdict template."
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [command]
  (ok :verdict-templates (->> (template/command->organization command)
                              :verdict-templates
                              :templates
                              (map template/verdict-template-summary))))

(defquery verdict-template-categories
  {:description      "Categories for the user's organization. Does not
   include legacy-only categories."
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [command]
  (ok :categories (template/organization-categories (template/command->organization command))))

(defquery verdict-template
  {:description      "Verdict template summary plus draft data. The
  template must be editable."
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id template-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]}
  [command]
  (ok (template/verdict-template-response-data (template/command->organization command)
                                               template-id)))

(defcommand update-and-open-verdict-template
  {:description      "Like verdict-template but also updates the template's
  settings dependencies."
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [org-id template-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])]
   :pre-checks       [pate-enabled
                      (template/verdict-template-check :editable)]}
  [command]
  (ok (template/verdict-template-update-and-open command)))

(defcommand toggle-delete-verdict-template
  {:description      "Toggle template's deletion status"
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id template-id delete]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])
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
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id template-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :template-id])]
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
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id category]
   :input-validators [(partial action/non-blank-parameters [:org-id :category])]
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
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id category path value]
   ;; Value is validated against schema on saving.
   :input-validators [(partial action/non-blank-parameters [:org-id :category])
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
               :filled   (template/settings-filled? {:data data}
                                                    category)}
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
  [{{:keys [operation template-id]} :data :as command}]
  (when (and operation (not (ss/blank? template-id)))
    (let [{category :category} (template/verdict-template (template/command->organization command)
                                                          template-id)]
      (when-not (and category
                     (util/=as-kw (template/operation->category operation)
                                  category))
        (fail :error.invalid-category)))))

(defquery default-operation-verdict-templates
  {:description      "Map where keys are operations and values template
  ids. Deleted templates are filtered out."
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [command]
  (ok :templates (template/operation-verdict-templates (template/command->organization command))))

(defcommand set-default-operation-verdict-template
  {:description         "Set default verdict template for a selected operation
  in the organization. If template-id is not given, the default is
  cleared."
   :feature             :pate
   :permissions         [{:required [:organization/admin]}]
   :parameters          [org-id operation]
   :optional-parameters [template-id]
   :input-validators    [(partial action/non-blank-parameters [:org-id :operation])]
   :pre-checks          [pate-enabled
                         (template/verdict-template-check :published :editable :blank)
                         organization-operation
                         operation-vs-template-category]}
  [command]
  (template/set-operation-verdict-template org-id
                                           operation
                                           template-id))

(defquery selectable-verdict-templates
  {:description      "Returns a map of where keys are permit types or
  operation names and templates (id, name pairs) are values. Permit
  types that are missing are not supported by Pate verdict template
  mechanism."
   :feature          :pate
   :permissions      [{:required [:organization/admin]}]
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
   :pre-checks       [pate-enabled]}
  [command]
  (ok :items (template/selectable-verdict-templates (template/command->organization command))))
