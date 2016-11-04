(ns lupapalvelu.assignment-itest
  (:require [midje.sweet :refer :all]
            [schema.core :as sc]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.assignment :refer [Assignment]]
            [lupapalvelu.assignment-api :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [lupapalvelu.domain :as domain]
            [sade.util :as util]))

(when (env/feature? :assignments)
  (apply-remote-minimal)

  (def ^:private not-completed?
    (partial expected-failure? "error.assignment-not-completed"))

  (def ^:private assignments-not-enabled?
    (partial expected-failure? "error.assignments-not-enabled"))

  (def ^:private application-not-accessible?
    (partial expected-failure? "error.application-not-accessible"))

  (def ^:private invalid-receiver?
    (partial expected-failure? "error.invalid-assignment-receiver"))

  (def ^:private invalid-assignment?
    (partial expected-failure? "error.invalid-assignment-id"))

  (defn create-assignment [from to application-id target desc]
    (command from :create-assignment
             :id            application-id
             :recipientId   to
             :target        target
             :description   desc))
  (defn update-assignment [who id assignment-id recipient description]
    (command who :update-assignment
             :id id
             :assignmentId assignment-id
             :recipientId recipient
             :description description))

  (defn complete-assignment [user assignment-id]
    (command user :complete-assignment :assignmentId assignment-id))

  (facts "Querying assignments"

    (fact "only authorities can see assignments"
      (query sonja :assignments) => ok?
      (query pena :assignments)  => unauthorized?)

    (fact "cannot query assignments if not enabled in organization"
      (query veikko :assignments) => assignments-not-enabled?)

    (fact "authorities can only see assignments belonging to their organizations"
      (let [{id :id} (create-app sonja :propertyId sipoo-property-id)
            {assignment-id :id} (create-assignment sonja ronja-id id ["target"] "Valmistuva")]
        (-> (query sonja :assignments) :assignments count)  => pos?
        (-> (query veikko :assignments) :assignments count) => zero?))

    (fact "assignments can be fetched by application id"
      (let [id1 (create-app-id sonja :propertyId sipoo-property-id)
            id2 (create-app-id ronja :propertyId sipoo-property-id)
            {assignment-id1-1 :id} (create-assignment sonja ronja-id id1 ["target"] "Hakemus 1")
            {assignment-id1-2 :id} (create-assignment sonja ronja-id id1 ["target"] "Hakemus 1")
            {assignment-id2-1 :id} (create-assignment sonja ronja-id id2 ["target"] "Hakemus 1")]
        (-> (query sonja :assignments-for-application :id id1) :assignments count) => 2
        (-> (query ronja :assignments-for-application :id id1) :assignments count) => 2
        (-> (query sonja :assignments-for-application :id id2) :assignments count) => 1
        (-> (query ronja :assignments-for-application :id id2) :assignments count) => 1
        (query veikko :assignments-for-application :id id1) => application-not-accessible?)))

  (facts "Creating assignments"
    (let [id (create-app-id sonja :propertyId sipoo-property-id)]

      (fact "only authorities can create assignments"
        (create-assignment sonja ronja-id id ["target"] "Kuvaus") => ok?
        (create-assignment pena sonja-id id ["target"] "Hommaa") => unauthorized?)
      (fact "only authorities can receive assignments"
        (create-assignment sonja pena-id id ["target"] "Penalle")        => invalid-receiver?
        (create-assignment sonja "does_not_exist_id" id ["target"] "Desc") => invalid-receiver?)

      (fact "assignments cannot be created if not enabled in organization"
        (create-assignment veikko veikko-id (:id (create-app veikko :propertyId tampere-property-id)) ["target"] "Ei onnistu")
        => assignments-not-enabled?)

      (fact "authorities can only create assignments for applications in their organizations"
        (create-assignment veikko sonja-id id ["target"] "Ei onnistu") => application-not-accessible?)
      (fact "after calling create-assignment, the assignment is created"
        (let [assignment-id (:id (create-assignment sonja ronja-id id ["target"] "Luotu?"))
              assignment    (:assignment (query sonja :assignment :assignmentId assignment-id))]
          assignment => truthy
          (sc/check Assignment assignment) => nil?
          (-> assignment :states first :type)   => "created"
          (-> assignment :states first :user :username)   => "sonja"
          (-> assignment :recipient :username) => "ronja"))

      (fact "Assigments are canceled with the application"
        (fact "initially active"
          (->> (query sonja :assignments-for-application :id id)
               :assignments
               (map :status)) => ["active", "active"])

        (command sonja :cancel-application-authority :id id :text "testing" :lang "fi") => ok?
        (query sonja :assignments-for-application :id id) => fail?

        (->> (query sonja :assignments)
             :assignments
             (filter (comp (partial = id) :id :application))
             (map :status)) => ["canceled", "canceled"])

      (fact "accessible after undo-cancellation"
        (command sonja :undo-cancellation :id id) => ok?
        (->> (query sonja :assignments-for-application :id id)
             :assignments
             (map :status)) => ["active", "active"])))

  (facts "Editing assignments"
    (let [id (:id (create-and-submit-application pena :propertyId sipoo-property-id))
          {assignment-id :id} (create-assignment sonja ronja-id id ["target"] "Edit1")]
      (fact "can change text"
        (update-assignment sonja id assignment-id ronja-id "foo") => ok?
        (-> (query ronja :assignment :assignmentId assignment-id)
            :assignment
            :description) => "foo")
      (fact "also ronja can change text"
        (update-assignment ronja id assignment-id ronja-id "faa") => ok?
        (-> (query ronja :assignment :assignmentId assignment-id)
            :assignment
            :description) => "faa")
      (fact "Ronja can change recipient to sonja"
        (update-assignment ronja id assignment-id sonja-id "Ota koppi") => ok?
        (-> (query ronja :assignment :assignmentId assignment-id)
            :assignment
            :recipient :username) => "sonja")
      (fact "inputs are validated"
        (update-assignment ronja id "foo" sonja-id "Ota koppi") => fail?
        (update-assignment ronja id (mongo/create-id) sonja-id "Ota koppi") => invalid-assignment?
        (update-assignment ronja id assignment-id "foo" "Ota koppi") => invalid-receiver?)))

  (facts "Completing assignments"
    (let [id (create-app-id sonja :propertyId sipoo-property-id)
          {assignment-id1 :id} (create-assignment sonja ronja-id id ["target"] "Valmistuva")
          {assignment-id2 :id} (create-assignment sonja ronja-id id ["target"] "Valmistuva")]
      (fact "Only authorities within the same organization can complete assignment"
        (complete-assignment pena assignment-id1)   => unauthorized?
        (complete-assignment veikko assignment-id1) => assignments-not-enabled?
        (complete-assignment ronja assignment-id1)  => ok?
        (complete-assignment ronja assignment-id1)  => not-completed?)

      (fact "Authorities CAN complete other authorities' assignments within their organizations"
        (complete-assignment sonja assignment-id2) => ok?)

      (fact "After calling complete-assignment, the assignment is completed"
        (-> (query sonja :assignment :assignmentId assignment-id1) :assignment :states last :type) => "completed")))

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
        (second (first (:targets targets-resp))) => (has every? (fn [target] (every? (partial contains? target) [:id :type]))))
      (fact "data from accordion-field is in display text"
        (:description (util/find-by-id hakija-doc-id party-target-values)) => (contains "SONJA"))))

  (facts "Assignments search"
    (apply-remote-minimal)
    (let [id1 (create-app-id sonja :propertyId sipoo-property-id)
          id2 (create-app-id ronja :propertyId sipoo-property-id)]

      (facts "text search finds approximate matches in description"
        (let [{assignment-id1 :id} (create-assignment sonja ronja-id id1 ["target"] "Kuvaava teksti")]
          (fact "uva eks - all"
              (->> (query sonja :assignments-search :searchText "uva eks" :state "all")
                   :data :assignments (map :description)) => (contains "Kuvaava teksti"))
          (fact "uva eks - created"
              (->> (query sonja :assignments-search :searchText "uva eks" :state "created")
                   :data :assignments (map :description)) => (contains "Kuvaava teksti"))
          (fact "uva eks - compeleted"
            (->> (query sonja :assignments-search :searchText "uva eks" :state "completed")
                 :data :assignments) => empty?)
          (fact "not even close"
            (->> (query sonja :assignments-search :searchText "not even close")
                 :data :assignments (map :description)) => empty?)

          (fact "recipient search finds correct assignments"
            (distinct
              (map #(get-in % [:recipient :id])
                   ; jos tähän vaihtaa datatables -> query, tulee schema error.
                   ; (query) muuttaa :recipient argumentin non-sequentialiksi..........
                   (-> (datatables sonja :assignments-search :recipient []) :data :assignments)))
            => (just #{ronja-id})
            (distinct
              (map #(get-in % [:recipient :id])
                   (-> (datatables sonja :assignments-search :recipient [sonja-id] :limit 5) :data :assignments)))
            => empty?)))

      (fact "no results after application is canceled"
        (command sonja :cancel-application-authority :id id1 :text "testing" :lang "fi") => ok?

        (-> (query sonja :assignments-search :searchText "uva eks" :state "all")
            :data :assignments) => empty?)

      (fact "get results again when cancalation is reverted"
        (command sonja :undo-cancellation :id id1) => ok?
        (-> (query sonja :assignments-search :searchText "uva eks" :state "all")
            :data :assignments count) => 1))))
