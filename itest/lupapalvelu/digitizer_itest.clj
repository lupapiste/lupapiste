(ns lupapalvelu.digitizer-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(defn archiving-project-id [propertyId kuntalupatunnus refreshBuildings]
  (:id (command digitoija :create-archiving-project
                :lang "fi"
                :x "404262.00"
                :y "6694511.00"
                :address "Street 1"
                :propertyId propertyId
                :organizationId "186-R"
                :kuntalupatunnus kuntalupatunnus
                :createAnyway true
                :createWithoutBuildings true
                :createWithDefaultLocation false
                :refreshBuildings refreshBuildings)))

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
                           :propertyId "18677128000000"
                           :refreshBuildings false) => ok?
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

(facts "Add multiple backendId and verdict dates"

  (fact "There can be multiple backendId"
    (let [app-id (archiving-project-id "18600101140005" "186-0005" true)]
      (command digitoija :store-archival-project-backend-ids :id app-id :verdicts [{:kuntalupatunnus "186-0003"} {:kuntalupatunnus "186-0004"}]) => ok?
      (let [verdict-ids (map :id (:verdicts (query-application digitoija app-id)))
            verdict-updates [{:id (first verdict-ids) :kuntalupatunnus "186-0003" :verdictDate 1512597600000}
                             {:id (second verdict-ids) :kuntalupatunnus "186-0004" :verdictDate 1512594000000}]
            _ (command digitoija :store-archival-project-backend-ids :id app-id :verdicts verdict-updates)
            verdicts (:verdicts (query-application digitoija app-id))]
        (count verdicts) => 2
        (:kuntalupatunnus (first verdicts)) => "186-0003"
        (:paatospvm (:0 (:poytakirjat (first (:paatokset (first verdicts)))))) => 1512597600000
        (:kuntalupatunnus (second verdicts)) => "186-0004"
        (:paatospvm (:0 (:poytakirjat (first (:paatokset (second verdicts)))))) => 1512594000000))))

(facts "Fetching buildings from backend"

  (fact "Creating archiving project without extra buildings"
    (let [app-id (archiving-project-id "18600101140006" "186-0006" false)
          application (query-application digitoija app-id)]
      (count (:buildings application)) => 1
      (count (domain/get-documents-by-name application "archiving-project")) => 1
      (count (:secondaryOperations application)) => 0))

  (fact "Creating archiving project with all buildings from backend"
    (let [app-id (archiving-project-id "18600101140007" "186-0007" true)
          application (query-application digitoija app-id)]
      (count (:buildings application)) => 2
      (count (domain/get-documents-by-name application "archiving-project")) => 2
      (count (:secondaryOperations application)) => 1))

  (fact "Change location and refresh all buildings"
    (let [app-id (archiving-project-id "18600101140008" "186-0008" false)
          app-before-change (query-application digitoija app-id)
          _ (command digitoija :change-location
                     :id app-id
                     :x 400062.00
                     :y 6664511.00
                     :address "New address"
                     :propertyId "18677128000000"
                     :refreshBuildings true) => ok?
          application (query-application digitoija app-id)]
      (count (:buildings app-before-change)) => 1
      (count (domain/get-documents-by-name app-before-change "archiving-project")) => 1
      (count (:secondaryOperations app-before-change)) => 0
      (count (:buildings application)) => 2
      (count (domain/get-documents-by-name application "archiving-project")) => 2
      (count (:secondaryOperations application)) => 1))

  (fact "Change location and not refresh buildings"
    (let [app-id (archiving-project-id "18600101140009" "186-0009" true)
          app-before-change (query-application digitoija app-id)
          _ (command digitoija :change-location
                     :id app-id
                     :x 400062.00
                     :y 6664511.00
                     :address "New address"
                     :propertyId "18677128000000"
                     :refreshBuildings false) => ok?
          application (query-application digitoija app-id)]
      (count (:buildings app-before-change)) => 2
      (count (domain/get-documents-by-name app-before-change "archiving-project")) => 2
      (count (:secondaryOperations app-before-change)) => 1
      (count (:buildings application)) => 2
      (count (domain/get-documents-by-name application "archiving-project")) => 2
      (count (:secondaryOperations application)) => 1)))

(facts "Removing buildings from project"

  (fact "Remove all buildings but primary operation building"
    (let [app-id (archiving-project-id "18600101140010" "186-0010" true)
          app-before-change (query-application digitoija app-id)
          _ (command digitoija :remove-buildings :id app-id) => ok?
          application (query-application digitoija app-id)]
      (count (:buildings app-before-change)) => 2
      (count (domain/get-documents-by-name app-before-change "archiving-project")) => 2
      (count (:secondaryOperations app-before-change)) => 1
      (count (:buildings application)) => 1
      (count (domain/get-documents-by-name application "archiving-project")) => 1
      (count (:secondaryOperations application)) => 0)))
