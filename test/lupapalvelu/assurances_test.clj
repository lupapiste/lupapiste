(ns lupapalvelu.assurances-test
  (:require [lupapalvelu.test-util :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.assurances :as assurances]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- make-id [id]
  (format "%024d" id))

(defn- create-test-application [primary-op & secondary-ops]
  {:primaryOperation    {:name primary-op}
   :secondaryOperations (mapv (fn [op] {:name op}) secondary-ops)})

(defn- create-test-organization [assurance-templates]
  {:application-submit-assurance-templates assurance-templates})

(defn- create-test-assurance-template [id required? modified all-ops? & ops]
  (util/assoc-when {:id             (make-id id)
                    :text           {:fi "Ootko varma?"
                                     :sv "Är du säker?"
                                     :en "Are you sure?"}
                    :required       required?
                    :modified       modified
                    :all-operations all-ops?}
                   :operations (seq ops)))

(sc/with-fn-validation
  (let [application  (create-test-application "primary-op1" "snd-op1" "snd-op3")
        organization (create-test-organization
                       [(create-test-assurance-template 1 true 1 true)
                        (create-test-assurance-template 2 true 2 false "primary-op1")
                        (create-test-assurance-template 3 false 3 false "primary-op2")
                        (create-test-assurance-template 4 true 4 false "primary-op2" "snd-op1")
                        (create-test-assurance-template 5 true 5 false "primary-op2" "snd-op2")
                        (create-test-assurance-template 6 false 6 false "primary-op3" "snd-op3")])]

    (facts "Templates for application contain: "
      (let [assurance-template-ids-for-app (map :id (assurances/get-application-submit-assurance-templates organization application))]
        (fact "templates that are set for all operations"
          assurance-template-ids-for-app => (contains (make-id 1)))
        (fact "templates for primary application's primary operation"
          assurance-template-ids-for-app => (contains (make-id 2)))
        (fact "templates for secondary application's primary operation"
          assurance-template-ids-for-app => (contains (make-id 4))
          assurance-template-ids-for-app => (contains (make-id 6)))
        (fact "only templates that are application's operations"
          assurance-template-ids-for-app =not=> (contains (make-id 3))
          assurance-template-ids-for-app =not=> (contains (make-id 5)))))

    (facts "Testing if user checked all the required assurances"
      (let [assurance-templates-for-app (assurances/get-application-submit-assurance-templates organization application)]
        (fact "No assurances"
          (assurances/required-assurances-filled? assurance-templates-for-app []) => false)
        (fact "Only some of the required assurances"
          (assurances/required-assurances-filled? assurance-templates-for-app []) => false
          (assurances/required-assurances-filled? assurance-templates-for-app
                                                  [{:id (make-id 1) :modified 1}]) => false
          (assurances/required-assurances-filled? assurance-templates-for-app
                                                  [{:id (make-id 2) :modified 2}]) => false
          (assurances/required-assurances-filled? assurance-templates-for-app
                                                  [{:id (make-id 4) :modified 4}]) => false)
        (fact "Only one that is not required"
          (assurances/required-assurances-filled? assurance-templates-for-app
                                                  [{:id (make-id 6) :modified 6}]) => false)
        (fact "All the required are checked"
          (assurances/required-assurances-filled? assurance-templates-for-app
                                                  [{:id (make-id 1) :modified 1}
                                                   {:id (make-id 2) :modified 2}
                                                   {:id (make-id 4) :modified 4}]) => true)
        (fact "Not required are also allowed"
          (assurances/required-assurances-filled? assurance-templates-for-app
                                                  [{:id (make-id 1) :modified 1}
                                                   {:id (make-id 2) :modified 2}
                                                   {:id (make-id 4) :modified 4}
                                                   {:id (make-id 6) :modified 6}]) => true)))

    (facts "Testing whether checked assurances are still valid"
      (let [assurance-templates-for-app (assurances/get-application-submit-assurance-templates organization application)]

        (fact "Valid (false) if modified timestamps match with ones in organization document"
          (assurances/assurances-expired? assurance-templates-for-app
                                          [{:id (make-id 1) :modified 1}
                                           {:id (make-id 2) :modified 2}
                                           {:id (make-id 4) :modified 4}
                                           {:id (make-id 6) :modified 6}]) => false)
        (fact "Invalid (true) if any of the timestamp do not match"
          (assurances/assurances-expired? assurance-templates-for-app
                                          [{:id (make-id 2) :modified 3}]) => true
          (assurances/assurances-expired? assurance-templates-for-app
                                          [{:id (make-id 1) :modified 2}
                                           {:id (make-id 2) :modified 2}
                                           {:id (make-id 4) :modified 4}
                                           {:id (make-id 6) :modified 6}]) => true
          (assurances/assurances-expired? assurance-templates-for-app
                                          [{:id (make-id 1) :modified 1}
                                           {:id (make-id 2) :modified 2}
                                           {:id (make-id 4) :modified 4}
                                           {:id (make-id 6) :modified 7}]) => true)
        (fact "Invalid (true) if assurances contain ones that do not exist in templates"
          (assurances/assurances-expired? assurance-templates-for-app
                                          [{:id (make-id 1) :modified 2}
                                           {:id (make-id 2) :modified 2}
                                           {:id (make-id 4) :modified 4}
                                           {:id (make-id 5) :modified 5}
                                           {:id (make-id 6) :modified 6}]) => true
          (assurances/assurances-expired? assurance-templates-for-app
                                          [{:id (make-id 5) :modified 5}
                                           {:id (make-id 6) :modified 6}]) => true)))))
