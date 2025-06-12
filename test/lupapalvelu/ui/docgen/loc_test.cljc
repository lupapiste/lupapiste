(ns lupapalvelu.ui.docgen.loc-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ui.docgen.loc :as loc]))

(deftest loc-key-test
  (let [doc-name "some-document"]
    (testing "i18nkey overrides"
      (is (= "some-key"
             (loc/loc-key doc-name [:path-a 1 :path-b :some-field] {:name    "some-field"
                                                                    :type    "checkbox"
                                                                    :i18nkey "some-key"}))))
    (testing "no field"
      (testing "different paths"
        (is (= "some-document"
               (loc/loc-key doc-name [])))
        (is (= "some-document.path-a"
               (loc/loc-key doc-name [:path-a])))
        (is (= "some-document.path-a.path-b"
               (loc/loc-key doc-name [:path-a :path-b])
               (loc/loc-key doc-name [:path-a 1 :path-b])
               (loc/loc-key doc-name [:path-a 1 :path-b 2])))))
    (testing "different paths"
      (is (= "some-document.some-field"
             (loc/loc-key doc-name [:some-field] {:name "some-field"
                                                  :type "checkbox"})))
      (is (= "some-document.path-a.some-field"
             (loc/loc-key doc-name [:path-a :some-field] {:name "some-field"
                                                          :type "checkbox"})))
      (is (= "some-document.path-a.path-b.some-field"
             (loc/loc-key doc-name [:path-a :path-b :some-field] {:name "some-field"
                                                                  :type "checkbox"})
             (loc/loc-key doc-name [:path-a 1 :path-b :some-field] {:name "some-field"
                                                                    :type "checkbox"})
             (loc/loc-key doc-name [:path-a 1 :path-b 2 :some-field] {:name "some-field"
                                                                      :type "checkbox"}))))
    (testing "path already contains element"
      (is (= "some-document.path-a.path-b.some-field"
             (loc/loc-key doc-name [:path-a 1 :path-b :some-field] {:name "some-field"
                                                                    :type "checkbox"})
             (loc/loc-key doc-name [:path-a 1 :path-b :some-field] {:name "some-field"
                                                                    :type "checkbox"})))
      (is (= "some-document.path-a.path-b.path-b"
             (loc/loc-key doc-name [:path-a 1 :path-b 2 :path-b]))))
    (testing "field is a group/select/table"
      ;; We (no longer) add the `_group_label` suffix automatically
      (is (= "some-document.path-a.path-b.some-field"
             (loc/loc-key doc-name [:path-a 1 :path-b :some-field] {:name "some-field"
                                                                    :type "group"})
             (loc/loc-key doc-name [:path-a 1 :path-b :some-field] {:name "some-field"
                                                                    :type "select"})
             (loc/loc-key doc-name [:path-a 1 :path-b :some-field] {:name "some-field"
                                                                    :type "table"}))))
    (testing "suffix helpers"
      (let [path   [:foo]
            schema {}]
        (testing "no i18nkey override"
          (is (= "some-document.foo._append_label"
                 (loc/loc-key-append-label doc-name path schema)))
          (is (= "some-document.foo._copy_label"
                 (loc/loc-key-copy-label doc-name path schema)))
          (is (= "some-document.foo._group_label"
                 (loc/loc-key-group-label doc-name path schema)))
          (is (= "some-document.foo.help"
                 (loc/loc-key-help-label doc-name path schema)))
          (is (= "some-document.foo"
                 (loc/loc-key-help-title doc-name path schema)))
          (is (= "some-document.foo._group_label"
                 (loc/loc-key-help-title doc-name path (assoc schema :type "select"))))
          (is (= "some-document.foo._group_label"
                 (loc/loc-key-help-title doc-name path (assoc schema :type "group")))))
        (testing "with i18nkey override"
          (let [schema (assoc schema :i18nkey "bar")]
            (is (= "bar._append_label"
                   (loc/loc-key-append-label doc-name path schema)))
            (is (= "bar._copy_label"
                   (loc/loc-key-copy-label doc-name path schema)))
            (is (= "bar"
                   (loc/loc-key-group-label doc-name path schema)))
            (is (= "bar.help"
                   (loc/loc-key-help-label doc-name path schema)))
            (is (= "bar"
                   (loc/loc-key-help-title doc-name path schema)))
            (is (= "bar"
                   (loc/loc-key-help-title doc-name path (assoc schema :type "select"))))
            (is (= "bar"
                   (loc/loc-key-help-title doc-name path (assoc schema :type "group"))))))))))
