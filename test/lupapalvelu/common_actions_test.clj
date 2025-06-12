(ns lupapalvelu.common-actions-test
  (:require [clojure.test.check.generators :as gen]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.generators.application :as app-gen]
            [lupapalvelu.generators.organization]
            [lupapalvelu.generators.permit :as permit-gen]
            [lupapalvelu.generators.user :as user-gen]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.mock.organization :as mock-org :refer [with-all-mocked-orgs with-mocked-orgs]]
            [lupapalvelu.mock.user :as mock-usr]
            [lupapalvelu.organization :as org]
            [lupapalvelu.server] ;; ensure all actions are registered by requiring server ns
            [lupapalvelu.test-util :refer [unauthorized?]]
            [lupapalvelu.user :as usr]
            [midje.experimental :as experimental]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [fail? ok?]]
            [sade.schema-generators :as ssg]
            [sade.schema-utils :as ssu]
            [sade.strings :as ss]))

(set! *warn-on-reflection* true)

(testable-privates lupapalvelu.action
                   user-is-not-allowed-to-access?
                   enrich-default-permissions
                   enrich-action-contexts)

(facts "enable-accordions"
  (against-background [(lockdown/lockdown?) => false]
    (with-all-mocked-orgs
      (let [application     (merge lupapalvelu.domain/application-skeleton
                                   {:permitType   "YA"
                                    :organization (:id mock-org/sipoo-ya)})
            action-skeleton {:user        (usr/with-org-auth mock-usr/sonja)
                             :application application
                             :data        {:id ""}}
            command         (build-action "enable-accordions" action-skeleton)]
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
          => fail?)))))

(def orgs-gen
  "Generates a set of organizations with different ids"
  (gen/let [org-ids (gen/set (ssg/generator org/OrgId) {:num-elements 10
                                                        :max-tries    50})
            orgs    (gen/vector (ssg/generator (ssu/select-keys org/Organization [:id :name :statementGivers])) 10)]
    (let [fix-id         (fn [id org] (assoc org :id id))
          with-fixed-ids (map fix-id org-ids orgs)]
      (set with-fixed-ids))))

(def enable-accordions-gen
  (gen/let [orgs        orgs-gen
            user        (user-gen/user-with-org-auth-gen (->> orgs (map (comp keyword :id)) (gen/elements)))
            application (app-gen/application-gen user
                                                 :permit-type-gen permit-gen/YA-biased-permit-type
                                                 :org-id-gen (gen/elements (map :id orgs)))]
    {:orgs        orgs
     :application application
     :user        user}))

(defn enable-accordions-test [{:keys [orgs application user]}]
  (let [action-skeleton           {:user        (usr/with-org-auth user)
                                   :application application
                                   :data        {:id ""}}
        action                    (build-action "enable-accordions" action-skeleton)
        org-id                    (:organization application)
        permit-type               (:permitType application)
        allowed-to-access?        (user-is-allowed-to-access?
                                    action application)
        insufficient-permissions? (-> (enrich-default-permissions action)
                                      (access-denied-by-insufficient-permissions))
        authority-in-org?         (not-empty (get-in user [:orgAuthz (keyword org-id)]))]
    (with-mocked-orgs orgs
      (against-background [(lockdown/lockdown?) => false]
        (cond (or (not allowed-to-access?)
                  insufficient-permissions?)   (fact "not allowed" (validate action) => fail?)
              (and authority-in-org?
                   (or (= permit-type "YA")
                       (= permit-type "ARK"))) (fact "YA / ARK always on" (validate action) => ok?)
              authority-in-org?                (fact "authority fails" (validate action) => fail?)
              :else                            (fact "else show" (validate action) => ok?))))))

(experimental/for-all "enable-accordions"
                      [gen-data enable-accordions-gen]
                      {:max-size 20 :num-tests 500}
                      (enable-accordions-test gen-data))

