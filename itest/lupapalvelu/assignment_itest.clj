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
    (query pena :assignments)  => unauthorized?)
  (fact "authorities can only see assignments belonging to their organizations"
    (let [{id :id} (create-app sonja :propertyId sipoo-property-id)
          {assignment-id :id} (create-assignment sonja "ronja" id ["target"] "Valmistuva")]
      (-> (query sonja :assignments) :assignments count)  => pos?
      (-> (query veikko :assignments) :assignments count) => zero?))
  (fact "assignments can be fetched by application id"
    (let [{id1 :id} (create-app sonja :propertyId sipoo-property-id)
          {id2 :id} (create-app ronja :propertyId sipoo-property-id)
          {assignment-id1-1 :id} (create-assignment sonja "ronja" id1 ["target"] "Hakemus 1")
          {assignment-id1-2 :id} (create-assignment sonja "ronja" id1 ["target"] "Hakemus 1")
          {assignment-id2-1 :id} (create-assignment sonja "ronja" id2 ["target"] "Hakemus 1")]
      (-> (query sonja :assignments-for-application :id id1) :assignments count) => 2
      (-> (query ronja :assignments-for-application :id id1) :assignments count) => 2
      (-> (query sonja :assignments-for-application :id id2) :assignments count) => 1
      (-> (query ronja :assignments-for-application :id id2) :assignments count) => 1
      (query veikko :assignments-for-application :id id1) => application-not-accessible?)))

(facts "Creating assignments"
  (let [{id :id} (create-app sonja :propertyId sipoo-property-id)]
    (fact "only authorities can create assignments"
      (create-assignment sonja "ronja" id ["target"] "Kuvaus") => ok?
      (create-assignment pena "sonja" id ["target"] "Hommaa") => unauthorized?)
    (fact "authorities can only create assignments for applications in their organizations"
      (create-assignment veikko "sonja" id ["target"] "Ei onnistu") => application-not-accessible?)))

(facts "Completing assignments"
  (let [{id :id}            (create-app sonja :propertyId sipoo-property-id)
        {assignment-id :id} (create-assignment sonja "ronja" id ["target"] "Valmistuva")]
    (fact "Only authorities within the same organization can complete assignment"
      (complete-assignment pena assignment-id)   => unauthorized?
      (complete-assignment veikko assignment-id) => not-completed?
      (complete-assignment ronja assignment-id)  => ok?
      (complete-assignment ronja assignment-id)  => not-completed?)))
