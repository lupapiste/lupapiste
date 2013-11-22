(ns lupapalvelu.tasks-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.xml.krysp.reader :as reader]
            [lupapalvelu.tasks :as tasks]))

(defn- task-by-type [task-type task] (= (str "task-" task-type) (-> task :schema-info :name)))

(facts "tasks"
  (let [xml (sade.xml/parse (slurp "resources/krysp/sample/verdict.xml"))
        cases (reader/->verdicts xml)
        tasks (tasks/verdicts->tasks {:verdicts cases :auth [{:id "pena's mongo id" :firstName "Pena" :lastName "Panaani" :role "owner"}]} 1312341259733)
        katselmukset (filter (partial task-by-type "katselmus") tasks)
        maaraykset (filter (partial task-by-type "lupamaarays") tasks)
        tyonjohtajat (filter (partial task-by-type "vaadittu-tyonjohtaja") tasks)]

    (-> tasks first :id) => truthy
    (-> tasks first :duedate) => nil
    (fact "created timestamp is set" (-> tasks first :created) => 1312341259733)

    (fact "Assignee is set"
      (-> tasks first :assignee :id) => "pena's mongo id"
      (-> tasks first :assignee :firstName) => "Pena"
      (-> tasks first :assignee :lastName) => "Panaani")

    ;(clojure.pprint/pprint tyonjohtajat)

    (facts "katselmukset"
      (count katselmukset) => 3
      (map :taskname katselmukset) => ["katselmus1" "katselmus2" "muu tarkastus"]
      )

    (facts "maaraykset"
      (count maaraykset) => 3
      (map :taskname maaraykset) => ["Maarays 1" "Maarays 2" "tee joku muukin tarkastus"]
      (map :data maaraykset) => (partial every? empty?)
      )

    (facts "tyonjohtajat"
      (count tyonjohtajat) => 3
      (map :taskname tyonjohtajat) => ["vastaava ylijohtaja" "vastaava varajohtaja" "altavastaava johtaja"]
      (map :data tyonjohtajat) => (partial every? empty?)
      )
    ))