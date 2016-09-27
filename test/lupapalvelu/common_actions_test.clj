(ns lupapalvelu.common-actions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.itest-util :refer [unauthorized?]]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.actions-api :as ca]
            ;; ensure all actions are registered by requiring server ns
            [lupapalvelu.server]))

(testable-privates lupapalvelu.action user-is-not-allowed-to-access?)

(facts "Allowed actions for statementGiver"
  (let [allowed-actions #{:give-statement
                          :save-statement-as-draft
                          :delete-statement
                          :get-possible-statement-statuses
                          :application
                          :allowed-actions
                          :allowed-actions-for-category
                          :validate-doc
                          :fetch-validation-errors
                          :add-comment
                          :comments
                          :attachments
                          :attachment
                          :attachments-tag-groups
                          :attachments-filters
                          :attachment-groups
                          :attachment-types
                          :attachment-operations
                          :view-attachment
                          :delete-attachment
                          :set-attachment-type
                          :set-attachment-meta
                          :upload-attachment
                          :rotate-pdf
                          :pdf-export
                          :submitted-application-pdf-export
                          :download-all-attachments
                          :download-attachment
                          :download-attachments
                          :delete-attachment-version
                          :change-urgency
                          :add-authority-notice
                          :foreman-applications
                          :foreman-history
                          :reduced-foreman-history
                          :add-application-tags
                          :get-organization-tags
                          :get-organization-areas
                          :preview-attachment
                          :document
                          :mark-seen
                          :pdfa-casefile
                          :get-building-info-from-wfs
                          :authority-notice
                          :application-guests
                          :statement-attachment-allowed
                          :ram-linked-attachments
                          :latest-attachment-version
                          :suti-application-data
                          :suti-application-products
                          :info-links
                          :info-link-delete
                          :info-link-reorder
                          :info-link-upsert
                          :organization-links
                          :mark-seen-organization-links
                          :redirect-to-3d-map
                          :ya-extensions}
        user {:id "user123" :organizations [] :role :applicant}
        application {:organization "999-R" :auth [{:id "user123" :role "statementGiver"}]}]
    (doseq [command (foreach-action {} user application {})
            :let [action (keyword (:action command))
                  result (user-is-not-allowed-to-access? command application)]]
      (fact {:midje/description (name action)}
        (fact "has user" (:user command) => user)
        (fact "has upplication" (:application command) => application)
        (if (allowed-actions action)
          result => nil?
          result => unauthorized?)))))


(facts "Actions with id and state 'draft' are not allowed for authority"
       (let [allowed-actions #{:invite-guest :delete-guest-application
                               :toggle-guest-subscription :application-guests :decline-invitation
                               :suti-update-id :suti-update-added :set-attachment-contents
                               :cancel-application :info-links :organization-links
                               :redirect-to-3d-map}]
    (doseq [[action data] (get-actions)
            :when (and
                    (= :command (keyword (:type data)))
                    (:authority (:user-roles data))
                    (some #{:id} (:parameters data))
                    (some #{:draft} (:states data)))
            :let [pre-checks (:pre-checks data)
                  checker-names (map #(-> % type .getName (ss/suffix "$")) pre-checks)
                  result (some (partial = "validate_authority_in_drafts") checker-names)]]
      (fact {:midje/description (name action)}
        (if (allowed-actions action)
          result => nil?
          result => truthy)))))

(facts "Allowed actions for reader authority"
  (let [user {:id "user123" :orgAuthz {:999-R #{:reader}} :role "authority"}
        application {:organization "999-R" :auth [] :id "123" :permitType "YA"}
        allowed-actions  #{; queries
                           :application :validate-doc :fetch-validation-errors :document
                           :get-organization-tags :get-organization-areas :get-possible-statement-statuses
                           :reduced-foreman-history :foreman-history :foreman-applications :enable-foreman-search
                           :get-building-info-from-wfs :tasks-tab-visible
                           :mark-seen :info-links :organization-links :mark-seen-organization-links
                           :pdfa-casefile :suti-application-data :suti-application-products
                           :redirect-to-3d-map :ya-extensions
                           :ram-linked-attachments :attachment-groups :attachments :attachment :attachments-filters :attachments-tag-groups
                           ; raw
                           :preview-attachment :view-attachment :download-attachment :download-attachments :download-all-attachments
                           :pdf-export
                           :application-guests :latest-attachment-version :submitted-application-pdf-export}]
    (doseq [command (foreach-action {} user application {})
            :let [action (keyword (:action command))
                  {user-roles :user-roles} (get-meta action)]]
      (when (and user-roles (not (user-roles :anonymous)))
        (let [result (user-is-not-allowed-to-access? command application)]

          (fact {:midje/description (name action)}
            (if (allowed-actions action)
              result => nil?
              result => unauthorized?)))))))
