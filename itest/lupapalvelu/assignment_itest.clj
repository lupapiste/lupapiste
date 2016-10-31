(ns lupapalvelu.assignment-itest
  (:require [midje.sweet :refer :all]
            [schema.core :as sc]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.assignment :refer [Assignment]]
            [lupapalvelu.assignment-api :refer :all]
            [sade.env :as env]
            [lupapalvelu.domain :as domain]
            [sade.util :as util]))

(when (env/feature? :assignments)
  (apply-remote-minimal)

  (def ^:private not-completed?
    (partial expected-failure? "error.assignment-not-completed"))

  (def ^:private application-not-accessible?
    (partial expected-failure? "error.application-not-accessible"))

  (def ^:private invalid-receiver?
    (partial expected-failure? "error.invalid-assignment-receiver"))

  (defn create-assignment [from to application-id target desc]
    (command from :create-assignment
             :id            application-id
             :recipientId   to
             :target        target
             :description   desc))

  (defn complete-assignment [user assignment-id]
    (command user :complete-assignment :assignmentId assignment-id))

  (facts "Querying assignments"

    (fact "only authorities can see assignments"
      (query sonja :assignments) => ok?
      (query pena :assignments)  => unauthorized?)

    (fact "authorities can only see assignments belonging to their organizations"
      (let [{id :id} (create-app sonja :propertyId sipoo-property-id)
            {assignment-id :id} (create-assignment sonja ronja-id id ["target"] "Valmistuva")]
        (-> (query sonja :assignments) :assignments count)  => pos?
        (-> (query veikko :assignments) :assignments count) => zero?))

    (fact "assignments can be fetched by application id"
      (let [{id1 :id} (create-app sonja :propertyId sipoo-property-id)
            {id2 :id} (create-app ronja :propertyId sipoo-property-id)
            {assignment-id1-1 :id} (create-assignment sonja ronja-id id1 ["target"] "Hakemus 1")
            {assignment-id1-2 :id} (create-assignment sonja ronja-id id1 ["target"] "Hakemus 1")
            {assignment-id2-1 :id} (create-assignment sonja ronja-id id2 ["target"] "Hakemus 1")]
        (-> (query sonja :assignments-for-application :id id1) :assignments count) => 2
        (-> (query ronja :assignments-for-application :id id1) :assignments count) => 2
        (-> (query sonja :assignments-for-application :id id2) :assignments count) => 1
        (-> (query ronja :assignments-for-application :id id2) :assignments count) => 1
        (query veikko :assignments-for-application :id id1) => application-not-accessible?)))

  (facts "Creating assignments"
    (let [{id :id} (create-app sonja :propertyId sipoo-property-id)]

      (fact "only authorities can create assignments"
        (create-assignment sonja ronja-id id ["target"] "Kuvaus") => ok?
        (create-assignment pena sonja-id id ["target"] "Hommaa") => unauthorized?)
      (fact "only authorities can receive assignments"
        (create-assignment sonja pena-id id ["target"] "Penalle")        => invalid-receiver?
        (create-assignment sonja "does_not_exist_id" id ["target"] "Desc") => invalid-receiver?)
      (fact "authorities can only create assignments for applications in their organizations"
        (create-assignment veikko sonja-id id ["target"] "Ei onnistu") => application-not-accessible?)
      (fact "after calling create-assignment, the assignment is created"
        (let [assignment-id (:id (create-assignment sonja ronja-id id ["target"] "Luotu?"))
              assignment    (query sonja :assignment :assignmentId assignment-id)]
          (:assignment assignment) => truthy
          (sc/check Assignment (:assignment assignment)) => nil?
          (-> assignment :assignment :creator :username)   => "sonja"
          (-> assignment :assignment :recipient :username) => "ronja"))))

  (facts "Completing assignments"
    (let [{id :id}            (create-app sonja :propertyId sipoo-property-id)
          {assignment-id1 :id} (create-assignment sonja ronja-id id ["target"] "Valmistuva")
          {assignment-id2 :id} (create-assignment sonja ronja-id id ["target"] "Valmistuva")]
      (fact "Only authorities within the same organization can complete assignment"
        (complete-assignment pena assignment-id1)   => unauthorized?
        (complete-assignment veikko assignment-id1) => not-completed?
        (complete-assignment ronja assignment-id1)  => ok?
        (complete-assignment ronja assignment-id1)  => not-completed?)

      (fact "Authorities CAN complete other authorities' assignments within their organizations"
        (complete-assignment sonja assignment-id2) => ok?)

      (fact "After calling complete-assignment, the assignment is completed"
        (-> (query sonja :assignment :assignmentId assignment-id1) :assignment :status) => "completed")))

  (facts "Assignment targets"
    (let [app-id (create-app-id sonja :propertyId sipoo-property-id)
          _ (generate-documents app-id sonja)
          hakija-doc-id (:id (domain/get-applicant-document (:documents (query-application sonja app-id))))
          update-resp (command sonja :update-doc :id app-id :doc hakija-doc-id :updates [["henkilo.henkilotiedot.etunimi" "SONJA"]])
          targets-resp (query sonja :assignment-targets :id app-id :lang "fi")
          party-target-values (second (first (filter (fn [[k _]] (= k "parties")) (:targets targets-resp))))]
      update-resp => ok?
      targets-resp => ok?

      (:targets targets-resp) => vector?
      (fact "targets are returned as key-val vectors"
        (:targets targets-resp) => (has every? (fn [[k v]] (and (string? k) (vector? v)))))
      (fact "keys for values look right"
        (second (first (:targets targets-resp))) => (has every? (fn [target] (every? (partial contains? target) [:displayText :id]))))
      (fact "data from accordion-field is in display text"
        (:displayText (util/find-by-id hakija-doc-id party-target-values)) => (contains "SONJA"))))

  (facts "Assignments search"
    (let [{id1 :id} (create-app sonja :propertyId sipoo-property-id)
          {id2 :id} (create-app ronja :propertyId sipoo-property-id)]

      (fact "text search finds approximate matches in description"
        (let [{assignment-id1 :id} (create-assignment sonja ronja-id id1 ["target"] "Kuvaava teksti")]
          (->> (query sonja :assignments-search :searchText "uva eks" :status "all")
               :data :assignments (map :description)) => (contains "Kuvaava teksti")
          (->> (query sonja :assignments-search :searchText "uva eks" :status "active")
               :data :assignments (map :description)) => (contains "Kuvaava teksti")
          (->> (query sonja :assignments-search :searchText "uva eks" :status "completed")
               :data :assignments) => empty?
          (->> (query sonja :assignments-search :searchText "not even close")
               :data :assignments (map :description)) => empty?)))))
