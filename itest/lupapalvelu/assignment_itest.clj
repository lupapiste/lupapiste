(ns lupapalvelu.assignment-itest
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [lupapalvelu.assignment :refer [Assignment]]
            [lupapalvelu.assignment-api :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.test-util :refer [in-text]]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

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

(def general-role-id "abba1111111111111111acdc")
(def kvv-role-id "abba1111111111111112acdc")

(defn get-assignment [apikey assignment-id]
  (:assignment (query apikey :assignment :assignmentId assignment-id)))

(defn check-email [app-id address & extras]
  (let [randy (last-email)]
    randy => (contains {:subject (str "Lupapiste: " address ", Sipoo. Liite lisätty.")
                        :to      "randy.random@example.com"})
    (apply in-text (-> randy :body :html
                       (ss/replace "\n" " ")
                       (ss/replace #" \s+" " "))
           (concat ["Pääpiirustuksia" "Looksie!" app-id address
                    "Sipoo" (str "/app/fi/applicant#!/application/" app-id
                                 "/attachments")]
                   extras))))

(facts "Querying assignments"

  (fact "only authorities can see assignments"
    (query sonja :assignments) => ok?
    (query pena :assignments)  => unauthorized?)

  (fact "cannot query assignments if not enabled in organization"
    (query veikko :assignments) => assignments-not-enabled?)

  (fact "authorities can only see assignments belonging to their organizations"
    (let [id (create-app-id sonja :propertyId sipoo-property-id)
          doc-id (-> (query-application sonja id) :documents first :id)
          {assignment-id :id} (create-assignment sonja ronja-id id [{:group "group" :id doc-id}] "Valmistuva")]
      (-> (query sonja :assignments) :assignments count)  => pos?
      (-> (query veikko :assignments) :assignments count) => zero?

    (fact "open assignments count for user"
      (-> (query ronja :assignment-count) :assignmentCount) => 1
      (-> (query sonja :assignment-count) :assignmentCount) => zero?

      (fact "completed assignment decreases the count"
            (complete-assignment ronja assignment-id) => ok?
            (-> (query ronja :assignment-count) :assignmentCount) => zero?))))

  (fact "assignments can be fetched by application id"
    (let [id1 (create-app-id sonja :propertyId sipoo-property-id)
          doc-id1 (-> (query-application sonja id1) :documents first :id)
          id2 (create-app-id ronja :propertyId sipoo-property-id)
          doc-id2 (-> (query-application sonja id2) :documents first :id)
          _ (create-assignment sonja ronja-id id1 [{:group "group" :id doc-id1}] "Hakemus 1")
          _ (create-assignment sonja ronja-id id1 [{:group "group" :id doc-id1}] "Hakemus 1")
          _ (create-assignment sonja ronja-id id2 [{:group "group" :id doc-id2}] "Hakemus 1")]
      (-> (query sonja :assignments-for-application :id id1) :assignments count) => 2
      (-> (query ronja :assignments-for-application :id id1) :assignments count) => 2
      (-> (query sonja :assignments-for-application :id id2) :assignments count) => 1
      (-> (query ronja :assignments-for-application :id id2) :assignments count) => 1
      (query veikko :assignments-for-application :id id1) => application-not-accessible?)))

(facts "Creating assignments"
  (let [id (create-app-id sonja :propertyId sipoo-property-id)
        doc-id (-> (query-application sonja id) :documents first :id)]

    (fact "only authorities can create assignments"
      (create-assignment sonja ronja-id id [{:group "group" :id doc-id}] "Kuvaus") => ok?
      (create-assignment pena sonja-id id [{:group "group" :id doc-id}] "Hommaa") => unauthorized?)
    (fact "only authorities can receive assignments"
      (create-assignment sonja pena-id id [{:group "group" :id doc-id}] "Penalle")        => invalid-receiver?
      (create-assignment sonja "does_not_exist_id" id [{:group "group" :id doc-id}] "Desc") => invalid-receiver?)

    (fact "assignments cannot be created if not enabled in organization"
      (create-assignment veikko veikko-id (create-app-id veikko :propertyId tampere-property-id)
                         [{:group "group" :id doc-id}] "Ei onnistu")
      => assignments-not-enabled?)

    (fact "authorities can only create assignments for applications in their organizations"
      (create-assignment veikko sonja-id id [{:group "group" :id doc-id}] "Ei onnistu") => application-not-accessible?)
    (fact "after calling create-assignment, the assignment is created"
      (let [assignment-id (:id (create-assignment sonja ronja-id id [{:group "group" :id doc-id}] "Luotu?"))
            assignment    (get-assignment sonja assignment-id)]
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

      (command sonja :cancel-application :id id :text "testing" :lang "fi") => ok?
      (query sonja :assignments-for-application :id id) => fail?

      (->> (query sonja :assignments)
           :assignments
           (filter (comp (partial = id) :id :application))
           (map :status)) => ["canceled", "canceled"])

    (fact "accessible after undo-cancellation"
      (command sonja :undo-cancellation :id id) => ok?
      (->> (query sonja :assignments-for-application :id id)
           :assignments
           (map :status)) => ["active", "active"])
    (fact "recipient is not necessary"
      (let [assignment-id (:id (create-assignment sonja nil id {:group "group" :id doc-id} "No recipient")) => ok?
            assignment    (get-assignment sonja assignment-id)]
        (-> assignment :recipient) => nil?))))

(facts "Editing assignments"
  (let [id                   (:id (create-and-submit-application pena :propertyId sipoo-property-id))
        doc-id               (-> (query-application sonja id) :documents first :id)
        {assignment-id :id}  (create-assignment sonja ronja-id id [{:group "group" :id doc-id}] "Edit1")
        {modified :modified} (get-assignment sonja assignment-id)]
    (fact "modified on creation"
      modified => pos?)
    (fact "can change text"
      (update-assignment sonja id assignment-id ronja-id "foo") => ok?
      (let [updated (get-assignment ronja assignment-id)]
        (> (:modified updated) modified) => true
        (:description updated) => "foo"))
    (fact "also ronja can change text"
      (update-assignment ronja id assignment-id ronja-id "faa") => ok?
      (-> (get-assignment ronja assignment-id)
          :description) => "faa")
    (fact "Ronja can change recipient to sonja"
      (update-assignment ronja id assignment-id sonja-id "Ota koppi") => ok?
      (-> (get-assignment ronja assignment-id)
          :recipient :username) => "sonja")
    (fact "inputs are validated"
      (update-assignment ronja id "foo" sonja-id "Ota koppi") => fail?
      (update-assignment ronja id (mongo/create-id) sonja-id "Ota koppi") => invalid-assignment?
      (update-assignment ronja id assignment-id "foo" "Ota koppi") => invalid-receiver?)
    (fact "recipient can be removed and added again"
      (update-assignment ronja id assignment-id nil "Ei vastaanottajaa") => ok?
      (-> (get-assignment ronja  assignment-id)
          :recipient) => nil
      (update-assignment ronja id assignment-id sonja-id "Ei vastaanottajaa") => ok?
      (-> (get-assignment ronja assignment-id)
          :recipient :username) => "sonja")))

(facts "Completing assignments"
  (let [id (create-app-id sonja :propertyId sipoo-property-id)
        doc-id (-> (query-application sonja id) :documents first :id)
        {assignment-id1 :id} (create-assignment sonja ronja-id id [{:group "group" :id doc-id}] "Valmistuva")
        {modified1 :modified} (get-assignment sonja assignment-id1)
        {assignment-id2 :id} (create-assignment sonja ronja-id id [{:group "group" :id doc-id}] "Valmistuva")
        {assignment-id3 :id} (create-assignment sonja nil id [{:group "group" :id doc-id}] "Valmistuva")]
    (fact "Only authorities within the same organization can complete assignment"
      modified1 => pos?
      (complete-assignment pena assignment-id1)   => unauthorized?
      (complete-assignment veikko assignment-id1) => assignments-not-enabled?
      (complete-assignment ronja assignment-id1)  => ok?
      (> (:modified (get-assignment ronja assignment-id1))
         modified1) => true
      (complete-assignment ronja assignment-id1)  => not-completed?)

    (fact "Authorities CAN complete other authorities' assignments within their organizations"
      (complete-assignment sonja assignment-id2) => ok?)

    (fact "After calling complete-assignment, the assignment is completed"
      (-> (get-assignment sonja  assignment-id1) :states last :type) => "completed")
    (fact "Assignments with no recipient can be completed"
      (complete-assignment ronja assignment-id3)  => ok?)))

(facts "Assignment targets"
  (let [app-id (create-app-id sonja :propertyId sipoo-property-id)
        _ (generate-documents! app-id sonja)
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
    (let [app-id         (create-app-id sonja :propertyId sipoo-property-id)
          _              (generate-documents! app-id sonja)
          app            (query-application sonja app-id)
          designer-doc   (domain/get-document-by-name app "suunnittelija")
          maksaja-doc    (domain/get-document-by-name app "maksaja")
          assignment-id1 (:id (create-assignment sonja sonja-id app-id [{:group "parties" :id (:id designer-doc)}] "Tarkista!"))
          assignment-id2 (:id (create-assignment sonja sonja-id app-id [{:group "parties" :id (:id designer-doc)}
                                                                        {:group "parties" :id (:id maksaja-doc)}] "Kaksi kohdetta"))
          assignments         (sort-by :modified (get-user-assignments sonja))
          designer-assignment (util/find-first #(= "parties" (get-in % [:targets 0 :group])) assignments)]
      (fact "assignment has designer target"
        (:id designer-assignment) => assignment-id1)
      (fact "party assignment is deleted after party document deletion if party is only target"
        (command sonja :remove-doc :id app-id :docId (:id designer-doc)) => ok?
        (let [new-query (get-user-assignments sonja)]
          (count new-query) => (dec (count assignments))
          new-query =not=> (contains {:id assignment-id1})
          (fact "Removed document is not a target for any assignment"
            (not-any? #(util/find-by-id (:id designer-doc) (:targets %)) new-query) => true)
          (fact "party is removed from targets where there are multiple targets"
            (some->> new-query (util/find-by-id assignment-id2) :targets)
            => (just [(contains {:id (:id maksaja-doc)})]))))))
  (facts "attachments"
    (let [app-id (create-app-id sonja :propertyId sipoo-property-id)
          app (query-application sonja app-id)
          target-attachment (first (:attachments app))]
      (fact "assignment for attachment"
        (create-assignment sonja sonja-id app-id [{:group "attachments" :id (:id target-attachment)}] "Onko liite kunnossa?") => ok?
        (mapcat (comp (partial map :id) :targets) (get-user-assignments sonja)) => (contains (:id target-attachment)))
      (fact "attachment deletion deletes assignment"
        (command sonja :delete-attachment :id app-id :attachmentId (:id target-attachment)) => ok?
        (map (comp :id :target) (get-user-assignments sonja)) =not=> (contains (:id target-attachment))))))

(facts "Disabling targets"
  (facts "documents"
    (let [app-id         (:id (create-and-submit-application sonja :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id))
          _              (generate-documents! app-id sonja)
          app            (query-application sonja app-id)
          designer-doc   (domain/get-document-by-name app "suunnittelija")
          maksaja-doc    (domain/get-document-by-name app "maksaja")
          assignment-id1 (:id (create-assignment sonja sonja-id app-id [{:group "documents" :id (:id designer-doc)}] "Tarkista!"))
          assignment-id2 (:id (create-assignment sonja sonja-id app-id [{:group "documents" :id (:id designer-doc)}
                                                                        {:group "documents" :id (:id maksaja-doc)}] "Kaksi kohdetta"))
          _              (give-legacy-verdict sonja app-id)
          assignments    (get-user-assignments sonja)]
      (fact "when document is disabled, assignments with the document as only target are canceled"
        (command sonja :approve-doc :id app-id :doc (:id designer-doc) :path nil :collection "documents") => ok?
        (command sonja :set-doc-status :id app-id :docId (:id designer-doc) :value "disabled") => ok?
        (let [assignments (get-user-assignments sonja)]
          (->> assignments (util/find-first #(= (:id %) assignment-id1)) :status) => "canceled")
          (->> assignments (util/find-first #(= (:id %) assignment-id2)) :status) => "active"))))

(facts "Assignments search"
  (apply-remote-minimal)
  (let [id1 (create-app-id sonja :propertyId sipoo-property-id :x 404369.304 :y 6693806.957) ; Included into Nikkila area
        doc-id1 (-> (query-application sonja id1) :documents first :id)
        id2 (create-app-id ronja :propertyId sipoo-property-id)
        doc-id2 (-> (query-application ronja id2) :documents first :id)
        nikkila-area (first (filter (fn [feature]
                                      (= "Nikkil\u00e4" (get-in feature [:properties :nimi])))
                                    (get-in (:body (decode-response (upload-area sipoo "753-R"))) [:areas :features])))]

    (facts "ids exist"
      id1 => truthy
      id2 => truthy)

    (facts "text search finds approximate matches in description"
      (let [_ (create-assignment sonja ronja-id id1 [{:group "group" :id doc-id1}] "Kuvaava teksti")
            _ (create-assignment ronja sonja-id id2 [{:group "group" :id doc-id2}] "Kuvaava teksti")]
        (fact "uva eks - all"
          (let [assignments (->> (query sonja :assignments-search :searchText "uva eks" :state "all")
                                 :data :assignments)]
            (:modified (first assignments)) => pos?
            (map :description assignments) => (contains "Kuvaava teksti")))
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
      (command sonja :cancel-application :id id1 :text "testing" :lang "fi") => ok?
      (command sonja :cancel-application :id id2 :text "testing" :lang "fi") => ok?

      (-> (query sonja :assignments-search :searchText "uva eks" :state "all")
          :data :assignments) => empty?)

    (fact "get results again when cancelation is reverted"
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
      )))

(defn poll-attachment-job [apikey job]
  (fact "poll was successful"
    (poll-job apikey :bind-attachments-job (:id job) (:version job) 25) => ok?)
  (Thread/sleep 1000))

(defn query-filter-assignments [apikey filter-id]
  (let [assignments (->> (query apikey :assignments-search)
                         :data :assignments)]
    (filter #(= (:filter-id %) filter-id) assignments)))

(defn query-recipients-by-role [apikey role-id]
  (->> (query apikey :assignments-search)
       :data
       :assignments
       (map :recipient)
       (filter #(= (:roleId %) role-id))))

(defn query-recipients-by-role-with-state [apikey role-id]
  (->> (query apikey :assignments-search)
       :data
       :assignments
       (filter #(some-> % :recipient :roleId (= role-id)))
       (map #(assoc (select-keys % [:recipient])
                    :completed? (some-> % :states last :type (= "completed"))))))

(defn prepare-application-and-assignments
  "1. Creates and submits application
   2. Adds assignment to Sonja
   3. Makes Sonja general handler
   4. Adds aitapiirustus and thus implicitly also a triggered assignment.
   Returns app-id, handler-id, attachment-id map."
  []
  (let [{app-id :id
         docs   :documents} (create-and-submit-application pena
                                                           :propertyId sipoo-property-id
                                                           :operation "pientalo")
        doc-id              (-> docs first :id)]
    (create-assignment ronja sonja-id app-id [{:group "group" :id doc-id}] "Todo!")
    (let [{handler-id :id} (command ronja :upsert-application-handler
                                    :id app-id
                                    :roleId general-role-id
                                    :userId (id-for-key sonja))
          attachment-id    (upload-file-and-bind pena
                                                 app-id
                                                 {:type     {:type-group "paapiirustus"
                                                             :type-id    "aitapiirustus"}
                                                  :contents "Aitaa kaatuu!"})]
      handler-id => truthy
      attachment-id => truthy
      (fact "The application now has two assignments"
        (:assignments (query ronja :assignments-for-application :id app-id))
        => (just [(contains {:trigger     "user-created"
                             :status      "active"
                             :description "Todo!"
                             :recipient   (contains {:firstName "Sonja"})})
                  (contains {:trigger     aita-filter-id
                             :filter-id   aita-filter-id
                             :status      "active"
                             :description "Aita ja asema"
                             :recipient   (contains {:firstName "Sonja"
                                                     :handlerId handler-id
                                                     :roleId    general-role-id})})]
                 :in-any-order))
      {:app-id app-id :handler-id handler-id :attachment-id attachment-id})))

(facts "automatic assignments"

  (let [filter-id         (upsert-automatic-assignment-filter
                            sipoo "753-R"
                            {:name     "Pääpiirustuksia"
                             :criteria {:attachment-types ["paapiirustus.asemapiirros"
                                                           "paapiirustus.pohjapiirustus"
                                                           "hakija.valtakirja"
                                                           "pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma"]}
                             :target   {:handler-role-id kvv-role-id}
                             :email    {:emails  ["randy.random@example.com"]
                                        :message "Looksie!"}})
        application       (create-and-submit-application pena
                                                         :address "Assignment Alley"
                                                         :operation "kerrostalo-rivitalo"
                                                         :propertyId sipoo-property-id)
        operation         (:primaryOperation application)
        application-id    (:id application)
        handler-resp      (command sonja :upsert-application-handler
                                   :id application-id
                                   :roleId kvv-role-id
                                   :userId (id-for-key sonja))
        ;; ; paapiirustus.asemapiirros, paapiirustus.pohjapiirustus, hakija.valtakirja
        ;; pelastusviranomaiselle_esitettavat_suunnitelmat.vaestonsuojasuunnitelma
        [asemapiirros pohjapiirustus valtakirja _
         :as attachments] (:attachments application)

        resp1             (upload-file pena "dev-resources/test-attachment.txt")
        file-id-1         (get-in resp1 [:files 0 :fileId])
        resp2             (upload-file pena "dev-resources/test-attachment.txt")
        file-id-2         (get-in resp2 [:files 0 :fileId])
        resp3             (upload-file pena "dev-resources/test-attachment.txt")
        file-id-3         (get-in resp3 [:files 0 :fileId])
        resp4             (upload-file pena "dev-resources/test-attachment.txt")
        file-id-4         (get-in resp4 [:files 0 :fileId])]

    (fact "preliminary checks"
      application  =not=> nil?
      resp1        =>     ok?
      resp2        =>     ok?
      resp3        =>     ok?
      handler-resp =>     ok?)

    (fact "automatic assignment is created for the filter"
      (let [{:keys [job]}      (command pena
                                        :bind-attachments
                                        :id application-id
                                        :filedatas [
                                                    {:fileId   file-id-1
                                                     :type     (:type asemapiirros)
                                                     :group    {:groupType  "operation"
                                                                :operations [operation]
                                                                :title      "Osapuolet"}
                                                     :contents "eka"}
                                                    {:fileId   file-id-2
                                                     :type     (:type pohjapiirustus)
                                                     :group    {:groupType nil}
                                                     :contents "toka"}])
            =>                 ok?
            _                  (poll-attachment-job pena job)
            filter-assignments (query-filter-assignments sonja filter-id)]
        (count filter-assignments) => 1
        (-> filter-assignments first :description)        => "Pääpiirustuksia"
        (->> filter-assignments first :targets (map :id)) => (just [(:id asemapiirros)
                                                                    (:id pohjapiirustus)]
                                                                    :in-any-order)))
    (fact "automatic assignment has recipient when application have handler with corresponding role"
      (let [filter-assignment (first (query-filter-assignments sonja filter-id))]
        (:recipient filter-assignment) => {:id        "777777777777777777000023",
                                           :username  "sonja",
                                           :firstName "Sonja",
                                           :lastName  "Sibbo",
                                           :roleId    kvv-role-id,
                                           :role      "authority",
                                           :handlerId (:id handler-resp)}))
    (fact "automatic assignment does not have user as a recipient when application doesn't have a handler with corresponding role"
      (let [filter-assignment (first (query-filter-assignments sonja aita-filter-id))]
        (:recipient filter-assignment) => {:roleId general-role-id}))
    (fact "new attachment is added as targets to existing trigger assignments"
      (let [{:keys [job]}      (command pena
                                        :bind-attachments
                                        :id application-id
                                        :filedatas [{:fileId   file-id-3 :type (:type valtakirja)
                                                     :group    {:groupType "parties"}
                                                     :contents "hakija"}])
            =>                 ok?
            _                  (poll-attachment-job pena job)
            filter-assignments (query-filter-assignments sonja filter-id)]
        (count filter-assignments) => 1
        (->> filter-assignments first :targets (map :id)) => (->> attachments
                                                                   (take 3)
                                                                   (map :id))))
    (fact "Email has been sent to Randy"
      (check-email application-id "Assignment Alley" "Valtakirja"
                   "Asuinkerrostalon tai rivitalon rakentaminen"))

    (fact "if the automatic assignment is completed, a new one is created when a new assignment is added for the corresponding trigger"
      (let [old-filter-assignments           (->> (query sonja :assignments-search :recipient [])
                                                  :data :assignments (filter #(= (:filter-id %) filter-id)))
            old-filter-assignment-id         (-> old-filter-assignments first :id)
            complete-resp                    (complete-assignment sonja old-filter-assignment-id)
            {:keys [job]}                    (command pena
                                                      :bind-attachments
                                                      :id application-id
                                                      :filedatas [{:fileId   file-id-4 :type (:type (get attachments 3))
                                                                   :contents "suunnitelma"}])
            _                                (poll-attachment-job pena job)
            filter-assignments               (query-filter-assignments sonja filter-id)
            non-completed-filter-assignments (filter #(not ((set (map :type (:states %))) "completed"))
                                                     filter-assignments)]
        complete-resp => ok?
        (count filter-assignments) => 2
        (count non-completed-filter-assignments) => 1
        (-> non-completed-filter-assignments first :id) =not=> old-filter-assignment-id))

    (fact "New trigger for KVV handler"
      (let [filter-id (upsert-automatic-assignment-filter sipoo "753-R"
                                                          {:name     "Stunning photography"
                                                           :criteria {:attachment-types ["muut.valokuva"]}
                                                           :target   {:handler-role-id kvv-role-id}})]
        filter-id => truthy
        (fact "Add photo and trigger assignment"
          (upload-file-and-bind pena application-id {:type     {:type-group "muut" :type-id "valokuva"}
                                                     :contents "Photo"
                                                     :group    {}}))
        (fact "New automatic assignment for Sonja"
          (count (query-filter-assignments sonja filter-id)) => 1
          (let [assis (get-in (query sonja :assignments-search :recipient [])
                              [:data :assignments])]
            (count (filter #(= (:filter-id %) filter-id) assis)) => 1)
          (fact "Sonja has two assignments for KVV-handler role"
            (let [kvv-recips (query-recipients-by-role-with-state sonja kvv-role-id)
                  is-sonja   #(just {:recipient  (contains {:id sonja-id})
                                     :completed? %})]
              kvv-recips => (just [(is-sonja true) (is-sonja false) (is-sonja false)]
                                  :in-any-order)
              (fact "When handler with assignment is changed, assignments handler should be changed"
                (let [upsert-handler-resp (command sonja :upsert-application-handler
                                                   :id application-id
                                                   :roleId kvv-role-id
                                                   :userId (id-for-key ronja)
                                                   :handlerId (:id handler-resp))
                      is-ronja            #(just {:recipient  (contains {:id ronja-id})
                                                  :completed? %})
                      recipients          (query-recipients-by-role-with-state sonja kvv-role-id)]
                  upsert-handler-resp => ok?
                  recipients => (just [(is-sonja true) (is-ronja false) (is-ronja false)]
                                      :in-any-order)
                  (:recipient (second recipients)) => {:id        (id-for-key ronja)
                                                       :username  "ronja"
                                                       :firstName "Ronja"
                                                       :lastName  "Sibbo"
                                                       :role      "authority"
                                                       :roleId    kvv-role-id
                                                       :handlerId (:id handler-resp)}))))))
      (fact "When handler role is changed, the assignments with old role no longer have recipients"
        (let [upsert-handler-resp (command sonja :upsert-application-handler
                                           :id application-id
                                           :roleId general-role-id
                                           :userId (id-for-key ronja)
                                           :handlerId (:id handler-resp))
              recipients          (query-recipients-by-role sonja kvv-role-id)]
          upsert-handler-resp => ok?
          (second recipients) => {:roleId kvv-role-id})))

    (facts "New applications"
      (let [{:keys [app-id handler-id]} (prepare-application-and-assignments)
            app2                        (prepare-application-and-assignments)]
        (fact "When handler is removed from application, its trigger assignment is cleared"
          (command sonja :remove-application-handler
                   :id app-id
                   :handlerId handler-id) => ok?
          (:assignments (query ronja :assignments-for-application :id app-id))
          => (just [(contains {:trigger     "user-created"
                               :status      "active"
                               :description "Todo!"
                               :recipient   (contains {:firstName "Sonja"})})
                    (contains {:filter-id   aita-filter-id
                               :status      "active"
                               :description "Aita ja asema"
                               :recipient   {:roleId general-role-id}})]
                   :in-any-order))
        (fact "The other application assignments are unchanged"
          (:assignments (query ronja :assignments-for-application :id (:app-id app2)))
          => (just [(contains {:trigger     "user-created"
                               :status      "active"
                               :description "Todo!"
                               :recipient   (contains {:firstName "Sonja"})})
                    (contains {:trigger     aita-filter-id
                               :filter-id   aita-filter-id
                               :status      "active"
                               :description "Aita ja asema"
                               :recipient   (contains {:firstName "Sonja"
                                                       :handlerId (:handler-id app2)
                                                       :roleId    general-role-id})})]
                   :in-any-order))
        (fact "Ronja into general handler: general handler's automatic assignments are assigned to Ronja"
          (let [{handler-id :id} (command ronja :upsert-application-handler
                                          :id app-id
                                          :roleId general-role-id
                                          :userId (id-for-key ronja))]
            (:assignments (query ronja :assignments-for-application :id app-id))
            => (just [(contains {:trigger     "user-created"
                                 :status      "active"
                                 :description "Todo!"
                                 :recipient   (contains {:firstName "Sonja"})})
                      (contains {:trigger     aita-filter-id
                                 :filter-id   aita-filter-id
                                 :status      "active"
                                 :description "Aita ja asema"
                                 :recipient   (contains {:firstName "Ronja"
                                                         :handlerId handler-id
                                                         :roleId    general-role-id})})]
                     :in-any-order)))))))

(facts "Return to draft removes assignments"
  (let [{app-id1 :app-id} (prepare-application-and-assignments)
        {app-id2 :app-id} (prepare-application-and-assignments)]
    (command sonja :return-to-draft :id app-id1 :text "Bu hao!" :lang "fi") => ok?
    (fact "Pena submits the application again"
      (command pena :submit-application :id app-id1) => ok?)
    (fact "Assignments are gone"
      (query sonja :assignments-for-application :id app-id1)
      => {:ok          true
          :assignments []})
    (fact "Other application still has its assignments"
      (query sonja :assignments-for-application :id app-id2)
      => (just {:ok          true
                :assignments (just [truthy truthy])}))))
