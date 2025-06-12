(ns lupapalvelu.ui.docgen.path-test
  (:require [clojure.test :refer [are deftest is testing]]
            [lupapalvelu.ui.docgen.path :as path]))

(deftest stringify-path-test
  (is (= []
         (path/stringify-path [])))
  (is (= ["foo"]
         (path/stringify-path [:foo])))
  (is (= ["12"]
         (path/stringify-path [12])))
  (is (= ["foo" "12" "bar"]
         (path/stringify-path [:foo 12 :bar])))
  (is (thrown-with-msg? #?(:clj RuntimeException :cljs js/Error)
                        #"can't stringify"
                        (path/stringify-path [:foo 1.2]))))

(deftest keywordify-path-test
  (is (= []
         (path/keywordify-path [])))
  (is (= [:foo]
         (path/keywordify-path [:foo])))
  (is (= [:foo :12 :bar]
         (path/keywordify-path [:foo 12 :bar]))))

(deftest integerize-coll-test
  (is (= []
         (path/integerize-coll [])))
  (is (= [1 2]
         (path/integerize-coll ["1" "2"])))
  (is (= [1 2 3 4]
         (path/integerize-coll ["1" "2" :3 :4])))
  (is (= [1 2 3]
         (path/integerize-coll ["1" :foo "2" :3]))))

(deftest path->str-test
  (is (= "foo.12.bar"
         (path/path->str [:foo 12 :bar]))))

(deftest path->id-test
  (is (= "cljs-1234-foo-12-bar"
         (path/path->id [:foo 12 :bar] "1234"))))

(deftest path->label-id-test
  (is (= "cljs-label-1234-foo-12-bar"
         (path/path->label-id [:foo 12 :bar] "1234"))))

(deftest path->group-id-test
  (is (= "cljs-group-1234-foo-12-bar"
         (path/path->group-id [:foo 12 :bar] "1234"))))

(deftest path->append-id-test
  (is (= "cljs-1234_foo_12_bar_append"
         (path/path->append-id [:foo 12 :bar] "1234"))))

(deftest path->warning-message-id
  (is (= "cljs-warn-1234-foo-12-bar"
         (path/path->warning-message-id [:foo 12 :bar] "1234"))))

(deftest path-without-indexes-test
  (is (= [:foo :bar]
         (path/path-without-indexes [:foo 12 :bar]))))

(deftest data->paths-map-test
  (is (= {}
         (path/data->path-map {})) "empty input")
  (is (= {}
         (path/data->path-map {:foo {:bar {}}})) "no `:value`")
  (is (= {}
         (path/data->path-map {:value 1})) "root must not be a `:value`")
  (is (= {[:foo] {:value 1}}
         (path/data->path-map {:foo {:value 1}})))
  (is (= {[:foo] {:value 1}
          [:bar] {:value 2}}
         (path/data->path-map {:foo {:value 1}
                               :bar {:value 2}})))
  (is (= {[:foo :bar] {:value 1}}
         (path/data->path-map {:foo {:bar {:value 1}}})))
  (is (= {[:foo :bar]  {:value 1}
          [:foo :quuz] {:value 2}}
         (path/data->path-map {:foo {:bar  {:value 1}
                                     :quuz {:value 2}}})))
  (is (= {[:quuz]        {:value "2"}
          [:foo :1 :bar] {:value 10}
          [:foo :2 :bar] {:value 20}}
         (path/data->path-map {:foo  {:1 {:bar {:value 10}}
                                      :2 {:bar {:value 20}}}
                               :quuz {:value "2"}})))
  ;; note that the map with `:value` & `:bar` is considered as a single value
  (is (= {[:foo] {:value 2
                  :bar   {:value 1}}}
         (path/data->path-map {:foo {:bar   {:value 1}
                                     :value 2}})))
  (testing "roundtrip as a sanity check"
    (let [roundtrip (comp (fn [m]
                            (reduce (fn [acc [path v]]
                                      (assoc-in acc path v))
                                    {}
                                    m))
                          path/data->path-map)]
      (are [input] (= input (roundtrip input))
        {}
        {:foo {:value 1}}
        {:foo  {:1 {:bar {:value 10}}
                :2 {:bar {:value 20}}}
         :quuz {:value "2"}}))))
