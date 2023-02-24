(ns lupapalvelu.pate.phrases-api
  "Phrases support for verdicts and verdict templates. Although the
  phrases are initially implemented for Pate, the mechanism does not
  require Pate to be enabled in an organization."
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.phrases :as phrases]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]))

(defn- org-id-valid
  "Pre-checker that fails if the org-id parameter does not match any of
  the authority admin's organizations."
  [command]
  (when (some-> command :data :organizationId)
    (when-not (template/command->organization command)
      (fail :error.invalid-organization))))

(defquery organization-phrases
  {:description      "Phrases for an authority admin's organization."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [org-id-valid]}
  [command]
  (ok :phrases (get (template/command->organization command)
                    :phrases
                    [])))

(defquery application-phrases
  {:description      "Phrases for the application organization."
   :user-roles       #{:authority}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   ;; TODO: Refine states
   :states           states/all-states}
  [{:keys [organization]}]
  (ok :phrases (get @organization :phrases [])))

(defquery custom-organization-phrase-categories
  {:description      "Phrases categories added by user for an authority admin's organization."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [org-id-valid]}
  [_]
  (ok :custom-categories (get (org/get-organization organizationId)
                              :custom-phrase-categories
                              [])))

(defquery custom-application-phrase-categories
  {:description      "Phrase categories added by user for the application organization."
   :user-roles       #{:authority}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           states/all-states}
  [{:keys [organization]}]
  (ok :custom-categories (get @organization
                              :custom-phrase-categories
                              [])))

(defcommand upsert-phrase
  {:description         "Update old or create new phrase."
   :permissions         [{:required [:organization/admin]}]
   :parameters          [organizationId category tag phrase]
   :optional-parameters [phrase-id]
   :input-validators    [phrases/valid-category
                         (partial action/non-blank-parameters [:organizationId :tag :phrase])]
   :pre-checks          [org-id-valid
                         phrases/phrase-id-ok]}
  [command]
  (phrases/upsert-phrase command))

(defcommand delete-phrase
  {:description      "Delete an existing phrase."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId phrase-id]
   :input-validators [(partial action/non-blank-parameters [:organizationId :phrase-id])]
   :pre-checks       [org-id-valid
                      phrases/phrase-id-exists]}
  [_]
  (phrases/delete-phrase organizationId phrase-id))

(defcommand save-phrase-category
  {:description      "Save custom phrase category"
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId :category]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [org-id-valid]}
  [command]
  (phrases/save-phrase-category command))

(defcommand delete-phrase-category
  {:description      "Delete custom phrase category"
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId category]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :pre-checks       [org-id-valid]}
  [_]
  (phrases/delete-phrase-category organizationId category))
