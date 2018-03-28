(ns lupapalvelu.pate.tasks-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.pate.tasks :refer :all]
            [lupapalvelu.pate.verdict-canonical-test :as verdict-test]))

(def test-pate-verdict verdict-test/verdict)

(def ts 1522223223639)

(fact "Creates task from plan"
  (plan->task test-pate-verdict ts "5a156ddf0e40adc8ee064464")
    => (contains {:schema-info {:name    "task-lupamaarays"
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
                                :vaaditutErityissuunnitelmat {:value ""}}}))

(fact "Creates task from condition"
  (condition->task test-pate-verdict ts [:id1 {:condition "muut lupaehdot - teksti"}])
    => (contains {:schema-info {:name    "task-lupamaarays"
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
                                :vaaditutErityissuunnitelmat {:value ""}}}))