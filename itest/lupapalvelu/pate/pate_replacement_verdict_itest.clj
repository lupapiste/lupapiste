(ns lupapalvelu.pate.pate-replacement-verdict-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :as core]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :kerrostalo-rivitalo))

(facts "PATE verdict replacement"

  (facts "Submit and approve application"
    (command pena :submit-application :id app-id) => ok?
    (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin description") => ok?
    (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (facts "Publish and replace verdict"
    (let [template-id              (-> pate-fixture/verdic-templates-setting :templates first :id)
          {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                            :id app-id
                                            :template-id template-id)]

      (fact "Set automatic calculation of other dates"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:automatic-verdict-dates] :value true) => no-errors?)
      (fact "Verdict date"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-date] :value (core/now)) => no-errors?)
      (fact "Verdict code"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-code] :value "hyvaksytty") => no-errors?)

      (fact "Draft cannot be replaced"
        (command sonja :new-pate-verdict-draft :id app-id
                 :template-id template-id
                 :replacement-id verdict-id)=> fail?)

      (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?

      (facts "Replacement verdict"
        (fact "First replacement draft"
          (let [{vid1 :verdict-id :as res} (command sonja :new-pate-verdict-draft :id app-id
                                            :template-id template-id
                                            :replacement-id verdict-id) => ok?]
            (fact "Only one replacement draft at the time"
              (command sonja :new-pate-verdict-draft :id app-id
                 :template-id template-id
                 :replacement-id verdict-id) => fail?)
            (fact "Delete the only replacement draft"
              (command sonja :delete-pate-verdict :id app-id
                       :verdict-id vid1) => ok?)))
        (let [{replacement-verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                            :id app-id
                                                            :template-id template-id
                                                            :replacement-id verdict-id)]

          (fact "Fill replacement verdict"
            (command sonja :edit-pate-verdict :id app-id :verdict-id replacement-verdict-id
                     :path [:automatic-verdict-dates] :value true) => no-errors?
            (command sonja :edit-pate-verdict :id app-id :verdict-id replacement-verdict-id
                     :path [:verdict-date] :value (core/now)) => no-errors?
            (command sonja :edit-pate-verdict :id app-id :verdict-id replacement-verdict-id
                     :path [:verdict-code] :value "hyvaksytty") => no-errors?)

          (fact "Only replacement verdict have replacement information before publishing"
            (let [application (query-application sonja app-id)
                  old-verdict (first (filter #(= verdict-id (:id %)) (:pate-verdicts application)))
                  new-verdict (first (filter #(= replacement-verdict-id (:id %)) (:pate-verdicts application)))]
              (:replacement old-verdict) => nil?
              (get-in new-verdict [:replacement :replaces]) => verdict-id))

          (fact "Publish replacement verdict"
            (command sonja :publish-pate-verdict
                     :id app-id
                     :verdict-id replacement-verdict-id) => ok?)

          (fact "Verdicts have replacement information"
            (let [application (query-application sonja app-id)
                  old-verdict (first (filter #(= verdict-id (:id %)) (:pate-verdicts application)))
                  new-verdict (first (filter #(= replacement-verdict-id (:id %)) (:pate-verdicts application)))]
              (get-in old-verdict [:replacement :replaced-by]) => replacement-verdict-id
              (get-in new-verdict [:replacement :replaces]) => verdict-id)))))))
