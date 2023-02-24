(ns lupapalvelu.pate.scheduled-publish-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :refer [now]]))

;;
;; Requires connection to Muuntaja, so run with VPN on...
;;

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation :kerrostalo-rivitalo))

(facts "scheduled publish"
  (let [template-id "5a7aff3e5266a1d9c1581956" ; from fixture
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
        (let [now-ts (now)
              {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                :id app-id :template-id template-id)]
          (fill-verdict sonja app-id verdict-id
                        :verdict-code "hyvaksytty"
                        :verdict-text "Scheduled verdict almost given"
                        :verdict-date now-ts
                        :bulletin-op-description "Changed pate bulletin description")
          (fact "generate dates"
            (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                     :path [:automatic-verdict-dates] :value true) => no-errors?)
          (facts "Sonja could..."
            (fact "publish verdict"
              sonja => (allowed? :publish-pate-verdict :id app-id :verdict-id verdict-id))
            (fact "edit verdict"
              sonja => (allowed? :edit-pate-verdict :id app-id :verdict-id verdict-id
                                 :path [:verdict-text] :value "foofaa"))
            (fact "delete verdict"
              sonja => (allowed? :delete-pate-verdict :id app-id :verdict-id verdict-id)))
          (facts "julkipano date in past does not work"
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
                => (partial expected-failure? :error.verdict.is-scheduled)))))))))
