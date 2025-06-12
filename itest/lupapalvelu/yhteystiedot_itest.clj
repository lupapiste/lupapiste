(ns lupapalvelu.yhteystiedot-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "application property owners"
    (let [application-id (create-app-id pena :operation "kiinteistonmuodostus" :propertyId "75312312340001")]
      (fact "Applicant can't query owners"
        (query pena :application-property-owners :id application-id) => unauthorized?)

      (let [resp (query ronja :application-property-owners :id application-id)]
        (fact "Authority got owners"
          resp => ok?
          (count (:owners resp)) => pos?)

        (fact "property id is echoed"
          (-> resp :owners first :propertyId) => "75312312340001")

        (fact "Got owners for one property"
          (->> resp :owners (map :propertyId) set count) => 1))

      (fact "Add property"
        (command pena :create-doc :id application-id :schemaName "secondary-kiinteistot" :updates [["kiinteisto.kiinteistoTunnus" "75312312340002"]]) => ok?)

      (fact "Got owners for two properties"
        (let [resp (query ronja :application-property-owners :id application-id)]
          (->> resp :owners (map :propertyId) set count)) => 2))))
