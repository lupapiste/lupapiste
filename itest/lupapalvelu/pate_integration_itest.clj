(ns lupapalvelu.pate-integration-itest
  (:require [lupapalvelu.factlet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]))

(apply-remote-fixture "pate-verdict")

(defn check-count-and-last-state [id msg-count state]
  (fact
    (count (integration-messages id)) => msg-count
    (get-in (last (integration-messages id)) [:data :toState :name]) => state))

(facts* "R"
  (let [app-id (create-app-id pena :propertyId sipoo-property-id)
        cancel-app (create-app-id pena :propertyId sipoo-property-id)]
    (fact "canceled sends message"                          ; TODO SHOULD IT SENT IF IT WAS DRAFT?
      (command pena :cancel-application :id cancel-app :lang "fi" :text "prkl") => ok?
      (check-count-and-last-state cancel-app 1 "canceled"))
    (fact "no message for draft"
      (count (integration-messages app-id)) => 0)
    (fact "open application doesnt generate message"        ; :open is skipped state
      (comment-application pena app-id true) => ok?
      (count (integration-messages app-id)) => 0)
    (fact "submitting generates message"
      (command pena :submit-application :id app-id) => ok?
      (check-count-and-last-state app-id 1 "submitted"))
    (fact "moving to draft from submitted do trigger state change"
      (command sonja :return-to-draft :id app-id :text "comment-text" :lang :fi) => ok?
      (check-count-and-last-state app-id 2 "draft"))
    (fact "submitting generates message again"
      (command pena :submit-application :id app-id) => ok?
      (check-count-and-last-state app-id 3 "submitted"))
    (command sonja :update-app-bulletin-op-description :id app-id :description "otsikko julkipanoon") => ok?
    (fact "sent generates message"
      (command sonja :approve-application :id app-id :lang "fi") => ok?
      (check-count-and-last-state app-id 4 "sent"))
    (fact "complementNeeded generates message"
      (command sonja :request-for-complement :id app-id) => ok?
      (check-count-and-last-state app-id 5 "complementNeeded"))
    (fact "Disable state change messages"
      (command admin :set-organization-boolean-attribute
               :enabled false
               :organizationId "753-R"
               :attribute :state-change-msg-enabled) => ok?)
    (fact "sent does not generate message"
      (command sonja :approve-application :id app-id :lang "fi") => ok?
      (check-count-and-last-state app-id 5 "complementNeeded"))
    (fact "Enable state change messages"
      (command admin :set-organization-boolean-attribute
               :enabled true
               :organizationId "753-R"
               :attribute :state-change-msg-enabled) => ok?)
    (fact "verdictGiven generates message"
      (facts "Publish verdict for application"
      (let [template-id          (-> pate-fixture/verdict-templates-setting-r :templates first :id)
            {:keys [verdict-id]} (command sonja :new-pate-verdict-draft :id app-id :template-id template-id)
            edit-verdict         (partial command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id)]

        (fact "Set automatic calculation of other dates"
          (edit-verdict :path [:automatic-verdict-dates] :value true) => no-errors?)
        (fact "Verdict date"
          (edit-verdict :path [:verdict-date] :value (now)) => no-errors?)
        (fact "Verdict code"
          (edit-verdict :path [:verdict-code] :value "hyvaksytty") => no-errors?)
        (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?))
      (check-count-and-last-state app-id 6 "verdictGiven"))
    #_(fact "canceled generates message"
      (command pena :cancel-application :id app-id :lang "fi" :text "prkl") => ok?
      (check-count-and-last-state app-id 5 "canceled"))))

(facts* "YA"
  (let [app-id               (create-app-id pena :propertyId sipoo-property-id :operation "ya-kayttolupa-terassit")
        template-id          (-> pate-fixture/verdict-templates-setting-ya :templates first :id)
        _                    (command pena :submit-application :id app-id)
        {:keys [verdict-id]} (command sonja :new-pate-verdict-draft :id app-id :template-id template-id)
        edit-verdict         (partial command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id)]

    (fact "Set automatic calculation of other dates"
      (edit-verdict :path [:automatic-verdict-dates] :value true) => no-errors?)
    (fact "Verdict date"
      (edit-verdict :path [:verdict-date] :value (now)) => no-errors?)
    (fact "Verdict code"
      (edit-verdict :path [:verdict-code] :value "hyvaksytty") => no-errors?)
    (fact "Set permit start date"
      (edit-verdict :path [:start-date] :value (now)) => no-errors?)
    (fact "Set permit as indefinitely valid"
      (edit-verdict :path [:no-end-date] :value true) => no-errors?)
    (fact "PATE YA verdict can now be published"
      (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)))
