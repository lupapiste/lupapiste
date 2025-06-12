(ns lupapalvelu.pate-integration-itest
  (:require [lupapalvelu.factlet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]))

(defn- check-count-and-last-state [id msg-count state]
  (let [state-msgs (filter #(= (:messageType %) "state-change")
                           (integration-messages id))]
    (fact {:midje/description (format "%s: %d state messages -> %s"
                                      id msg-count state)}
      (count state-msgs) => msg-count
      (get-in (last state-msgs) [:data :toState :name]) => state)))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Configure dmcities"
    (let [cfg {:url      (str (server-address) "/dev/dummy-dmcity")
               :username "test-user"
               :password "test-secret"
               :vault    "test-vault"
               :targets  {:state true}}]
      (fact "753-R"
        (command admin :upsert-dmcity-configuration
                 :organizationId "753-R"
                 :configuration (assoc-in cfg [:targets :R] true))
        => ok?)
      (fact "753-YA"
        (command admin :upsert-dmcity-configuration
                 :organizationId "753-YA"
                 :configuration (assoc-in cfg [:targets :YA] true))
        => ok?)))

  (facts* "R"
    (let [app-id         (create-app-id pena :propertyId sipoo-property-id)
          cancel-app     (create-app-id pena :propertyId sipoo-property-id)
          {app-id-2 :id} (create-and-submit-application pena :propertyId sipoo-property-id)]
      (fact "canceled sends message"
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
        (command admin :toggle-dmcity-configuration-target
                 :enabled false
                 :organizationId "753-R"
                 :target :state) => ok?)
      (fact "sent does not generate message"
        (command sonja :approve-application :id app-id :lang "fi") => ok?
        (check-count-and-last-state app-id 5 "complementNeeded"))
      (fact "Enable state change messages"
        (command admin :toggle-dmcity-configuration-target
                 :enabled true
                 :organizationId "753-R"
                 :target :state)
        => ok?)
      (fact "verdictGiven generates message"
        (facts "Publish verdict for application"
          (let [template-id          (-> pate-fixture/verdict-templates-r first :id)
                {:keys [verdict-id]} (command sonja :new-pate-verdict-draft :id app-id :template-id template-id)
                edit-verdict         (partial command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id)]

            (fact "Set automatic calculation of other dates"
              (edit-verdict :path [:automatic-verdict-dates] :value true) => no-errors?)
            (fact "Verdict date"
              (edit-verdict :path [:verdict-date] :value (now)) => no-errors?)
            (fact "Verdict code"
              (edit-verdict :path [:verdict-code] :value "hyvaksytty") => no-errors?)
            (fact "Verdict giver first name"
              (edit-verdict :path [:giver-first-name] :value "Sonja") => no-errors?)
            (fact "Verdict giver last name"
              (edit-verdict :path [:giver-last-name] :value "Sippo") => no-errors?)
            (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?))
        (check-count-and-last-state app-id 6 "verdictGiven"))
      (fact "sending already sent application is not ok"
        (command sonja :approve-application-after-verdict :id app-id :lang "fi") => fail?)
      (facts "Publish verdict for application even if not approved"
        (let [template-id          (-> pate-fixture/verdict-templates-r first :id)
              {:keys [verdict-id]} (command sonja :new-pate-verdict-draft :id app-id-2 :template-id template-id)
              edit-verdict         (partial command sonja :edit-pate-verdict :id app-id-2 :verdict-id verdict-id)]

          (fact "Set automatic calculation of other dates"
            (edit-verdict :path [:automatic-verdict-dates] :value true) => no-errors?)
          (fact "Verdict date"
            (edit-verdict :path [:verdict-date] :value (now)) => no-errors?)
          (fact "Verdict code"
            (edit-verdict :path [:verdict-code] :value "hyvaksytty") => no-errors?)
          (fact "Verdict giver first name"
            (edit-verdict :path [:giver-first-name] :value "Sonja") => no-errors?)
          (fact "Verdict giver last name"
            (edit-verdict :path [:giver-last-name] :value "Sippo") => no-errors?)
          (fact "Verdict handler"
            (edit-verdict :path [:handler] :value "Sonja Sippo") => no-errors?)
          (command sonja :publish-pate-verdict :id app-id-2 :verdict-id verdict-id) => no-errors?))
      (fact "sending unsent but verdictful application is ok"
        (command sonja :approve-application-after-verdict :id app-id-2 :lang "fi") => ok?)))

  (facts* "YA"
    (let [app-id               (create-app-id pena :propertyId sipoo-property-id :operation "ya-kayttolupa-terassit")
          template-id          (-> pate-fixture/verdict-templates-ya first :id)
          _                    (command pena :submit-application :id app-id)
          {:keys [verdict-id]} (command sonja :new-pate-verdict-draft :id app-id :template-id template-id)
          edit-verdict         (partial command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id)]

      (fact "Set automatic calculation of other dates"
        (edit-verdict :path [:automatic-verdict-dates] :value true) => no-errors?)
      (fact "Verdict date"
        (edit-verdict :path [:verdict-date] :value (now)) => no-errors?)
      (fact "Verdict code"
        (edit-verdict :path [:verdict-code] :value "hyvaksytty") => no-errors?)
      (fact "Verdict giver"
        (edit-verdict :path [:giver] :value "päättäjä") => no-errors?)
      (fact "Set permit start date"
        (edit-verdict :path [:start-date] :value (now)) => no-errors?)
      (fact "Set permit as indefinitely valid"
        (edit-verdict :path [:no-end-date] :value true) => no-errors?)
      (fact "PATE YA verdict can now be published"
        (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)
      (fact "YA-messages"
        ;; submitted, verdictGiven
        (check-count-and-last-state app-id 2 "verdictGiven")))))
