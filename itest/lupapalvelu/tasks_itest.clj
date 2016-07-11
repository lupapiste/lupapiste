(ns lupapalvelu.tasks-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [sade.util :refer [fn->>]]))

(apply-remote-minimal)

(defn- task-by-type [task-type task] (= (str "task-" task-type) (-> task :schema-info :name)))

(let [application (create-and-submit-application pena :propertyId sipoo-property-id)
      application-id (:id application)
      resp (command sonja :check-for-verdict :id application-id)
      application (query-application pena application-id)
      modified (:modified application)
      tasks (:tasks application)
      katselmukset (filter (partial task-by-type "katselmus") tasks)
      maaraykset (filter (partial task-by-type "lupamaarays") tasks)
      tyonjohtajat (filter (partial task-by-type "vaadittu-tyonjohtaja") tasks)]

  (fact "fixture has 2 verdicts and 9 tasks"
    resp => ok?
    (:verdictCount resp) => 2
    (:taskCount resp) => 9)

  (fact "id is set" (-> tasks first :id) => truthy)
  (fact "duedate is not set" (-> tasks first :duedate) => nil)
  (fact "source is verdict" (-> tasks first :source :type) => "verdict")
  (fact "not closed" (-> tasks first :closed) => nil)
  (fact "requires user action" (-> tasks first :state) => "requires_user_action")

  (fact "created timestamp is set" (-> tasks first :created) => truthy)

  (fact "Assignee is set"
    (-> tasks first :assignee :id) => pena-id
    (-> tasks first :assignee :firstName) => "Pena"
    (-> tasks first :assignee :lastName) => "Panaani")

    (fact "Applicant can see Tasks tab"
          (query pena :tasks-tab-visible :id application-id) => ok?)
    (fact "Authority can see Tasks tab"
          (query sonja :tasks-tab-visible :id application-id) => ok?)
    (fact "Reader authority can see Tasks tab"
          (query luukas :tasks-tab-visible :id application-id) => ok?)

  (facts "katselmukset"
    (count katselmukset) => 3
    (map :taskname katselmukset) => ["Aloituskokous" "K\u00e4ytt\u00f6\u00f6nottotarkastus" "loppukatselmus"]
    (-> katselmukset first :data :katselmuksenLaji :value) => "aloituskokous"
    (map #(get-in % [:data :vaadittuLupaehtona :value]) katselmukset) => (partial every? true?))

  (facts "maaraykset"
    (count maaraykset) => 3
    (map :taskname maaraykset) => ["Radontekninen suunnitelma" "Ilmanvaihtosuunnitelma" "Valaistussuunnitelma"]
    (map #(get-in % [:data :maarays :value]) maaraykset) => (partial every? truthy))

  (facts "tyonjohtajat"
    (count tyonjohtajat) => 3
    (map :taskname tyonjohtajat) => ["Vastaava ty\u00f6njohtaja" "Vastaava IV-ty\u00f6njohtaja" "Ty\u00f6njohtaja"]
    (map :data tyonjohtajat) => (partial not-any? empty?))

  (fact "totally 9 tasks in fixture"
    (count tasks) => 9)

  (let [resp (command sonja :check-for-verdict :id application-id)
        application (query-application pena application-id)
        tasks (:tasks application)]

    (fact "read 2 verdicts again"
      resp => ok?
      (:verdictCount resp) => 2)

    (fact "application has been modified"
      (:modified application) => (partial < modified))

    (fact "tasks were not generated again"
      (:taskCount resp) => 0
      (distinct (map :created tasks)) => [modified]

      (reduce + 0 (map #(let [schema (schemas/get-schema (:schema-info %))]
                         (model/modifications-since-approvals (:body schema) [] (:data %) {} true modified)) tasks)) => 0))

  (let [task-id (->> tasks (remove (fn->> :schema-info :subtype keyword (= :review))) first :id)
        review-id (->> tasks (filter (fn->> :schema-info :subtype keyword (= :review))) first :id)
        task (doc-persistence/by-id application :tasks task-id)
        review (doc-persistence/by-id application :tasks review-id)]

    (fact "Pena can't approve"
      (command pena :approve-task :id application-id :taskId task-id) => unauthorized?)

    (facts* "Approve the first non-review task"
      (let [_ (command sonja :approve-task :id application-id :taskId task-id) => ok?
            updated-app (query-application pena application-id)
            updated-task (doc-persistence/by-id updated-app :tasks task-id)]
        (:state task) => "requires_user_action"
        (:state updated-task) => "ok"))

    (fact "Review cannot be approved"
       (command sonja :approve-task :id application-id :taskId review-id) => (partial expected-failure? "error.invalid-task-type"))

    (facts* "Reject the first task"
      (let [_ (command sonja :reject-task :id application-id :taskId task-id) => ok?
            updated-app (query-application pena application-id)
            updated-task (doc-persistence/by-id updated-app :tasks task-id)]
        (:state updated-task) => "requires_user_action"))

    (fact "Review cannot be rejected"
      (command sonja :reject-task :id application-id :taskId review-id) => (partial expected-failure? "error.invalid-task-type")))

  (facts "create task"
    (fact "Applicant can't create tasks"
      (command pena :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus") => unauthorized?)
    (fact "Authority can create tasks"
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus" :taskSubtype "aloituskokous") => ok?
      (let [application (query-application pena application-id)
            tasks (:tasks application)
            buildings (:buildings application)
            katselmus-task (some #(when (and (= "task-katselmus" (-> % :schema-info :name))
                                             (= "do the shopping" (:taskname %)))
                                   %)
                                 tasks)
            katselmus-rakennus-data (-> katselmus-task :data :rakennus)]
        katselmus-task => truthy
        (fact "katselmuksenLaji saved to doc"
          (-> katselmus-task :data :katselmuksenLaji :value) => "aloituskokous")
        (fact "buildings are saved to task-katselmus"
          (count buildings) => (count (vals katselmus-rakennus-data)))))
    (fact "Can't create documents with create-task command"
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "uusiRakennus") => (partial expected-failure? "illegal-schema"))
    (fact "Can't create katselmus without valid katselmuksenLaji"
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus") => (partial expected-failure? "error.illegal-value:select")
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus" :taskSubtype "foofaa") => (partial expected-failure? "error.illegal-value:select")))

  (facts "review edit and done"
    (let [task-id (-> tasks second :id)]
      (fact "readonly field can't be updated"
        (command sonja :update-task :id application-id :doc task-id :updates [["katselmuksenLaji" "rakennekatselmus"]]) => (partial expected-failure? :error-trying-to-update-readonly-field))

      (facts "mark done"
        (fact "can't be done as required fieds are not filled"
          (command sonja :review-done :id application-id :taskId task-id :lang "fi") => fail?)

        (command sonja :update-task :id application-id :doc task-id :updates [["rakennus.0.rakennus.jarjestysnumero" "1"]
                                                                              ["rakennus.0.rakennus.rakennusnro" "001"]
                                                                              ["rakennus.0.rakennus.valtakunnallinenNumero" "1234567892"]
                                                                              ["rakennus.0.tila.tila" "osittainen"]
                                                                              ["rakennus.0.rakennus.kiinttun" (:propertyId application)]
                                                                              ["katselmus.tila" "osittainen"]
                                                                              ["katselmus.pitoPvm" "12.04.2016"]
                                                                              ["katselmus.pitaja" "Sonja Sibbo"]]) => ok?

        (fact "can now be marked done, required fields are filled"
          (command sonja :review-done :id application-id :taskId task-id :lang "fi") => ok?)

        (fact "notes can still be updated"
          (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.huomautukset.kuvaus" "additional notes"]]) => ok?)

        (fact "review state can no longer be updated"
          (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.tila" "osittainen"]]) => fail?)

        (let [app (query-application sonja application-id)
              tasks (:tasks app)
              expected-count (+ 9 1 1) ; fixture + prev. facts + review-done
              new-task (last tasks)
              new-id (:id new-task)]
          (fact "as the review was not final, a new task has been createad"
            (count tasks) => expected-count)
          (fact "buildings are populated to newly created task"
            (count (:buildings app)) => (-> new-task :data :rakennus vals count))

          (fact "mark the new task final"
                (command sonja :update-task :id application-id :doc new-id :updates [ ["katselmus.tila" "lopullinen"]
                                                                                 ["katselmus.pitoPvm" "12.04.2016"]
                                                                                 ["katselmus.pitaja" "Sonja Sibbo"]]) => ok?)

          (fact "mark the new task done"
            (command sonja :review-done :id application-id :taskId new-id :lang "fi") => ok?)

          (let [tasks (:tasks (query-application sonja application-id))]
            (fact "no new tasks were generated (see count above)"
              (count tasks) => expected-count)

            (fact "the original task was marked final too"
              (get-in (second tasks) [:data :katselmus :tila :value]) => "lopullinen")))))))
