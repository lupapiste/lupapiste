(ns lupapalvelu.ui.docgen.feature.source-value-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(use-fixtures :once test-utils/js-services-fixture)

(def initial-email "test@lupapiste.fi")
(def doc-1 {:id     "doc-1"
            :schema {:info {}
                     :body [{:name "someGroup"
                             :type "group"
                             :body [{:name    "sahkoposti"
                                     :type    "string"
                                     :subtype "email"}]}]}
            :data   {:someGroup {:0 {:sahkoposti {:source      "some system"
                                                  :sourceValue "some value"}}
                                 :1 {:sahkoposti {:source      "some system"
                                                  :sourceValue nil}}}}})

(def app (assoc test-utils/app :documents [doc-1]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest some-source-value-test
  (rf-test/run-test-sync
    (let [source-email                    "some value"
          doc-id                          (:id doc-1)
          email-path                      [:someGroup 0 :sahkoposti]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (testing "initial state after app load"
        (is (nil? (<sub [::value/current-field-value doc-id email-path])))
        (is (<sub [::source-value/has-source? doc-id email-path]))
        (is (<sub [::source-value/source-value-modified? doc-id email-path]))
        (is (= source-email (<sub [::source-value/source-value doc-id email-path])))
        (is (= source-email (<sub [::source-value/source-value-or-state doc-id email-path]))))

      (let [on-change! (docgen-common/make-on-change {:doc-id doc-id
                                                      :path   email-path})
            on-blur!   (docgen-common/make-on-blur {:app-id        app-id
                                                    :doc-id        doc-id
                                                    :path          email-path
                                                    :backend-value initial-email})]
        (testing "user sets the same email as the source value"
          (on-change! source-email)

          (is (= source-email (<sub [::value/current-field-value doc-id email-path])))
          (is (= source-email (<sub [::source-value/source-value doc-id email-path])))
          (is (not (<sub [::source-value/source-value-modified? doc-id email-path])))
          (is (= ::source-value/not-modified
                 (<sub [::source-value/source-value-or-state doc-id email-path]))))

        (testing "user moves to next field"
          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["someGroup.0.sahkoposti" source-email]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:someGroup 0 :sahkoposti]]
                                                                     :values      [source-email]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:someGroup 0 :sahkoposti]]
                                                                   :values      [source-email]}]
                          :resp    {:ok      true
                                    :results []}})

          (on-blur! source-email)

          (testing "value committed"
            (is (= source-email (<sub [::value/current-field-value doc-id email-path])))))

        (testing "user clears field"

          (on-change! "")

          (is (nil? (<sub [::value/current-field-value doc-id email-path])))
          (is (<sub [::source-value/source-value-modified? doc-id email-path]))
          (is (= source-email (<sub [::source-value/source-value doc-id email-path])))
          (is (= source-email (<sub [::source-value/source-value-or-state doc-id email-path])))

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
            (is (nil? (<sub [::value/current-field-value doc-id email-path])))
            (is (<sub [::source-value/source-value-modified? doc-id email-path]))
            (is (= source-email (<sub [::source-value/source-value doc-id email-path])))
            (is (= source-email (<sub [::source-value/source-value-or-state doc-id email-path])))))))))

(deftest nil-source-value-test
  (rf-test/run-test-sync
    (let [new-email                       "some value"
          doc-id                          (:id doc-1)
          email-path                      [:someGroup 1 :sahkoposti]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (testing "initial state after app load"
        (is (nil? (<sub [::value/current-field-value doc-id email-path])))
        (is (<sub [::source-value/has-source? doc-id email-path]))
        (is (not (<sub [::source-value/source-value-modified? doc-id email-path])))
        (is (nil? (<sub [::source-value/source-value doc-id email-path])))
        (is (= ::source-value/not-modified
               (<sub [::source-value/source-value-or-state doc-id email-path]))))

      (let [on-change! (docgen-common/make-on-change {:doc-id doc-id
                                                      :path   email-path})
            on-blur!   (docgen-common/make-on-blur {:app-id        app-id
                                                    :doc-id        doc-id
                                                    :path          email-path
                                                    :backend-value initial-email})]
        (testing "user sets a new email"
          (on-change! new-email)

          (is (= new-email (<sub [::value/current-field-value doc-id email-path])))
          (testing "source value is reported as nil and does not match the current value"
            (is (nil? (<sub [::source-value/source-value doc-id email-path])))
            (is (<sub [::source-value/source-value-modified? doc-id email-path]))
            (is (nil? (<sub [::source-value/source-value-or-state doc-id email-path])))))

        (testing "user moves to next field"
          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["someGroup.1.sahkoposti" new-email]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:someGroup 1 :sahkoposti]]
                                                                     :values      [new-email]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:someGroup 1 :sahkoposti]]
                                                                   :values      [new-email]}]
                          :resp    {:ok      true
                                    :results []}})

          (on-blur! new-email)

          (testing "value committed"
            (is (= new-email (<sub [::value/current-field-value doc-id email-path])))))

        (testing "user clears field"

          (on-change! "")

          (is (nil? (<sub [::value/current-field-value doc-id email-path])))
          (testing "the current empty string and source value nil are considered equal"
            (is (not (<sub [::source-value/source-value-modified? doc-id email-path])))
            (is (nil? (<sub [::source-value/source-value doc-id email-path])))
            (is (= ::source-value/not-modified
                   (<sub [::source-value/source-value-or-state doc-id email-path]))))

          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["someGroup.1.sahkoposti" ""]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:someGroup 1 :sahkoposti]]
                                                                     :values      [""]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:someGroup 1 :sahkoposti]]
                                                                   :values      [""]}]
                          :resp    {:ok      true
                                    :results []}})
          (on-blur! "")

          (testing "value committed"
            (is (nil? (<sub [::value/current-field-value doc-id email-path])))
            (is (not (<sub [::source-value/source-value-modified? doc-id email-path])))
            (is (nil? (<sub [::source-value/source-value doc-id email-path])))
            (is (= ::source-value/not-modified
                   (<sub [::source-value/source-value-or-state doc-id email-path])))))))))
