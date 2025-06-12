(ns lupapalvelu.document.flags-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [lupapalvelu.document.flags :as flags]
            [lupapalvelu.document.schemas :as schemas]))

(deftest schema->flag-paths-test
  (testing "no schemas"
    (is (= nil
           (flags/schema->flag-paths {}))))
  (testing "schema with no flags"
    (is (= nil
           (flags/schema->flag-paths (schemas/get-schema {:name "hankkeen-kuvaus"})))))
  (testing "schema with flags within a map"
    (is (= {:include {:yht-219 [[:body 1 :body 2]
                                [:body 1 :body 3]
                                [:body 1 :body 4 :show-when]]}}
           (flags/schema->flag-paths (schemas/get-schema {:name "hakijan-yhteyshenkilo"})))))
  (testing "complex schema with in- and exclusions"
    (is (= {:include {:yht-219                  [[:body 1 :body 1 :body 1 :body 2]
                                                 [:body 1 :body 1 :body 1 :body 3]
                                                 [:body 1 :body 1 :body 1 :body 4 :show-when]]
                      :rakennusluokka           [[:body 4 :body 2]]
                      :rakval-223               [[:body 4 :body 3]]
                      :rakval-224               [[:body 4 :body 4]]
                      :rakval-226               [[:body 4 :body 5]
                                                 [:body 4 :body 6]
                                                 [:body 4 :body 7]
                                                 [:body 6 :body 7 :body 1]
                                                 [:body 6 :body 7 :body 2]
                                                 [:body 6 :body 9]
                                                 [:body 6 :body 10]]
                      :ryhti-data-2026          [[:body 6 :body 4]
                                                 [:body 6 :body 5]]
                      :building-product-catalog [[:body 5] [:body 5 :body 1]]}
            :exclude {:ryhti-data-2026 [[:body 6 :body 6]]
                      :rakval-226      [[:body 4 :body 3]
                                        [:body 4 :body 4]]}}
           (flags/schema->flag-paths (schemas/get-schema {:name "uusiRakennus"}))))))
(run-tests)
