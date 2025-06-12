(ns lupapalvelu.ui.docgen.feature.select-options-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ui.docgen.feature.select-options :as select]
            [sade.util :as util]))

(deftest select-options-test
  (let [schema      {:name "field-name"
                     :type "select"
                     :body [{:name "a"}
                            {:name "b"}
                            {:name "c"}]}
        doc-i18nkey :document
        path        [:group-name 0 :field-name]]
    (testing "basic case"
      (is (= {"a" "document.group-name.field-name.a"
              "b" "document.group-name.field-name.b"
              "c" "document.group-name.field-name.c"}
             (select/make-options-map (util/mmap schema doc-i18nkey path)))))
    (testing "i18nkey on body"
      (let [schema {:name "field-name"
                    :type "select"
                    :body [{:name "a"}
                           {:name "b" :i18nkey "foo"}
                           {:name "c"}]}]
        (is (= {"a" "document.group-name.field-name.a"
                "b" "foo"
                "c" "document.group-name.field-name.c"}
               (select/make-options-map (util/mmap schema doc-i18nkey path))))))
    (testing "i18nkey on schema"
      (let [schema {:name    "field-name"
                    :type    "select"
                    :i18nkey "foo"
                    :body    [{:name "a"}
                              {:name "b"}
                              {:name "c"}]}]
        (is (= {"a" "foo.a"
                "b" "foo.b"
                "c" "foo.c"}
               (select/make-options-map (util/mmap schema doc-i18nkey path))))))
    (testing "schema includes other key"
      (let [schema {:name      "field-name"
                    :type      "select"
                    :i18nkey   "foo"
                    :other-key "some-other-field"
                    :body      [{:name "a"}
                                {:name "b"}
                                {:name "c"}]}]
        (is (= {"a"     "foo.a"
                "b"     "foo.b"
                "c"     "foo.c"
                "other" "select-other"}
               (select/make-options-map (util/mmap schema doc-i18nkey path))))))))
