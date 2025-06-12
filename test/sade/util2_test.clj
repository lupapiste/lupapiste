(ns sade.util2-test
  "`sade.util` unit tests with `clojure.test`. See `sade.util-test` for Midje tests."
  (:require [clojure.test :refer :all]
            [sade.util :as util]))

(deftest kw-flatten-map-test
  (testing "No prefix"
    (is (nil? (util/kw-flatten-map nil)))
    (is (nil? (util/kw-flatten-map nil nil)))
    (is (nil? (util/kw-flatten-map "" nil)))
    (is (nil? (util/kw-flatten-map {})))
    (is (nil? (util/kw-flatten-map "" {})))
    (is (nil? (util/kw-flatten-map {:foo {:bar nil}
                                    :baz nil})))
    (is (nil? (util/kw-flatten-map "" {:foo {:bar nil}
                                       :baz nil})))
    (is (= {:one.two.three 3
            :one.four.five [5 5 5]
            :six           :seven}
           (util/kw-flatten-map {:one {:two  {:three 3}
                                       :four {:five [5 5 5]}}
                                 :six :seven})))
    (is (= {:name                       "hello"
            :dictionary.flag.toggle     {}
            :dictionary.note.text.lines 5
            :title                      :info
            :l10n.info.text.fi          "Huom!"}
           (util/kw-flatten-map {:name       "hello"
                                 :dictionary {:flag {:toggle {}}
                                              :note {:text {:lines 5}}}
                                 :title      :info
                                 :l10n       {:info.text {:fi "Huom!"}}})))

    (is (= {:foo.bar #{{:nested? true}}}
           (util/kw-flatten-map {:foo {:bar #{{:nested? true}}}}))))
  (testing "With prefix"
    (is (nil? (util/kw-flatten-map "hello" nil)))
    (is (nil? (util/kw-flatten-map {})))
    (is (nil? (util/kw-flatten-map :hello {})))
    (is (nil? (util/kw-flatten-map :hello {:foo {:bar nil}
                                           :baz nil})))
    (is (= {:hello.one.two.three 3
            :hello.one.four.five [5 5 5]
            :hello.six           :seven}
           (util/kw-flatten-map "hello" {:one {:two  {:three 3}
                                               :four {:five [5 5 5]}}
                                         :six :seven})))
    (is (= {:foo.bar.baz 10}
           (util/kw-flatten-map :foo.bar {:baz 10 :hii nil})))))

(deftest compare-colls-test
  (testing "empty coll is equal to itself"
    (is (zero? (util/compare-colls [] []))))

  (testing "single value in coll"
    (doseq [v [1 "1" -1 "asd" 1.2]]
      (testing "empty coll is less than anything"
        (is (neg? (util/compare-colls [] [v]))))
      (testing "identical single value colls are equal"
        (is (zero? (util/compare-colls [v] [v])))
        (is (zero? (util/compare-colls [v] [(str v)])))
        (is (zero? (util/compare-colls [(str v)] [(str v)]))))))

  (testing "collections"
    (doseq [coll [[1 2] [1 2 3] [4 2 1 3] [1 1 1]]]
      (testing "collection is equal to itself"
        (is (zero? (util/compare-colls coll coll))))
      (testing "incrementing any makes coll larger"
        (is (pos? (util/compare-colls
                    ;; increment last value
                    (update coll (dec (count coll)) inc)
                    coll)))
        (is (pos? (util/compare-colls
                    ;; increment first value
                    (update coll 0 inc)
                    coll))))
      (testing "shorter coll is less than if otherwise equal"
        (is (neg? (util/compare-colls (pop coll) coll)))))))

(deftest deep-merge-with-key-test
  (testing "basic deep merge behavior"
    (is (= {:a 2 :b 3} (util/deep-merge-with-key (constantly nil) {:a 1} {:a 2 :b 3})))
    (is (= {:a {:b 2 :c 3}} (util/deep-merge-with-key (constantly nil) {:a {:b 1}} {:a {:b 2 :c 3}}))))

  (testing "custom merge behavior for specific key"
    (let [f (fn [k v1 v2] (when (= k :ryhti) v1))]
      (is (= {:a 2 :ryhti {:x 1}}
             (util/deep-merge-with-key f {:a 1 :ryhti {:x 1}} {:a 2 :ryhti {:x 2}})))
      ;; Nested :ryhti
      (is (= {:a {:ryhti {:x 1} :b 2}}
             (util/deep-merge-with-key f {:a {:ryhti {:x 1} :b 1}} {:a {:ryhti {:x 2} :b 2}})))))

  (testing "custom merge can use both values"
    (let [f (fn [k v1 v2] (when (= k :count) (+ v1 v2)))]
      (is (= {:count 3 :other "new"}
             (util/deep-merge-with-key f {:count 1 :other "old"} {:count 2 :other "new"})))))

  (testing "nils are ignored"
    (is (= {:a 1} (util/deep-merge-with-key (constantly nil) {:a 1} nil)))
    (is (= {:a 1} (util/deep-merge-with-key (constantly nil) nil {:a 1}))))

  (testing "skipping keys"
    (let [f (fn [k v1 v2] (when (= k :ryhti) :sade.util/skip))]
      (is (= {:a 1} (util/deep-merge-with-key f {:a 1 :ryhti {:x 1}} {:a 1 :ryhti {:x 2}})))
      (is (= {:a {:b 1}} (util/deep-merge-with-key f {:a {:b 1 :ryhti {:x 1}}} {:a {:b 1}}))))))

(deftest ->safe-double-test
  (testing "parses numbers and strings with different decimal separators"
    (is (= 1.5 (util/->safe-double 1.5)))
    (is (= 1.5 (util/->safe-double "1.5")))
    (is (= 1.5 (util/->safe-double "1,5")))
    (is (= -2.75 (util/->safe-double "-2,75"))))

  (testing "returns default for invalid values"
    (is (= 0.0 (util/->safe-double nil)))
    (is (= 0.0 (util/->safe-double "not-a-number")))
    (is (= 4.2 (util/->safe-double nil 4.2)))
    (is (= 4.2 (util/->safe-double "NaN" 4.2)))
    (is (= 4.2 (util/->safe-double "Infinity" 4.2)))))

(deftest prefix-matches-test
  (is (util/prefix-matches? [:foo 1 :bar 2] []))
  (is (util/prefix-matches? [:foo 1 :bar 2] [:foo]))
  (is (util/prefix-matches? [:foo 1 :bar 2] [:foo 1]))
  (is (util/prefix-matches? [:foo 1 :bar 2] [:foo 1 :bar]))
  (is (util/prefix-matches? [:foo 1 :bar 2] [:foo 1 :bar 2]))
  (is (not (util/prefix-matches? [:foo 1 :bar 2] [:foo 1 :bar 2 :quuz])))
  (is (not (util/prefix-matches? [:foo 1 :bar 2] [:bar])))
  (is (not (util/prefix-matches? [:foo 1 :bar 2] [1]))))
