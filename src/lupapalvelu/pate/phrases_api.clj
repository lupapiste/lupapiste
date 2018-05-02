(ns lupapalvelu.pate.phrases-api
  "Phrases support for verdicts and verdict templates."
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.pate.phrases :as phrases]
            [lupapalvelu.pate.verdict-template :as template]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]))

(defquery organization-phrases
  {:description "Phrases for the authority admin's organization."
   :permissions [{:required [:organization/admin]}]
   :feature     :pate}
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
   :parameters          [category tag phrase]
   :optional-parameters [phrase-id]
   :input-validators    [phrases/valid-category
                         (partial action/non-blank-parameters [:tag :phrase])]
   :pre-checks          [phrases/phrase-id-ok]
   :feature             :pate}
  [command]
  (phrases/upsert-phrase command))

(defcommand delete-phrase
  {:description      "Delete an existing phrase."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [phrase-id]
   :input-validators [(partial action/non-blank-parameters [:phrase-id])]
   :pre-checks       [phrases/phrase-id-exists]
   :feature          :pate}
  [{user :user}]
  (phrases/delete-phrase (usr/authority-admins-organization-id user)
                         phrase-id))
