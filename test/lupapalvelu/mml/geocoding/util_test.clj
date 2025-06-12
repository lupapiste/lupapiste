(ns lupapalvelu.mml.geocoding.util-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.mml.geocoding.util :as util]))

(deftest query-test
  (is (= "katu*" (util/query->search-text {:street "katu"})))
  (is (= "katu* 7" (util/query->search-text {:street "katu"
                                             :number "7"})))
  (is (= "katu*, kaupunki" (util/query->search-text {:street "katu"
                                                     :city   "kaupunki"})))
  (is (= "katu* 7, kaupunki" (util/query->search-text {:street "katu"
                                                       :number "7"
                                                       :city   "kaupunki"})))
  (is (= "katu* 7, kaupunki, 12345"
         (util/query->search-text {:street      "katu"
                                   :number      "7"
                                   :postal-code "12345"
                                   :city        "kaupunki"}))))

(deftest address-sort-test
  (let [street (fn [street-name number] {:street street-name :number number})
        sorted [(street "A" "")
                (street "AA" "")
                (street "AA" "1")
                (street "AA" "1A")
                (street "AA" "1A1")
                (street "AA" "1 A 2")
                (street "AA" "1A3")
                (street "AA" "1B")
                (street "AA" "2")
                (street "AA" "A")
                (street "AAA" "")
                (street "AB" "")
                (street "AB" "1")]]
    (is (= sorted (util/sort-addresses (shuffle sorted))))))
