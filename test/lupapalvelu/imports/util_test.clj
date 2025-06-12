(ns lupapalvelu.imports.util-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.imports.util :as im-util]
            [monger.operators :refer :all]
            [sade.date :as date]
            [schema.test :as st])
  (:import [org.apache.poi.ss.usermodel DateUtil]))

(set! *warn-on-reflection* true)

(use-fixtures :once st/validate-schemas)

(deftest excel-date->zoned-date-time-test
  (testing "Fails as nil"
    (doseq [n [nil 1743003692219 100001 -1 "hello"]]
      (is (nil? (im-util/excel-date->zoned-date-time n)))))
  (testing "Success"
    (is (= (date/zoned-date-time "31.12.1899")
           (im-util/excel-date->zoned-date-time 0)))
    (is (= (date/zoned-date-time "10.1.2023")
           (im-util/excel-date->zoned-date-time 44936)))
    (is (= (date/zoned-date-time "10.1.2023")
           (im-util/excel-date->zoned-date-time 44936.0 false)))
    (is (= (date/zoned-date-time "5.7.2011")
           (im-util/excel-date->zoned-date-time 40729))))
  (testing "Window1904"
    (is (= (date/zoned-date-time "1.1.1904")
           (im-util/excel-date->zoned-date-time 0 true)))
    (is (= (date/zoned-date-time "11.1.2027")
           (im-util/excel-date->zoned-date-time 44936 true)))
    (is (= (date/zoned-date-time "11.1.2027")
           (im-util/excel-date->zoned-date-time 44936.0 true)))
    (is (= (date/zoned-date-time "5.7.2011")
           (im-util/excel-date->zoned-date-time 39267 true))))
  (testing "Second opinion"
    (is (= (date/zoned-date-time "5.7.2011")
           (date/zoned-date-time (DateUtil/getJavaDate 40729))))
    (is (= (date/zoned-date-time "5.7.2011")
           (date/zoned-date-time (DateUtil/getJavaDate 39267.0 true))))))

(deftest make-title-test
  (testing "Nil and blanks allowed"
    (is (= {:fi nil :sv nil}
           (im-util/make-title nil)))
    (is (= {:fi "" :sv ""}
           (im-util/make-title "  "))))
  (testing "Typical case"
    (is (= {:fi "Otsikko" :sv "Otsikko"}
           (im-util/make-title " Otsikko ")))))

(deftest make-user-ts-test
  (testing "Fail (if schema validation is used)"
    (is (thrown? Exception (im-util/make-user-ts nil)))
    (is (thrown? Exception (im-util/make-user-ts {})))
    (is (thrown? Exception (im-util/make-user-ts "hello" "world")))
    (is (thrown? Exception (im-util/make-user-ts {:user true}))))
  (testing "Success"
    (is (= {:user-id "hello" :ts 12345}
           (im-util/make-user-ts {:user "hello" :created 12345})))
    (is (= {:user-id "hello" :ts 12345}
           (im-util/make-user-ts "hello" 12345)))
    (with-redefs [date/timestamp (constantly 67890)]
      (is (= {:user-id "hello" :ts 67890}
             (im-util/make-user-ts {:user "hello"})))
      (is (= {:user-id "hello" :ts 67890}
             (im-util/make-user-ts "hello"))))))

