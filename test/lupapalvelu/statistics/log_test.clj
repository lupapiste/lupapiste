(ns lupapalvelu.statistics.log-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.statistics-test-util :refer [test-datetime]]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.statistics.entry :as stats-e]
            [lupapalvelu.statistics.log :as stats-log]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [sade.date :as date]
            [sade.env :as env]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(use-fixtures :once (fn [f]
                      (env/with-feature-value :statistics-processing true
                        (sc/with-fn-validation
                          (f)))))

;; Test data fixtures
(def monday-9am (test-datetime 2024 1 8 9 0))
(def friday-4pm (test-datetime 2024 1 12 16 0))

(def test-application
  {:id               "LP-753-2024-00001"
   :organization     "753-R"
   :permitType       "R"
   :state            "submitted"
   :submitted        (date/timestamp monday-9am)
   :primaryOperation {:name "pientalo"}})

;; ----------------------------------
;; processing-logdata tests
;; ----------------------------------

(deftest processing-logdata-basic-test
  (testing "Returns logdata with submitted-ts and processing"
    (let [processing {:complexity :medium :status    :running
                      :used       4       :remaining 18 :delays 0
                      :deadline   1712556000000}
          app        test-application]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [])]
        (let [result (stats-log/processing-logdata app)]
          (is (= (date/timestamp monday-9am) (:submitted-ts result)))
          (is (= processing (:processing result)))
          (is (nil? (:delays result)))
          (is (nil? (:verdict-ts result)))))))

  (testing "Returns logdata with minimal processing (no-complexity status)"
    (let [processing {:status :no-complexity}
          app        test-application]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [])]
        (let [result (stats-log/processing-logdata app)]
          (is (= (date/timestamp monday-9am) (:submitted-ts result)))
          (is (= {:status :no-complexity} (:processing result))))))))

(deftest processing-logdata-with-verdict-test
  (testing "Includes verdict-ts when application has Lupapiste verdict"
    (let [verdict-ts (date/timestamp friday-4pm)
          processing {:complexity :medium :status :finished}
          app        (assoc test-application
                            :pate-verdicts [{:category  "r"
                                             :published {:published verdict-ts}}])]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [])]
        (let [result (stats-log/processing-logdata app)]
          (is (= verdict-ts (:verdict-ts result)))))))

  (testing "Includes verdict-ts when application has legacy verdict"
    (let [verdict-ts (date/timestamp friday-4pm)
          processing {:complexity :medium :status :finished}
          app        (assoc test-application
                            :verdicts [{:paatokset [{:poytakirjat [{:paatospvm verdict-ts}]}]}])]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [])]
        (let [result (stats-log/processing-logdata app)]
          (is (= verdict-ts (:verdict-ts result))))))))

(deftest processing-logdata-with-delays-test
  (testing "Includes delays when delay entries exist"
    (let [delay-start  (date/timestamp (test-datetime 2024 1 9 10 0))
          delay-end    (date/timestamp (test-datetime 2024 1 10 14 0))
          processing   {:complexity :medium :status :running :delays 1}
          delay-entry  {:delay-start delay-start
                        :created     {:text "Waiting for documents"}
                        :explanation "Customer requested extension"
                        :closed      {:ts delay-end}}]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [delay-entry])]
        (let [result (stats-log/processing-logdata test-application)
              delays (:delays result)]
          (is (= 1 (count delays)))
          (is (= delay-start (:start-ts (first delays))))
          (is (= delay-end (:end-ts (first delays))))
          (is (= "Waiting for documents" (:text (first delays))))
          (is (= "Customer requested extension" (:explanation (first delays))))))))

  (testing "Delays are sorted by start-ts"
    (let [delay1 {:delay-start 1000
                  :created     {:text "Second delay"}
                  :closed      {:ts 2000}}
          delay2 {:delay-start 500
                  :created     {:text "First delay"}
                  :closed      {:ts 800}}
          processing {:complexity :medium :status :running :delays 2}]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [delay1 delay2])]
        (let [result (stats-log/processing-logdata test-application)
              delays (:delays result)]
          (is (= 2 (count delays)))
          (is (= 500 (:start-ts (first delays))))
          (is (= 1000 (:start-ts (second delays))))))))

  (testing "Open delay has no end-ts"
    (let [delay-start (date/timestamp (test-datetime 2024 1 9 10 0))
          delay-entry {:delay-start delay-start
                        :created     {:text "Ongoing delay"}}
          processing  {:complexity :medium :status :delayed :delays 1}]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [delay-entry])]
        (let [result (stats-log/processing-logdata test-application)
              delay  (first (:delays result))]
          (is (= delay-start (:start-ts delay)))
          (is (nil? (:end-ts delay)))
          (is (= "Ongoing delay" (:text delay)))))))

  (testing "No delays key when delay-entries returns empty"
    (let [processing {:complexity :medium :status :running}]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [])]
        (let [result (stats-log/processing-logdata test-application)]
          (is (not (contains? result :delays)))))))

  (testing "No delays key when delay-entries returns nil"
    (let [processing {:complexity :medium :status :running}]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly nil)]
        (let [result (stats-log/processing-logdata test-application)]
          (is (not (contains? result :delays))))))))

(deftest processing-logdata-multiple-delays-test
  (testing "Multiple delays with various states"
    (let [delay1 {:delay-start 1000
                  :created     {:text "First delay - closed"}
                  :explanation "Reason 1"
                  :closed      {:ts 2000}}
          delay2 {:delay-start 3000
                  :created     {:text "Second delay - closed"}
                  :closed      {:ts 4000}}
          delay3 {:delay-start 5000
                  :created     {:text "Third delay - open"}}
          processing {:complexity :medium :status :delayed :delays 3}]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [delay3 delay1 delay2])]
        (let [result (stats-log/processing-logdata test-application)
              delays (:delays result)]
          (is (= 3 (count delays)))
          ;; Sorted by start-ts
          (is (= [1000 3000 5000] (mapv :start-ts delays)))
          ;; First has an explanation
          (is (= "Reason 1" (:explanation (first delays))))
          ;; Second has no explanation
          (is (nil? (:explanation (second delays))))
          ;; Third is open (no end-ts)
          (is (nil? (:end-ts (nth delays 2)))))))))

(deftest processing-logdata-complete-test
  (testing "Complete logdata with all fields"
    (let [verdict-ts  (date/timestamp friday-4pm)
          delay-entry {:delay-start (date/timestamp (test-datetime 2024 1 9 10 0))
                       :created     {:text "Delay text"}
                       :explanation "Delay reason"
                       :closed      {:ts (date/timestamp (test-datetime 2024 1 10 14 0))}}
          processing  {:complexity :medium
                       :status     :finished
                       :used       4
                       :remaining  59
                       :delays     1
                       :deadline   1712556000000}
          app         (assoc test-application
                             :pate-verdicts [{:category  "r"
                                              :published {:published verdict-ts}}])]
      (with-redefs [stats-db/calculate-processing (constantly processing)
                    stats-e/delay-entries         (constantly [delay-entry])]
        (let [result (stats-log/processing-logdata app)]
          (is (= (date/timestamp monday-9am) (:submitted-ts result)))
          (is (= verdict-ts (:verdict-ts result)))
          (is (= processing (:processing result)))
          (is (= 1 (count (:delays result)))))))))
