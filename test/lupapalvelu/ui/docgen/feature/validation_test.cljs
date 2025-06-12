(ns lupapalvelu.ui.docgen.feature.validation-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(t/use-fixtures :once test-utils/js-services-fixture)

(def doc-1 {:id               "doc-1"
            :schema           {:info {}
                               :body [{:name    "sahkoposti"
                                       :type    "string"
                                       :subtype "email"}
                                      {:name    "puhelin"
                                       :type    "string"
                                       :subtype "tel"}]}
            :validationErrors [{:path     ["sahkoposti"]
                                :element  {:locKey "sahkoposti"}
                                :document :whatever
                                :result   ["warn" "illegal-email"]}
                               {:path     ["puhelin"]
                                :element  {:locKey "puhelin"}
                                :document :whatever
                                :result   ["warn" "illegal-tel"]}]
            :data             {:sahkoposti {:value            "foo"
                                            :validationResult ["warn" "illegal-email"]}
                               :puhelin    {:value            "bar"
                                            :validationResult ["warn" "illegal-tel"]}}})

(def app (assoc test-utils/app :documents [doc-1]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest validation-test
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-1)
          email-path                      [:sahkoposti]
          tel-path                        [:puhelin]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (testing "initial state after app load"
        (testing "no updates from backend yet but data initialized"
          (is (= {doc-id {email-path [{:loc-key "sahkoposti"
                                       :level   :warn
                                       :error   "illegal-email"}]
                          tel-path   [{:loc-key "puhelin"
                                       :level   :warn
                                       :error   "illegal-tel"}]}}
                 (<sub [::validation/validation]))))
        (testing "warning reported for fields"
          (is (= [{:loc-key "sahkoposti"
                   :level   :warn
                   :error   "illegal-email"}]
                 (<sub [::validation/field-validation doc-id email-path])))
          (is (= [{:loc-key "puhelin"
                   :level   :warn
                   :error   "illegal-tel"}]
                 (<sub [::validation/field-validation doc-id tel-path])))))

      (let [on-change! (docgen-common/make-on-change {:doc-id doc-id
                                                      :path   email-path})
            on-blur!   (docgen-common/make-on-blur {:app-id        app-id
                                                    :doc-id        doc-id
                                                    :path          email-path
                                                    :backend-value nil})]
        (testing "user changes value, but it is not yet submitted to backend"
          (on-change! "test@lupapiste.fi")
          (testing "warning is still present as no action was called"
            (is (= [{:loc-key "sahkoposti"
                     :level   :warn
                     :error   "illegal-email"}]
                   (<sub [::validation/field-validation doc-id email-path])))))
        (testing "user moves to next field"
          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["sahkoposti" "test@lupapiste.fi"]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:sahkoposti]]
                                                                     :values      ["test@lupapiste.fi"]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:sahkoposti]]
                                                                   :values      ["test@lupapiste.fi"]}]
                          :resp    {:ok      true
                                    ;; Pretend email is now ok, but tel is still incorrect
                                    :results [{:path     ["puhelin"]
                                               :element  {:name    "puhelin"
                                                          :locKey  "puhelin"
                                                          :type    "string"
                                                          :subtype "tel"}
                                               :document {:id     doc-id
                                                          :name   "foo"
                                                          :locKey "foo"}
                                               :result   ["warn" "illegal-tel"]}]}})

          (on-blur! "test@lupapiste.fi")

          (testing "warning regarding telephone number is still present"
            (is (= {doc-id {tel-path [{:loc-key "puhelin"
                                       :level   :warn
                                       :error   "illegal-tel"}]}} (<sub [::validation/validation])))
            (is (= [{:loc-key "puhelin"
                     :level   :warn
                     :error   "illegal-tel"}]
                   (<sub [::validation/field-validation doc-id tel-path]))))
          (testing "email warning cleared"
            (is (= []
                   (<sub [::validation/field-validation doc-id email-path])))))))))
