(ns lupapalvelu.pate.scheduled-publish-itest
  (:require [lupapalvelu.fixture.pate-verdict]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]))

(with-test-system (-> {:fixture            "pate-verdict"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "scheduled publish (julkipano)"
    (let [template-id  "5a7aff3e5266a1d9c1581956" ; from fixture
          {app-id :id} (create-and-open-application pena
                                                    :propertyId sipoo-property-id
                                                    :operation :sisatila-muutos
                                                    :address "Schedule alley")]
      (facts "Pena fills and submits application"
        (fill-sisatila-muutos-application pena app-id)
        (command pena :submit-application :id app-id) => ok?

        ;; See below for a similar kuulutus test
        (fact "can change bulletin process"
          (command sonja :change-bulletin-process :id app-id :process "julkipano")
          => ok?)

        (fact "Sonja approves application"
          (command sonja :update-app-bulletin-op-description
                   :id app-id
                   :description "Bullet the blue sky.") => ok?
          (command sonja :approve-application :id app-id :lang "fi") => ok?)
        (facts "New verdict"
          (let [now-ts                   (now)
                {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                  :id app-id :template-id template-id)]
            (fill-verdict sonja app-id verdict-id
                          :verdict-code "hyvaksytty"
                          :verdict-text "Scheduled verdict almost given"
                          :verdict-date now-ts
                          :bulletin-op-description "Changed pate bulletin description"
                          :giver-first-name "Pena"
                          :giver-last-name "päättäjä")
            (fact "generate dates"
              (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                       :path [:automatic-verdict-dates] :value true) => no-errors?)
            (facts "Sonja could..."
              (fact "publish verdict"
                (allowed? sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => true)
              (fact "edit verdict"
                (allowed? sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                          :path [:verdict-text] :value "foofaa") => true)
              (fact "delete verdict"
                (allowed? sonja :delete-pate-verdict :id app-id :verdict-id verdict-id) => true))
            (facts "julkipano date in past does not work"
              (fact "unset automatic verdict dates calculation"
                (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                         :path [:automatic-verdict-dates] :value false) => no-errors?)
              (fact "set julkipano date"
                (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                         :path [:julkipano] :value "1.1.1990")
                => ok?)
              (fact "scheduled publish error"
                (command sonja :scheduled-verdict-publish :id app-id :verdict-id verdict-id)
                => (partial expected-failure? :error.verdict.no-julkipano-date)))
            (fact "toggle automatic dates back"
              (command sonja :edit-pate-verdict
                       :id app-id :verdict-id verdict-id
                       :path [:automatic-verdict-dates] :value true)
              => ok?)
            (facts "make it scheduled"
              (fact "command"
                (command sonja :scheduled-verdict-publish :id app-id :verdict-id verdict-id)
                => ok?)
              (let [verdict (:verdict (query sonja :pate-verdict :id app-id :verdict-id verdict-id))]
                (fact "state correct"
                  (:state verdict) => "scheduled"))
              (facts "after which Sonja can..."
                (fact "not publish verdict"
                  (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id)
                  => (partial expected-failure? :error.verdict.not-editable))
                (fact "not edit verdict"
                  (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                           :path [:verdict-text] :value "foofaa")
                  => (partial expected-failure? :error.verdict.not-editable))
                (fact "not delete verdict"
                  (command sonja :delete-pate-verdict :id app-id :verdict-id verdict-id)
                  => (partial expected-failure? :error.verdict.is-scheduled))))))))))

(with-test-system (-> {:fixture            "pate-verdict"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "scheduled publish (kuulutus)"
    (let [template-id  "5a7aff3e5266a1d9c1581956" ; from fixture
          {app-id :id} (create-and-open-application pena
                                                    :propertyId sipoo-property-id
                                                    :operation :sisatila-muutos
                                                    :address "Schedule alley")]
      (facts "Pena fills and submits application"
        (fill-sisatila-muutos-application pena app-id)
        (command pena :submit-application :id app-id) => ok?

        (fact "Sonja approves application"
          (command sonja :update-app-bulletin-op-description
                   :id app-id
                   :description "Bullet the blue sky.") => ok?
          (command sonja :approve-application :id app-id :lang "fi") => ok?)
        (facts "New verdict"
          (let [now-ts                   (now)
                {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                  :id app-id :template-id template-id)]
            (fill-verdict sonja app-id verdict-id
                          :verdict-code "hyvaksytty"
                          :verdict-text "Scheduled verdict almost given"
                          :verdict-date now-ts
                          :bulletin-op-description "Changed pate bulletin description"
                          :giver-first-name "Sonja"
                          :giver-last-name "Sibbo")
            (fact "generate dates"
              (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                       :path [:automatic-verdict-dates] :value true) => no-errors?)
            (facts "Sonja could..."
              (fact "publish verdict"
                (allowed? sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => true)
              (fact "edit verdict"
                (allowed? sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                          :path [:verdict-text] :value "foofaa") => true)
              (fact "delete verdict"
                (allowed? sonja :delete-pate-verdict :id app-id :verdict-id verdict-id) => true))
            (facts "julkipano date in past does not work"
              (fact "unset automatic verdict dates calculation"
                (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                         :path [:automatic-verdict-dates] :value false) => no-errors?)
              (fact "set kuulutus date"
                (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                         :path [:k-kuulutus] :value "1.1.1990")
                => ok?)
              (fact "scheduled publish error"
                (command sonja :scheduled-verdict-publish :id app-id :verdict-id verdict-id)
                => (partial expected-failure? :error.verdict.no-julkipano-date)))
            (fact "toggle automatic dates back"
              (command sonja :edit-pate-verdict
                       :id app-id :verdict-id verdict-id
                       :path [:automatic-verdict-dates] :value true)
              => ok?)
            (facts "make it scheduled"
              (fact "command"
                (command sonja :scheduled-verdict-publish :id app-id :verdict-id verdict-id)
                => ok?)
              (let [verdict (:verdict (query sonja :pate-verdict :id app-id :verdict-id verdict-id))]
                (fact "state correct"
                  (:state verdict) => "scheduled"))
              (facts "after which Sonja can..."
                (fact "not publish verdict"
                  (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id)
                  => (partial expected-failure? :error.verdict.not-editable))
                (fact "not edit verdict"
                  (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                           :path [:verdict-text] :value "foofaa")
                  => (partial expected-failure? :error.verdict.not-editable))
                (fact "not delete verdict"
                  (command sonja :delete-pate-verdict :id app-id :verdict-id verdict-id)
                  => (partial expected-failure? :error.verdict.is-scheduled))))))))))
