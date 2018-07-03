(ns lupapalvelu.application-options-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(facts "Municipality hears neighbors"
  (let [{app-id :id :as app} (create-application pena
                                                 :propertyId sipoo-property-id
                                                 :operation "pientalo")]
    (some-> app :options :municipalityHearsNeighbors) => falsey
    (:permitType app) => "R"
    (fact "Checkbox visible"
      (query pena :municipality-hears-neighbors-visible
             :id app-id)=> ok?)
    (fact "Check the checkbox"
      (command pena :set-municipality-hears-neighbors
               :id app-id
               :enabled true)=> ok?
      (some-> (query-application pena app-id)
              :options :municipalityHearsNeighbors) => true)
    (fact "Submit application"
      (command pena :submit-application :id app-id) => ok?)
    (fact "Sonja fetches verdict for the application"
      (command sonja :check-for-verdict :id app-id) => ok?)
    (facts "Foreman application"
      (let [for-id        (create-foreman-application app-id
                                                      pena
                                                      mikko-id
                                                      "vastaava ty\u00F6njohtaja"
                                                      "A")
            foreman-error (partial expected-failure?
                                   :error.foreman-application)]
        (fact "Check not visible"
          (query pena :municipality-hears-neighbors-visible
                 :id for-id)
          => foreman-error)
        (fact "... and cannot be toggled"
          (command pena :set-municipality-hears-neighbors
                   :id for-id
                   :enabled true)
          => foreman-error)))))
