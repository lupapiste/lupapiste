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
    (command sipoo :create-inspection-summary-template :name "foo" :templateText "bar\nbar2\n\n bar3") => unauthorized?)
  (fact "Create template ok"
    (command jarvenpaa :create-inspection-summary-template :name "foo" :templateText "bar\nbar2\n\n bar3") => ok?)
  (fact "Created template included in the query result"
    (let [resp (query jarvenpaa :organization-inspection-summary-settings)
          data (-> resp :templates)
          id1 (-> data first :id)]
      resp => ok?
      (count data) => 1
      (first data) => (contains {:name "foo" :id id1 :items ["bar" "bar2" "bar3"]})
      (command jarvenpaa :modify-inspection-summary-template :templateId "invalid-id" :name "foo2" :templateText "bar325146") => (partial expected-failure? :error.not-found)
      (command jarvenpaa :modify-inspection-summary-template :templateId id1 :name "foo2" :templateText "bar325146") => ok?
      (fact "Map template into operations as default"
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :kerrostalo-rivitalo :templateId id1) => ok?
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :pientalo :templateId id1) => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) => (contains [{:kerrostalo-rivitalo id1}
                                                                                                             {:pientalo id1}] :in-any-order)
      (fact "...and unmapping"
        (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :pientalo :templateId "_unset") => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) =not=> (contains {:pientalo id1})))
      (fact "Deleting the template"
        (command jarvenpaa :delete-inspection-summary-template :templateId id1) => ok?
        (-> (query jarvenpaa :organization-inspection-summary-settings) :operations-templates) => empty?))))

(facts "Inspection summaries in applications"
  (fact "Create test data"
    (let [app-sipoo     (create-and-submit-application pena :propertyId sipoo-property-id :address "Peltomaankatu 9")
          app-jarvenpaa (create-and-submit-application pena :propertyId jarvenpaa-property-id :address "Järvikatu 29")
          _ (command jarvenpaa :create-inspection-summary-template :name "foo" :templateText "bar\nbar2\n\n bar3")
          templates (-> (query jarvenpaa :organization-inspection-summary-settings) :templates)]
      (command jarvenpaa :set-inspection-summary-template-for-operation :operationId :kerrostalo-rivitalo :templateId (-> templates first :id)) => ok?

      (fact "Feature not enabled in Sipoo"
        (query sipoo :inspection-summaries-for-application :id (:id app-sipoo)) => unauthorized?)
      (fact "Default template created upon verdict given"
        (give-verdict raktark-jarvenpaa (:id app-jarvenpaa) :verdictId "3323") => ok?
         (-> (query raktark-jarvenpaa :inspection-summaries-for-application :id (:id app-jarvenpaa)) :summaries first :name) => "foo")
      (fact "Applicant can see summaries but not create new ones"
        (query pena :inspection-summaries-for-application :id (:id app-jarvenpaa)) => ok?
        (command pena :create-inspection-summary :id (:id app-jarvenpaa)) => unauthorized?))))
