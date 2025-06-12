(ns lupapalvelu.inspection-summary-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.inspection-summary :as summary]))

(deftest template-operation-changes-test
  (let [template-id "id-1"]
    (testing "add template to multiple operations"
      (testing "no existing templates in use"
        (let [operations-templates {}]
          (is (= {"$addToSet" {:inspection-summary.operations-templates.op1 template-id
                               :inspection-summary.operations-templates.op2 template-id}}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   ["op1" "op2"]))
              "adds template to both operations")))
      (testing "one operation already uses template"
        (let [operations-templates {:op1 [template-id]}]
          (is (= {"$addToSet" {:inspection-summary.operations-templates.op2 template-id}}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   ["op1" "op2"]))
              "adds template to other operation")))
      (testing "two operations use template, other is unknown and only refers to template"
        (let [operations-templates {:op1   [template-id]
                                    :opFoo [template-id]}]
          (is (= {"$addToSet" {:inspection-summary.operations-templates.op2 template-id}
                  "$unset"    {:inspection-summary.operations-templates.opFoo 1}}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   ["op1" "op2"]))
              "adds template to op2, removes whole opFoo as it's now empty")))
      (testing "two operations use template, other is unknown and refers to other templates too"
        (let [operations-templates {:op1   [template-id]
                                    :opFoo [template-id "id-??"]}]
          (is (= {"$addToSet" {:inspection-summary.operations-templates.op2 template-id}
                  "$pull"     {:inspection-summary.operations-templates.opFoo template-id}}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   ["op1" "op2"]))
              "adds template to op2, removes template from opFoo")))
      (testing "one operation has some other template too"
        (let [operations-templates {:op1 [template-id "id-??"]}]
          (is (= {"$addToSet" {:inspection-summary.operations-templates.op2 template-id}}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   ["op1" "op2"]))
              "adds only missing template, ignores other templates"))))
    (testing "from one operation"
      (testing "two operations refer to template"
        (let [operations-templates {:op1 [template-id]
                                    :op2 [template-id]}]
          (is (= {"$unset" {:inspection-summary.operations-templates.op2 1}}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   ["op1"]))
              "remove whole other operation"))))
    (testing "removing all templates"
      (testing "no existing templates"
        (let [operations-templates {}]
          (is (= {}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   []))
              "nothing to do, no templates in use")))
      (testing "other existing templates"
        (let [operations-templates {:op ["id-??"]}]
          (is (= {}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   []))
              "nothing to do, target template not in use")))
      (testing "only single template in use"
        (let [operations-templates {:op [template-id]}]
          (is (= {"$unset" {:inspection-summary.operations-templates.op 1}}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   []))
              "remove whole operation")))
      (testing "multiple templates in use"
        (let [operations-templates {:op [template-id "id-??"]}]
          (is (= {"$pull" {:inspection-summary.operations-templates.op template-id}}
                 (summary/build-template-operation-changes
                   operations-templates
                   template-id
                   []))
              "remove just template from operation"))))))