(facts "Allowed actions for organization statementGiver"
  (let [allowed-actions  #{:add-application-tags
                           :add-authority-notice
                           :add-comment
                           :allowed-action
                           :allowed-actions
                           :allowed-actions-for-category
                           :application
                           :application-gis-data
                           :application-guests
                           :application-handlers
                           :application-has-foreman
                           :application-info-tab-visible
                           :application-operations
                           :application-organization-archive-enabled
                           :application-organization-tags
                           :application-statement-tab-visible
                           :application-summary-tab-visible
                           :arcgis-feature-geometry
                           :arcgis-layer-tile
                           :attachment
                           :attachment-groups
                           :attachment-operations
                           :attachment-types
                           :attachments
                           :attachments-filters
                           :attachments-tag-groups
                           :authority-notice
                           :authorized-to-apply-submit-restriction-to-other-auths
                           :bind-attachment
                           :bind-attachments
                           :building-site-information
                           :bulletin-comments
                           :bulletin-versions
                           :change-urgency
                           :climate-reports-valid
                           :comments
                           :connect-sso
                           :create-application
                           :delete-attachment
                           :delete-attachment-version
                           :designer-applications
                           :designer-consent-given
                           :disconnect-sso
                           :dmcity-enabled
                           :document
                           :document-by-name
                           :document-states
                           :download-all-attachments
                           :download-attachment
                           :download-attachments
                           :download-xkt-file
                           :enable-accordions
                           :external-states
                           :fetch-ifc-doc
                           :fetch-linked-file-bim-results
                           :fetch-linked-file-metadata
                           :fetch-linked-file-metadatas-for-application
                           :fetch-validation-errors
                           :foreman-applications
                           :foreman-consent-given
                           :foreman-documents
                           :foreman-responsibility-timestamps
                           :generate-xkt-file
                           :get-assurances-for-application
                           :get-building-info-from-wfs
                           :get-foreman-submit-settings
                           :get-organization-areas
                           :get-possible-statement-statuses
                           :give-statement
                           :ifc-data-file-version-ids
                           :info-construction-status
                           :info-link-delete
                           :info-link-reorder
                           :info-link-upsert
                           :info-links
                           :inspection-summaries-for-application
                           :job-status
                           :latest-attachment-version
                           :linked-file-bim-result-bcf
                           :linked-file-version
                           :location-operations
                           :low-carbon-assessment
                           :map-config
                           :mark-entries-seen
                           :mark-seen
                           :mark-seen-organization-links
                           :openinforequest
                           :organization-links
                           :pate-contract-tab
                           :pate-verdict-tab
                           :pate-verdicts
                           :pdf-export
                           :pdf-foreman
                           :pdfa-casefile
                           :plan-document-infos
                           :printing-order-pricing
                           :processing-days
                           :processing-entries
                           :processing-entries-tab
                           :processing-log-pdf
                           :processing-logdata
                           :processing-requests-status
                           :proposal-pdf
                           :published-pate-verdict
                           :published-verdict-attachment-ids
                           :ram-linked-attachments
                           :read-model-data-job
                           :redirect-to-3d-map
                           :reduced-foreman-history
                           :removable-attachment-versions
                           :resolve-multi-attachment-updates
                           :rotate-pdf
                           :save-statement-as-draft
                           :set-attachment-group-enabled
                           :set-attachment-meta
                           :set-attachment-target
                           :set-attachment-type
                           :statement-attachment-allowed
                           :submitted-application-pdf-export
                           :suti-application-data
                           :suti-application-products
                           :task-order
                           :tasks-tab-visible
                           :update-user
                           :upload-attachment
                           :upload-file-authenticated
                           :validate-doc
                           :verdict-pdf
                           :view-attachment
                           :view-file
                           :xkt-file-info
                           :ya-application
                           :ya-extensions}
        user             {:id   "user123"
                          :role "authority"}
        application      {:organization "999-R"
                          :auth         [{:id   "user123"
                                          :role "statementGiver"}]}
        command-sceleton {:web          {}
                          :user         user
                          :application  application
                          :data         {}
                          :organization (delay {:statementGivers [{:id "user123"}]})}]
    (doseq [command (->> (foreach-action command-sceleton)
                         (map enrich-default-permissions)
                         (map enrich-action-contexts))
            :let [action (:action command)
                  result (or (user-is-not-allowed-to-access? command application)
                             (access-denied-by-insufficient-permissions command))]]
      (fact {:midje/description (name action)}
        (fact "has user" (:user command) => user)
        (fact "has application" (:application command) => application)
        (fact result => (if (allowed-actions action)
                          nil?
                          unauthorized?))))))

