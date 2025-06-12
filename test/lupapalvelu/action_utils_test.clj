(ns lupapalvelu.action-utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.action-utils :as autils])
  (:import [java.util Set]))

(set! *warn-on-reflection* true)

(deftest find-lazy-test
  (testing "no unrealized vals"
    (is (= [] (autils/find-lazy {} [])))
    (is (= [] (autils/find-lazy {:foo 1} [])))
    (is (= [] (autils/find-lazy {:foo {:bar [1 2 3]}} [])))
    (is (= [] (autils/find-lazy {:foo (doall (map identity [1 2 3]))} [])))
    ;; TIL: lazy seqs nested within clojure sets are automatically evaluated
    ;; but not plain Java sets, see below
    (is (= [] (autils/find-lazy {:foo #{(map identity [1 2 3])}} [])))
    (is (= [] (autils/find-lazy {:foo #{1 2 [3 (map identity [1 2 3])]}} []))))
  (testing "unrealized vals"
    (is (= [[:foo]] (autils/find-lazy {:foo (map identity [1 2 3])} [])))
    (is (= [[:foo]] (autils/find-lazy {:foo (delay 42)} [])))
    (is (= [[:foo :bar :baz]] (autils/find-lazy {:foo {:bar {:baz (map identity [1 2 3])}}} [])))
    (is (= [[:foo 0]] (autils/find-lazy {:foo [(map identity [1 2 3])]} [])))
    (is (= [[:foo 2]] (autils/find-lazy {:foo [1 2 (map identity [1 2 3])]} [])))
    ;; plain Java sets do not automatically realize their contents
    (is (= [[:foo 0]] (autils/find-lazy {:foo (Set/of (map identity [1 2 3]))} [])))))
