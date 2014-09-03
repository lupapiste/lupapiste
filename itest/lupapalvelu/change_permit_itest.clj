(ns lupapalvelu.change-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "A change permit can be created based on current R application after verdict has been given."
  (let [apikey                 sonja
        application-id         (create-app-id apikey
                                 :municipality sonja-muni
                                 :address "Paatoskuja 12")
        application            (query-application apikey application-id) => truthy]
    (generate-documents application apikey)
    (command apikey :submit-application :id application-id) => ok?
    (command apikey :approve-application :id application-id :lang "fi") => ok?
    (command apikey :create-change-permit :id application-id) => (partial expected-failure? "error.command-illegal-state")
    (give-verdict apikey application-id) => ok?
    (let [application (query-application apikey application-id)]
      (:state application) => "verdictGiven"
      )
    apikey => (allowed? :create-change-permit :id application-id)))

(fact* "Change permit can only be applied for an R type of application."
  (let [apikey                 sonja
        municipality           sonja-muni
        application            (create-and-submit-application apikey
                                 :municipality municipality
                                 :address "Paatoskuja 13"
                                 :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id         (:id application)]
    (generate-documents application apikey)
    (command apikey :approve-application :id application-id :lang "fi") => ok?
    (give-verdict apikey application-id) => ok?
    (let [application (query-application apikey application-id) => truthy]
      (:state application) => "verdictGiven")
    (command apikey :create-change-permit :id application-id) => (partial expected-failure? "error.invalid-permit-type")))
