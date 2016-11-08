(ns lupapalvelu.assignment-itest
  (:require [midje.sweet :refer :all]
            [schema.core :as sc]
            [sade.env :as env]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.assignment :refer [Assignment]]
            [lupapalvelu.assignment-api :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))

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

  (facts "Querying assignments"

    (fact "only authorities can see assignments"
      (query sonja :assignments) => ok?
      (query pena :assignments)  => unauthorized?)

    (fact "cannot query assignments if not enabled in organization"
      (query veikko :assignments) => assignments-not-enabled?)

    (fact "authorities can only see assignments belonging to their organizations"
      (let [{id :id} (create-app sonja :propertyId sipoo-property-id)
            doc-id (-> (query-application sonja id) :documents first :id)
            {assignment-id :id} (create-assignment sonja ronja-id id {:group "group" :id doc-id} "Valmistuva")]
        (-> (query sonja :assignments) :assignments count)  => pos?
        (-> (query veikko :assignments) :assignments count) => zero?))

    (fact "assignments can be fetched by application id"
      (let [id1 (create-app-id sonja :propertyId sipoo-property-id)
            doc-id1 (-> (query-application sonja id1) :documents first :id)
            id2 (create-app-id ronja :propertyId sipoo-property-id)
            doc-id2 (-> (query-application sonja id2) :documents first :id)
            {assignment-id1-1 :id} (create-assignment sonja ronja-id id1 {:group "group" :id doc-id1} "Hakemus 1")
            {assignment-id1-2 :id} (create-assignment sonja ronja-id id1 {:group "group" :id doc-id1} "Hakemus 1")
            {assignment-id2-1 :id} (create-assignment sonja ronja-id id2 {:group "group" :id doc-id2} "Hakemus 1")]
        (-> (query sonja :assignments-for-application :id id1) :assignments count) => 2
        (-> (query ronja :assignments-for-application :id id1) :assignments count) => 2
        (-> (query sonja :assignments-for-application :id id2) :assignments count) => 1
        (-> (query ronja :assignments-for-application :id id2) :assignments count) => 1
        (query veikko :assignments-for-application :id id1) => application-not-accessible?)))

  (facts "Creating assignments"
    (let [id (create-app-id sonja :propertyId sipoo-property-id)
          doc-id (-> (query-application sonja id) :documents first :id)]

      (fact "only authorities can create assignments"
        (create-assignment sonja ronja-id id {:group "group" :id doc-id} "Kuvaus") => ok?
        (create-assignment pena sonja-id id {:group "group" :id doc-id} "Hommaa") => unauthorized?)
      (fact "only authorities can receive assignments"
        (create-assignment sonja pena-id id {:group "group" :id doc-id} "Penalle")        => invalid-receiver?
        (create-assignment sonja "does_not_exist_id" id {:group "group" :id doc-id} "Desc") => invalid-receiver?)

      (fact "assignments cannot be created if not enabled in organization"
        (create-assignment veikko veikko-id (:id (create-app veikko :propertyId tampere-property-id)) {:group "group" :id doc-id} "Ei onnistu")
        => assignments-not-enabled?)

      (fact "authorities can only create assignments for applications in their organizations"
        (create-assignment veikko sonja-id id {:group "group" :id doc-id} "Ei onnistu") => application-not-accessible?)
      (fact "after calling create-assignment, the assignment is created"
        (let [assignment-id (:id (create-assignment sonja ronja-id id {:group "group" :id doc-id} "Luotu?"))
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
          doc-id (-> (query-application sonja id) :documents first :id)
          {assignment-id :id} (create-assignment sonja ronja-id id {:group "group" :id doc-id} "Edit1")]
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
          doc-id (-> (query-application sonja id) :documents first :id)
          {assignment-id1 :id} (create-assignment sonja ronja-id id {:group "group" :id doc-id} "Valmistuva")
          {assignment-id2 :id} (create-assignment sonja ronja-id id {:group "group" :id doc-id} "Valmistuva")]
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
          app (query-application sonja app-id)
          hakija-doc-id (:id (domain/get-applicant-document (:documents app)))
          update-resp (command sonja :update-doc :id app-id :doc hakija-doc-id :updates [["henkilo.henkilotiedot.etunimi" "SONJA"]])
          targets-resp (query sonja :assignment-targets :id app-id :lang "fi")
          party-target-values (second (first (filter (fn [[k _]] (= k "parties")) (:targets targets-resp))))]
      update-resp => ok?
      targets-resp => ok?

      (:targets targets-resp) => vector?
      (fact "targets are returned as key-val vectors"
        (:targets targets-resp) => (has every? (fn [[k v]] (and (string? k) (vector? v)))))
      (fact "keys for values look right"
        (second (first (:targets targets-resp))) => (has every? (fn [target] (every? (partial contains? target) [:id :type-key]))))
      (fact "data from accordion-field is in display text"
        (:description (util/find-by-id hakija-doc-id party-target-values)) => (contains "SONJA"))))

  (facts "Deleting targets"
    (facts "documents"
      (let [app-id (create-app-id sonja :propertyId sipoo-property-id)
            _ (generate-documents app-id sonja)
            app (query-application sonja app-id)
            designer-doc     (domain/get-document-by-name app "suunnittelija")
            assignment-id (:id (create-assignment sonja sonja-id app-id {:group "parties" :id (:id designer-doc)} "Tarkista!"))
            assignments         (get-user-assignments sonja)
            designer-assignment (util/find-first #(= "parties" (get-in % [:target :group])) assignments)]
        (fact "assignment has designer target"
          (:id designer-assignment) => assignment-id)
        (fact "party assignment is deleted after party document deletion"
          (command sonja :remove-doc :id app-id :docId (:id designer-doc)) => ok?
          (let [new-query (get-user-assignments sonja)]
            (count new-query) => (dec (count assignments))
            (map :id new-query) =not=> (contains assignment-id)))))
    (facts "attachments"
      (let [app-id (create-app-id sonja :propertyId sipoo-property-id)
            app (query-application sonja app-id)
            target-attachment (first (:attachments app))]
        (fact "assignment for attachment"
          (create-assignment sonja sonja-id app-id {:group "attachments" :id (:id target-attachment)} "Onko liite kunnossa?") => ok?
          (map (comp :id :target) (get-user-assignments sonja)) => (contains (:id target-attachment)))
        (fact "attachment deletion deletes assignment"
          (command sonja :delete-attachment :id app-id :attachmentId (:id target-attachment)) => ok?
          (map (comp :id :target) (get-user-assignments sonja)) =not=> (contains (:id target-attachment))))
        ))

  (facts "Assignments search"
    (apply-remote-minimal)
    (let [id1 (create-app-id sonja :propertyId sipoo-property-id :x 404369.304 :y 6693806.957) ; Included into Nikkila area
          doc-id1 (-> (query-application sonja id1) :documents first :id)
          id2 (create-app-id ronja :propertyId sipoo-property-id)
          doc-id2 (-> (query-application ronja id2) :documents first :id)
          nikkila-area (first (filter (fn [feature]
                                    (= "Nikkil\u00e4" (get-in feature [:properties :nimi])))
                                      (get-in (:body (decode-response (upload-area sipoo))) [:areas :features])))]

      (facts "ids exist"
        id1 => truthy
        id2 => truthy)

      (facts "text search finds approximate matches in description"
        (let [{assignment-id1 :id} (create-assignment sonja ronja-id id1 {:group "group" :id doc-id1} "Kuvaava teksti")
              {assignment-id2 :id} (create-assignment ronja sonja-id id2 {:group "group" :id doc-id2} "Kuvaava teksti")]
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
                   ; jos tahan vaihtaa datatables -> query, tulee schema error.
                   ; (query) muuttaa :recipient argumentin non-sequentialiksi..........
                   (-> (datatables sonja :assignments-search :recipient []) :data :assignments)))
            => (just #{ronja-id sonja-id} :in-any-order)
            (distinct
              (map #(get-in % [:recipient :id])
                   (-> (datatables sonja :assignments-search :recipient [sonja-id] :limit 5) :data :assignments)))
            => (just #{sonja-id}))

          (fact "date filter"
            (let [today-12am      (t/today-at-midnight)
                  yesterday-12am  (t/minus today-12am (-> 1 clj-time.core/days))
                  tomorrow-12am   (t/plus today-12am (-> 1 clj-time.core/days))
                  result-empty    (datatables sonja :assignments-search :createdDate {:start (tc/to-long yesterday-12am)
                                                                                      :end   (tc/to-long today-12am)})
                  result-has-data (datatables sonja :assignments-search :createdDate {:start (tc/to-long today-12am)
                                                                                      :end   (tc/to-long tomorrow-12am)})]
              result-empty => ok?
              (-> result-empty :data :assignments) => empty?
              result-has-data => ok?
              (-> result-has-data :data :assignments count) => 2))))

      (fact "areas search"
        (-> (datatables sonja :assignments-search :area [(-> nikkila-area :id)]) :data :assignments count) => 1)
      (fact "no results after application is canceled"
        (command sonja :cancel-application-authority :id id1 :text "testing" :lang "fi") => ok?
        (command sonja :cancel-application-authority :id id2 :text "testing" :lang "fi") => ok?

        (-> (query sonja :assignments-search :searchText "uva eks" :state "all")
            :data :assignments) => empty?)

      (fact "get results again when cancalation is reverted"
        (command sonja :undo-cancellation :id id1) => ok?
        (-> (query sonja :assignments-search :searchText "uva eks" :state "all")
            :data :assignments count) => 1)

      (facts "state"
        (let [assignments (-> (datatables sonja :assignments-search :state "all") :data :assignments)
              first-assignment (first assignments)]
          (fact "count for all"
            (count assignments) => 1)
          (fact "count for created"
              (-> (datatables sonja :assignments-search :state "created") :data :assignments count) => 1)
          (fact "count for completed"
            (-> (datatables sonja :assignments-search :state "completed") :data :assignments count) => 0)
          (fact "count for invalid"
            (-> (datatables sonja :assignments-search :state "foo")) => fail?)

          (complete-assignment sonja (:id first-assignment)) => ok?

          (facts "after completion"
            (fact "count for all"
              (count (-> (datatables sonja :assignments-search :state "all") :data :assignments)) => 1)
            (fact "count for created"
              (-> (datatables sonja :assignments-search :state "created") :data :assignments count) => 0)
            (fact "count for completed"
              (-> (datatables sonja :assignments-search :state "completed") :data :assignments count) => 1)))
        ))))
