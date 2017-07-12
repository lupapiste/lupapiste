(ns lupapalvelu.common-actions-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test :refer [is]]
            [sade.schema-generators :as ssg]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.generators.organization :as org-gen]
            [lupapalvelu.generators.user :as user-gen]
            [lupapalvelu.mock.organization :as mock-org
             :refer [with-all-mocked-orgs with-mocked-orgs]]
            [lupapalvelu.mock.user :as mock-usr]
            [lupapalvelu.user :as user]
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
            [lupapalvelu.authorization :as auth]))

(testable-privates lupapalvelu.action user-is-not-allowed-to-access?)

(facts "enable-accordions"
  (with-all-mocked-orgs
    (let [application (merge lupapalvelu.domain/application-skeleton
                             {:permitType "YA"
                              :organization (keyword (:id mock-org/sipoo-ya))})
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

(def permit-type-generator
  (-> (lupapalvelu.permit/permit-types)
      keys
      gen/elements))

(def YA-biased-permit-type
  (gen/frequency [[1 (gen/return "YA")]
                  [1 permit-type-generator]]))

(def authority-biased-user-role
  (gen/frequency [[1 (gen/return "authority")]
                  [1 (ssg/generator usr/Role)]]))

(def user-id-gen (ssg/generator usr/Id))

(def application-role-gen (gen/elements roles/all-authz-roles))

(defn single-auth-gen [& {:keys [user-id-gen application-role-gen]
                          :or {user-id-gen          user-id-gen
                               application-role-gen application-role-gen}}]
  (gen/let [user-id user-id-gen
            role application-role-gen]
    {:role role
     :id user-id}))

(defn application-auths-gen [user]
  (gen/let [auths (gen/vector (single-auth-gen))
            give-user-auths? gen/boolean
            users-auths (single-auth-gen :user-id-gen (gen/return (:id user)))]
    (if give-user-auths?
      (conj auths users-auths)
      auths)))

(defn application-gen [orgs user]
  (let [org-ids (map (comp keyword :id) orgs)]
    (gen/let [permit-type YA-biased-permit-type
              org-id (gen/elements org-ids)
              application-auths (application-auths-gen user)]
      (merge lupapalvelu.domain/application-skeleton
             {:permitType permit-type
              :organization org-id
              :auths application-auths}))))

(defn user-gen [orgs]
  (let [org-ids    (map (comp keyword :id) orgs)
        org-id-gen (gen/elements org-ids)
        base-user-gen (ssg/generator usr/User
                                     {usr/OrgId org-id-gen
                                      usr/Role authority-biased-user-role})]
    (gen/fmap usr/with-org-auth base-user-gen)))

(def enable-accordions-gen
  (gen/let [orgs (org-gen/generate)
            user (user-gen orgs)
            application (application-gen orgs user)]
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
        authority? (usr/authority? user)
        allowed-to-access? (action/user-is-allowed-to-access?
                             action application)
        authority-in-org? (usr/user-is-authority-in-organization? user (name org-id))
        auths-in-application? (auth/user-authz? roles/all-authenticated-user-roles
                                                application
                                                user)]
    (with-mocked-orgs orgs
      (cond (not allowed-to-access?)   (is (fail? (validate action)))
            (and authority?
                 authority-in-org?
                 (= permit-type "YA")) (is (ok? (validate action)))
            authority?                 (is (fail? (validate action)))
            :else                      (is (ok? (validate action)))))))

(def enable-accordions-prop
  (prop/for-all [gen-data enable-accordions-gen]
    (enable-accordions-test gen-data)))

(fact "enable-accordions-spec"
  (tc/quick-check 200 enable-accordions-prop :max-size 10))

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
