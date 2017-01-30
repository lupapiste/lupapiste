(ns lupapalvelu.inspection-summary-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [clojure.set :refer [difference]]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(facts "inspection-summary-templates"
  (fact "Feature not enabled in Sipoo"
    (query sipoo :organization-inspection-summary-settings) => unauthorized?
    (command sipoo :modify-inspection-summary-template :func "create" :name "foo" :templateText "bar\nbar2\n\n bar3") => unauthorized?)
  (fact "Create template ok"
    (command jarvenpaa :modify-inspection-summary-template :func "create" :name "foo" :templateText "bar\nbar2\n\n bar3") => ok?)
  (fact "Created template included in the query result"
    (let [resp (query jarvenpaa :organization-inspection-summary-settings)
          data (-> resp :templates)
          id1 (-> data first :id)]
      resp => ok?
      (count data) => 1
      (first data) => (contains {:name "foo" :id id1 :items ["bar" "bar2" "bar3"]})
      (command jarvenpaa :modify-inspection-summary-template :func "update" :templateId "invalid-id" :name "foo2" :templateText "bar325146") => (partial expected-failure? :error.not-found)
      (command jarvenpaa :modify-inspection-summary-template :func "update" :templateId id1 :name "foo2" :templateText "bar325146") => ok?
      (fact "Map template into operations as default"
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :kerrostalo-rivitalo :templateId id1) => ok?
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :pientalo :templateId id1) => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) => (contains [{:kerrostalo-rivitalo id1}
                                                                                                             {:pientalo id1}] :in-any-order)
      (fact "...and unmapping"
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :kerrostalo-rivitalo :templateId "_unset") => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) =not=> (contains {:kerrostalo-rivitalo id1})))
      (fact "Deleting the template"
        (command jarvenpaa :modify-inspection-summary-template :func "delete" :templateId id1) => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) => empty?))))

(facts "Inspection summaries in applications"
  (fact "Create test data"
    (let [app-sipoo     (create-and-submit-application pena :propertyId sipoo-property-id :address "Peltomaankatu 9")
          jarvenpaa-templates-from-admin
                        (-> (query jarvenpaa :organization-inspection-summary-settings) :templates)
          app-jarvenpaa (create-and-submit-application pena :propertyId jarvenpaa-property-id :address "Järvikatu 29")]
      (fact "Feature not enabled in Sipoo"
        (query sipoo :inspection-summary-templates-for-application :id (:id app-sipoo)) => unauthorized?)
      (fact "Feature enabled in Järvenpää"
        (query raktark-jarvenpaa :inspection-summary-templates-for-application :id (:id app-jarvenpaa)) => (contains {:templates jarvenpaa-templates-from-admin})))))