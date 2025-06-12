(ns lupapalvelu.db-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.db :as db]
            [monger.operators :refer :all]
            [sade.date :as date]))

(def now-ts (date/timestamp "7.2.2024"))

(deftest with-sheriff-note-test
  (with-redefs [date/now (constantly now-ts)]

    (testing "throws on nil or blank note"
      (is (thrown? AssertionError (db/with-sheriff-note nil)))
      (is (thrown? AssertionError (db/with-sheriff-note " "))))

    (testing "basic note"
      (is (= {$push {:_sheriff-notes {:note    "hello"
                                      :created now-ts}}}
             (db/with-sheriff-note "hello"))))

    (testing "note with existing $push"
      (is (= {$push {:buildings      {:localId "yeah"}
                     :_sheriff-notes {:note    "hello"
                                      :created now-ts}}}
             (db/with-sheriff-note "hello" {$push {:buildings {:localId "yeah"}}}))))

    (testing "note with custom timestamp and multiple operators"
      (is (= {$set  {:address "Somewhere"}
              $push {:buildings      {:name "Hut"}
                     :_sheriff-notes {:note    "hello"
                                      :created (date/timestamp "1.1.2024")}}}
             (db/with-sheriff-note "hello"
               (date/zoned-date-time "1.1.2024")
               {$set  {:address "Somewhere"}
                $push {:buildings {:name "Hut"}}}))))

    (testing "note with extra key-values"
      (is (= {$set  {:address "Somewhere"}
              $push {:buildings      {:name "Hut"}
                     :_sheriff-notes {:note    "world"
                                      :created (date/timestamp "3.10.2024")
                                      :one     1 :two 2
                                      :extra   "Other information"}}}
             (db/with-sheriff-note " world " "3.10.2024"
               {$set  {:address "Somewhere"}
                $push {:buildings {:name "Hut"}}}
               :one 1 :two 2 :extra " Other information  "))))))

(deftest with-reporting-override-test
  (with-redefs [date/now (constantly now-ts)]

    (testing "basic override"
      (is (= {$set {:reportingDbOverrideTs now-ts}}
             (db/with-reporting-override))))

    (testing "override with existing update map"
      (is (= {$set   {:address               "Somewhere else"
                      :reportingDbOverrideTs now-ts
                      :foo                   "bar"}
              $unset {:hii true}}
             (db/with-reporting-override {$set   {:address "Somewhere else"
                                                  :foo     "bar"}
                                          $unset {:hii true}}))))

    (testing "override with custom timestamp"
      (is (= {$set   {:address               "Somewhere else"
                      :reportingDbOverrideTs (date/timestamp "20.1.2026")
                      :foo                   "bar"}
              $unset {:hii true}}
             (db/with-reporting-override "20.1.2026" {$set   {:address "Somewhere else"
                                                              :foo     "bar"}
                                                      $unset {:hii true}}))))

    (testing "combined with sheriff note - sheriff note first"
      (is (= {$set  {:reportingDbOverrideTs now-ts}
              $push {:_sheriff-notes {:note    "hello"
                                      :created now-ts}}}
             (db/with-sheriff-note "hello" (db/with-reporting-override)))))

    (testing "combined with sheriff note - reporting override first"
      (is (= {$set  {:reportingDbOverrideTs now-ts}
              $push {:_sheriff-notes {:note    "hello"
                                      :created now-ts}}}
             (db/with-reporting-override (db/with-sheriff-note "hello")))))))
