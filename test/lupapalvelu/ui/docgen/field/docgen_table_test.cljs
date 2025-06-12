(ns lupapalvelu.ui.docgen.field.docgen-table-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.field.docgen-table :as docgen-table]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(t/use-fixtures :once test-utils/js-services-fixture)

(def doc-1 {:id     "doc-1"
            :schema {:info {}
                     :body [{:name "someGroup"
                             :type "group"
                             :body [{:name "someTable"
                                     :type "table"
                                     :body [{:name "fieldA"
                                             :type "string"}
                                            {:name "fieldB"
                                             :type "string"}
                                            {:name     "fieldC"
                                             :type     "string"
                                             :readonly true}]}]}]}
            :data   {:someGroup {:someTable {:0 {:fieldA {:value "A0"}
                                                 :fieldB {:value "B0"}
                                                 :fieldC {:value "C0"}}
                                             :3 {:fieldA {:value "A3"}
                                                 :fieldB {:value "B3"}
                                                 :fieldC {:value "C3"}}}}}})

(def doc-2 {:id     "doc-2"
            :schema {:info {}
                     :body [{:name "someGroup"
                             :type "group"
                             :body [{:name "someTable"
                                     :type "table"
                                     :body [{:name "fieldA"
                                             :type "string"}]}
                                    {:name                  "anotherTable"
                                     :type                  "table"
                                     :repeating-allow-empty true
                                     :body                  [{:name "fieldB"
                                                              :type "string"}]}]}]}
            :data   {:someGroup {:someTable    {}
                                 :anotherTable {}}}})

(def app (assoc test-utils/app :documents [doc-1
                                           doc-2]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest copy-row-test
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-1)
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (testing "let's do some local changes to one of the cells"
        (let [change! (docgen-common/make-on-change-immediate {:app-id app-id
                                                               :doc-id doc-id
                                                               :path   [:someGroup :someTable 3 :fieldB]})]
          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["someGroup.someTable.3.fieldB" "B3-changed"]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:someGroup :someTable 3 :fieldB]]
                                                                     :values      ["B3-changed"]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:someGroup :someTable 3 :fieldB]]
                                                                   :values      ["B3-changed"]}]
                          :resp    {:ok true}})

          (change! "B3-changed")))

      (testing "let's accidentally try to overwrite the last row"
        (let [next-idx           3
              original-docs      (<sub [::docs/documents])
              original-committed (<sub [::committed/committed])
              readonly-paths     (<sub [::field-schemas/readonly-paths doc-id [:someGroup :someTable]])]
          (testing "nothing should happen"
            (>evt [::docgen-common/copy-last-row {:app-id       app-id
                                                  :doc-id       doc-id
                                                  :path         [:someGroup :someTable]
                                                  :next-idx     next-idx
                                                  :ignore-paths readonly-paths}])

            (is (= original-docs (<sub [::docs/documents])))
            (is (= original-committed (<sub [::committed/committed]))))))

      (testing "properly add a new row"
        (let [next-idx       4
              readonly-paths (<sub [::field-schemas/readonly-paths doc-id [:someGroup :someTable]])]
          (expect-action {:name    :update-doc
                          :params  {:id         app-id
                                    :doc        doc-id
                                    :updates    [["someGroup.someTable.4.fieldA" "A3"]
                                                 ["someGroup.someTable.4.fieldB" "B3-changed"]]
                                    :collection :documents}
                          :success [::docgen-common/on-save-success {:app-id      app-id
                                                                     :doc-id      doc-id
                                                                     :field-paths [[:someGroup :someTable next-idx :fieldA]
                                                                                   [:someGroup :someTable next-idx :fieldB]]
                                                                     :values      ["A3" "B3-changed"]}]
                          :error   [::docgen-common/on-save-error {:app-id      app-id
                                                                   :doc-id      doc-id
                                                                   :field-paths [[:someGroup :someTable next-idx :fieldA]
                                                                                 [:someGroup :someTable next-idx :fieldB]]
                                                                   :values      ["A3" "B3-changed"]}]
                          :resp    {:ok true}})

          (>evt [::docgen-common/copy-last-row {:app-id       app-id
                                                :doc-id       doc-id
                                                :path         [:someGroup :someTable]
                                                :next-idx     next-idx
                                                :ignore-paths readonly-paths}])

          (testing "new (empty) row was created"
            (is (= {} (<sub [::docs/field-data doc-id [:someGroup :someTable (keyword (str next-idx))]]))))
          (testing "new values were committed"
            (is (= "A3" (<sub [::committed/field-committed doc-id [:someGroup :someTable next-idx :fieldA]])))
            (is (= "B3-changed" (<sub [::committed/field-committed doc-id [:someGroup :someTable next-idx :fieldB]])))))))))

(deftest copy-row-repeating-allow-empty-test
  (rf-test/run-test-sync
    (let [doc-id                          (:id doc-2)
          forbid-empty-table-path         [:someGroup :someTable]
          allow-empty-table-path          [:someGroup :anotherTable]
          {:keys [expect-action handler]} (test-utils/make-mock-command)]

      (rf/reg-fx :action/command handler)

      ;; Initialize feature(s)
      (>evt [::docgen/app-loaded {:app     app
                                  :details details}])

      (testing "initial state when empty"
        (is (= [0] (<sub [::docs/field-child-keys doc-id forbid-empty-table-path])))
        (is (nil? (<sub [::docs/field-child-keys doc-id allow-empty-table-path])))

        (is (= 1 (<sub [::docs/next-child-id doc-id forbid-empty-table-path])))
        (is (= 0 (<sub [::docs/next-child-id doc-id allow-empty-table-path]))))

      (testing "add row to both tables"
        (>evt [::docgen-table/append-row-clicked {:doc-id   doc-id
                                                  :path     forbid-empty-table-path
                                                  :next-idx 1}])
        (>evt [::docgen-table/append-row-clicked {:doc-id   doc-id
                                                  :path     allow-empty-table-path
                                                  :next-idx 0}]))

      (testing "after adding a new row to both tables"
        (is (= [0 1] (<sub [::docs/field-child-keys doc-id forbid-empty-table-path])))
        (is (= [0] (<sub [::docs/field-child-keys doc-id allow-empty-table-path])))

        (is (= 2 (<sub [::docs/next-child-id doc-id forbid-empty-table-path])))
        (is (= 1 (<sub [::docs/next-child-id doc-id allow-empty-table-path])))))))
