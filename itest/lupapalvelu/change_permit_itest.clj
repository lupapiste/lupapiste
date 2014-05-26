(ns lupapalvelu.change-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "A change permit can be created based on current R application after verdict has been given."
  (let [application-id         (create-app-id pena
                                 :municipality sonja-muni
                                 :address "Paatoskuja 12")
        application            (query-application pena application-id) => truthy]
    (generate-documents application sonja)
    (command pena :submit-application :id application-id) => ok?
    (command sonja :approve-application :id application-id :lang "fi") => ok?
    (command sonja :create-change-permit :id application-id) => (partial expected-failure? "error.command-illegal-state")
    (command sonja :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
    (let [application (query-application sonja application-id)]
      (:state application) => "verdictGiven")
    sonja => (allowed? :create-change-permit :id application-id)))

(fact* "Change permit can only be applied for an R type of application."
  (let [application            (create-and-submit-application pena
                                 :municipality sonja-muni
                                 :address "Paatoskuja 13"
                                 :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        application-id         (:id application)]
    (generate-documents application sonja)
    (command sonja :approve-application :id application-id :lang "fi") => ok?
    (command sonja :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
    (let [application (query-application sonja application-id) => truthy]
      (:state application) => "verdictGiven")
    (command sonja :create-change-permit :id application-id) => (partial expected-failure? "error.invalid-permit-type")))