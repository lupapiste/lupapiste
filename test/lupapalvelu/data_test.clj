(ns lupapalvelu.data-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.data :as data]))

(def root ::data/root)

(deftest pathify-test
  (testing "Nil"
    (doseq [args [[nil] [nil nil] [{} [[[nil]]]] #{nil}
                  [true false true]]]
      (is (nil? (apply data/pathify args)))))
  (testing "Path"
    (is (= [:one] (data/pathify :one)))
    (is (= [:one] (data/pathify "one")))
    (is (= [:one] (data/pathify [[[[[[[[["one"]]]]]]]]])))
    (is (= [:one :2 :three]
           (data/pathify nil [] [[["one"] 2] [[:three #{} nil]]])))))

(deftest root?-test
  (testing "false"
    (doseq [m [{} nil {:root 1} {::root 2}]]
      (is (false? (data/root? m)))))
  (testing "true"
    (is (true? (data/root? {root 1})))))

(deftest datapath-test
  (testing "Fail"
    (is (thrown? AssertionError (data/datapath "oops"))))
  (testing "Path"
    (is (= [root ::oops]  (data/datapath ::oops)))
    (is (= [root ::oops :one :two]
           (data/datapath ::oops "one" [[[nil :two false]]])))))

(deftest set-d-test
  (testing "set-d"
    (is (= {root {::data "hello"}}
           (data/set-d ::data {} "hello")))
    (is (= {root {::data {:foo "bar"}}}
           (data/set-d ::data {} "foo" "bar")))
    (is (= {root {::data {:foo {:bar 12}}}}
           (data/set-d ::data {} "foo" "bar" nil {} false 12)))
    (is (= {root {::data {:foo {:bar {:12 nil}}}}}
           (data/set-d ::data {} "foo" "bar" nil {} false 12 nil)))))

(deftest get-d-test
  (testing "get-d"
    (is (= "hello" (data/get-d ::data {root {::data "hello"}})))
    (is (nil? (data/get-d ::data {root {::data "hello"}} :hello)))
    (is (= "bar"
           (data/get-d ::data {root {::data {:foo "bar"}}}  "foo")))
    (is (= 12
           (data/get-d ::data {root {::data {:foo {:bar 12}}}}
                       "foo" "bar" nil {} false)))
    (is (nil? (data/get-d ::data {root {::data {:foo {:bar {:12 nil}}}}}
                          "foo" "bar" nil {} false 12)))))

(deftest unset-d-test
  (testing "nothing"
    (is (= {root {::data {:foo {:bar {:one 1}}}}}
           (data/unset-d ::data {root {::data {:foo {:bar {:one 1}}}}}
                         "foo" :bar "two"))))
  (testing "leaf"
    (is (= {root {::data {:foo {:bar {:one 1}}}}}
           (data/unset-d ::data {root {::data {:foo {:bar {:one 1 :two 2}}}}}
                         "foo" :bar "two"))))
  (testing "branch"
    (is (= {root {::data {:foo {:bar {:one 1}}}}}
           (data/unset-d ::data {root {::data {:foo {:bar {:one 1 :two 2}}}}}
                         "foo" :bar "two")))
    (is (= {root {::data {:three 3}}}
           (data/unset-d ::data {root {::data {:foo   {:bar {:one 1}}
                                               :three 3}}}
                         "foo" :bar "one")))
    (is (= {}
           (data/unset-d ::data {root {::data {:foo   {:bar {:one 1}}
                                               :three 3}}})))
    (is (= {}
           (data/unset-d ::data {root {::data {:foo {:bar {:one 1}}}}}
                         "foo" :bar "one")))))

(deftest ctx-d-test
  (testing "rooted"
    (is (= "bar"
           (data/ctx-d ::data {root {::data {:foo "bar"}}}  "foo")))
    (is (= 12
           (data/ctx-d ::data {root {::data {:foo {:bar 12}}}}
                       "foo" "bar" nil {} false))))
  (testing "regular"
    (is (= "bar"
           (data/ctx-d ::data {:foo "bar"}  "foo")))
    (is (= 12
           (data/ctx-d ::data {:foo {:bar 12}}
                       "foo" "bar" nil {} false)))))

(deftest clear-test
  (is (= {:foo 1} (data/clear {:foo 1})))
  (is (= {:foo 1} (data/clear {:foo 1 root 2})))
  (is (= {} (data/clear {root {::data {:foo {:bar 12}}}}))))
