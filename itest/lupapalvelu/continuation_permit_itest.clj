(ns lupapalvelu.continuation-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "Continuation permit creation"
  (apply-remote-minimal)

  (let [municipality           sonja-muni

        ;; Verdict given application, YA permit type
        verdict-given-application-ya (create-and-submit-application pena
                                       :municipality municipality
                                       :address "Paatoskuja 14"
                                       :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        verdict-given-application-ya-id (:id verdict-given-application-ya)

        ;; Verdict given application, R permit type
        verdict-given-application-r    (create-and-submit-application pena :municipality municipality :address "Paatoskuja 15") => truthy
        verdict-given-application-r-id (:id verdict-given-application-r)]

    ;; YA app
    (generate-documents verdict-given-application-ya sonja)
    (command sonja :approve-application :id verdict-given-application-ya-id :lang "fi") => ok?
    ;; Jatkoaika permit can be applied only for applications in state "verdictGiven or "constructionStarted
    (command sonja :create-continuation-period-permit :id verdict-given-application-ya-id) => (partial expected-failure? "error.command-illegal-state")
    (command sonja :give-verdict :id verdict-given-application-ya-id
                                  :verdictId "aaa" :status 42 :name "Paatoksen antaja"
                                  :given 123 :official 124) => ok?
    ;; R app
    (generate-documents verdict-given-application-r sonja)
    (command sonja :approve-application :id verdict-given-application-r-id :lang "fi") => ok?
    (command sonja :give-verdict :id verdict-given-application-r-id
                                  :verdictId "aaa" :status 42 :name "Paatoksen antaja"
                                  :given 123 :official 124) => ok?
    ;; Jatkoaika permit can be applied only for YA type of applications
    (command pena :create-continuation-period-permit :id verdict-given-application-r-id) => (partial expected-failure? "error.invalid-permit-type")

    ;; Verdict given application, of operation "ya-jatkoaika"
    (let [create-jatkoaika-resp     (command pena :create-continuation-period-permit :id verdict-given-application-ya-id) => ok?
          jatkoaika-application-id  (:id create-jatkoaika-resp)
          jatkoaika-application     (query-application pena jatkoaika-application-id) => truthy
          _                         (command pena :submit-application :id jatkoaika-application-id) => ok?
          _                         (generate-documents jatkoaika-application sonja)
          _                         (command sonja :create-continuation-period-permit :id jatkoaika-application-id)
                                      => (partial expected-failure? "error.command-illegal-state")
          _                         (command sonja :approve-application :id jatkoaika-application-id :lang "fi") => ok?
          jatkoaika-application     (query-application sonja jatkoaika-application-id) => truthy]

      ;; Jatkoaika permit cannot be applied for applications with operation "ya-jatkoaika".
      ;; When a jatkoaika application is approved it goes straight into the state "closed".
      ;; It is forbidden to add jatkolupa for a jatkolupa, but already the wrong state blocks the try.
      (:state jatkoaika-application) => "closed"
      (command sonja :give-verdict :id jatkoaika-application-id
                                    :verdictId "aaa" :status 42 :name "Paatoksen antaja"
                                    :given 123 :official 124) => (partial expected-failure? "error.command-illegal-state")
      (command pena :create-continuation-period-permit :id jatkoaika-application-id) => (partial expected-failure? "error.command-illegal-state"))))