(deftest parse-cell-test
  (testing "defaults to error"
    (is (= {:error :foobar}
           (im-util/parse-cell {} :foobar "hello")))
    (is (= {:error nil}
           (im-util/parse-cell nil nil ""))))

  (testing "text"
    (is (= {:value "hello"}
           (im-util/parse-cell {} :text "  hello  "))))

  (testing "date"
    (testing "fails"
      (is (= {:error :date} (im-util/parse-cell {} :date "bad")))
      (is (= {:error :date} (im-util/parse-cell {} :date "123bad"))))
    (testing "preview"
      (is (= {:value nil} (im-util/parse-cell {} :date "" :excel)))
      (is (= {:value (date/timestamp "1.1.1900")}
             (im-util/parse-cell {} :date "1" :excel)))
      (is (= {:value (date/timestamp "5.7.2011")}
             (im-util/parse-cell {} :date "40729" :excel)))
      (is (= {:value (date/timestamp "5.7.2011")}
             (im-util/parse-cell {} :date "5.7.2011" :excel)))
      (is (= {:value (date/timestamp "5.7.2011")}
             (im-util/parse-cell {} :date (str (date/timestamp "5.7.2011")) :excel))))
    (testing "preview, window1904"
      (is (= {:value nil}
             (im-util/parse-cell {:rowdata {:window1904 true}} :date "" :excel)))
      (is (= {:value (date/timestamp "2.1.1904")}
             (im-util/parse-cell {:rowdata {:window1904 true}} :date "1" :excel)))
      (is (= {:value (date/timestamp "6.7.2015")}
             (im-util/parse-cell {:rowdata {:window1904 true}} :date "40729" :excel)))
      (is (= {:value (date/timestamp "5.7.2011")}
             (im-util/parse-cell {:rowdata {:window1904 true}} :date "5.7.2011" :excel)))
      (is (= {:value (date/timestamp "5.7.2011")}
             (im-util/parse-cell {:rowdata {:window1904 true}}
                         :date (str (date/timestamp "5.7.2011")) :excel))))
    (testing "no preview"
      (is (= {:value nil} (im-util/parse-cell {} :date "" :excel)))
      (is (= {:value -7200000} ;; start of day
             (im-util/parse-cell {} :date "1")))
      (is (= {:value -7200000}
             (im-util/parse-cell {} :date "40729")))
      (is (= {:value (date/timestamp "5.7.2011")}
             (im-util/parse-cell {} :date "5.7.2011")))
      (is (= {:value (date/timestamp "5.7.2011")}
             (im-util/parse-cell {} :date (str (date/timestamp "5.7.2011")))))))

  (testing "number"
    (testing "fails"
      (is (= {:error :number} (im-util/parse-cell {} :number "123bad")))
      (is (= {:error :number} (im-util/parse-cell {} :number "bad123")) ))
    (testing "success"
      (is (= {:value nil} (im-util/parse-cell {} :number "")))
      (is (= {:value nil} (im-util/parse-cell {} :number "   ")))
      (is (= {:value 123.46} (im-util/parse-cell {} :number " 123,455 ")))
      (is (= {:value -0.12} (im-util/parse-cell {} :number " -0.12345 ")))
      (is (= {:value 123} (im-util/parse-cell {} :number " 123 ")))))

  (testing "application-id"
    (testing "fails"
      (is (= {:error :application-id}
             (im-util/parse-cell {} :application-id "bad"))))
    (testing "success"
      (is (= {:value "LP-123-2025-12345"}
             (im-util/parse-cell {} :application-id " LP-123-2025-12345 ")))
      (is (= {:value "LX-123-2025-12345"}
             (im-util/parse-cell {} :application-id " LX-123-2025-12345 ")))
      (is (= {:value nil} (im-util/parse-cell {} :application-id "  ")))))

  (testing "backend-id"
    (is (= {:value "foobar"}
           (im-util/parse-cell {} :backend-id " foobar ")))
    (is (= {:value nil} (im-util/parse-cell {} :backend-id "  ")))))

(deftest with-modified-test
  (testing "fail (when validation is on)"
    (is (thrown? Exception (im-util/with-modified nil nil))))
  (with-redefs [date/timestamp (constantly 67890)]
    (testing "modified"
      (is (= {$set {:modified {:ts 12345 :user-id "bob"}}}
             (im-util/with-modified  {:user {:id "bob"} :created 12345} nil)))
      (is (= {$set   {:hello    "world"
                      :modified {:ts 67890 :user-id "bob"}}
              $unset {:foo true}}
             (im-util/with-modified  {:user {:id "bob"}} {$set   {:hello "world"}
                                                          $unset {:foo true}}))))
    (testing "other field"
      (is (= {$set {:other {:ts 12345 :user-id "bob"}}}
             (im-util/with-modified  {:user {:id "bob"} :created 12345} :other nil)))
      (is (= {$set   {:hello "world"
                      :other {:ts 67890 :user-id "bob"}}
              $unset {:foo true}}
             (im-util/with-modified  {:user {:id "bob"}} :other {$set   {:hello "world"}
                                                                 $unset {:foo true}}))))))
