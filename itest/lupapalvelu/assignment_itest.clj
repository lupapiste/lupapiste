(ns lupapalvelu.assignment-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.assignment-api :refer :all]))

(apply-remote-minimal)

(def ^:private not-completed?
  (partial expected-failure? "error.assignment-not-completed"))

(def ^:private application-not-accessible?
  (partial expected-failure? "error.application-not-accessible"))

(defn create-assignment [from to application-id target desc]
  (command from :create-assignment
           :id          application-id
           :recipient   to
           :target      target
           :description desc))

(defn complete-assignment [user assignment-id]
  (command user :complete-assignment :assignmentId assignment-id))

(facts "Querying assignments"
  (fact "only authorities can have assignments"
    (query sonja :assignments) => ok?
    (query pena :assignments)  => unauthorized?))

(facts "Creating assignments"
  (let [{id :id} (create-app sonja :propertyId sipoo-property-id)]
    (fact "only authorities can create assignments"
      (create-assignment sonja "ronja" id ["target"] "Kuvaus") => ok?
      (create-assignment pena "sonja" id ["target"] "Hommaa") => unauthorized?)
    (fact "authority can only create assignments for applications in his or her organizations"
      (create-assignment veikko "sonja" id ["target"] "Ei onnistu") => application-not-accessible?)))

(facts "Completing assignments"
  (let [{id :id}            (create-app sonja :propertyId sipoo-property-id)
        {assignment-id :id} (create-assignment sonja "ronja" id ["target"] "Valmistuva")]
    (fact "Only authorities within the same organization can complete assignment"
      (complete-assignment pena assignment-id)   => unauthorized?
      (complete-assignment veikko assignment-id) => not-completed?
      (complete-assignment ronja assignment-id)  => ok?
      (complete-assignment ronja assignment-id)  => not-completed?)))
