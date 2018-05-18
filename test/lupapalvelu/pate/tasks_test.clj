(ns lupapalvelu.pate.tasks-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.pate.tasks :refer :all]
            [lupapalvelu.pate.verdict-canonical-test :as verdict-test]
            [lupapalvelu.tasks :as tasks]))

(def test-pate-verdict verdict-test/verdict)

(def ts 1522223223639)

(facts "Plans"
  (fact "Create tasks from plans"
    (let [tasks (plans->tasks test-pate-verdict ts)
          validation-results (->> tasks
                                  (map (partial tasks/task-doc-validation "task-lupamaarays"))
                                  (filter seq))]
      tasks => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :order   20
                                               :version 1}
                                 :closed      nil
                                 :created     1522223223639
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Suunnitelmat"
                                 :data        {:maarays                     {:value ""}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})
                      (contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :order   20
                                               :version 1}
                                 :closed      nil
                                 :created     1522223223639
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Suunnitelmat2"
                                 :data        {:maarays                     {:value ""}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})])
      validation-results => empty?))

  (fact "Plans not included"
    (plans->tasks (assoc-in test-pate-verdict [:data :plans-included] false) ts)
    => nil)

  (fact "No plan selected"
    (plans->tasks (assoc-in test-pate-verdict [:data :plans] nil) ts) => []
    (plans->tasks (assoc-in test-pate-verdict [:data :plans] []) ts) => [])

  (fact "Only one plan selected, language is English"
    (plans->tasks (-> test-pate-verdict
                      (assoc-in [:data :language] "en")
                      (update-in [:data :plans] (partial take 1)))
                  ts)
    => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                       :type    :task
                                       :order   20
                                       :version 1}
                         :closed      nil
                         :created     1522223223639
                         :state       :requires_user_action
                         :source      {:type "verdict"
                                       :id   "1a156dd40e40adc8ee064463"}
                         :assignee    {}
                         :duedate     nil
                         :taskname    "Plans"
                         :data        {:maarays                     {:value ""}
                                       :kuvaus                      {:value ""}
                                       :vaaditutErityissuunnitelmat {:value ""}}})])))

(facts "Conditions"
  (fact "Create tasks from conditions"
    (let [tasks              (conditions->tasks test-pate-verdict ts)
          validation-results (->> tasks
                                  (map (partial tasks/task-doc-validation "task-lupamaarays"))
                                  (filter seq))]
      tasks => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :order   20
                                               :version 1}
                                 :closed      nil
                                 :created     1522223223639
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "muut lupaehdot - teksti"
                                 :data        {:maarays                     {:value ""}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})
                      (contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :order   20
                                               :version 1}
                                 :closed      nil
                                 :created     1522223223639
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "toinen teksti"
                                 :data        {:maarays                     {:value ""}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})])
      validation-results => empty?))

  (fact "Only one condition"
    (conditions->tasks (assoc-in test-pate-verdict [:data :conditions]
                                 {:foo {:condition "Hello world!"}})
                       ts)
    => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                       :type    :task
                                       :order   20
                                       :version 1}
                         :closed      nil
                         :created     1522223223639
                         :state       :requires_user_action
                         :source      {:type "verdict"
                                       :id   "1a156dd40e40adc8ee064463"}
                         :assignee    {}
                         :duedate     nil
                         :taskname    "Hello world!"
                         :data        {:maarays                     {:value ""}
                                       :kuvaus                      {:value ""}
                                       :vaaditutErityissuunnitelmat {:value ""}}})])))

(facts "Foremen"
  (fact "Creates tasks from foremen"
    (let [tasks              (foremen->tasks (update-in test-pate-verdict
                                                        [:data :foremen]
                                                        (partial take 2))
                                             ts)
          validation-results (->> tasks
                                  (map (partial tasks/task-doc-validation
                                                "task-vaadittu-tyonjohtaja"))
                                  (filter seq))]
      tasks => (just [(contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :order   10
                                               :version 1}
                                 :closed      nil
                                 :created     1522223223639
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Erityisalojen ty\u00f6njohtaja"
                                 :data        {:asiointitunnus {:value ""}
                                               :osapuolena     {:value false}}})
                      (contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :order   10
                                               :version 1}
                                 :closed      nil
                                 :created     1522223223639
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "1a156dd40e40adc8ee064463"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Ilmanvaihtoty\u00f6njohtaja"
                                 :data        {:asiointitunnus {:value ""}
                                               :osapuolena     {:value false}}})])
      validation-results => empty?))

  (fact "Foremen not included"
    (foremen->tasks (assoc-in test-pate-verdict [:data :foremen-included] false) ts)
    => nil)

  (fact "No foreman selected"
    (foremen->tasks (assoc-in test-pate-verdict [:data :foremen] nil) ts) => []
    (foremen->tasks (assoc-in test-pate-verdict [:data :foremen] []) ts) => [])

  (fact "Only one foreman selected, language is Swedish"
    (foremen->tasks (-> test-pate-verdict
                        (assoc-in [:data :language] "sv")
                        (update-in [:data :foremen] (partial drop 3)))
                    ts)
    => (just [(contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                       :type    :task
                                       :subtype :foreman
                                       :order   10
                                       :version 1}
                         :closed      nil
                         :created     1522223223639
                         :state       :requires_user_action
                         :source      {:type "verdict"
                                       :id   "1a156dd40e40adc8ee064463"}
                         :assignee    {}
                         :duedate     nil
                         :taskname    "Arbetsledare inom vatten och avlopp"
                         :data        {:asiointitunnus {:value ""}
                                       :osapuolena     {:value false}}})])))
