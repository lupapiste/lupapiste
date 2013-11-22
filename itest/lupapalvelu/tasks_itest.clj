(ns lupapalvelu.tasks-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.xml.krysp.reader :as reader]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.document.model :as model]
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

  (fact "fixture has 2 verdics and 9 tasks"
    resp => ok?
    (:verdictCount resp) => 2
    (:taskCount resp) => 9)

  (fact "id is set" (-> tasks first :id) => truthy)
  (fact "duedate is not set" (-> tasks first :duedate) => nil)
  (fact "source is verdict" (-> tasks first :source :type) => "verdict")

  (fact "created timestamp is set" (-> tasks first :created) => truthy)

  (fact "Assignee is set"
    (-> tasks first :assignee :id) => pena-id
    (-> tasks first :assignee :firstName) => "Pena"
    (-> tasks first :assignee :lastName) => "Panaani")

  (facts "katselmukset"
    (count katselmukset) => 3
    (map :taskname katselmukset) => ["katselmus1" "katselmus2" "muu tarkastus"]
    (-> katselmukset first :data :katselmuksenLaji :value) => "aloituskokous"
    (map #(get-in % [:data :vaadittuLupaehtona :value]) katselmukset) => (partial every? true?))

  (facts "maaraykset"
    (count maaraykset) => 3
    (map :taskname maaraykset) => ["Maarays 1" "Maarays 2" "tee joku muukin tarkastus"]
    (map :data maaraykset) => (partial every? empty?))

  (facts "tyonjohtajat"
    (count tyonjohtajat) => 3
    (map :taskname tyonjohtajat) => ["vastaava ylijohtaja" "vastaava varajohtaja" "altavastaava johtaja"]
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
  )
