(ns lupapalvelu.bim.checking-plan.bim-checking-plan-api-test
  "Integration tests for BIM checking plans (i.e. BIM checkset configuration) API.
  In short, the Lupapiste admin must first create the plans, then the authority admin
  can configure them as the default options for operations."
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.test-util :refer [err]]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(def org-id "753-R")
(def non-b-org-id "753-YA")

(def auth-admin sipoo)
(def non-b-auth-admin sipoo-ya)

(with-test-system {:fixture "minimal"}
  (with-local-actions

    (fact "Set BIM compliance test (Taika) flag off"
      (command admin :set-organization-boolean-attribute
               :enabled false
               :organizationId org-id
               :attribute "bim-compliance-check-enabled")
      => ok?)

    (fact "Auth-admin for building organization can query plans"
      (query auth-admin :bim-checking-plans-for-org :organizationId org-id)
      => ok?)

    (fact "Auth-admin for non-building organization can't query plans"
      (query non-b-auth-admin :bim-checking-plans-for-org :organizationId non-b-org-id)
      => (err :error.not-bim-organization))

    ;; Mock the file being already uploaded since we don't use it directly here
    (fact "Lupapiste admin creates plan"
      (command admin :create-bim-checking-plan :object-key "mock-object-key") => ok?
      (command admin :create-bim-checking-plan :object-key "mock-object-key2") => ok?
      (against-background [(storage/get-metadata anything "mock-object-key") => {:filename "mock.cset"}
                           (storage/get-metadata anything "mock-object-key2") => {:filename "mock2.cset"}]))

    (let [{[{plan-id-1 :id} {plan-id-2 :id}] :bim-checking-plans} (query admin :bim-checking-plans)]

      (fact "Plans exists"
        plan-id-1 => string?
        plan-id-2 => string?)

      (fact "Set plan-id-2 to be compliance checking plan"
        (command admin :update-bim-checking-plan
                 :plan-id plan-id-2
                 :updates {:checking-type "code-compliance"})
        => ok?)

      (fact "Auth-admin can't configure disabled plan"
        (command auth-admin :update-bim-checking-plan-for-operation
                 :organizationId org-id
                 :operation "asuinrakennus"
                 :plan-id plan-id-1
                 :enabled true)
        => (err :error.bim-checking-plan-disabled))

      (fact "Auth-admin can't see disabled plan"
        (:bim-checking-plans (query auth-admin :bim-checking-plans-for-org :organizationId org-id))
        => [])

      (fact "Lupapiste admin enables plan"
        (command admin :update-bim-checking-plan :plan-id plan-id-1 :updates {:enabled     true
                                                                              :title       "Test checking plan"
                                                                              :description "Test description"})
        => ok?)

      (fact "Auth-admin can see enabled plan"
        (:bim-checking-plans (query auth-admin :bim-checking-plans-for-org :organizationId org-id))
        => [{:checking-type "model-validation"
             :description   "Test description"
             :model-type    "architectural"
             :enabled       true
             :id            plan-id-1
             :title         "Test checking plan"}])

      (fact "Lupapiste admin enables second (compliance) plan"
        (command admin :update-bim-checking-plan :plan-id plan-id-2 :updates {:enabled     true
                                                                              :title       "Test checking plan - compliance"
                                                                              :description "Test description"})
        => ok?)

      (fact "Auth-admin can not see compliance plan because Taika features are off "
        (:bim-checking-plans (query auth-admin :bim-checking-plans-for-org :organizationId org-id))
        => [{:checking-type "model-validation"
             :description   "Test description"
             :model-type    "architectural"
             :enabled       true
             :id            plan-id-1
             :title         "Test checking plan"}])

      (fact "Admin sets Taika features on for organization"
        (command admin :set-organization-boolean-attribute
                 :enabled true
                 :organizationId org-id
                 :attribute "bim-compliance-check-enabled")
        => ok?)

      (fact "Auth-admin can see both plans"
        (:bim-checking-plans (query auth-admin :bim-checking-plans-for-org :organizationId org-id))
        => (just [{:checking-type "model-validation"
                   :description   "Test description"
                   :model-type    "architectural"
                   :enabled       true
                   :id            plan-id-1
                   :title         "Test checking plan"}
                  {:checking-type "code-compliance"
                   :description   "Test description"
                   :model-type    "architectural"
                   :enabled       true
                   :id            plan-id-2
                   :title         "Test checking plan - compliance"}]
                 :in-any-order))

      (fact "Lupapiste admin can't update non-editable keys"
        (command admin :update-bim-checking-plan :plan-id plan-id-1 :updates {:id "12345"})
        => (err :error.illegal-value:schema-validation))

      (fact "Auth-admin configures plan"
        (command auth-admin :update-bim-checking-plan-for-operation
                 :organizationId org-id
                 :operation "asuinrakennus"
                 :plan-id plan-id-1
                 :enabled true)
        => ok?))))
