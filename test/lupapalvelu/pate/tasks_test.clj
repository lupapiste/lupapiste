(ns lupapalvelu.pate.tasks-test
  (:require [lupapalvelu.pate.tasks :refer :all]
            [lupapalvelu.pate.verdict-canonical-test :as verdict-test]
            [lupapalvelu.tasks :as tasks]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(def test-pate-verdict verdict-test/verdict)

(def ts 1522223223639)

(testable-privates lupapalvelu.pate.tasks
                   reviews->tasks conditions->tasks
                   plans->tasks foremen->tasks
                   legacy-reviews->tasks legacy-conditions->tasks
                   legacy-foremen->tasks)

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

(def legacy-verdict {:legacy? true
                     :id      "legacy-id"
                     :data    {:conditions {:id1 {:name "First condition"}
                                            :id2 {:name "Second condition"}}
                               :foremen {:id3 {:role "Vastaava ty\u00f6njohtaja"}
                                         :id4 {:role "Some random foreman"}}}})

(facts "Legacy conditions"
  (fact "Two conditions"
    (let [tasks (legacy-conditions->tasks legacy-verdict
                                          12345)]
      (fact "Valid tasks"
        (->> tasks
             (map (partial tasks/task-doc-validation "task-lupamaarays"))
             (filter seq))=> empty?)

      tasks => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :order   20
                                               :version 1}
                                :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "First condition"
                                 :data        {:maarays                     {:value ""}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})
                      (contains {:schema-info {:name    "task-lupamaarays"
                                               :type    :task
                                               :order   20
                                               :version 1}
                                 :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Second condition"
                                 :data        {:maarays                     {:value ""}
                                               :kuvaus                      {:value ""}
                                               :vaaditutErityissuunnitelmat {:value ""}}})])))
  (fact "Only one condition"
    (legacy-conditions->tasks (assoc-in legacy-verdict
                                        [:data :conditions] {:foo {:name "Hello world!"}})
                              12345)
    => (just [(contains {:schema-info {:name    "task-lupamaarays"
                                       :type    :task
                                       :order   20
                                       :version 1}
                         :closed      nil
                         :created     12345
                         :state       :requires_user_action
                         :source      {:type "verdict"
                                       :id   "legacy-id"}
                         :assignee    {}
                         :duedate     nil
                         :taskname    "Hello world!"
                         :data        {:maarays                     {:value ""}
                                       :kuvaus                      {:value ""}
                                       :vaaditutErityissuunnitelmat {:value ""}}})]))
  (fact "No conditions"
    (legacy-conditions->tasks (assoc-in legacy-verdict
                                        [:data :conditions] nil)
                              12345)
    => empty?))

(facts "Legacy foremen"
  (fact "Two foremen"
    (let [tasks (legacy-foremen->tasks legacy-verdict 12345)]
      (fact "Valid tasks"
        (->> tasks
             (map (partial tasks/task-doc-validation "task-vaadittu-tyonjohtaja"))
             (filter seq))=> empty?)
      tasks => (just [(contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :order   10
                                               :version 1}
                                 :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Vastaava ty\u00f6njohtaja"
                                 :data        {:asiointitunnus {:value ""}
                                               :osapuolena     {:value false}}})
                      (contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :order   10
                                               :version 1}
                                 :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Some random foreman"
                                 :data        {:asiointitunnus {:value ""}
                                               :osapuolena     {:value false}}})])))
  (fact "Only one foreman"
    (let [tasks (legacy-foremen->tasks (assoc-in legacy-verdict
                                                 [:data :foremen]
                                                 {:bar {:role "Primus inter pares"}})
                                       12345)]
      tasks => (just [(contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                               :type    :task
                                               :subtype :foreman
                                               :order   10
                                               :version 1}
                                 :closed      nil
                                 :created     12345
                                 :state       :requires_user_action
                                 :source      {:type "verdict"
                                               :id   "legacy-id"}
                                 :assignee    {}
                                 :duedate     nil
                                 :taskname    "Primus inter pares"
                                 :data        {:asiointitunnus {:value ""}
                                               :osapuolena     {:value false}}})])))

  (fact "No foremen"
    (legacy-foremen->tasks (assoc-in legacy-verdict
                                     [:data :foremen] nil)
                           12345)
    => empty?))
