(ns lupapalvelu.continuation-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "Continuation permit creation"
  (apply-remote-minimal)

  (let [apikey                 sonja
        property-id            sipoo-property-id

        ;; Verdict given application, YA permit type
        verdict-given-application-ya (create-and-submit-application apikey
                                       :propertyId property-id
                                       :address "Paatoskuja 14"
                                       :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        verdict-given-application-ya-id (:id verdict-given-application-ya)

        ;; Verdict given application, R permit type
        verdict-given-application-r    (create-and-submit-application apikey :propertyId property-id :address "Paatoskuja 15") => truthy
        verdict-given-application-r-id (:id verdict-given-application-r)]

    ;; YA app
    (generate-documents verdict-given-application-ya apikey)
    (command apikey :approve-application :id verdict-given-application-ya-id :lang "fi") => ok?
    ;; Jatkoaika permit can be applied only for applications in state "verdictGiven or "constructionStarted
    (command apikey :create-continuation-period-permit :id verdict-given-application-ya-id) => (partial expected-failure? "error.command-illegal-state")
    (give-verdict apikey verdict-given-application-ya-id) => ok?
    ;; R app
    (generate-documents verdict-given-application-r apikey)
    (command apikey :update-app-bulletin-op-description :id verdict-given-application-r-id :description "otsikko julkipanoon") => ok?
    (command apikey :approve-application :id verdict-given-application-r-id :lang "fi") => ok?
    (give-verdict apikey verdict-given-application-r-id) => ok?
    ;; Jatkoaika permit can be applied only for YA type of applications
    (command apikey :create-continuation-period-permit :id verdict-given-application-r-id) => (partial expected-failure? "error.invalid-permit-type")

    ;; Verdict given application, of operation "ya-jatkoaika"
    (let [create-jatkoaika-resp     (command apikey :create-continuation-period-permit :id verdict-given-application-ya-id) => ok?
          jatkoaika-application-id  (:id create-jatkoaika-resp)
          jatkoaika-application     (query-application apikey jatkoaika-application-id) => truthy
          _                         (command apikey :submit-application :id jatkoaika-application-id) => ok?
          _                         (generate-documents jatkoaika-application apikey)
          _                         (command apikey :create-continuation-period-permit :id jatkoaika-application-id)
                                      => (partial expected-failure? "error.command-illegal-state")
          _                         (command apikey :approve-application :id jatkoaika-application-id :lang "fi") => ok?
          jatkoaika-application     (query-application apikey jatkoaika-application-id) => truthy]

      ;; Jatkoaika permit cannot be applied for applications with operation "ya-jatkoaika".
      ;; When a jatkoaika application is approved it goes straight into the state "finished".
      ;; It is forbidden to add jatkolupa for a jatkolupa, but already the wrong state blocks the try.
      (:state jatkoaika-application) => "finished"
      (give-verdict apikey jatkoaika-application-id) => (partial expected-failure? "error.command-illegal-state")
      (command apikey :create-continuation-period-permit :id jatkoaika-application-id) => (partial expected-failure? "error.command-illegal-state"))))

(facts "raktyo-aloit-loppuunsaat (R continuation application)"
  (let [{app-id :id} (create-and-submit-application pena
                                              :propertyId sipoo-property-id
                                              :operation :pientalo)
        continue-id (create-app-id pena
                                   :propertyId sipoo-property-id
                                   :operation :raktyo-aloit-loppuunsaat)]
    (fact "Link continuation to the original"
      (command pena :add-link-permit :linkPermitId app-id :id continue-id) => ok?)
    (fact "Verdict for the original"
      (command sonja :check-for-verdict :id app-id) => ok?)
    (fact "Submit the continuation"
      (command pena :submit-application :id continue-id) => ok?)
    (fact "Verdict for the continuation application"
      (command sonja :check-for-verdict :id continue-id) => ok?)
    (fact "After verdict given continuation application is ready"
      (-> (query-application sonja continue-id) :state) => "ready")
    (fact "Change permits are not supported for continuations"
      (command pena :create-change-permit :id continue-id) => (partial expected-failure? "error.command-illegal-state"))
    (fact "Change permit for the original can be created"
      (command pena :create-change-permit :id app-id) => ok?)))
