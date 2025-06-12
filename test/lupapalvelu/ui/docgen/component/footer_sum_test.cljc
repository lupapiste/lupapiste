(ns lupapalvelu.ui.docgen.component.footer-sum-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ui.docgen.component.footer-sum :as footer-sum]))

(def columns {"first-column"  ["1.1" "2.12345" "3"]
              "second-column" ["10" "11" "12"]
              "third-column"  ["kg" "t" "kg"]
              "fourth-column" ["t" "t" "t"]
              "fifth-column"  ["10" "" "1"]
              "sixth-column"  ["10" "asd" "1"]})

(deftest footer-unit-test
  ;; NOTE: figuring out the unit does not depend on the schema
  (let [args {:column-data (fn [column] (get columns column))
              :subschema   {:name "some-schema"
                            :type "string"}}]
    (testing "no unit"
      (is (nil? (footer-sum/footer-unit (assoc args :footer "first-column")))))
    (testing "explicit unit"
      (is (= :t
             (footer-sum/footer-unit (assoc args :footer {:amount  "second-column"
                                                          :unitKey :t})))))
    (testing "unit given in another field"
      (testing "one of the columns has kg as unit"
        (is (= :kg
               (footer-sum/footer-unit (assoc args :footer {:amount "second-column"
                                                            :unit   "third-column"})))))
      (testing "none of the columns have kg as unit"
        (is (= :tonnia
               (footer-sum/footer-unit (assoc args :footer {:amount "second-column"
                                                            :unit   "fourth-column"}))))))))

(deftest amount-unit-test
  (let [args {:column-data (fn [column] (get columns column))
              :subschema   {:name "some-schema"
                            :type "string"}}]
    (testing "plain numbers"
      (is (= 6.22345
             (footer-sum/footer-amount (assoc args
                                              :footer "first-column"
                                              :subschema   {:name "first-column"
                                                            :type "string"})
                                       :kg)))
      (testing "invalid input"
        (is (= ::footer-sum/invalid
               (footer-sum/footer-amount (assoc args
                                                :footer "sixth-column"
                                                :subschema   {:name "sixth-column"
                                                              :type "string"})
                                         :kg)))))
    (testing "explicit unit, sum values as tons"
      (is (= 33
             (footer-sum/footer-amount (assoc args
                                              :footer {:amount  "second-column"
                                                       :unitKey :t}
                                              :subschema   {:name "second-column"
                                                            :type "string"})
                                       :t)))
      (testing "invalid input"
        (is (= ::footer-sum/invalid
               (footer-sum/footer-amount (assoc args
                                                :footer {:amount  "sixth-column"
                                                         :unitKey :t}
                                                :subschema   {:name "sixth-column"
                                                              :type "string"})
                                         :t)))))
    (testing "unit given in another field"
      (testing "convert tons to kg and calculate sum"
        ;; 10 kg + 11 t + 12 kg = 11022 kg
        (is (= 11022
               (footer-sum/footer-amount (assoc args
                                                :footer {:amount "second-column"
                                                         :unit   "third-column"}
                                                :subschema   {:name "second-column"
                                                              :type "string"})
                                         :kg))))
      (testing "none of the columns have kg as unit"
        ;; 10 t + 11 t + 12 t = 33 t
        (is (= 33
               (footer-sum/footer-amount (assoc args
                                                :footer {:amount "second-column"
                                                         :unit   "fourth-column"}
                                                :subschema   {:name "second-column"
                                                              :type "string"})
                                         :t))))
      (testing "invalid input"
        (is (= ::footer-sum/invalid
               (footer-sum/footer-amount (assoc args
                                                :footer {:amount "sixth-column"
                                                         :unit   "fourth-column"}
                                                :subschema   {:name "sixth-column"
                                                              :type "string"})
                                         :t)))))
    (testing "schema is of type calculation"
      ;; sum of all values in the column(s)
      (is (= 39.22345
             (footer-sum/footer-amount (assoc args
                                              :footer "some-calculation"
                                              :subschema   {:name    "second-column"
                                                            :type    "calculation"
                                                            :columns ["first-column"
                                                                      "second-column"]})
                                       :t)))
      (testing "invalid input"
        (is (= ::footer-sum/invalid
               (footer-sum/footer-amount (assoc args
                                                :footer "some-calculation"
                                                :subschema   {:name    "second-column"
                                                              :type    "calculation"
                                                              :columns ["first-column"
                                                                        "sixth-column"]})
                                         :t)))))
    (testing "empty values are zero"
      (is (= 11
             (footer-sum/footer-amount (assoc args
                                              :footer {:amount  "fifth-column"
                                                       :unitKey :t}
                                              :subschema   {:name "fifth-column"
                                                            :type "string"})
                                       :t))))))
