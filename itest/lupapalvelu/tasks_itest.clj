(ns lupapalvelu.tasks-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.xml.krysp.reader :as reader]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]))

(defn- task-by-type [task-type task] (= (str "task-" task-type) (-> task :schema-info :name)))

(let [application (create-and-submit-application pena :municilapity sonja-muni)
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
    (map :data tyonjohtajat) => (partial every? empty?))

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

  (let [task-id (-> tasks first :id)
        task (doc-persistence/by-id application :tasks task-id)]

    (fact "Pena can't approve"
      (command pena :approve-task :id application-id :taskId task-id) => unauthorized?)

    (facts* "Approve the first task"
      (let [_ (command sonja :approve-task :id application-id :taskId task-id) => ok?
            updated-app (query-application pena application-id)
            updated-task (doc-persistence/by-id updated-app :tasks task-id)]
        (:state task) => "requires_user_action"
        (:state updated-task) => "ok"))

    (facts* "Reject the first task"
      (let [_ (command sonja :reject-task :id application-id :taskId task-id) => ok?
            updated-app (query-application pena application-id)
            updated-task (doc-persistence/by-id updated-app :tasks task-id)]
        (:state updated-task) => "requires_user_action")))

  (facts "create task"
    (fact "Applicant can't create tasks"
      (command pena :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus") => unauthorized?)
    (fact "Authority can create tasks"
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus") => ok?
      (some #(and (= "do the shopping" (:taskname %)) (= "task-katselmus") (-> % :schema-info :name)) (:tasks (query-application pena application-id))) => truthy)
    (fact "Can't create documents with create-task command"
      (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "uusiRakennus") => (partial expected-failure? "illegal-schema")))

  )
