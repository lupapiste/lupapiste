(ns lupapalvelu.digitizer-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(facts "Create digitizer project with default location"

    (fact "Admin can set default location for organization"
      (command jarvenpaa :set-default-digitalization-location :x "404262.00" :y "6694511.00") => ok?

      (let [organization (first (:organizations (query admin :organizations)))]
        (get-in organization [:default-digitalization-location :x]) => "404262.00"
        (get-in organization [:default-digitalization-location :y]) => "6694511.00"))

    (let [response (command digitoija :create-archiving-project
                                      :lang "fi"
                                      :x ""
                                      :y ""
                                      :address ""
                                      :propertyId ""
                                      :organizationId "186-R"
                                      :kuntalupatunnus "186-00X"
                                      :createAnyway true
                                      :createWithoutBuildings true
                                      :createWithDefaultLocation true)
          app-id (:id response)
          application (query-application digitoija app-id)]

      (fact "Digitizer can create project without location"
        response => ok?
        (:address application) => "Sijainti puuttuu"
        (:x (:location application)) => 404262.00
        (:y (:location application)) => 6694511.00)

      (fact "Digitizer can change location"
        (command digitoija :change-location
                           :id app-id
                           :x 400062.00
                           :y 6664511.00
                           :address "New address"
                           :propertyId "18677128000000") => ok?
        (let [application (query-application digitoija app-id)]
          (:address application) => "New address"
          (:x (:location application)) => 400062.00
          (:y (:location application)) => 6664511.00))

      (fact "Operations, buildings and documents are changed correctly")
        (let [application (query-application digitoija app-id)
              primary-operation-id (get-in application [:primaryOperation :id])
              secondary-operation-id (:id (first (:secondaryOperations application)))
              first-building-id (:operationId (first (:buildings application)))
              second-building-id (:operationId (second (:buildings application)))
              documents (domain/get-documents-by-name application "archiving-project")
              first-building-doc-op-id (get-in (first documents) [:schema-info :op :id])
              second-building-doc-op-id (get-in (second documents) [:schema-info :op :id])]
          (= primary-operation-id first-building-id first-building-doc-op-id) => true?
          (= secondary-operation-id second-building-id second-building-doc-op-id) => true?)))