(facts "Actions with id and state 'draft' are not allowed for authority"
  (let [allowed-actions #{:invite-guest
                          :delete-guest-application
                          :toggle-guest-subscription
                          :application-guests
                          :decline-invitation
                          :suti-update-id
                          :suti-update-added
                          :cancel-application
                          :info-links
                          :organization-links
                          :update-application-company-notes
                          :redirect-to-3d-map
                          :set-operation-location
                          :init-resumable-upload-for-application}]
    (doseq [[action data] (get-actions)
            :when         (and
                            (= :command (keyword (:type data)))
                            (:authority (:user-roles data))
                            (some #{:id} (:parameters data))
                            (some #{:draft} (:states data)))
            :let          [pre-checks    (:pre-checks data)
                           checker-names (map (fn [check]
                                                (let [^Class c (type check)]
                                                  (-> c .getName (ss/suffix "$")))) pre-checks)
                           result        (some (partial = "validate_authority_in_drafts") checker-names)]]
      (fact {:midje/description (name action)}
        (if (allowed-actions action)
          (fact "is nil"
            result => nil?)
          (fact "is truthy"
            result => truthy))))))

(defn allowed-authority-actions
  "Allowed actions when determined solely by the user-role. Thus, if an action uses
  permissions it is ignored."
  [role allowed-actions]
  (let [user        {:id "user123" :orgAuthz {:999-R #{role}} :role "authority"}
        application {:organization "999-R" :auth [] :id "123" :permitType "YA"}]
    (doseq [command (foreach-action {:web {} :user user :application application :data {}})
            :let [action (keyword (:action command))
                  {user-roles :user-roles} (get-meta action)]]
      (when (and user-roles (not (user-roles :anonymous)))
        (let [result (user-is-not-allowed-to-access? command application)]

          (fact {:midje/description (name action)}
            (if (allowed-actions action)
              (fact "is nil"
                result => nil?)
              (fact "is unauthorized"
                result => unauthorized?))))))))

(def reader-actions
  "Old-school reader actions"
  #{; queries
    :application :validate-doc :fetch-validation-errors :document
    :get-possible-statement-statuses
    :reduced-foreman-history :foreman-history :foreman-applications
    :get-building-info-from-wfs :mark-seen :info-links :organization-links
    :mark-seen-organization-links :pdfa-casefile :suti-application-data
    :suti-application-products
    :ya-extensions :authority-notice
    :ram-linked-attachments :attachment-groups :attachments
    :attachment :attachments-filters :attachments-tag-groups
    :application-organization-archive-enabled
    ;; pate
    :pate-verdicts :pate-verdict :pate-verdict-tab
    :pate-contract-tab :published-pate-verdict
    :published-verdict-attachment-ids :task-order
    ;; raw
    :download-all-attachments :download-attachments
    :pdf-export
    :application-guests :submitted-application-pdf-export
    ;; tab visibility
    :tasks-tab-visible :application-info-tab-visible
    :application-summary-tab-visible :nearby-application-markers})

(facts "Allowed actions for reader authority"
  (allowed-authority-actions :reader reader-actions))

(facts "Allowed actions for biller authority. Apart from the invoice api, the biller is reader."
  (allowed-authority-actions :biller reader-actions))
