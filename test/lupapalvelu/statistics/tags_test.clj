(ns lupapalvelu.statistics.tags-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.statistics.tags :as tags]
            [sade.strings :as ss]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(use-fixtures :once (fn [f]
                      (sc/with-fn-validation
                        (f))))

;; ----------------------------------
;; Test helpers
;; ----------------------------------

(defn mock-loc-fn
  "Mock localization function that returns the key or fills placeholders."
  ([key]
   (name key))
  ([key & args]
   (str (name key) ":" (ss/join "," args))))

(defn mock-date-fn
  "Mock date formatting function."
  [ts]
  (str "DATE:" ts))

(def test-options
  {:loc-fn  mock-loc-fn
   :date-fn mock-date-fn})

;; ----------------------------------
;; Test data
;; ----------------------------------

(def no-complexity-logdata
  {:submitted-ts 1704697200000
   :processing   {:status :no-complexity}})

(def minimal-logdata
  {:submitted-ts 1704697200000
   :processing   {:status           :running
                  :complexity       :medium
                  :remaining        90
                  :remaining-period {:y 0 :m 3 :d 0}
                  :used             0
                  :used-period      {:y 0 :m 0 :d 0}
                  :delays           0
                  :deadline         1712556000000}})

(def logdata-with-verdict
  {:submitted-ts 1704697200000
   :verdict-ts   1704783600000
   :processing   {:status           :finished
                  :complexity       :medium
                  :remaining        10
                  :remaining-period {:y 0 :m 0 :d 10}
                  :used             80
                  :used-period      {:y 0 :m 2 :d 20}
                  :delays           0
                  :deadline         1712556000000}})

(def logdata-with-delays
  {:submitted-ts 1704697200000
   :processing   {:status           :delayed
                  :complexity       :medium
                  :remaining        15
                  :remaining-period {:y 0 :m 0 :d 15}
                  :used             75
                  :used-period      {:y 0 :m 2 :d 15}
                  :delays           2
                  :deadline         1712556000000}
   :delays       [{:start-ts    1704700000000
                   :end-ts      1704800000000
                   :text        "Waiting for documents"
                   :explanation "Customer requested extension"}
                  {:start-ts 1704900000000
                   :text     "Missing signatures"}]})

(def logdata-overdue
  {:submitted-ts 1704697200000
   :processing   {:status           :running
                  :complexity       :small
                  :remaining        -5
                  :remaining-period {:y 0 :m 0 :d -5}
                  :used             95
                  :used-period      {:y 0 :m 3 :d 5}
                  :delays           0
                  :deadline         1707375600000}})

;; ----------------------------------
;; Helper functions for extracting data from hiccup
;; ----------------------------------

(defn find-elements
  "Recursively finds all elements matching a predicate in hiccup structure."
  [pred hiccup]
  (cond
    (pred hiccup) [hiccup]
    (vector? hiccup) (mapcat (partial find-elements pred) hiccup)
    (seq? hiccup) (mapcat (partial find-elements pred) hiccup)
    :else []))

(defn find-by-tag
  "Finds all elements with the given tag."
  [tag hiccup]
  (find-elements #(and (vector? %) (= tag (first %))) hiccup))

(defn find-tables
  "Finds all table elements."
  [hiccup]
  (find-by-tag :table.reset hiccup))

(defn find-rows
  "Finds all tr elements."
  [hiccup]
  (find-by-tag :tr hiccup))

(defn find-cells
  "Finds all td elements."
  [hiccup]
  (find-by-tag :td hiccup))

(defn cell-content
  "Extracts the text content from a td cell."
  [cell]
  (last cell))

(defn cell-style
  "Extracts the style from a td cell."
  [cell]
  (get-in cell [1 :style]))

;; ----------------------------------
;; processing-log-tags tests
;; ----------------------------------

(deftest processing-log-tags-structure-test
  (testing "Returns a div with proper structure"
    (let [result (tags/processing-log-tags minimal-logdata test-options)]
      (is (vector? result))
      (is (= :div (first result)))
      (is (map? (second result)))
      (is (contains? (second result) :style))))

  (testing "Contains at least one table"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          tables (find-tables result)]
      (is (pos? (count tables)))))

  (testing "Tables have reset class and collapse style"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          tables (find-tables result)]
      (doseq [table tables]
        (is (= :table.reset (first table)))
        (is (= :collapse (get-in table [1 :style :border-collapse])))))))

