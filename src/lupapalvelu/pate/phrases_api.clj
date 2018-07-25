(ns lupapalvelu.pate.phrases-api
  "Phrases support for verdicts and verdict templates. Although the
  phrases are initially implemented for Pate, the mechanism does not
  require Pate to be enabled in an organization."
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.pate.phrases :as phrases]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]))

(defn- org-id-valid
  "Pre-checker that fails if the org-id parameter does not match any of
  the authority admin's organizations."
  [command]
  (when (some-> command :data :org-id)
    (when-not (template/command->organization command)
      (fail :error.invalid-organization))))

(defquery organization-phrases
  {:description      "Phrases for an authority admin's organization."
   :permissions      [{:required [:organization/admin]}]
   :feature          :pate
   :parameters       [:org-id]
   :input-validators [(partial action/non-blank-parameters [:org-id])]
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
   :feature          :pate
   ;; TODO: Refine states
   :states           states/all-states}
  [{:keys [organization]}]
  (ok :phrases (get @organization :phrases [])))

(defcommand upsert-phrase
  {:description         "Update old or create new phrase."
   :permissions         [{:required [:organization/admin]}]
   :parameters          [:org-id category tag phrase]
   :optional-parameters [phrase-id]
   :input-validators    [phrases/valid-category
                         (partial action/non-blank-parameters [:org-id :tag :phrase])]
   :pre-checks          [org-id-valid
                         phrases/phrase-id-ok]
   :feature             :pate}
  [command]
  (phrases/upsert-phrase command))

(defcommand delete-phrase
  {:description      "Delete an existing phrase."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [org-id phrase-id]
   :input-validators [(partial action/non-blank-parameters [:org-id :phrase-id])]
   :pre-checks       [org-id-valid
                      phrases/phrase-id-exists]
   :feature          :pate}
  [{user :user}]
  (phrases/delete-phrase org-id phrase-id))
