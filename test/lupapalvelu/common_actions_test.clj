(ns lupapalvelu.common-actions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test :refer [is]]
            [sade.schema-generators :as ssg]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.user :as user]
            [lupapalvelu.itest-util :refer [unauthorized?]]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.actions-api :as ca]
            ;; ensure all actions are registered by requiring server ns
            [lupapalvelu.server]
            [lupapalvelu.action :as action]))

(testable-privates lupapalvelu.action user-is-not-allowed-to-access?)

(def user-is-allowed-to-access?
  (complement user-is-not-allowed-to-access?))

(def permit-type-generator
  (-> (lupapalvelu.permit/permit-types)
      keys
      gen/elements))

(defn silly-application-generator
  [organization-id-generator]
  ;;TODO: generoi hakemukseen organization
  ;; userin org-authzin sallitut arvot:
  ;;  authorization/all-org-authz-roles
  (gen/let [permit-type permit-type-generator
            org-id organization-id-generator]
    (merge lupapalvelu.domain/application-skeleton
           {:permitType permit-type
            :organization org-id})))

(defn user-and-application-generator
  [org-id-generator]
  (let [str-org-id-generator (gen/fmap name org-id-generator)]
    (gen/let [user (ssg/generator user/User
                                  {user/OrgId org-id-generator})
              application (silly-application-generator str-org-id-generator)]
      [user application])))

(def with-limited-org-id-pool
  (let [org-ids (gen/sample (ssg/generator user/OrgId) 10)
        org-id-generator (gen/elements org-ids)]
    (user-and-application-generator org-id-generator)))

(defn test-with-input [user application]
  (let [command (action->command
                  {:user user
                   :data {:id "100"}
                   :application application}
                  "enable-accordions")]
    (validate command)))

#_(defspec user-is-allowed-to-access?-spec
  (prop/for-all [user (ssg/generator user/User)
                 application silly-application-generator]
    (let [command (action->command
                    {:user user
                     :data {:id "100"}
                     :application application}
                    "enable-accordions")
          result (test-with-input user application)]
      (if (and (user/authority? user)
               ;; kayttajalla on org-authz hakemuksen organisaatiossa
               (= "YA"
                  (:permitType application)))
        (is (nil? result))
        (is (false? (:ok result)))))))

(facts "Allowed actions for statementGiver"
  (let [allowed-actions #{:give-statement
                          :save-statement-as-draft
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
                          :view-file
                          :delete-attachment
                          :set-attachment-type
                          :set-attachment-meta
                          :set-attachment-group-enabled
                          :upload-attachment
                          :upload-file-authenticated
                          :bind-attachment
                          :bind-attachments
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
                          :ya-extensions
                          :tasks-tab-visible
                          :application-info-tab-visible
                          :application-summary-tab-visible
                          :application-verdict-tab-visible
                          :application-handlers
                          :application-organization-archive-enabled}
        user {:id "user123" :organizations [] :role :applicant}
        application {:organization "999-R" :auth [{:id "user123" :role "statementGiver"}]}]
    (doseq [command (foreach-action {:web {} :user user :application application :data {}})
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
                               :suti-update-id :suti-update-added
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
                           :get-possible-statement-statuses
                           :reduced-foreman-history :foreman-history :foreman-applications
                           :get-building-info-from-wfs :mark-seen :info-links :organization-links
                           :mark-seen-organization-links :pdfa-casefile :suti-application-data :suti-application-products
                           :redirect-to-3d-map :ya-extensions
                           :ram-linked-attachments :attachment-groups :attachments :attachment :attachments-filters :attachments-tag-groups
                           :application-organization-archive-enabled
                           ; raw
                           :download-all-attachments :download-attachments
                           :pdf-export
                           :application-guests :submitted-application-pdf-export
                           ; tab visibility
                           :tasks-tab-visible :application-info-tab-visible :application-summary-tab-visible
                           :application-verdict-tab-visible}]
    (doseq [command (foreach-action {:web {} :user user :application application :data {}})
            :let [action (keyword (:action command))
                  {user-roles :user-roles} (get-meta action)]]
      (when (and user-roles (not (user-roles :anonymous)))
        (let [result (user-is-not-allowed-to-access? command application)]

          (fact {:midje/description (name action)}
            (if (allowed-actions action)
              result => nil?
              result => unauthorized?)))))))
