(ns lupapalvelu.ui.docgen.feature.show-hide-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.flags :as flags]
            [lupapalvelu.ui.docgen.feature.show-hide :as show-hide]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]))

(t/use-fixtures :once test-utils/js-services-fixture)

(def doc
  {:id     "doc"
   :schema {:info {}
            :body []}
   :data   {:some-group {:some-value {:value 1}}
            :another-group {:another-value {:value 2}}}})

(def another-doc
  {:id     "doc-2"
   :schema {:info {:name "another-doc"}
            :body []}
   :data   {:bar {:value 2}}})

(def app (assoc test-utils/app :documents [doc
                                           another-doc]))
(def app-id test-utils/app-id)
(def details test-utils/details)

(deftest get-full-source-path-test
  (testing "relative paths"
    (is (= [:foo] (show-hide/get-full-source-path "foo" [:bar] nil)))
    (is (= [:bar :foo] (show-hide/get-full-source-path "foo" [:bar :baz] nil)))
    (is (= [:bar :foo :quuz] (show-hide/get-full-source-path "foo/quuz" [:bar :baz] nil))))
  (testing "absolute paths"
    (is (= [:foo] (show-hide/get-full-source-path "/foo" [:bar :baz] nil)))
    (is (= [:foo :quuz] (show-hide/get-full-source-path "/foo/quuz" [:bar :baz] nil))))
  (testing "path to another document"
    (is (= [:foo] (show-hide/get-full-source-path "foo" [:bar :baz] "another-doc")))
    (is (= [:foo :quuz] (show-hide/get-full-source-path "foo/quuz" [:bar :baz] "another-doc")))
    (is (= [:foo :quuz] (show-hide/get-full-source-path "/foo/quuz" [:bar :baz] "another-doc")))))

(deftest show-hide-test
  (rf-test/run-test-sync
    (>evt [::docgen/app-loaded {:app     app
                                :details details}])

    (is (contains? (<sub [::flags/schema-flags]) :rakval-226))
    (is (= "doc-2" (<sub [::docs/doc-id-by-name "another-doc"])))

    (let [args {:doc-id "doc"
                :path   [:some-group :target]
                :schema {}}]
      (is (show-hide/show-field? args)
          "empty schema is shown")

      (testing "show-when"
        (is (show-hide/show-field? (assoc args :schema {:show-when {:path   "some-value"
                                                                    :values [1]}}))
            "show when expected matches value")
        (is (show-hide/show-field? (assoc args :schema {:show-when {:path   "some-value"
                                                                    :values [1 2 3]}}))
            "show when expected contains value")
        (is (not (show-hide/show-field? (assoc args :schema {:show-when {:path   "some-value"
                                                                         :values [2]}})))
            "hide when value does not match expected")
        (is (show-hide/show-field? (assoc args :schema {:show-when {:path     "bar"
                                                                    :values   [2]
                                                                    :document "another-doc"}}))
            "show when expected matches value in another doc")
        (is (show-hide/show-field? (assoc args :schema {:show-when {:path           "some-value"
                                                                    :values         [2]
                                                                    :schema-exclude :rakval-226}}))
            "show when `schema-exclude` discards filter")

        (is (show-hide/show-field? (assoc args :schema {:show-when {:path   "/another-group/another-value"
                                                                    :values [2]}}))
            "show when expected contains value")
        (is (not (show-hide/show-field? (assoc args :schema {:show-when {:path   "/another-group/another-value"
                                                                         :values [1]}})))
            "show when expected contains value")))

    ))
