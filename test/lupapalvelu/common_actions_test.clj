(ns lupapalvelu.common-actions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.actions-api :as ca]))

(testable-privates lupapalvelu.action user-is-not-allowed-to-access?)

(facts "Allowed actions for statementGiver"
  (let [allowed-actions #{:give-statement
                          :application
                          :allowed-actions
                          :validate-doc
                          :fetch-validation-errors
                          :add-comment
                          :attachment-types
                          :attachment-operations
                          :should-see-unsubmitted-statements
                          :view-attachment
                          :delete-attachment
                          :set-attachment-type
                          :set-attachment-meta
                          :upload-attachment
                          :download-all-attachments
                          :download-attachment
                          :delete-attachment-version
                          :change-urgency
                          :add-authority-notice
                          :foreman-applications
                          :foreman-history
                          :reduced-foreman-history
                          :add-application-tags
                          :get-organization-tags
                          :preview-attachment}
        user {:id "user123" :organizations [] :role :applicant}
        application {:organization "999-R" :auth [{:id "user123" :role "statementGiver"}]}]
    (doseq [command (ca/foreach-action user {} application)
            :let [action (keyword (:action command))
                  result (doc-result (user-is-not-allowed-to-access? command application) action)]]
      (if (allowed-actions action)
        result => (doc-check nil?)
        result => (doc-check = unauthorized)))))

(facts "Actions with id and state 'draft' are not allowed for authority"
  (let [allowed-actions #{:decline-invitation}] ; Authority can always decline his/hers invitation
    (doseq [[action data] (get-actions)
            :when (and
                    (= :command (keyword (:type data)))
                    (:authority (:user-roles data))
                    (some #{:id} (:parameters data))
                    (some #{:draft} (:states data)))
            :let [pre-checks (:pre-checks data)
                  checker-names (map #(-> % type .getName (ss/suffix "$")) pre-checks)
                  result (doc-result (some (partial = "validate_authority_in_drafts") checker-names) action)]]
      (if (allowed-actions action)
        result => (doc-check nil?)
        result => (doc-check truthy)))))
