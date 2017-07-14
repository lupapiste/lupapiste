(ns lupapalvelu.matti.matti-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.matti.matti :as matti]
            [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- command->organization
  "User-organizations is not available for input-validators."
  [{:keys [user user-organizations]}]
  (util/find-by-id (usr/authority-admins-organization-id user)
                   user-organizations ))

;; ----------------------------------
;; Pre-checks
;; ----------------------------------
(defn- verdict-template-exists [{data :data :as command}]
  (when-not (matti/verdict-template (command->organization command)
                                    (:template-id data))
    (fail :error.verdict-template-not-found)))

(defn- check-template [{{template-id :template-id} :data :as command}
                       check-fn]
  (when template-id
    (if-let [template (matti/verdict-template (command->organization command)
                                              template-id)]
      (check-fn template)
      (fail :error.verdict-template-not-found))))

(defn- verdict-template-editable
  "Template exists and is editable (not deleted)."
  [command]
  (check-template command
                  #(when (:deleted %)
                     (fail :error.verdict-template-deleted))))

(defn- verdict-template-has-name
  [command]
  (check-template command
                  #(when-not (-> % :name ss/not-blank?)
                     (fail :error.verdict-template-name-missing))))

(defn- valid-category [{{category :category} :data :as command}]
  (when category
    (when-not (util/includes-as-kw? (matti/organization-categories (command->organization command))
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
      (if-let [generic (matti/generic (command->organization command)
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
  {:description "Creates new empty template. Returns template id, name
  and draft."
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
  {:parameters       [template-id name]
   :input-validators [(partial action/non-blank-parameters [:name])]
   :pre-checks       [verdict-template-editable]
   :user-roles       #{:authorityAdmin}}
  [{created :created :as command}]
  (matti/set-name (command->organization command)
                  template-id
                  created
                  name)
  (ok :modified created))

(defcommand save-verdict-template-draft-value
  {:description      "Incremental save support for verdict template
  drafts. Returns modified timestamp."
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id path value]
   ;; Value is validated upon saving according to the schema.
   :input-validators [(partial action/vector-parameters [:path])
                      (partial action/non-blank-parameters [:template-id])]
   :pre-checks       [verdict-template-editable]}
  [{:keys [created] :as command}]
  (if-let [error (matti/save-draft-value (command->organization command)
                                         template-id
                                         created
                                         path
                                         value)]
    (fail error)
    (ok :modified created)))

(defcommand publish-verdict-template
  {:description      "Creates new verdict template version. The version
  includes also the current settings."
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [verdict-template-editable
                      verdict-template-has-name]}
  [{:keys [created user user-organizations] :as command}]
  (matti/publish-verdict-template (command->organization command)
                                  template-id
                                  created)
  (ok :published created))

(defquery verdict-templates
  {:description "Id, name, modified, published and deleted maps for
  every verdict template. The response also includes category list."
   :user-roles #{:authorityAdmin}}
  [command]
  (ok :verdict-templates (->> (command->organization command)
                              :verdict-templates
                              :templates
                              (map matti/verdict-template-summary))))

(defquery verdict-template-categories
  {:description "Categories for the user's organization"
   :user-roles #{:authorityAdmin}}
  [command]
  (ok :categories (matti/organization-categories (command->organization command))))

(defquery verdict-template
  {:description      "Verdict template summary plus draft data. The
  template must be editable."
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [verdict-template-editable]}
  [command]
  (let [template (matti/verdict-template (command->organization command)
                                         template-id)]
    (ok (assoc (matti/verdict-template-summary template)
               :draft (:draft template)))))

(defcommand toggle-delete-verdict-template
  {:description      "Toggle template's deletion status"
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id delete]
   :input-validators [(partial action/non-blank-parameters [:template-id])
                      (partial action/boolean-parameters [:delete])]
   :pre-checks       [verdict-template-exists]}
  [command]
  (matti/set-deleted (command->organization command)
                     template-id
                     delete))

(defcommand copy-verdict-template
  {:description      "Makes copy of the template. The new template does not
  have any published versions. In other words, the draft of the
  original is copied."
   :user-roles       #{:authorityAdmin}
   :parameters       [template-id]
   :input-validators [(partial action/non-blank-parameters [:template-id])]
   :pre-checks       [verdict-template-exists]}
  [{:keys [created lang] :as command}]
  (ok (matti/copy-verdict-template (command->organization command)
                                    template-id
                                    created
                                    lang)))

;; ----------------------------------
;; Verdict template settings API
;; ----------------------------------

(defquery verdict-template-settings
  {:description      "Settings matching the category or empty response."
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [command]
  (when-let [settings (matti/settings (command->organization command)
                                      category)]
    (ok :settings settings)))

(defcommand save-verdict-template-settings-value
  {:description      "Incremental save support for verdict template
  settings. Returns modified timestamp. Creates settings if needed."
   :user-roles       #{:authorityAdmin}
   :parameters       [category path value]
   ;; Value is validated against schema on saving.
   :input-validators [(partial action/non-blank-parameters [:category])
                      (partial action/vector-parameters [:path])]
   :pre-checks       [valid-category]}
  [{:keys [created] :as command}]
  (matti/save-settings-value (command->organization command)
                             category
                             created
                             path
                             value)
  (ok :modified created))

;; ----------------------------------
;; Verdict template reviews API
;; ----------------------------------

(defquery verdict-template-reviews
  {:description      "Reviews matching the category"
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [command]
  (ok :reviews (matti/generic-list (command->organization command)
                                   category
                                   :reviews)))

(defcommand add-verdict-template-review
  {:description      "Creates empty review for the settings
  category. Returns review."
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
  (matti/set-review-details (command->organization command)
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
   :user-roles       #{:authorityAdmin}
   :parameters       [category]
   :input-validators [(partial action/non-blank-parameters [:category])]
   :pre-checks       [valid-category]}
  [command]
  (ok :plans (matti/generic-list (command->organization command)
                                 category
                                 :plans)))

(defcommand add-verdict-template-plan
  {:description      "Creates empty plan for the settings
  category. Returns plan."
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
   :user-roles          #{:authorityAdmin}
   :parameters          [plan-id]
   :optional-parameters [fi sv en type deleted]
   :input-validators    [(partial action/non-blank-parameters [:plan-id])
                         (supported-parameters :plan-id :fi :sv :en :deleted)
                         settings-generic-details-valid]
   :pre-checks          [(settings-generic-editable :plans)]}
  [{:keys [created user data] :as command}]
  (matti/set-plan-details (command->organization command)
                          created
                          plan-id
                          data)
  (ok :plan (matti/plan (usr/authority-admins-organization user)
                        plan-id)
      :modified created))
