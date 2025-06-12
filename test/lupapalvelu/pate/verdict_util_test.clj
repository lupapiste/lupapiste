(ns lupapalvelu.pate.verdict-util-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.pate.verdict-util :as vu]))

(deftest loc-item-test
  (testing "not found"
    (is (nil? (vu/loc-item {} :foo "bar")))
    (is (nil? (vu/loc-item {:references {:foo []}} :foo "bar")))
    (is (nil? (vu/loc-item {:references {:foo [{:code "one"}
                                               {:id "two"}]}}
                           :foo "bar")))
    (is (nil? (vu/loc-item {:references {:foo [{:code "one"}
                                               {:id "two"}]}}
                           :foo nil))))
  (testing "found by code"
    (is (= {:code "one"}
           (vu/loc-item {:references {:foo [{:code "one"}
                                            {:id "two"}]}}
                        :foo "one"))))
  (testing "found by id"
    (is (= {:id "two"}
           (vu/loc-item {:references {:foo [{:code "one"}
                                            {:id "two"}]}}
                        :foo "two")))
    (is (= {:id "one"}
           (vu/loc-item {:references {:foo [{:code "one"}
                                            {:id "two"}
                                            {:id "one"}]}}
                        :foo "one")))))

(deftest loc-item-text-test
  (testing "not found, fallback to value"
    (is (= "bar" (vu/loc-item-text {} :foo "bar" "fi")))
    (is (nil? (vu/loc-item-text {} :foo nil "fi")))
    (is (= "one" (vu/loc-item-text {:references {:foo [{:code "one"
                                                        :sv   "Ett"
                                                        :en   "First"}
                                                       {:id "two"}]}}
                                   :foo "one" "fi"))))
  (testing "found by code"
    (is (= "Ett"
           (vu/loc-item-text {:references {:foo [{:code "one"
                                                  :sv   "Ett"
                                                  :en   "One"}
                                                 {:id "two"}]}}
                        :foo "one" "sv"))))
  (testing "found by id"
    (is (= "Kaksi"
           (vu/loc-item-text {:references {:foo [{:code "one"}
                                                 {:id "two"
                                                  :fi "Kaksi"}]}}
                             :foo "two" "fi")))))

(deftest valid-verdict-giver-test
  (testing "viranhaltija requires both first name and last name"
    (is (true? (vu/valid-verdict-giver? "viranhaltija" {:first-name "John"
                                                        :last-name  "Doe"})))
    (is (true? (vu/valid-verdict-giver? :viranhaltija {:first-name "John"
                                                       :last-name  "Doe"})))
    (is (false? (vu/valid-verdict-giver? "viranhaltija" {:first-name "John"})))
    (is (false? (vu/valid-verdict-giver? "viranhaltija" {:last-name "Doe"})))
    (is (false? (vu/valid-verdict-giver? "viranhaltija" {:first-name ""
                                                         :last-name  "Doe"})))
    (is (false? (vu/valid-verdict-giver? "viranhaltija" {:first-name "John"
                                                         :last-name  ""})))
    (is (false? (vu/valid-verdict-giver? "viranhaltija" {:first-name ""
                                                         :last-name  ""})))
    (is (false? (vu/valid-verdict-giver? "viranhaltija" {:first-name "  "
                                                         :last-name  "  "})))
    (is (false? (vu/valid-verdict-giver? "viranhaltija" {})))
    (is (false? (vu/valid-verdict-giver? "viranhaltija" {:code "ABC"
                                                         :fi   "Finnish"}))))

  (testing "lautakunta requires fi, sv, and en"
    (is (true? (vu/valid-verdict-giver? "lautakunta" {:fi "Finnish"
                                                      :sv "Swedish"
                                                      :en "English"})))
    (is (true? (vu/valid-verdict-giver? :lautakunta {:fi "Finnish"
                                                     :sv "Swedish"
                                                     :en "English"})))
    (is (true? (vu/valid-verdict-giver? "lautakunta" {:fi   "Finnish"
                                                      :sv   "Swedish"
                                                      :en   "English"
                                                      :code "ABC"})))
    (is (false? (vu/valid-verdict-giver? "lautakunta" {:fi "Finnish"
                                                       :sv "Swedish"})))
    (is (false? (vu/valid-verdict-giver? "lautakunta" {:fi "Finnish"
                                                       :en "English"})))
    (is (false? (vu/valid-verdict-giver? "lautakunta" {:sv "Swedish"
                                                       :en "English"})))
    (is (false? (vu/valid-verdict-giver? "lautakunta" {:fi ""
                                                       :sv "Swedish"
                                                       :en "English"})))
    (is (false? (vu/valid-verdict-giver? "lautakunta" {:fi "Finnish"
                                                       :sv ""
                                                       :en "English"})))
    (is (false? (vu/valid-verdict-giver? "lautakunta" {:fi "Finnish"
                                                       :sv "Swedish"
                                                       :en ""})))
    (is (false? (vu/valid-verdict-giver? "lautakunta" {:fi "  "
                                                       :sv "Swedish"
                                                       :en "English"})))
    (is (false? (vu/valid-verdict-giver? "lautakunta" {}))))

  (testing "other giver types always pass"
    (is (true? (vu/valid-verdict-giver? nil {:fi "Finnish"})))
    (is (true? (vu/valid-verdict-giver? nil {})))
    (is (true? (vu/valid-verdict-giver? "other" {:code "ABC"})))))
