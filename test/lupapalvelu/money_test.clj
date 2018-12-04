(ns lupapalvelu.money-test
  (:require [lupapalvelu.money :refer [sum-by discounted-value sum-with-discounts]]
            [clojurewerkz.money.amounts :as ma]
            [clojurewerkz.money.currencies :refer [EUR USD]]
            [midje.sweet :refer :all]))

(facts "sum-by"
       (fact "Sum of items {:value 10} and {:value 15} and {:value 20} is 45€"
             (let [data [{:value 10} {:value 15} {:value 20}]]
               (sum-by :value data) => {:major 45
                                        :minor 4500
                                        :text "EUR45.00"
                                        :currency "EUR"}))
       (fact "Sum of items {:value 10} and {:value 15} and {:value 20} is 45USD with curency USD"
             (let [data [{:value 10} {:value 15} {:value 20}]]
               (sum-by :value data {:currency USD}) => {:major 45
                                        :minor 4500
                                        :text "USD45.00"
                                        :currency "USD"}))
       (fact "Sum of items {:value 10.5} and {:value 40.32} and {:value 11.21} is 62.03€"
             (let [data [{:value 10.5} {:value 40.32} {:value 11.21}]]
               (sum-by :value data) => {:major 62
                                        :minor 6203
                                        :text "EUR62.03"
                                        :currency "EUR"}))
       (fact "Sums items of {:value Money(10)} and {:value Money(10.52)} is 20.52€"
             (let [data [{:value (ma/amount-of EUR 10)} {:value (ma/amount-of EUR 10.52)}]]
               (sum-by :value data) => {:major 20
                                        :minor 2052
                                        :text "EUR20.52"
                                        :currency "EUR"}))
       (fact "Throws assertion error, when currency is string"
             (let [data [{:value (ma/amount-of EUR 10)} {:value (ma/amount-of EUR 10.52)}]]
               (sum-by :value data {:currency "EUR"}) => (throws AssertionError))))

(facts "discounted-value-for-percentage"
       (fact "Discounted value of 10% for 10 is 9"
             (let [result (discounted-value 10 10)]
               (ma/major-of result) => 9
               (ma/minor-of (discounted-value 10 10)) => 900))
       (fact "Discounted value of 40% for 23.43 is 14.06"
             (let [result (discounted-value 23.43 40)]
               (ma/major-of result) => 14
               (ma/minor-of result) => 1406))
       (fact "Discounted value of 40% for 23.42 is 14.05"
             (let [result (discounted-value 23.42 40)]
               (ma/major-of result) => 14
               (ma/minor-of result) => 1405)))

(facts "discounted-value-for-decimal"
       (fact "Discounted value of 0.9 for 0.10 is 9"
             (let [result (discounted-value 10 0.90 {:is-decimal? true})]
               (ma/major-of result) => 9
               (ma/minor-of result) => 900))
       (fact "Discounted value of 0.60 for 23.43 is 14.06"
             (let [result (discounted-value 23.43 0.60 {:is-decimal? true})]
               (ma/major-of result) => 14
               (ma/minor-of result) => 1406))
       (fact "Discounted value of 0.60 for 23.42 is 14.05"
             (let [result (discounted-value 23.42 0.60 {:is-decimal? true})]
               (ma/major-of result) => 14
               (ma/minor-of result) => 1405)))

(facts "sum-with-discounts"
       (fact "Sum of values {:value 10 :discount 10} and {:value 43.32 :discount 54} is 28.93"
             (let [data [{:value 10 :discount 10} {:value 43.32 :discount 54}]]
               (sum-with-discounts :value :discount data) => {:major 28
                                                              :minor 2893
                                                              :text "EUR28.93"
                                                              :currency "EUR"}))
       (fact "Sum of values {:value 10 :discount 100} and {:value 43.32 :discount 100} is 0"
             (let [data [{:value 10 :discount 100} {:value 43.32 :discount 100}]]
               (sum-with-discounts :value :discount data) => {:major 0
                                                              :minor 0
                                                              :text "EUR0.00"
                                                              :currency "EUR"}))
       (fact "Sum of values {:value 10 :discount 101} and {:value 43.32 :discount 100} is throws assertion error"
             (let [data [{:value 10 :discount 101} {:value 43.32 :discount 100}]]
               (sum-with-discounts :value :discount data) => (throws AssertionError)))

       (fact "Sum of values {:value 10 :discount -1} and {:value 43.32 :discount 100} is throws assertion error"
             (let [data [{:value 10 :discount -1} {:value 43.32 :discount 100}]]
               (sum-with-discounts :value :discount data) => (throws AssertionError)))

       (fact "Sum of values {:value 10 :discount 10} and {:value 43.32 :discount 54} is 28.93 USD when currency is USD"
             (let [data [{:value 10 :discount 10} {:value 43.32 :discount 54}]]
               (sum-with-discounts :value :discount data :currency USD) => {:major 28
                                                              :minor 2893
                                                              :text "USD28.93"
                                                              :currency "USD"}))
       (fact "Sum of values {:value 10 :discount 0.10} and {:value 43.32 :discount 0.46} is 28.93"
             (let [data [{:value 10 :discount 0.90} {:value 43.32 :discount 0.46}]]
               (sum-with-discounts :value :discount data :discount-is-decimal true) => {:major 28
                                                              :minor 2893
                                                              :text "EUR28.93"
                                                                                        :currency "EUR"}))
       (fact "Sum of values {:value 10 :discount 0} and {:value 43.32 :discount 0} is 0"
             (let [data [{:value 10 :discount 0} {:value 43.32 :discount 0}]]
               (sum-with-discounts :value :discount data :discount-is-decimal true) => {:major 0
                                                                                        :minor 0
                                                                                        :text "EUR0.00"
                                                                                        :currency "EUR"}))

       (fact "Sum of values {:value 10 :discount 1.1} and {:value 43.32 :discount 0.10} is throws assetion error"
             (let [data [{:value 10 :discount 1.1} {:value 43.32 :discount 0.10}]]
               (sum-with-discounts :value :discount data :discount-is-decimal true) => (throws AssertionError)))

       (fact "Sum of values {:value 10 :discount -0.1} and {:value 43.32 :discount 0.10} is throws assetion error"
             (let [data [{:value 10 :discount -0.1} {:value 43.32 :discount 0.10}]]
               (sum-with-discounts :value :discount data :discount-is-decimal true) => (throws AssertionError)))

       (fact "Sum of values {:value 10 :discount 0.10} and {:value 43.32 :discount 0.46} is 28.93 USD when currency is USD"
             (let [data [{:value 10 :discount 0.90} {:value 43.32 :discount 0.46}]]
               (sum-with-discounts :value :discount data :discount-is-decimal true :currency USD) => {:major 28
                                                              :minor 2893
                                                              :text "USD28.93"
                                                              :currency "USD"})))
