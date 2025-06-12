(ns sade.schema-errors-test
  (:require [clojure.test :refer [deftest is testing]]
            [sade.schema-errors :as se]
            [schema.core :as sc]))

(deftest validation-error-test
  (testing "scalar"
    (is (= [{:field []
             :error "not-valid"
             :value 123}]
           (-> (sc/check sc/Str 123)
               se/error-or-errors->data))))
  (testing "coll"
    (is (= [{:field [1]
             :error "not-valid"
             :value 123}]
           (-> (sc/check [sc/Str] ["x" 123 "y"])
               se/error-or-errors->data))))
  (testing "map"
    (is (= [{:field ["a"]
             :error "not-valid"
             :value 123}]
           (-> (sc/check {:a sc/Str} {:a 123})
               se/error-or-errors->data))))
  (testing "complex"
    (is (= [{:field ["a"]
             :error "not-valid"
             :value 123}
            {:field ["b" 2]
             :error "not-valid"
             :value 456}
            {:field ["c" "d" 0]
             :error "not-valid"
             :value 89}
            {:field ["e" 0 "f"]
             :error "not-valid"
             :value 1}]
           (-> (sc/check {:a sc/Str
                          :b [sc/Str]
                          :c {:d [sc/Str]}
                          :e [{:f sc/Str}]}
                         {:a 123
                          :b ["x" "y" 456]
                          :c {:d [89]}
                          :e [{:f 1}]})
               se/error-or-errors->data))))
  (testing "enum"
    (is (= [{:field    ["foo"]
             :error    "not-valid"
             :value    4
             :expected [1 2 3]}]
           (-> (sc/check {:foo (sc/enum 1 2 3)}
                         {:foo 4})
               se/error-or-errors->data))))
  (testing "constrained"
    (is (= [{:field    ["foo"]
             :error    "not-valid"
             :value    "a"
             :expected "more than one char"}]
           (-> (sc/check {:foo (sc/constrained sc/Str (fn [s] (< 1 (count s))) "more than one char")}
                         {:foo "a"})
               se/error-or-errors->data)))))

(deftest schema-helpers-test
  (testing "pattern"
    (let [schema {:foo (se/pattern-str #"\d{1}")}]
      (is (nil? (sc/check schema {:foo "7"})))
      (is (=  [{:error    "not-valid"
                :expected [:pattern "\\d{1}"]
                :field    ["foo"]
                :value    "12"}]
              (-> (sc/check schema {:foo "12"})
                  se/error-or-errors->data)))))
  (testing "fixed-str"
    (let [schema {:foo (se/fixed-str 3)}]
      (is (nil? (sc/check schema {:foo "123"})))
      (is (=  [{:error    "not-valid"
                :expected [:fixed 3]
                :field    ["foo"]
                :value    "12"}]
              (-> (sc/check schema {:foo "12"})
                  se/error-or-errors->data)))))
  (testing "min-max-str"
    (let [schema {:foo (se/min-max-str 1 3)}]
      (is (nil? (sc/check schema {:foo "1"})))
      (is (nil? (sc/check schema {:foo "12"})))
      (is (nil? (sc/check schema {:foo "123"})))
      (is (=  [{:error    "not-valid"
                :expected [:min-max-str 1 3]
                :field    ["foo"]
                :value    ""}]
              (-> (sc/check schema {:foo ""})
                  se/error-or-errors->data)))
      (is (=  [{:error    "not-valid"
                :expected [:min-max-str 1 3]
                :field    ["foo"]
                :value    "1234"}]
              (-> (sc/check schema {:foo "1234"})
                  se/error-or-errors->data)))))
  (testing "max-str"
    (let [schema {:foo (se/max-str 3)}]
      (is (nil? (sc/check schema {:foo ""})))
      (is (nil? (sc/check schema {:foo "1"})))
      (is (nil? (sc/check schema {:foo "12"})))
      (is (nil? (sc/check schema {:foo "123"})))
      (is (=  [{:error    "not-valid"
                :expected [:max-str 3]
                :field    ["foo"]
                :value    "1234"}]
              (-> (sc/check schema {:foo "1234"})
                  se/error-or-errors->data)))))
  (testing "not-empty-seq"
    (let [schema {:foo (se/not-empty-seq {:bar sc/Str})}]
      (is (nil? (sc/check schema {:foo [{:bar ""}]})))
      (is (nil? (sc/check schema {:foo [{:bar ""} {:bar "123"}]})))
      (is (=  [{:error    "not-valid"
                :expected [:not-empty]
                :field    ["foo"]
                :value    nil}]
              (-> (sc/check schema {:foo nil})
                  se/error-or-errors->data)))
      (is (=  [{:error    "not-valid"
                :expected [:not-empty]
                :field    ["foo"]
                :value    []}]
              (-> (sc/check schema {:foo []})
                  se/error-or-errors->data)))
      (testing "compared with `sc/one`"
        ;; Indicates problems with the nested values
        (is (=  [{:field ["foo" 0 "bar"]
                  :error "missing-required-key"}
                 {:field ["foo" 0 "quuz"]
                  :error "disallowed-key"}]
                (-> (sc/check schema {:foo [{:quuz "123"}]})
                    se/error-or-errors->data)))
        ;; Indicates problems with the sequence
        (is (=  [{:error    "not-valid"
                  :field    ["foo" 0]
                  :expected :foos}]
                (-> (sc/check {:foo [(sc/one {:bar sc/Str} :foos)]} {:foo [{:quuz "123"}]})
                    se/error-or-errors->data))))))
  (testing "has-one-of"
    (let [schema {:foo (se/has-one-of {sc/Keyword sc/Int} [:bar :quuz])}]
      (is (nil? (sc/check schema {:foo {:bar 1}})))
      (is (nil? (sc/check schema {:foo {:quuz 2}})))
      (is (nil? (sc/check schema {:foo {:bar 1 :quuz 2}})))
      (is (nil? (sc/check schema {:foo {:bar 1 :quuz 2 :kek 3}})))
      (is (=  [{:error    "not-valid"
                :expected [:required-keys #{:bar :quuz}]
                :field    ["foo"]
                :value    {:kek 3}}]
              (-> (sc/check schema {:foo {:kek 3}})
                  se/error-or-errors->data)))
      (is (=  [{:field ["foo" "kek"]
                :error "not-valid"
                :value "1"}]
              (-> (sc/check schema {:foo {:kek "1"}})
                  se/error-or-errors->data))))))
