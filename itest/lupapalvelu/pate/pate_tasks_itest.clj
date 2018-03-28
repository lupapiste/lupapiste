(ns lupapalvelu.pate.pate-tasks-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :as core]
            [sade.util :as util]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :varasto-tms))

(facts "PATE tasks"

  (fact "submit" (command pena :submit-application :id app-id) => ok?)
  (fact "bulletinOpDescription..."
    (command sonja :update-app-bulletin-op-description :id app-id :description "Bullet the blue sky.") => ok?)
  (fact "Sonja approves" (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (let [verdict-draft       (command sonja :new-pate-verdict-draft
                                     :id app-id
                                     :template-id (-> pate-fixture/verdic-templates-setting
                                                      :templates
                                                      first
                                                      :id))
        verdict-id          (get-in verdict-draft [:verdict :id])
        verdict-data        (get-in verdict-draft [:verdict :data])]

    (facts "fill verdict data and submit verdict"
      (fact "Set automatic calculation of other dates"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:automatic-verdict-dates] :value true) => no-errors?)
      (fact "Verdict date"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-date] :value (util/to-finnish-date (core/now))) => no-errors?)
      (fact "Verdict code"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-code] :value "hyvaksytty") => no-errors?)
      (fact "Add plans"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:plans] :value ["5a85960a809b5a1e454f3233"]) => no-errors?)

      (fact "Only Aloituskokous and Loppukatselmus are used in verdict"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:reviews] :value ["5a7affbf5266a1d9c1581957" "5a7affcc5266a1d9c1581958"]) => no-errors?)




      )))