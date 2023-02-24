(ns lupapalvelu.continuation-permit-itest
  (:require [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]))

(fact* "Continuation permit creation"
  (apply-remote-minimal)

  (fact "Add continuation permit support to the organization"
    (command sipoo-ya :set-organization-selected-operations :organizationId "753-YA"
             :operations [:ya-katulupa-vesi-ja-viemarityot
                          :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen
                          :ya-kayttolupa-mainostus-ja-viitoitus
                          :ya-kayttolupa-terassit
                          :ya-kayttolupa-vaihtolavat
                          :ya-kayttolupa-nostotyot
                          :ya-jatkoaika]) => ok?)

  (fact "Add 753-R handler roles"
    (command sipoo :upsert-handler-role :organizationId "753-R"
             :name {:fi "Morjens" :sv "Hej" :en "Howdy"})
    => ok?
    (command sipoo :upsert-handler-role :organizationId "753-R"
             :name {:fi "Soonmoro" :sv "Hejdå" :en "Bye"})
    => ok?)

  (fact "Make Veikko a 753-R authority"
    (command sipoo :upsert-organization-user :organizationId "753-R"
             :email (email-for-key veikko)
             :firstName "Veikko" :lastName "Viranomainen"
             :roles [:authority]) => ok?)

  (fact "Add continuation permit support to YMP organization"
    (command oulu :set-organization-selected-operations :organizationId "564-YMP"
             :operations [:maa-aineslupa
                          :maa-aineslupa-jatkoaika]) => ok?)

  (let [property-id                      sipoo-property-id
        ;; Verdict given application, YA permit type
        verdict-given-application-ya     (create-and-submit-application
                                           sonja
                                           :propertyId property-id
                                           :address "Paatoskuja 14"
                                           :operation "ya-katulupa-vesi-ja-viemarityot")
        =>                               truthy
        verdict-given-application-ya-id  (:id verdict-given-application-ya)
        ;; Verdict given application, R permit type
        verdict-given-application-r      (create-and-submit-application
                                           sonja
                                           :propertyId property-id
                                           :address "Paatoskuja 15")
        =>                               truthy
        verdict-given-application-r-id   (:id verdict-given-application-r)
        ;; Verdict given application, MAL permit type
        verdict-given-application-mal    (create-and-submit-application
                                           olli
                                           :propertyId oulu-property-id
                                           :address "Paatoskuja 16"
                                           :operation "maa-aineslupa")
        =>                               truthy
        verdict-given-application-mal-id (:id verdict-given-application-mal)
        [role-id1 role-id2]              (->> (query sonja :application-organization-handler-roles
                                                     :id verdict-given-application-r-id)
                                              :handlerRoles
                                              rest
                                              (map :id))]

    ;; YA app
    (generate-documents! verdict-given-application-ya sonja)

    (command sonja :approve-application :id verdict-given-application-ya-id :lang "fi") => ok?

    ;; Jatkoaika permit can be applied only for applications in state "verdictGiven or "constructionStarted
    (command sonja :create-continuation-period-permit :id verdict-given-application-ya-id) => (partial expected-failure? "error.command-illegal-state")
    (give-legacy-verdict sonja verdict-given-application-ya-id)

    ;; R app
    (generate-documents! verdict-given-application-r sonja)

    (fact "Add general and other handlers"
      (command sonja :upsert-application-handler :id verdict-given-application-r-id
               :userId ronja-id :roleId sipoo-general-handler-id) => ok?
      (command sonja :upsert-application-handler :id verdict-given-application-r-id
               :userId sonja-id :roleId role-id1) => ok?
      (command sonja :upsert-application-handler :id verdict-given-application-r-id
               :userId veikko-id :roleId role-id2) => ok?)

    (command sonja :update-app-bulletin-op-description :id verdict-given-application-r-id
             :description "otsikko julkipanoon") => ok?
    (command sonja :approve-application :id verdict-given-application-r-id
             :lang "fi") => ok?

    (fact "Disable handler role and remove Veikko from 753-R"
      (command sipoo :toggle-handler-role :organizationId "753-R"
               :roleId role-id1 :enabled false) => ok?
      (command sipoo :remove-user-organization :organizationId "753-R"
               :email (email-for-key veikko)) => ok?)

    (give-legacy-verdict sonja verdict-given-application-r-id)

    (facts "R continuation period and encumbrance permits"
      (let [{cont-id :id} (command sonja :create-continuation-period-permit
                                   :id verdict-given-application-r-id)
            {enc-id :id}  (command sonja :create-encumbrance-permit
                                   :id verdict-given-application-r-id)
            handlers-ok?  (just (contains {:roleId  sipoo-general-handler-id
                                           :general true
                                           :userId  ronja-id}))]
        (:handlers (query-application sonja cont-id)) => handlers-ok?
        (:handlers (query-application sonja enc-id)) => handlers-ok?))

    ;; Verdict given application, of operation "ya-jatkoaika"
    (let [create-jatkoaika-resp    (command sonja :create-continuation-period-permit :id verdict-given-application-ya-id)
          =>                       ok?
          jatkoaika-application-id (:id create-jatkoaika-resp)
          jatkoaika-application    (query-application sonja jatkoaika-application-id)
          =>                       truthy
          _                        (command sonja :submit-application :id jatkoaika-application-id)
          =>                       ok?
          _                        (generate-documents! jatkoaika-application sonja)
          _                        (command sonja :create-continuation-period-permit :id jatkoaika-application-id)
          =>                       (partial expected-failure? "error.command-illegal-state")
          _                        (command sonja :approve-application :id jatkoaika-application-id :lang "fi")
          =>                       ok?
          jatkoaika-application    (query-application sonja jatkoaika-application-id) => truthy]

      ;; Jatkoaika permit cannot be applied for applications with operation "ya-jatkoaika".
      ;; When a jatkoaika application is approved it goes straight into the state "finished".
      ;; It is forbidden to add jatkolupa for a jatkolupa, but already the wrong state blocks the try.
      (:state jatkoaika-application) => "finished"
      (fact "Verdict cannot be given in the finished state"
        (command sonja :new-legacy-verdict-draft
                 :id jatkoaika-application-id)
        => (err :ya-extension-application))
      (command sonja :create-continuation-period-permit :id jatkoaika-application-id) => (partial expected-failure? "error.command-illegal-state"))

    ;; MAL app
    (generate-documents! verdict-given-application-mal olli)
    (command olli :approve-application :id verdict-given-application-mal-id :lang "fi") => ok?
    (give-legacy-verdict olli verdict-given-application-mal-id)
    (let [result   (command olli :create-continuation-period-permit :id verdict-given-application-mal-id)
          link-cmd #(command olli % :id verdict-given-application-mal-id :linkPermitId (:id result))]
      result => ok?
      (link-cmd :remove-link-permit-by-app-id) => ok?
      (link-cmd :add-link-permit) => ok?)))

(facts "raktyo-aloit-loppuunsaat (R continuation application)"
  (let [{app-id :id} (create-and-submit-application pena
                                                    :propertyId sipoo-property-id
                                                    :operation :pientalo)
        continue-id  (create-app-id pena
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
      (command pena :create-change-permit :id app-id) => ok?)
    (facts "Continuatian application change state targets"
      (fact "ready"
        (:states (query sonja :change-application-state-targets :id continue-id))
        => (just ["ready" "extinct" "appealed"] :in-any-order))
      (fact "appealed"
        (command sonja :change-application-state :id continue-id
                 :state "appealed") => ok?
        (:states (query sonja :change-application-state-targets :id continue-id))
        => (just ["ready" "appealed"] :in-any-order))
      (fact "extinct"
        (command sonja :change-application-state :id continue-id
                 :state "ready")
        (command sonja :change-application-state :id continue-id
                 :state "extinct")=> ok?
        (:states (query sonja :change-application-state-targets :id continue-id))
        => empty?))))
