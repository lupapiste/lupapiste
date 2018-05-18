(ns lupapalvelu.pate.pate-tasks-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :as core]
            [sade.util :as util]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :varasto-tms))

(defn add-condition [add-cmd fill-cmd condition]
  (let [changes      (:changes (add-cmd))
        condition-id (-> changes first first last keyword)]
    (fact "Add new condition"
      condition-id => truthy
      (when condition
        (fact "Fill the added condition"
          (fill-cmd condition-id condition) => ok?)))
    condition-id))

(defn add-verdict-condition [app-id verdict-id condition]
  (add-condition #(command sonja :edit-pate-verdict
                           :id app-id
                           :verdict-id verdict-id
                           :path [:add-condition]
                           :value true)
                 #(command sonja :edit-pate-verdict
                           :id app-id
                           :verdict-id verdict-id
                           :path [:conditions %1 :condition]
                           :value %2)
                 condition))

(facts "PATE tasks"

  (facts "Submit and approve application"
    (command pena :submit-application :id app-id) => ok?
    (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin description") => ok?
    (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                          :id app-id
                                          :template-id (-> pate-fixture/verdic-templates-setting
                                                           :templates
                                                           first
                                                           :id))
        {references :references} (query sonja :pate-verdict
                                        :id app-id
                                        :verdict-id verdict-id)]
    (facts "Fill verdict data and publish verdict"
      (fact "Set automatic calculation of other dates"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:automatic-verdict-dates] :value true) => no-errors?)
      (fact "Verdict date"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-date] :value (core/now)) => no-errors?)
      (fact "Verdict code"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-code] :value "hyvaksytty") => no-errors?)
      (fact "Add plans"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:plans]
                 :value (->> references :plans (map :id))) => no-errors?)
      ;Add conditions
      (add-verdict-condition app-id verdict-id "ehto 1")
      (add-verdict-condition app-id verdict-id "ehto 2")
      (add-verdict-condition app-id verdict-id "ehto 3")

      (fact "Publish PATE verdict"
        (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?))

    (let [application (query-application sonja app-id)
          tasks       (filter #(= (:source %) {:type "verdict" :id verdict-id})
                              (:tasks application))]
      (facts "Tasks are created into application"
        (fact "Task count"
          ;; One foreman, three reviews, three conditions and two plans.
          (count tasks) => (+ 1 3 3 2))
        (fact "Plans"
          (util/find-first #(= (:taskname %) "Suunnitelmat") tasks) => (contains {:schema-info {:name    "task-lupamaarays"
                                                                                                :order   20
                                                                                                :type    "task"
                                                                                                :version 1}
                                                                                  :taskname    "Suunnitelmat"})
          (util/find-first #(= (:taskname %) "ErityisSuunnitelmat") tasks) => (contains {:schema-info {:name    "task-lupamaarays"
                                                                                                       :order   20
                                                                                                       :type    "task"
                                                                                                       :version 1}
                                                                                         :taskname    "ErityisSuunnitelmat"}))
        (fact "Conditions"
          (util/find-first #(= (:taskname %) "ehto 1") tasks) => (contains {:schema-info {:name    "task-lupamaarays"
                                                                                          :order   20
                                                                                          :type    "task"
                                                                                          :version 1}
                                                                            :taskname    "ehto 1"})
          (util/find-first #(= (:taskname %) "ehto 2") tasks) => (contains {:schema-info {:name    "task-lupamaarays"
                                                                                          :order   20
                                                                                          :type    "task"
                                                                                          :version 1}
                                                                            :taskname    "ehto 2"})
          (util/find-first #(= (:taskname %) "ehto 3") tasks) => (contains {:schema-info {:name    "task-lupamaarays"
                                                                                          :order   20
                                                                                          :type    "task"
                                                                                          :version 1}
                                                                            :taskname    "ehto 3"}))
        (fact "Foreman"
          (util/find-first #(= (:taskname %) "Vastaava ty\u00f6njohtaja") tasks)
          => (contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                      :order   10
                                      :type    "task"
                                      :subtype "foreman"
                                      :version 1}
                        :taskname    "Vastaava ty\u00f6njohtaja"})))))
  (facts "Another verdict"
    (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                            :id app-id
                                            :template-id (-> pate-fixture/verdic-templates-setting
                                                             :templates
                                                             first
                                                             :id))
          {references :references} (query sonja :pate-verdict
                                          :id app-id
                                          :verdict-id verdict-id)]
      (facts "Fill verdict data and publish verdict"
        (fact "Set automatic calculation of other dates"
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                   :path [:automatic-verdict-dates] :value true) => no-errors?)
        (fact "Verdict date"
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                   :path [:verdict-date] :value (core/now)) => no-errors?)
        (fact "Verdict code"
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                   :path [:verdict-code] :value "ehdollinen") => no-errors?)
        (fact "Add plan"
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                   :path [:plans]
                   :value [(->> references :plans (util/find-by-key :fi "Suunnitelmat") :id)])
          => no-errors?)
                                        ;Add condition
        (add-verdict-condition app-id verdict-id "new condition")

        (fact "Publish PATE verdict"
          (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?))

      (let [application (query-application sonja app-id)
            tasks       (filter #(= (:source %) {:type "verdict" :id verdict-id})
                                (:tasks application))]

        (facts "Tasks are created into application"
          (fact "New tasks count"
            ;; One foreman, three reviews, one condition and one plan.
            (count tasks) => (+ 1 3 1 1))
          (fact "Total task count includes the old tasks as well"
            (-> application :tasks count) => (+ 9 6))
          (fact "Plans"
            (util/find-first #(= (:taskname %) "Suunnitelmat") tasks)
            => (contains {:schema-info {:name    "task-lupamaarays"
                                        :order   20
                                        :type    "task"
                                        :version 1}
                          :taskname    "Suunnitelmat"}))
          (fact "Conditions"
            (util/find-first #(= (:taskname %) "new condition") tasks)
            => (contains {:schema-info {:name    "task-lupamaarays"
                                        :order   20
                                        :type    "task"
                                        :version 1}
                          :taskname    "new condition"}))
          (fact "Foreman"
            (util/find-first #(= (:taskname %) "Vastaava ty\u00f6njohtaja") tasks)
            => (contains {:schema-info {:name    "task-vaadittu-tyonjohtaja"
                                        :order   10
                                        :type    "task"
                                        :subtype "foreman"
                                        :version 1}
                          :taskname    "Vastaava ty\u00f6njohtaja"})))))))
