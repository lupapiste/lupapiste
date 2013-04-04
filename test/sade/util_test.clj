(ns sade.util-test
  (:use sade.util
        midje.sweet))

(facts
  (fact (dissoc-in {:a {:b \b :c \c}} [:a :b]) => {:a {:c \c}})
  (fact (dissoc-in {:a {:b \b :c \c}} [:a :x]) => {:a {:b \b :c \c}}))

(facts
  (fact (select {:a \a :b \b :c \c} [:a :c]) => [\a \c])
  (fact (select {:a \a :b \b :c \c} [:c :a]) => [\c \a])
  (fact (select {:a \a :b \b :c \c} [:x :a :y]) => [nil \a nil])
  (fact (select nil [:a :c]) => [nil nil])
  (fact (select {:a \a :b \b :c \c} nil) => nil))

(facts "deep-merge-with"
  (fact
    (deep-merge-with + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
                     {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
    => {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}))

(facts "Contains value"
  (fact (contains-value? nil nil) => false)
  (fact (contains-value? [] nil) => false)
  (fact (contains-value? nil true?) => false)
  (fact (contains-value? [] true?) => false)
  (fact (contains-value? [nil] true?) => false)
  (fact (contains-value? [false] true?) => false)
  (fact (contains-value? [true] true?) => true)
  (fact (contains-value? [true false] true?) => true)
  (fact (contains-value? [false true] true?) => true)
  (fact (contains-value? [false [false false [false [false {"false" true}]]]] true?) => true)
  (fact (contains-value? [false [false false [false [false {"true" false}]]]] true?) => false))
