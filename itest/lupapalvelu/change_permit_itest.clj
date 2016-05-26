(ns lupapalvelu.change-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [clojure.set :refer [intersection]]))

(facts "Change permit"
  (let [apikey              sonja
        application         (create-and-submit-application
                              apikey
                              :propertyId sipoo-property-id
                              :address "Paatoskuja 12")
        application-id      (:id application)]

    (generate-documents application apikey)

    (command apikey :approve-application :id application-id :lang "fi") => ok?

    (fact "can not be created based on current R application before verdict has been given"
      (command apikey :create-change-permit :id application-id) => (partial expected-failure? "error.command-illegal-state"))

    (fact "Give verdict to original application"
      (give-verdict apikey application-id) => ok?)

    (let [application (query-application apikey application-id)]
      (:state application) => "verdictGiven")

    apikey => (allowed? :create-change-permit :id application-id)

    (let [resp (command apikey :create-change-permit :id application-id)
          {change-id :id} resp]
      (fact "was created" resp => ok?)

      (let [change-app (query-application apikey change-id)
           old-doc (domain/get-document-by-name application "uusiRakennus")
           change-doc (domain/get-document-by-name change-app "uusiRakennus")
           change-op (:primaryOperation change-app)]
       (fact "Operation id is not the same in new application"
         (:id change-op) =not=> (-> application :primaryOperation :id))
       (fact "Document has new operation id"
         (-> change-doc :schema-info :op :id) =not=> (-> old-doc :schema-info :op :id)
         (-> change-doc :schema-info :op :id) => (:id change-op))

       (fact "All documents have new ids"
         (let [old-ids (set (map :id (:documents application)))
               new-ids (set (map :id (:documents change-app)))]
           (intersection new-ids old-ids) => empty?))))))

(facts "Change permit can only be applied for an R type of application."
  (let [apikey                 sonja
        property-id            sipoo-property-id
        application            (create-and-submit-application apikey
                                 :propertyId property-id
                                 :address "Paatoskuja 13"
                                 :operation "ya-katulupa-vesi-ja-viemarityot")
        application-id         (:id application)]

    application-id => string?

    (generate-documents application apikey)
    (command apikey :approve-application :id application-id :lang "fi") => ok?
    (give-verdict apikey application-id) => ok?
    (let [application (query-application apikey application-id) => truthy]
      (:state application) => "verdictGiven")
    (command apikey :create-change-permit :id application-id) => (partial expected-failure? "error.invalid-permit-type")))
