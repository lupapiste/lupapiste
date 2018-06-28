(ns lupapalvelu.common-actions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :refer [safe-update-in]]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test :refer [is]]
            [sade.schema-generators :as ssg]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.generators.application :as app-gen]
            [lupapalvelu.generators.organization]
            [lupapalvelu.generators.permit :as permit-gen]
            [lupapalvelu.generators.user :as user-gen]
            [lupapalvelu.mock.organization :as mock-org
             :refer [with-all-mocked-orgs with-mocked-orgs]]
            [lupapalvelu.mock.user :as mock-usr]
            [lupapalvelu.itest-util :refer [unauthorized?]]
            [lupapalvelu.test-util :refer [passing-quick-check catch-all]]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.actions-api :as ca]
    ;; ensure all actions are registered by requiring server ns
            [lupapalvelu.server]
            [lupapalvelu.action :as action]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.authorization :as auth]
            [sade.schema-utils :as ssu]))

(testable-privates lupapalvelu.action user-is-not-allowed-to-access? enrich-default-permissions enrich-action-contexts)

(facts "enable-accordions"
  (with-all-mocked-orgs
    (let [application (merge lupapalvelu.domain/application-skeleton
                             {:permitType "YA"
                              :organization (:id mock-org/sipoo-ya)})
          action-skeleton {:user (usr/with-org-auth mock-usr/sonja)
                           :application application
                           :data {:id ""}}
          command (build-action "enable-accordions" action-skeleton)]
      (facts "permitType YA"
        (fact "authority that has orgAuthz in applications org"
          (validate command) => ok?)
        (fact "authority without orgAuthz in applications org"
          (validate (build-action
                      "enable-accordions"
                      (assoc action-skeleton :user (usr/with-org-auth mock-usr/ronja))))
          => fail?))
      (fact "other permitType"
        (validate (build-action
                    "enable accordions"
                    (assoc-in action-skeleton [:application :permitType] "R")))
        => fail?))))

(def orgs-gen
  "Generates a set of organizations with different ids"
  (gen/let [org-ids (gen/set (ssg/generator org/OrgId) {:num-elements 10
                                                        :max-tries 50})
            orgs (gen/vector (ssg/generator (ssu/select-keys org/Organization [:id :name :statementGivers])) 10)]
    (let [fix-id (fn [id org] (assoc org :id id))
          with-fixed-ids (map fix-id org-ids orgs)]
      (set with-fixed-ids))))

(def enable-accordions-gen
  (gen/let [orgs orgs-gen
            user (user-gen/user-with-org-auth-gen (->> orgs (map (comp keyword :id)) (gen/elements)))
            application (app-gen/application-gen user
                                                 :permit-type-gen permit-gen/YA-biased-permit-type
                                                 :org-id-gen      (gen/elements (map :id orgs)))]
    {:orgs orgs
     :application application
     :user user}))

(defn enable-accordions-test [{:keys [orgs application user]}]
  (let [action-skeleton {:user (usr/with-org-auth user)
                         :application application
                         :data {:id ""}}
        action (build-action "enable-accordions" action-skeleton)
        org-id (:organization application)
        permit-type (:permitType application)
        allowed-to-access? (action/user-is-allowed-to-access?
                             action application)
        authority-in-org? (not-empty (get-in user [:orgAuthz (keyword org-id)]))
        auths-in-application? (auth/user-authz? roles/all-authenticated-user-roles
                                                application
                                                user)]
    (with-mocked-orgs orgs
      (cond (not allowed-to-access?)         (fail? (validate action))
            (and authority-in-org?
                 (or (= permit-type "YA")
                     (= permit-type "ARK"))) (ok? (validate action))
            authority-in-org?                (fail? (validate action))
            :else                            (ok? (validate action))))))

(def enable-accordions-prop
  (prop/for-all [gen-data enable-accordions-gen]
    (enable-accordions-test gen-data)))

(defn relevant-keys-accordions [quick-check-result]
  (-> quick-check-result
      (safe-update-in [:shrunk :smallest 0 :application]
                             select-keys [:permitType :organization :id])
      (dissoc :fail)))

(fact "enable-accordions-spec"
  (relevant-keys-accordions (tc/quick-check 500 enable-accordions-prop :max-size 20))
  =>
  passing-quick-check)

(facts "Allowed actions for organization statementGiver"
  (let [allowed-actions #{:give-statement
                          :save-statement-as-draft
                          :get-possible-statement-statuses
                          :application
                          :openinforequest
                          :allowed-actions
                          :allowed-actions-for-category
                          :validate-doc
                          :fetch-validation-errors
                          :add-comment
                          :enable-accordions
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
                          :reduced-foreman-history
                          :add-application-tags
                          :application-organization-tags
                          :get-organization-areas
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
                          :ya-application
                          :tasks-tab-visible
                          :application-info-tab-visible
                          :application-summary-tab-visible
                          :application-verdict-tab-visible
                          :application-statement-tab-visible
                          :application-handlers
                          :application-organization-archive-enabled
                          :create-application
                          :document-states
                          :authorized-to-apply-submit-restriction-to-other-auths}

        user {:id "user123" :role :applicant}
        application {:organization "999-R" :auth [{:id "user123" :role "statementGiver"}]}]
    (doseq [command (foreach-action {:web {} :user user :application application :data {} :organization (delay {:statementGivers [{:id "user123"}]})})
            :let [action (keyword (:action command))
                  result (or (user-is-not-allowed-to-access? command application)
                             (-> command
                                 enrich-default-permissions
                                 enrich-action-contexts
                                 access-denied-by-insufficient-permissions))]]
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
                               :update-application-company-notes
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
                           :redirect-to-3d-map :ya-extensions :authority-notice
                           :ram-linked-attachments :attachment-groups :attachments :attachment :attachments-filters :attachments-tag-groups
                           :application-organization-archive-enabled
                           ;; pate
                           :pate-verdicts :pate-verdict :pate-verdict-tab :pate-contract-tab
                           ;; raw
                           :download-all-attachments :download-attachments
                           :pdf-export
                           :application-guests :submitted-application-pdf-export
                           ;; tab visibility
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
