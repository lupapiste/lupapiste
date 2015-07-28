(ns lupapalvelu.link-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "Link permit creation and removal"
  (apply-remote-minimal)

  (let [apikey      sonja
        property-id sipoo-property-id

        ;; App 1 - with same permit type, approved
        approved-application      (create-and-submit-application apikey
                                    :propertyId property-id
                                    :address "Paatoskuja 13"
                                    :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        approved-application-id   (:id approved-application)
        _                         (generate-documents approved-application apikey)
        _                         (command apikey :approve-application :id approved-application-id :lang "fi") => ok?

        ;; App 2 - with same permit type, verdict given
        verdict-given-application (create-and-submit-application apikey
                                    :propertyId property-id
                                    :address "Paatoskuja 14"
                                    :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        verdict-given-application-id (:id verdict-given-application)
        _                         (generate-documents verdict-given-application apikey)
        _                         (command apikey :approve-application :id verdict-given-application-id :lang "fi") => ok?
        _                         (give-verdict apikey verdict-given-application-id) => ok?

        ;; App 3 - with same permit type, verdict given, of operation "ya-jatkoaika"
        create-jatkoaika-resp     (command apikey :create-continuation-period-permit :id verdict-given-application-id) => ok?
        jatkoaika-application-id  (:id create-jatkoaika-resp)
        jatkoaika-application     (query-application apikey jatkoaika-application-id) => truthy

        ;; App 4 - old submitted application
        submitted-application     (create-and-submit-application apikey
                                    :propertyId property-id
                                    :address "Paatoskuja 13"
                                    :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        submitted-application-id  (:id submitted-application)
        _                         (generate-documents submitted-application apikey)

        ;; App that gets a link permit attached to it
        test-application          (create-and-submit-application apikey
                                    :propertyId property-id
                                    :address "Paatoskuja 15"
                                    :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        test-application-id       (:id test-application)]

    (fact "New ya-jatkoaika requires link permit"
      (let [new-application-id (create-app-id apikey :operation "ya-jatkoaika" :propertyId property-id) ]
        (query apikey :link-permit-required :id new-application-id) => ok?))

    (fact "ya-jatkoaika has a link permit"
      (count (:linkPermitData jatkoaika-application)) => 1

      (fact "so a link permit is no longer required"
        (query apikey :link-permit-required :id jatkoaika-application-id) => fail?))

    (fact "approving ya-jatkoaika leads to closed state"
      (command apikey :submit-application :id jatkoaika-application-id :confirm false) => ok?
      (generate-documents jatkoaika-application apikey)
      (command apikey :approve-application :id jatkoaika-application-id :lang "fi") => ok?
      (:state (query-application apikey jatkoaika-application-id)) => "closed")


    ;;
    ;; The "matches", i.e. the contents of the dropdown selection component in the link-permit dialog
    ;; is allowed to have only these kind of applications:
    ;;    - different id than current application has, but same permit-type
    ;;    - do not have a previous link-permit relationship to the current application
    ;;    - not in draft state
    ;;    - whose operation is not of type "ya-jatkoaika"
    ;;
    (fact "The jatkoaika application is not among the matches in the link permit dropdown selection"
      (let [matches-resp (query apikey :app-matches-for-link-permits :id test-application-id) => ok?
            matches (:app-links matches-resp)]
        (count matches) => 3
        (every? #{approved-application-id
                  verdict-given-application-id
                  submitted-application-id} (map :id matches)) => true?))

    (fact "Can not insert invalid key"
      (command apikey :add-link-permit :id test-application-id :linkPermitId "foo.bar") => fail?
      (command apikey :add-link-permit :id test-application-id :linkPermitId " ") => fail?)

    (fact "A link permit is succesfully added to an application in submitted state"
      (command apikey :add-link-permit :id test-application-id :linkPermitId verdict-given-application-id) => ok?)

    (fact "Adding link permit to approved application in sent state fails"
      (command apikey :add-link-permit
        :id approved-application-id :linkPermitId verdict-given-application-id) => (partial expected-failure? "error.command-illegal-state"))

    (fact "Authority gives verdict and then adds link permit (in verdict-given state)"
      (command apikey :add-link-permit :id verdict-given-application-id :linkPermitId approved-application-id) => ok?)

    (fact "Test app is added as link permit to another app"
      (command apikey :add-link-permit :id submitted-application-id :linkPermitId test-application-id) => ok?)

    (fact "Test application has valid link permit relations"
      (let [app (query-application apikey test-application-id) => truthy]
       (count (:appsLinkingToUs app)) => 1
       (-> app :appsLinkingToUs first :id) => submitted-application-id
       (count (:linkPermitData app)) => 1
       (let [link-permit-data (-> app :linkPermitData first)]
         (:id link-permit-data)        => verdict-given-application-id
         (:type link-permit-data)      => "lupapistetunnus"
         (:operation link-permit-data) => "ya-katulupa-vesi-ja-viemarityot")))

    (fact "Application matches for dropdown selection contents do not include the applications that have a link-permit relation to the current application"
      (let [matches-resp (query apikey :app-matches-for-link-permits :id test-application-id) => ok?
            matches (:app-links matches-resp)]
        (count matches) => 1
        (-> matches first :id) => approved-application-id))))
