(ns lupapalvelu.ui.docgen.feature.documents-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ui.docgen.feature.documents :as docs]))

(deftest first-value-test
  (testing "strings (text inputs, selects..)"
    (is (nil? (docs/first-value [])))
    (is (nil? (docs/first-value [""])))
    (is (nil? (docs/first-value [nil ""])))
    (is (nil? (docs/first-value ["" nil])))
    (is (nil? (docs/first-value ["" "" ""])))
    (is (nil? (docs/first-value ["" "foo"])))
    (is (nil? (docs/first-value ["  " "asd"])))
    (is (= "foo" (docs/first-value ["foo" ""])))
    (is (= "foo" (docs/first-value [nil "foo"])))
    (is (= "  foo  " (docs/first-value ["  foo  " ""]))))

  (testing "booleans / checkboxes"
    (is (true? (docs/first-value [true])))
    (is (true? (docs/first-value [nil true])))
    (is (true? (docs/first-value [true false])))
    (is (false? (docs/first-value [nil false true])))
    (is (true? (docs/first-value [nil true false])))))
