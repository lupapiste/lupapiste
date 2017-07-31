(ns lupapalvelu.matti.phrases-api
  "Phrases support for verdicts and verdict templates."
  (:require [lupapalvelu.action :refer [defquery defcommand] :as action]
            [lupapalvelu.matti.matti :as matti]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [lupapalvelu.user :as usr]
            [lupapalvelu.matti.phrases :as phrases]))

(defquery organization-phrases
  {:description "Phrases for the authority admin's organization."
   :user-roles  #{:authorityAdmin}
   :feature     :matti}
  [command]
  (ok (select-keys (matti/command->organization command)
                   [:phrases])))

(defquery application-phrases
  {:description      "Phrases for the application organization."
   :user-roles       #{:authority}
   :parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :feature          :matti
   ;; TODO: Refine states
   :states           states/all-states}
  [{:keys [organization]}]
  (ok (select-keys @organization [:phrases])))

(defcommand upsert-phrase
  {:description         "Update old or create new phrase."
   :user-roles          #{:authorityAdmin}
   :parameters          [category tag phrase]
   :optional-parameters [phrase-id]
   :input-validators    [phrases/valid-category
                         (partial action/non-blank-parameters [:tag :phrase])]
   :pre-checks          [phrases/phrase-id-ok]}
  [command]
  (phrases/upsert-phrase command))

(defcommand delete-phrase
  {:description "Delete existing phrase"
   :user-roles #{:authorityAdmin}
   :parameters [phrase-id]
   :input-validators [(partial action/non-blank-parameters [:phrase-id])]
   :pre-checks [phrases/phrase-id-exists]}
  [{user :user}]
  (phrases/delete-phrase (usr/authority-admins-organization-id user)
                         phrase-id))
