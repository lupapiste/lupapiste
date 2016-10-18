(ns lupapalvelu.assignment-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.assignment-api :refer :all]))

(apply-remote-minimal)

(facts "Querying assignments"
  (fact "only authorities can have assignments"
    (query sonja :assignments) => ok?
    (query pena :assignments)  => unauthorized?))

(facts "Creating assignments"
  (let [{id :id} (create-app sonja :propertyId sipoo-property-id)]
    (fact "only authorities can create assignments"
      (command sonja :add-assignment
               :id          id
               :recipient   "pena"
               :target      ["target"]
               :description "desc") => ok?
      (command pena :add-assignment
               :id          id
               :recipient   "pena"
               :target      ["target"]
               :description "desc") => unauthorized?)))