(deftest processing-log-tags-no-complexity-test
  (testing "Renders no complexity logdata without errors"
    (let [result (tags/processing-log-tags no-complexity-logdata test-options)]
      (is (vector? result))))

  (testing "Contains only submitted and status information"
    (let [result (tags/processing-log-tags no-complexity-logdata test-options)
          texts  (->> (find-cells result)
                      (mapv last)
                      set)]
      (is (= #{"submitted" "statistics.processing.status"
               "DATE:1704697200000" "statistics.processing.status.no-complexity"}
             texts)))))

(deftest processing-log-tags-minimal-test
  (testing "Renders minimal logdata without errors"
    (let [result (tags/processing-log-tags minimal-logdata test-options)]
      (is (vector? result))))

  (testing "Contains submitted timestamp"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          cells  (find-cells result)]
      (is (some #(= "DATE:1704697200000" (cell-content %)) cells))))

  (testing "Contains status"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          cells  (find-cells result)]
      (is (some #(= "statistics.processing.status.running" (cell-content %)) cells))))

  (testing "Contains complexity reference"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          cells  (find-cells result)]
      (is (some #(= "pate.complexity.medium (3 unit.kk)" (cell-content %)) cells)))))

(deftest processing-log-tags-verdict-test
  (testing "Shows verdict-ts label when verdict exists"
    (let [result (tags/processing-log-tags logdata-with-verdict test-options)
          cells  (find-cells result)]
      (is (some #(= "statistics.processing.verdict-ts" (cell-content %)) cells))))

  (testing "Shows deadline label when no verdict"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          cells  (find-cells result)]
      (is (some #(= "statistics.processing.deadline-ts" (cell-content %)) cells))))

  (testing "Shows verdict timestamp when verdict exists"
    (let [result (tags/processing-log-tags logdata-with-verdict test-options)
          cells  (find-cells result)]
      (is (some #(= "DATE:1704783600000" (cell-content %)) cells))))

  (testing "Shows deadline timestamp when no verdict"
    (let [logdata (assoc-in minimal-logdata [:processing :deadline] 1712556000000)
          result  (tags/processing-log-tags logdata test-options)
          cells   (find-cells result)]
      (is (some #(= "DATE:1712556000000" (cell-content %)) cells)))))

(deftest processing-log-tags-delays-test
  (testing "Creates two tables when delays exist"
    (let [result (tags/processing-log-tags logdata-with-delays test-options)
          tables (find-tables result)]
      (is (= 2 (count tables)))))

  (testing "Creates one table when no delays"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          tables (find-tables result)]
      (is (= 1 (count tables)))))

  (testing "Shows delays column header when delays exist"
    (let [result (tags/processing-log-tags logdata-with-delays test-options)
          cells  (find-cells result)]
      (is (some #(= "statistics.tags.delays" (cell-content %)) cells))))

  (testing "Does not show delays column when no delays"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          cells  (find-cells result)]
      (is (not (some #(= "statistics.tags.delays" (cell-content %)) cells)))))

  (testing "Shows delay entry text"
    (let [result (tags/processing-log-tags logdata-with-delays test-options)
          cells  (find-cells result)]
      (is (some #(= "Waiting for documents" (cell-content %)) cells))
      (is (some #(= "Missing signatures" (cell-content %)) cells))))

  (testing "Shows delay explanation"
    (let [result (tags/processing-log-tags logdata-with-delays test-options)
          cells  (find-cells result)]
      (is (some #(= "Customer requested extension" (cell-content %)) cells))))

  (testing "Shows delay timestamps"
    (let [result (tags/processing-log-tags logdata-with-delays test-options)
          cells  (find-cells result)]
      (is (some #(= "DATE:1704700000000" (cell-content %)) cells))
      (is (some #(= "DATE:1704800000000" (cell-content %)) cells))
      (is (some #(= "DATE:1704900000000" (cell-content %)) cells)))))

(deftest processing-log-tags-remaining-test
  (testing "Shows remaining period"
    (let [result (tags/processing-log-tags logdata-with-verdict test-options)
          cells  (find-cells result)]
      ;; remaining-period {:y 0 :m 0 :d 10} -> "statistics.period.d:10"
      (is (some #(= "statistics.period.d:10" (cell-content %)) cells))))

  (testing "Shows delays count when delays exist"
    (let [result (tags/processing-log-tags logdata-with-delays test-options)
          cells  (find-cells result)]
      ;; delays: 2 -> "statistics.period.d:2"
      (is (some #(= "statistics.period.d:2" (cell-content %)) cells)))))

(deftest processing-log-tags-overdue-styling-test
  (testing "Applies late styling when remaining is negative"
    (let [result (tags/processing-log-tags logdata-overdue test-options)
          cells  (find-cells result)
          ;; remaining-period {:y 0 :m 0 :d -5} -> "statistics.period.d:-5"
          remaining-cell (first (filter #(= "statistics.period.d:-5" (cell-content %))
                                        cells))]
      (is (some? remaining-cell))
      (let [style (cell-style remaining-cell)]
        (is (= :bold (:font-weight style)))
        (is (= :red (:color style))))))

  (testing "Does not apply late styling when remaining is positive"
    (let [result (tags/processing-log-tags logdata-with-verdict test-options)
          cells  (find-cells result)
          ;; remaining-period {:y 0 :m 0 :d 10} -> "statistics.period.d:10"
          remaining-cell (first (filter #(= "statistics.period.d:10" (cell-content %))
                                        cells))]
      (is (some? remaining-cell))
      (let [style (cell-style remaining-cell)]
        (is (not= :red (:color style)))))))

(deftest processing-log-tags-localization-test
  (testing "Calls loc-fn for localized text"
    (let [loc-calls (atom [])
          tracking-loc-fn (fn [& args]
                            (swap! loc-calls conj args)
                            (apply mock-loc-fn args))
          _result (tags/processing-log-tags minimal-logdata
                                            {:loc-fn  tracking-loc-fn
                                             :date-fn mock-date-fn})]
      (is (pos? (count @loc-calls)))
      (is (some #(= [:submitted] %) @loc-calls))
      (is (some #(= [:statistics.processing.complexity] %) @loc-calls))
      (is (some #(= [:statistics.processing.status] %) @loc-calls))))

  (testing "Calls date-fn for timestamps"
    (let [date-calls (atom [])
          tracking-date-fn (fn [ts]
                             (swap! date-calls conj ts)
                             (mock-date-fn ts))
          _result (tags/processing-log-tags minimal-logdata
                                            {:loc-fn  mock-loc-fn
                                             :date-fn tracking-date-fn})]
      (is (some #(= 1704697200000 %) @date-calls)))))

(deftest processing-log-tags-cell-styling-test
  (testing "Cells have vertical-align top"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          cells  (find-cells result)]
      (doseq [cell cells]
        (is (= :top (get-in (cell-style cell) [:vertical-align]))))))

  (testing "Header cells have bold styling"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          cells  (find-cells result)
          header-cells (filter #(#{"submitted"
                                   "statistics.processing.complexity"
                                   "statistics.processing.status"
                                   "statistics.processing.deadline-ts"
                                   "statistics.processing.remaining"}
                                 (cell-content %))
                               cells)]
      (doseq [cell header-cells]
        (is (= :bold (get-in (cell-style cell) [:font-weight])))))))

(deftest processing-log-tags-delay-table-headers-test
  (testing "Delay table has correct headers"
    (let [result (tags/processing-log-tags logdata-with-delays test-options)
          cells  (find-cells result)]
      (is (some #(= "statistics.entries.delay.created" (cell-content %)) cells))
      (is (some #(= "statistics.entries.explanation" (cell-content %)) cells))
      (is (some #(= "statistics.entries.delay.start" (cell-content %)) cells))
      (is (some #(= "statistics.entries.delay.end" (cell-content %)) cells)))))

;; ----------------------------------
;; Helper function tests
;; ----------------------------------

(deftest complexity->text-test
  (testing "Returns nil for nil complexity"
    (is (nil? (tags/complexity->text mock-loc-fn nil))))

  (testing "Formats special complexity (1 month)"
    (is (= "pate.complexity.special (1 unit.kk)"
           (tags/complexity->text mock-loc-fn :special))))

  (testing "Formats small complexity (3 months)"
    (is (= "pate.complexity.small (3 unit.kk)"
           (tags/complexity->text mock-loc-fn :small))))

  (testing "Formats medium complexity (3 months)"
    (is (= "pate.complexity.medium (3 unit.kk)"
           (tags/complexity->text mock-loc-fn :medium))))

  (testing "Formats large complexity (3 months)"
    (is (= "pate.complexity.large (3 unit.kk)"
           (tags/complexity->text mock-loc-fn :large))))

  (testing "Formats very-large complexity (6 months)"
    (is (= "pate.complexity.very-large (6 unit.kk)"
           (tags/complexity->text mock-loc-fn :very-large))))

  (testing "Formats extra-large complexity (6 months)"
    (is (= "pate.complexity.extra-large (6 unit.kk)"
           (tags/complexity->text mock-loc-fn :extra-large)))))

(deftest period->text-test
  (testing "Returns '0 days' for all-zero period"
    (is (= "statistics.period.d:0"
           (tags/period->text mock-loc-fn {:y 0 :m 0 :d 0}))))

  (testing "Shows only days when only days are non-zero"
    (is (= "statistics.period.d:5"
           (tags/period->text mock-loc-fn {:y 0 :m 0 :d 5}))))

  (testing "Shows only months when only months are non-zero"
    (is (= "statistics.period.m:2"
           (tags/period->text mock-loc-fn {:y 0 :m 2 :d 0}))))

  (testing "Shows only years when only years are non-zero"
    (is (= "statistics.period.y:1"
           (tags/period->text mock-loc-fn {:y 1 :m 0 :d 0}))))

  (testing "Shows years and months"
    (is (= "statistics.period.y:1 statistics.period.m:3"
           (tags/period->text mock-loc-fn {:y 1 :m 3 :d 0}))))

  (testing "Shows months and days"
    (is (= "statistics.period.m:2 statistics.period.d:15"
           (tags/period->text mock-loc-fn {:y 0 :m 2 :d 15}))))

  (testing "Shows all parts when all non-zero"
    (is (= "statistics.period.y:1 statistics.period.m:2 statistics.period.d:10"
           (tags/period->text mock-loc-fn {:y 1 :m 2 :d 10}))))

  (testing "Handles negative values"
    (is (= "statistics.period.d:-5"
           (tags/period->text mock-loc-fn {:y 0 :m 0 :d -5}))))

  (testing "Returns '0 days' for nil period"
    (is (= "statistics.period.d:0"
           (tags/period->text mock-loc-fn nil))))

  (testing "Returns '0 days' for empty period"
    (is (= "statistics.period.d:0"
           (tags/period->text mock-loc-fn {})))))

(deftest processing-log-tags-summary-test
  (testing "Summary section contains title"
    (let [result (tags/processing-log-tags minimal-logdata test-options)
          h3s    (find-by-tag :h3 result)]
      (is (some #(= "statistics.summary.title" (last %)) h3s))))

  (testing "Summary section contains label and used period"
    (let [result (tags/processing-log-tags minimal-logdata test-options)]
      ;; Find all text content in the result
      (is (ss/includes? (str result) "statistics.summary.label"))
      (is (ss/includes? (str result) "statistics.summary.title"))))

  (testing "Summary section shows used period for application with history"
    (let [result (tags/processing-log-tags logdata-with-verdict test-options)]
      ;; used-period {:y 0 :m 2 :d 20} -> "statistics.period.m:2 statistics.period.d:20"
      (is (ss/includes? (str result) "statistics.period.m:2 statistics.period.d:20"))))

  (testing "Summary section shows zero used period for fresh application"
    (let [result (tags/processing-log-tags minimal-logdata test-options)]
      ;; Find the summary div with used-period (the one with font-weight bold)
      ;; used-period {:y 0 :m 0 :d 0} -> "statistics.period.d:0"
      (is (ss/includes? (str result) "statistics.period.d:0"))))

  (testing "Summary section is not shown when complexity is missing"
    (let [result (tags/processing-log-tags no-complexity-logdata test-options)]
      (is (not (ss/includes? (str result) "statistics.summary.title")))
      (is (not (ss/includes? (str result) "statistics.summary.label"))))))
