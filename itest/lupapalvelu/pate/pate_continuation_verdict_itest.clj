(ns lupapalvelu.pate.pate-continuation-verdict-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :as core]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :kerrostalo-rivitalo))

(facts "PATE continuation verdict"

  (facts "Submit and approve application"
    (command pena :submit-application :id app-id) => ok?
    (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin description") => ok?
    (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (facts "Publish verdict for main application"
    (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                            :id app-id
                                            :template-id (-> pate-fixture/verdict-templates-setting-r
                                                             :templates
                                                             first
                                                             :id))]

      (fact "Set automatic calculation of other dates"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:automatic-verdict-dates] :value true) => no-errors?)
      (fact "Verdict date"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-date] :value (core/now)) => no-errors?)
      (fact "Verdict code"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-code] :value "hyvaksytty") => no-errors?)
      (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?))

  (facts "Create continuation application"
    (let [continuation-resp   (command pena :create-continuation-period-permit :id app-id)
          continuation-app-id (:id continuation-resp)
          _                   (command pena :submit-application :id continuation-app-id)
          _                   (command sonja :update-app-bulletin-op-description
                                       :id continuation-app-id
                                       :description "Bulletin description")
          _                   (command sonja :approve-application :id continuation-app-id :lang "fi")
          {c-verdict-id :verdict-id}     (command sonja :new-pate-verdict-draft
                                       :id continuation-app-id
                                       :template-id "5acc68c953b771ded5d45605")]

      (facts "Publish PATE continuation verdict"
        (fact "Set automatic calculation of other dates"
          (command sonja :edit-pate-verdict :id continuation-app-id :verdict-id c-verdict-id
                   :path [:automatic-verdict-dates] :value false) => no-errors?)
        (fact "Verdict date"
          (command sonja :edit-pate-verdict :id continuation-app-id :verdict-id c-verdict-id
                   :path [:verdict-date] :value 1523440000000) => no-errors?)
        (fact "Anto date"
          (command sonja :edit-pate-verdict :id continuation-app-id :verdict-id c-verdict-id
                   :path [:anto] :value 1523440000000) => no-errors?)
        (fact "Voimassa date"
          (command sonja :edit-pate-verdict :id continuation-app-id :verdict-id c-verdict-id
                   :path [:voimassa] :value 1587116810000) => no-errors?)
        (fact "Verdict code"
          (command sonja :edit-pate-verdict :id continuation-app-id :verdict-id c-verdict-id
                   :path [:verdict-code] :value "hyvaksytty") => no-errors?)
        (command sonja :publish-pate-verdict :id continuation-app-id :verdict-id c-verdict-id) => no-errors?)

      (check-verdict-date sonja continuation-app-id 1523440000000)

      (facts "Main application should have continuation period info"
        (let [application          (query-application sonja app-id)
              continuation-period  (first (:continuationPeriods application))]
          (fact "There is one continuation period"
            (count (:continuationPeriods application)) => 1)
          (fact "Continuation period data is correct"
            (:handler continuation-period) => "Sonja Sibbo"
            (:continuationAppId continuation-period) => continuation-app-id
            (:continuationPeriodEnd continuation-period) => 1587116810000))))))
