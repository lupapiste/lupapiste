(ns lupapalvelu.ui.docgen.feature.committed-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(t/use-fixtures :once test-utils/js-services-fixture)

(def initial-email "test@lupapiste.fi")
(def new-email "other@lupapiste.fi")
(def doc-1 {:id     "doc-1"
            :schema {:info {}
                     :body [{:name "someGroup"
                             :type "group"
                             :body [{:name    "sahkoposti"
                                     :type    "string"
                                     :subtype "email"}]}]}
            :data   {:someGroup {:0 {:sahkoposti {:value initial-email}}}}})

(def app (assoc test-utils/app :documents [doc-1]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest committed-test
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-1)
          email-path                      [:someGroup 0 :sahkoposti]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (testing "initial state after app load"
        (testing "no committed values yes"
          (is (= {} (<sub [::committed/committed])))
          (is (nil? (<sub [::committed/field-committed doc-id email-path])))
          (is (= initial-email
                 (<sub [::committed/backend-submitted-value doc-id email-path])))
          (is (= initial-email
                 (<sub [::value/current-field-value doc-id email-path])))))

      (let [on-change! (docgen-common/make-on-change {:doc-id doc-id
                                                      :path   email-path})
            on-blur!   (docgen-common/make-on-blur {:app-id        app-id
                                                    :doc-id        doc-id
                                                    :path          email-path
                                                    :backend-value initial-email})]
        (testing "user changes value, but it is not yet submitted to backend"
          (on-change! new-email)

          (is (= {} (<sub [::committed/committed])))
          (is (= new-email (<sub [::value/current-field-value doc-id email-path]))))

        (testing "user moves to next field"
          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["someGroup.0.sahkoposti" new-email]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:someGroup 0 :sahkoposti]]
                                                                     :values      ["other@lupapiste.fi"]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:someGroup 0 :sahkoposti]]
                                                                   :values      ["other@lupapiste.fi"]}]
                          :resp    {:ok      true
                                    :results []}})

          (on-blur! new-email)

          (testing "value committed"
            (is (= new-email (<sub [::committed/field-committed doc-id email-path])))
            (is (= new-email (<sub [::committed/backend-submitted-value doc-id email-path])))
            (is (= new-email (<sub [::value/current-field-value doc-id email-path])))))

        (testing "user clears field"

          (on-change! "")

          (is (nil? (<sub [::value/current-field-value doc-id email-path])))

          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["someGroup.0.sahkoposti" ""]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:someGroup 0 :sahkoposti]]
                                                                     :values      [""]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:someGroup 0 :sahkoposti]]
                                                                   :values      [""]}]
                          :resp    {:ok      true
                                    :results []}})
          (on-blur! "")

          (testing "value committed"
            (is (= "" (<sub [::committed/field-committed doc-id email-path])))
            (is (nil? (<sub [::committed/backend-submitted-value doc-id email-path])))
            (is (nil? (<sub [::value/current-field-value doc-id email-path])))))))))
