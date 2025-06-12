(ns lupapalvelu.statistics.db-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.statistics-test-util :refer [test-datetime test-fixture]]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.statistics.entry :as stats-e]
            [sade.date :as date]))

(set! *warn-on-reflection* true)

(use-fixtures :once test-fixture)

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
;; read-processing tests
;; ----------------------------------

(deftest read-processing-test
  (testing "Returns nil for empty application"
    (is (nil? (stats-db/read-processing {}))))

  (testing "Returns nil when no _stats key"
    (is (nil? (stats-db/read-processing {:id "LP-123"}))))

  (testing "Returns nil when no processing in _stats"
    (is (nil? (stats-db/read-processing {:_stats {}}))))

  (testing "Returns empty map for empty processing"
    (is (= {} (stats-db/read-processing {:_stats {:processing {}}}))))

  (testing "Returns processing with only complexity"
    (is (= {:complexity :medium}
           (stats-db/read-processing {:_stats {:processing {:complexity 2}}}))))

  (testing "Returns full processing data"
    (is (= {:complexity       :extra-large
            :status           :running
            :used             10
            :used-period      {:y 0 :m 0 :d 10}
            :remaining        50
            :remaining-period {:y 0 :m 1 :d 19}
            :delays           2
            :deadline         1234567890
            :requests         0}
           (stats-db/read-processing
             {:_stats {:processing {:complexity       5
                                    :status           1
                                    :used             10
                                    :used-period      {:y 0 :m 0 :d 10}
                                    :remaining        50
                                    :remaining-period {:y 0 :m 1 :d 19}
                                    :delays           2
                                    :deadline         1234567890
                                    :requests         0}}}))))

  (testing "Coerces db status integer to keyword"
    (is (= :delayed
           (:status (stats-db/read-processing
                      {:_stats {:processing {:complexity 2 :status 2}}})))))

  (testing "Returns processing with requests count"
    (is (= {:complexity :medium
            :status     :running
            :requests   5}
           (stats-db/read-processing
             {:_stats {:processing {:complexity 2
                                    :status     1
                                    :requests   5}}})))))

;; ----------------------------------
;; calculate-processing nil cases
;; ----------------------------------

(deftest calculate-processing-nil-cases-test
  (testing "Returns nil for non-R permit type"
    (is (nil? (stats-db/calculate-processing
                (assoc test-application :permitType "YA")
                :medium))))

  (testing "Returns nil for foreman application"
    (is (nil? (stats-db/calculate-processing
                (assoc-in test-application [:primaryOperation :name]
                          "tyonjohtajan-nimeaminen-v2")
                :medium))))

  (testing "Returns nil for notice"
    (is (nil? (stats-db/calculate-processing
                (assoc-in test-application [:primaryOperation :name]
                          "purkamisilmoitus")
                :medium))))

  (testing "Returns nil for converted (facta-imported) application"
    (is (nil? (stats-db/calculate-processing
                (assoc test-application :facta-imported true)
                :medium))))

  (testing "Returns nil for info request"
    (is (nil? (stats-db/calculate-processing
                (assoc test-application :infoRequest true)
                :medium))))

  (testing "Returns nil for pre-submission state"
    (is (nil? (stats-db/calculate-processing
                (assoc test-application :state "draft")
                :medium))))

  (testing "Returns nil when application has no submitted timestamp"
    (is (nil? (stats-db/calculate-processing
                (dissoc test-application :submitted)
                :medium)))))

;; ----------------------------------
;; calculate-processing no-complexity
;; ----------------------------------

(deftest calculate-processing-no-complexity-test
  (testing "Returns :no-complexity status when complexity not provided"
    (let [result (stats-db/calculate-processing test-application nil)]
      (is (= {:status :no-complexity} result))))

  (testing "Single-arity returns nil when no existing complexity in application"
    (let [app (dissoc test-application :_stats)
          result (stats-db/calculate-processing app)]
      (is (= {:status :no-complexity} result)))))

;; ----------------------------------
;; calculate-processing basic functionality
;; ----------------------------------

(deftest calculate-processing-basic-test
  (with-redefs [date/now                             (constantly friday-4pm)
                stats-e/delay-ranges                 (constantly [])
                stats-e/application-open-entry-count (constantly 0)]
    (testing "Calculates processing with running status"
      (let [result (stats-db/calculate-processing test-application :medium)]
        (is (= :running (:status result)))
        (is (= :medium (:complexity result)))
        (is (number? (:used result)))
        (is (pos? (:used result)))
        (is (number? (:remaining result)))
        (is (number? (:delays result)))
        (is (= 0 (:delays result)))
        (is (number? (:deadline result)))
        (is (= 0 (:requests result)))
        (is (map? (:used-period result)))
        (is (map? (:remaining-period result)))))

    (testing "Calculates with different complexity values"
      (let [result-special (stats-db/calculate-processing test-application :special)
            result-medium (stats-db/calculate-processing test-application :medium)
            result-xl     (stats-db/calculate-processing test-application :extra-large)]
        (is (= :special (:complexity result-special)))
        (is (= :medium (:complexity result-medium)))
        (is (= :extra-large (:complexity result-xl)))
        ;; Different complexities should have different deadlines
        (is (< (:deadline result-special) (:deadline result-medium)))
        (is (< (:deadline result-medium) (:deadline result-xl)))))

    (testing "Calculates with open requests"
      (with-redefs [stats-e/application-open-entry-count (constantly 3)]
        (let [result (stats-db/calculate-processing test-application :medium)]
          (is (= 3 (:requests result))))))))

;; ----------------------------------
;; calculate-processing status tests
;; ----------------------------------

(deftest calculate-processing-status-test
  (with-redefs [stats-e/delay-ranges                 (constantly [])
                stats-e/application-open-entry-count (constantly 0)]

    (testing "Returns :running status when no verdict and not delayed"
      (with-redefs [date/now (constantly friday-4pm)]
        (let [result (stats-db/calculate-processing test-application :medium)]
          (is (= :running (:status result))))))

    (testing "Returns :finished status when verdict exists"
      (let [verdict-ts (date/timestamp friday-4pm)
            app        (assoc test-application
                              :pate-verdicts [{:category  "r"
                                               :published {:published verdict-ts}}])]
        (with-redefs [date/now (constantly friday-4pm)]
          (let [result (stats-db/calculate-processing app :medium)]
            (is (= :finished (:status result)))))))

    (testing "Returns :delayed status when currently in delay period"
      (let [now-ts (date/timestamp friday-4pm)
            ;; Create a delay range that includes current time
            delay-range [(- now-ts 1000) (+ now-ts 1000)]]
        (with-redefs [date/now         (constantly friday-4pm)
                      stats-e/delay-ranges (constantly [delay-range])]
          (let [result (stats-db/calculate-processing test-application :medium)]
            (is (= :delayed (:status result)))))))

    (testing "Finished status takes priority over delayed"
      (let [verdict-ts  (date/timestamp friday-4pm)
            now-ts      (date/timestamp friday-4pm)
            delay-range [(- now-ts 1000) (+ now-ts 1000)]
            app         (assoc test-application
                               :pate-verdicts [{:category  "r"
                                                :published {:published verdict-ts}}])]
        (with-redefs [date/now           (constantly friday-4pm)
                      stats-e/delay-ranges (constantly [delay-range])]
          (let [result (stats-db/calculate-processing app :medium)]
            (is (= :finished (:status result)))))))))

;; ----------------------------------
;; calculate-processing time calculations
;; ----------------------------------

(deftest calculate-processing-time-test
  (with-redefs [stats-e/delay-ranges                 (constantly [])
                stats-e/application-open-entry-count (constantly 0)]

    (testing "Used days increases as time passes"
      (with-redefs [date/now (constantly monday-9am)]
        (let [result-monday (stats-db/calculate-processing test-application :special)]
          (with-redefs [date/now (constantly friday-4pm)]
            (let [result-friday (stats-db/calculate-processing test-application :special)]
              (is (< (:used result-monday) (:used result-friday)))
              (is (> (:remaining result-monday) (:remaining result-friday))))))))

    (testing "Deadline is correctly set based on complexity"
      (with-redefs [date/now (constantly monday-9am)]
        (let [result-1month (stats-db/calculate-processing test-application :special)
              result-3month (stats-db/calculate-processing test-application :medium)
              result-6month (stats-db/calculate-processing test-application :extra-large)]
          ;; Deadlines should be in order: 1 month < 3 months < 6 months
          (is (< (:deadline result-1month) (:deadline result-3month)))
          (is (< (:deadline result-3month) (:deadline result-6month))))))

    (testing "Remaining can be negative when deadline passed"
      (let [far-future (test-datetime 2025 1 8 9 0)]
        (with-redefs [date/now (constantly far-future)]
          (let [result (stats-db/calculate-processing test-application :special)]
            (is (neg? (:remaining result)))))))))

;; ----------------------------------
;; calculate-processing with delays
;; ----------------------------------

(deftest calculate-processing-delays-test
  (with-redefs [date/now                             (constantly friday-4pm)
                stats-e/application-open-entry-count (constantly 0)]

    (testing "Delays are subtracted from used time"
      (let [monday-ts         (date/timestamp monday-9am)
            tuesday-ts        (date/timestamp (test-datetime 2024 1 9 9 0))
            ;; 1-day delay from Monday to Tuesday
            delay-range       [monday-ts tuesday-ts]
            result-no-delay   (with-redefs [stats-e/delay-ranges (constantly [])]
                                (stats-db/calculate-processing test-application :special))
            result-with-delay (with-redefs [stats-e/delay-ranges (constantly [delay-range])]
                                (stats-db/calculate-processing test-application :special))]
        ;; Used should be less with delay
        (is (< (:used result-with-delay) (:used result-no-delay)))
        ;; Delays should be tracked
        (is (= 1 (:delays result-with-delay))
            "One full day")
        (is (zero? (:delays result-no-delay)))))

    (testing "Deadline extends with delays"
      (let [monday-ts         (date/timestamp monday-9am)
            tuesday-ts        (date/timestamp (test-datetime 2024 1 9 9 0))
            delay-range       [monday-ts tuesday-ts]
            result-no-delay   (with-redefs [stats-e/delay-ranges (constantly [])]
                                (stats-db/calculate-processing test-application :special))
            result-with-delay (with-redefs [stats-e/delay-ranges (constantly [delay-range])]
                                (stats-db/calculate-processing test-application :special))]
        ;; Deadline should be later with delays
        (is (> (:deadline result-with-delay) (:deadline result-no-delay)))))

    (testing "Multiple delay ranges are handled correctly"
      (let [monday-ts    (date/timestamp monday-9am)
            tuesday-ts   (date/timestamp (test-datetime 2024 1 9 9 0))
            wednesday-ts (date/timestamp (test-datetime 2024 1 10 9 0))
            thursday-ts  (date/timestamp (test-datetime 2024 1 11 9 0))
            ;; Two 1-day delays
            delay-ranges [[monday-ts tuesday-ts] [wednesday-ts thursday-ts]]]
        (with-redefs [stats-e/delay-ranges (constantly delay-ranges)]
          (let [result (stats-db/calculate-processing test-application :special)]
            ;; Should track total delays
            (is (= 2 (:delays result))
                "Two full days")))))))

;; ----------------------------------
;; calculate-processing period tests
;; ----------------------------------

(deftest calculate-processing-period-test
  (let [;; Submission date: Jan 8, 2024, 12:00
        submission-date (test-datetime 2024 1 8 12 0)
        app             (assoc test-application :submitted (date/timestamp submission-date))]

    (with-redefs [stats-e/delay-ranges                 (constantly [])
                  stats-e/application-open-entry-count (constantly 0)]

      (testing "Day 0 (same day as submission)"
        (with-redefs [date/now (constantly (test-datetime 2024 1 8 12 0))]
          (let [result (stats-db/calculate-processing app :medium)]
            (is (= {:y 0 :m 0 :d 0} (:used-period result)))
            ;; Medium = 3 months remaining
            (is (= {:y 0 :m 3 :d 0} (:remaining-period result))))))

      (testing "After 1 week (Jan 15)"
        (with-redefs [date/now (constantly (test-datetime 2024 1 15 9 0))]
          (let [result (stats-db/calculate-processing app :medium)]
            ;; 5 workdays (Jan 8-12) + 2 workdays (Jan 15) = 5 workdays (Jan 8 excluded as start)
            (is (map? (:used-period result)))
            (is (= 0 (:y (:used-period result))))
            (is (= 0 (:m (:used-period result))))
            (is (pos? (:d (:used-period result))))
            ;; Remaining period should be less than 3 months
            (is (map? (:remaining-period result)))
            (is (number? (:d (:remaining-period result)))))))

      (testing "Exactly 1 month used (Feb 8)"
        (with-redefs [date/now (constantly (test-datetime 2024 2 8 12 0))]
          (let [result (stats-db/calculate-processing app :medium)]
            (is (= {:y 0 :m 1 :d 0} (:used-period result)))
            ;; 3 months - 1 month = 2 months remaining
            (is (= {:y 0 :m 2 :d 0} (:remaining-period result))))))

      (testing "Exactly 3 months used - deadline day (Apr 8)"
        (with-redefs [date/now (constantly (test-datetime 2024 4 8 12 0))]
          (let [result (stats-db/calculate-processing app :medium)]
            (is (= {:y 0 :m 3 :d 0} (:used-period result)))
            ;; 3 months - 3 months = 0 remaining
            (is (= {:y 0 :m 0 :d 0} (:remaining-period result))))))

      (testing "Overdue (past deadline - Apr 15)"
        (with-redefs [date/now (constantly (test-datetime 2024 4 15 12 0))]
          (let [result (stats-db/calculate-processing app :medium)]
            ;; Used more than 3 months
            (is (= 0 (:y (:used-period result))))
            (is (= 3 (:m (:used-period result))))
            (is (pos? (:d (:used-period result))))
            ;; Remaining period is negative (overdue)
            (is (= 0 (:y (:remaining-period result))))
            (is (= 0 (:m (:remaining-period result))))
            (is (neg? (:d (:remaining-period result)))))))

      (testing "6-month complexity (very-large), day 0"
        (with-redefs [date/now (constantly (test-datetime 2024 1 8 12 0))]
          (let [result (stats-db/calculate-processing app :very-large)]
            (is (= {:y 0 :m 0 :d 0} (:used-period result)))
            ;; very-large = 6 months remaining
            (is (= {:y 0 :m 6 :d 0} (:remaining-period result)))))))))

;; ----------------------------------
;; calculate-processing two-arity test
;; ----------------------------------

(deftest calculate-processing-two-arity-test
  (with-redefs [date/now                             (constantly friday-4pm)
                stats-e/delay-ranges                 (constantly [])
                stats-e/application-open-entry-count (constantly 0)]

    (testing "Single-arity uses complexity from application's processing data"
      ;; Complexity 2 in DB format = :medium in keyword format
      (let [app-with-complexity (assoc test-application
                                       :_stats {:processing {:complexity 2}})
            result (stats-db/calculate-processing app-with-complexity)]
        (is (= :medium (:complexity result)))
        (is (= :running (:status result)))))

    (testing "Single-arity returns :no-complexity when no processing data in application"
      (let [result (stats-db/calculate-processing test-application)]
        ;; When no processing data exists, proc-complexity returns nil
        ;; which causes calculate-processing to return {:status :no-complexity}
        (is (= {:status :no-complexity} result))))

    (testing "Two-arity uses provided complexity, ignoring application data"
      ;; App has :medium (2) but we provide :extra-large
      (let [app-with-complexity (assoc test-application
                                       :_stats {:processing {:complexity 2}})
            result (stats-db/calculate-processing app-with-complexity :extra-large)]
        ;; Should use provided :extra-large, not the :medium from app
        (is (= :extra-large (:complexity result)))
        (is (= :running (:status result)))))))

;; ----------------------------------
;; processing+extras tests
;; ----------------------------------

(deftest processing+extras-test
  (let [app     (assoc test-application :_stats {:processing {:complexity 2
                                                              :status     1
                                                              :used       5
                                                              :remaining  58}})
        command {:application app :user {:id "user-123"}}]

    (testing "Returns nil when no processing"
      (is (nil? (stats-db/processing+extras {:application {} :user {}}))))

    (testing "Adds verdict-ts when requested"
      (let [verdict-ts       1234567890
            app-with-verdict (assoc app :pate-verdicts
                                    [{:category  "r"
                                      :published {:published verdict-ts}}])]
        (is (= verdict-ts
               (:verdict-ts (stats-db/processing+extras
                              {:application app-with-verdict :user {}}
                              :extras #{:verdict-ts}))))))

    (testing "Does not add verdict-ts when not requested"
      (is (not (contains? (stats-db/processing+extras command :extras #{})
                          :verdict-ts))))

    (testing "Adds unseen? when requested"
      (with-redefs [stats-e/unseen-entries? (constantly true)]
        (is (true? (:unseen? (stats-db/processing+extras command :extras #{:unseen?}))))))

    (testing "Does not add unseen? when not requested"
      (with-redefs [stats-e/unseen-entries? (constantly true)]
        (is (not (contains? (stats-db/processing+extras command :extras #{:verdict-ts})
                            :unseen?)))))

    (testing "Returns processing with both extras by default"
      (with-redefs [stats-e/unseen-entries? (constantly true)]
        (let [verdict-ts       1234567890
              app-with-verdict (assoc app :pate-verdicts
                                      [{:category  "r"
                                        :published {:published verdict-ts}}])
              result           (stats-db/processing+extras (assoc command
                                                                  :application app-with-verdict))]
          (is (= :medium (:complexity result)))
          (is (= :running (:status result)))
          (is (= verdict-ts (:verdict-ts result)))
          (is (contains? result :unseen?)))))

    (testing "Uses provided processing instead of reading from application"
      (let [custom-processing {:complexity :extra-large :status :delayed}]
        (is (= :extra-large (:complexity (stats-db/processing+extras
                                           command
                                           :processing custom-processing
                                           :extras #{}))))))))

;; ----------------------------------
;; Query and projection tests
;; ----------------------------------

(deftest processing-query-test
  (testing "processing-query contains expected structure"
    (is (map? stats-db/processing-query))
    (is (contains? stats-db/processing-query :state))
    (is (contains? stats-db/processing-query :_stats.processing.status))))

(deftest processing-path-test
  (testing "processing-path is correct"
    (is (= [:_stats :processing] stats-db/processing-path))))

(deftest processing-projection-test
  (testing "processing-projection is keyword path"
    (is (= :_stats.processing stats-db/processing-projection))))

;; ----------------------------------
;; calculate-processing detailed tests
;; with exact dates and values
;; ----------------------------------

(deftest calculate-processing-exact-values-test
  "Tests calculate-processing with exact, verifiable dates and values.
   Checks every field in the Processing schema."

  ;; Application submitted on Monday January 8, 2024 at 9:00 AM
  ;; For :special complexity (1 month), deadline is February 8, 2024 end of day
  ;; Deadline timestamp: 1707429599999 (Feb 8, 2024 23:59:59.999 Europe/Helsinki)

  (let [submitted-ts (date/timestamp (test-datetime 2024 1 8 9 0))
        ;; Feb 8, 2024 end of day in Europe/Helsinki timezone
        special-deadline-ts 1707429599999
        make-app (fn []
                   {:id               "LP-753-2024-00001"
                    :organization     "753-R"
                    :permitType       "R"
                    :state            "submitted"
                    :submitted        submitted-ts
                    :primaryOperation {:name "pientalo"}})]

    (with-redefs [stats-e/delay-ranges                 (constantly [])
                  stats-e/application-open-entry-count (constantly 0)]

      (testing "Day 0: Processing calculated on submission day (Jan 8 at noon)"
        ;; Now: Jan 8, 2024 at 12:00 PM (same day as submission)
        ;; Used: 0 days (no full days elapsed)
        ;; Remaining: 31 days until Feb 8 end of day
        (with-redefs [date/now (constantly (test-datetime 2024 1 8 12 0))]
          (let [result (stats-db/calculate-processing (make-app) :special)]
            (is (= :running (:status result)))
            (is (= :special (:complexity result)))
            (is (= 0 (:used result)) "No full days elapsed yet")
            (is (= {:y 0 :m 0 :d 0} (:used-period result)))
            (is (= 31 (:remaining result)) "31 days from Jan 8 to Feb 8 end of day")
            (is (= {:y 0 :m 1 :d 0} (:remaining-period result)) "1 month remaining")
            (is (= 0 (:delays result)))
            (is (= special-deadline-ts (:deadline result)))
            (is (= 0 (:requests result))))))

      (testing "Day 4: After a work week (Jan 12 Friday at 5 PM)"
        ;; Now: Jan 12, 2024 at 5:00 PM
        ;; Used: 4 days (Jan 8-9, 9-10, 10-11, 11-12)
        ;; Remaining: 27 days until Feb 8 end of day
        (with-redefs [date/now (constantly (test-datetime 2024 1 12 17 0))]
          (let [result (stats-db/calculate-processing (make-app) :special)]
            (is (= :running (:status result)))
            (is (= :special (:complexity result)))
            (is (= 4 (:used result)) "4 full days: Mon-Tue, Tue-Wed, Wed-Thu, Thu-Fri")
            (is (= {:y 0 :m 0 :d 4} (:used-period result)))
            (is (= 27 (:remaining result)) "27 days from Jan 12 to Feb 8 end of day")
            (is (= {:y 0 :m 0 :d 27} (:remaining-period result)))
            (is (= 0 (:delays result)))
            (is (= special-deadline-ts (:deadline result)))
            (is (= 0 (:requests result))))))

      (testing "Day 7: After one full week (Jan 15 Monday at 9 AM)"
        ;; Now: Jan 15, 2024 at 9:00 AM
        ;; Used: 7 days
        ;; Remaining: 24 days until Feb 8 end of day
        (with-redefs [date/now (constantly (test-datetime 2024 1 15 9 0))]
          (let [result (stats-db/calculate-processing (make-app) :special)]
            (is (= :running (:status result)))
            (is (= :special (:complexity result)))
            (is (= 7 (:used result)) "7 days from Jan 8 to Jan 15")
            (is (= {:y 0 :m 0 :d 7} (:used-period result)))
            (is (= 24 (:remaining result)) "24 days from Jan 15 to Feb 8 end of day")
            (is (= {:y 0 :m 0 :d 24} (:remaining-period result)))
            (is (= 0 (:delays result)))
            (is (= special-deadline-ts (:deadline result)))
            (is (= 0 (:requests result))))))

      (testing "Exactly one month later: deadline day (Feb 8 at noon)"
        ;; Now: Feb 8, 2024 at 12:00 PM (deadline day)
        ;; Used: 31 days
        ;; Remaining: 0 days (same day as deadline)
        (with-redefs [date/now (constantly (test-datetime 2024 2 8 12 0))]
          (let [result (stats-db/calculate-processing (make-app) :special)]
            (is (= :running (:status result)))
            (is (= :special (:complexity result)))
            (is (= 31 (:used result)) "31 days from Jan 8 to Feb 8")
            (is (= {:y 0 :m 1 :d 0} (:used-period result)) "1 month used")
            (is (= 0 (:remaining result)) "0 days remaining on deadline day")
            (is (= {:y 0 :m 0 :d 0} (:remaining-period result)) "No time remaining")
            (is (= 0 (:delays result)))
            (is (= special-deadline-ts (:deadline result)))
            (is (= 0 (:requests result))))))

      (testing "Past deadline: one week overdue (Feb 15 at noon)"
        ;; Now: Feb 15, 2024 at 12:00 PM (7 days past deadline)
        ;; Used: 38 days
        ;; Remaining: -6 days (negative because past deadline)
        (with-redefs [date/now (constantly (test-datetime 2024 2 15 12 0))]
          (let [result (stats-db/calculate-processing (make-app) :special)]
            (is (= :running (:status result)))
            (is (= :special (:complexity result)))
            (is (= 38 (:used result)) "38 days from Jan 8 to Feb 15")
            (is (= {:y 0 :m 1 :d 7} (:used-period result)) "1 month and 7 days used")
            (is (= -6 (:remaining result)) "6 days past deadline (negative)")
            (is (= {:y 0 :m 0 :d -7} (:remaining-period result)) "7 days past in period form")
            (is (= 0 (:delays result)))
            (is (= special-deadline-ts (:deadline result)))
            (is (= 0 (:requests result))))))

      (testing "With open requests"
        ;; Same as Day 4, but with 3 open requests
        (with-redefs [date/now                             (constantly (test-datetime 2024 1 12 17 0))
                      stats-e/application-open-entry-count (constantly 3)]
          (let [result (stats-db/calculate-processing (make-app) :special)]
            (is (= :running (:status result)))
            (is (= :special (:complexity result)))
            (is (= 4 (:used result)))
            (is (= {:y 0 :m 0 :d 4} (:used-period result)))
            (is (= 27 (:remaining result)))
            (is (= {:y 0 :m 0 :d 27} (:remaining-period result)))
            (is (= 0 (:delays result)))
            (is (= special-deadline-ts (:deadline result)))
            (is (= 3 (:requests result)) "3 open requests")))))))

(deftest calculate-processing-exact-delays-test
  ;; Tests delay calculations with exact timestamps and expected values.
  ;; Checks all 9 Processing schema fields: status, complexity, used, used-period,
  ;; remaining, remaining-period, delays, deadline, requests

  (let [submitted-ts (date/timestamp (test-datetime 2024 1 8 9 0))]
    (with-redefs [stats-e/application-open-entry-count (constantly 0)]

      (testing "3-day delay in the middle of processing"
        ;; Submitted Jan 8, delay from Jan 10 to Jan 13 (3 days)
        ;; Now is Jan 15 at 5 PM
        ;; Total calendar days: 7, minus 3 delay days = 4 work days used
        ;; :special = 1 month, base deadline Feb 8, +3 delay days = Feb 11 23:59:59.999
        (let [delay-start (date/timestamp (test-datetime 2024 1 10 9 0))
              delay-end   (date/timestamp (test-datetime 2024 1 13 9 0))
              now         (test-datetime 2024 1 15 17 0)
              application {:id               "LP-753-2024-00001"
                           :organization     "753-R"
                           :permitType       "R"
                           :state            "submitted"
                           :submitted        submitted-ts
                           :primaryOperation {:name "pientalo"}}]
          (with-redefs [date/now             (constantly now)
                        stats-e/delay-ranges (constantly [[delay-start delay-end]])]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields
              (is (= :running (:status result)))
              (is (= :special (:complexity result)))
              (is (= 4 (:used result)) "7 calendar days - 3 delay days = 4 work days")
              (is (= {:y 0 :m 0 :d 4} (:used-period result)))
              (is (= 27 (:remaining result)) "31 day month - 4 used = 27 remaining")
              (is (= {:y 0 :m 0 :d 27} (:remaining-period result)))
              (is (= 3 (:delays result)) "3 days of delay")
              (is (= 1707688799999 (:deadline result)) "Feb 11, 2024 23:59:59.999")
              (is (= 0 (:requests result)))))))

      (testing "Currently in delay period"
        ;; Submitted Jan 8, delay started Jan 12, now is Jan 15 at noon
        ;; When currently in delay, the delay-ranges function returns delay-end as now-ts
        ;; So delay is 3 days (Jan 12 to Jan 15)
        ;; Work days before delay: 4 days (Jan 8-11)
        ;; :special = 1 month, base deadline Feb 8, +3 delay days = Feb 11 23:59:59.999
        (let [delay-start (date/timestamp (test-datetime 2024 1 12 9 0))
              now-ts      (date/timestamp (test-datetime 2024 1 15 12 0))
              now         (test-datetime 2024 1 15 12 0)
              application {:id               "LP-753-2024-00001"
                           :organization     "753-R"
                           :permitType       "R"
                           :state            "submitted"
                           :submitted        submitted-ts
                           :primaryOperation {:name "pientalo"}}]
          (with-redefs [date/now             (constantly now)
                        stats-e/delay-ranges (constantly [[delay-start now-ts]])
                        stats-e/delayed?     (constantly true)]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields
              (is (= :delayed (:status result)) "Currently in delay period")
              (is (= :special (:complexity result)))
              (is (= 4 (:used result)) "4 work days before delay started")
              (is (= {:y 0 :m 0 :d 4} (:used-period result)))
              (is (= 27 (:remaining result)) "31 - 4 = 27 remaining")
              (is (= {:y 0 :m 0 :d 27} (:remaining-period result)))
              (is (= 3 (:delays result)) "3 delay days so far (Jan 12-15)")
              (is (= 1707688799999 (:deadline result)) "Feb 11, 2024 23:59:59.999")
              (is (= 0 (:requests result)))))))

      (testing "Multiple non-overlapping delays"
        ;; Submitted Jan 8
        ;; Delay 1: Jan 10-12 (2 days)
        ;; Delay 2: Jan 17-19 (2 days)
        ;; Now is Jan 22 at 5 PM (14 calendar days from submission)
        ;; Work days = 14 - 4 delays = 10 days
        ;; :special = 1 month, base deadline Feb 8, +4 delay days = Feb 12 23:59:59.999
        (let [delay1-start (date/timestamp (test-datetime 2024 1 10 9 0))
              delay1-end   (date/timestamp (test-datetime 2024 1 12 9 0))
              delay2-start (date/timestamp (test-datetime 2024 1 17 9 0))
              delay2-end   (date/timestamp (test-datetime 2024 1 19 9 0))
              now          (test-datetime 2024 1 22 17 0)
              application  {:id               "LP-753-2024-00001"
                            :organization     "753-R"
                            :permitType       "R"
                            :state            "submitted"
                            :submitted        submitted-ts
                            :primaryOperation {:name "pientalo"}}]
          (with-redefs [date/now             (constantly now)
                        stats-e/delay-ranges (constantly [[delay1-start delay1-end]
                                                          [delay2-start delay2-end]])]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields
              (is (= :running (:status result)))
              (is (= :special (:complexity result)))
              (is (= 10 (:used result)) "14 calendar days - 4 delay days = 10 work days")
              (is (= {:y 0 :m 0 :d 10} (:used-period result)))
              (is (= 21 (:remaining result)) "31 - 10 = 21 remaining")
              (is (= {:y 0 :m 0 :d 21} (:remaining-period result)))
              (is (= 4 (:delays result)) "2 + 2 = 4 days of delays")
              (is (= 1707775199999 (:deadline result)) "Feb 12, 2024 23:59:59.999")
              (is (= 0 (:requests result)))))))

      (testing "Delay extends deadline - comparison without delay"
        ;; Submitted Jan 8, :special = 1 month deadline = Feb 8
        ;; Now is Jan 25 at noon (17 calendar days from submission)
        ;; No delays: 17 work days used, 14 remaining
        (let [now         (test-datetime 2024 1 25 12 0)
              application {:id               "LP-753-2024-00001"
                           :organization     "753-R"
                           :permitType       "R"
                           :state            "submitted"
                           :submitted        submitted-ts
                           :primaryOperation {:name "pientalo"}}]
          (with-redefs [date/now             (constantly now)
                        stats-e/delay-ranges (constantly [])]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields - no delay case
              (is (= :running (:status result)))
              (is (= :special (:complexity result)))
              (is (= 17 (:used result)) "17 calendar days = 17 work days")
              (is (= {:y 0 :m 0 :d 17} (:used-period result)))
              (is (= 14 (:remaining result)) "31 - 17 = 14 remaining")
              (is (= {:y 0 :m 0 :d 14} (:remaining-period result)))
              (is (= 0 (:delays result)) "No delays")
              (is (= 1707429599999 (:deadline result)) "Feb 8, 2024 23:59:59.999")
              (is (= 0 (:requests result)))))))

      (testing "Delay extends deadline - comparison with 7-day delay"
        ;; Submitted Jan 8, :special = 1 month
        ;; Delay: Jan 15-22 (7 days)
        ;; Now is Jan 25 at noon (17 calendar days, but 10 work days due to delay)
        ;; With 7-day delay: deadline extended to Feb 15 23:59:59.999
        (let [delay-start (date/timestamp (test-datetime 2024 1 15 9 0))
              delay-end   (date/timestamp (test-datetime 2024 1 22 9 0))
              now         (test-datetime 2024 1 25 12 0)
              application {:id               "LP-753-2024-00001"
                           :organization     "753-R"
                           :permitType       "R"
                           :state            "submitted"
                           :submitted        submitted-ts
                           :primaryOperation {:name "pientalo"}}]
          (with-redefs [date/now             (constantly now)
                        stats-e/delay-ranges (constantly [[delay-start delay-end]])]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields - with 7-day delay
              (is (= :running (:status result)))
              (is (= :special (:complexity result)))
              (is (= 10 (:used result)) "17 calendar days - 7 delay days = 10 work days")
              (is (= {:y 0 :m 0 :d 10} (:used-period result)))
              (is (= 21 (:remaining result)) "31 - 10 = 21 remaining")
              (is (= {:y 0 :m 0 :d 21} (:remaining-period result)))
              (is (= 7 (:delays result)) "7 days of delay")
              (is (= 1708034399999 (:deadline result)) "Feb 15, 2024 23:59:59.999")
              (is (= 0 (:requests result))))))))))

(deftest calculate-processing-with-verdict-test
  ;; Tests processing calculation when application has verdict.
  ;; Checks all 9 Processing schema fields: status, complexity, used, used-period,
  ;; remaining, remaining-period, delays, deadline, requests

  (let [submitted-ts (date/timestamp (test-datetime 2024 1 8 9 0))]
    (with-redefs [stats-e/delay-ranges                 (constantly [])
                  stats-e/application-open-entry-count (constantly 0)]

      (testing "Finished status with exact verdict date"
        ;; Submitted Jan 8, verdict given Jan 22 at 15:30 (14 calendar days)
        ;; :special = 1 month, deadline Feb 8 23:59:59.999
        ;; Used: 14 days, remaining: 31 - 14 = 17 days
        (let [verdict-ts  (date/timestamp (test-datetime 2024 1 22 15 30))
              now         (test-datetime 2024 1 25 12 0)
              application {:id               "LP-753-2024-00001"
                           :organization     "753-R"
                           :permitType       "R"
                           :state            "verdictGiven"
                           :submitted        submitted-ts
                           :primaryOperation {:name "pientalo"}
                           :pate-verdicts    [{:category  "r"
                                               :published {:published verdict-ts}}]}]
          (with-redefs [date/now (constantly now)]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields
              (is (= :finished (:status result)))
              (is (= :special (:complexity result)))
              (is (= 14 (:used result)) "14 days from Jan 8 to Jan 22")
              (is (= {:y 0 :m 0 :d 14} (:used-period result)))
              (is (= 17 (:remaining result)) "31 - 14 = 17 days remaining")
              (is (= {:y 0 :m 0 :d 17} (:remaining-period result)))
              (is (= 0 (:delays result)))
              (is (= 1707429599999 (:deadline result)) "Feb 8, 2024 23:59:59.999")
              (is (= 0 (:requests result)))))))

      (testing "Finished with delays counted correctly"
        ;; Submitted Jan 8, 3-day delay Jan 10-13, verdict Jan 22
        ;; Used: 14 calendar days - 3 delay days = 11 work days
        ;; Remaining: 31 - 11 = 20 days
        ;; Deadline extended by 3 days: Feb 11 23:59:59.999
        (let [delay-start (date/timestamp (test-datetime 2024 1 10 9 0))
              delay-end   (date/timestamp (test-datetime 2024 1 13 9 0))
              verdict-ts  (date/timestamp (test-datetime 2024 1 22 15 30))
              now         (test-datetime 2024 1 25 12 0)
              application {:id               "LP-753-2024-00001"
                           :organization     "753-R"
                           :permitType       "R"
                           :state            "verdictGiven"
                           :submitted        submitted-ts
                           :primaryOperation {:name "pientalo"}
                           :pate-verdicts    [{:category  "r"
                                               :published {:published verdict-ts}}]}]
          (with-redefs [date/now             (constantly now)
                        stats-e/delay-ranges (constantly [[delay-start delay-end]])]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields
              (is (= :finished (:status result)))
              (is (= :special (:complexity result)))
              (is (= 11 (:used result)) "14 calendar days - 3 delay days = 11 work days")
              (is (= {:y 0 :m 0 :d 11} (:used-period result)))
              (is (= 20 (:remaining result)) "31 - 11 = 20 days remaining")
              (is (= {:y 0 :m 0 :d 20} (:remaining-period result)))
              (is (= 3 (:delays result)) "3 days of delay")
              (is (= 1707688799999 (:deadline result)) "Feb 11, 2024 23:59:59.999 (extended by 3 days)")
              (is (= 0 (:requests result)))))))

      (testing "Finished status overrides delayed status"
        ;; Even if currently in a delay range, verdict makes it :finished
        ;; Delay range spans 1 day around now-ts
        ;; Used: 14 calendar days - 1 delay day = 13 work days
        ;; Remaining: 31 - 13 = 18 days
        (let [verdict-ts  (date/timestamp (test-datetime 2024 1 22 15 30))
              now-ts      (date/timestamp (test-datetime 2024 1 25 12 0))
              delay-range [(- now-ts 1000) (+ now-ts 86400000)] ; ~1 day delay
              application {:id               "LP-753-2024-00001"
                           :organization     "753-R"
                           :permitType       "R"
                           :state            "verdictGiven"
                           :submitted        submitted-ts
                           :primaryOperation {:name "pientalo"}
                           :pate-verdicts    [{:category  "r"
                                               :published {:published verdict-ts}}]}]
          (with-redefs [date/now             (constantly (test-datetime 2024 1 25 12 0))
                        stats-e/delay-ranges (constantly [delay-range])]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields
              (is (= :finished (:status result)) "Finished takes priority over delayed")
              (is (= :special (:complexity result)))
              (is (= 13 (:used result)) "14 calendar days - 1 delay day = 13 work days")
              (is (= {:y 0 :m 0 :d 13} (:used-period result)))
              (is (= 18 (:remaining result)) "31 - 13 = 18 days remaining")
              (is (= {:y 0 :m 0 :d 18} (:remaining-period result)))
              (is (= 1 (:delays result)) "1 day of delay")
              (is (= 1707515999999 (:deadline result)) "Feb 9, 2024 23:59:59.999 (extended by 1 day)")
              (is (= 0 (:requests result)))))))

      (testing "Verdict given after deadline (past due)"
        ;; Submitted Jan 8, :special = 1 month, deadline Feb 8
        ;; Verdict given Feb 15 (7 days past deadline)
        ;; Used: 38 days (Jan 8 to Feb 15)
        ;; Remaining: 31 - 38 = -7 days (negative = overdue)
        (let [verdict-ts  (date/timestamp (test-datetime 2024 2 15 15 30))
              now         (test-datetime 2024 2 20 12 0)
              application {:id               "LP-753-2024-00001"
                           :organization     "753-R"
                           :permitType       "R"
                           :state            "verdictGiven"
                           :submitted        submitted-ts
                           :primaryOperation {:name "pientalo"}
                           :pate-verdicts    [{:category  "r"
                                               :published {:published verdict-ts}}]}]
          (with-redefs [date/now (constantly now)]
            (let [result (stats-db/calculate-processing application :special)]
              ;; All 9 Processing schema fields
              (is (= :finished (:status result)))
              (is (= :special (:complexity result)))
              (is (= 38 (:used result)) "38 days from Jan 8 to Feb 15")
              (is (= {:y 0 :m 1 :d 7} (:used-period result)) "1 month and 7 days")
              (is (= -6 (:remaining result)) "Negative remaining = 6 days overdue")
              (is (= {:y 0 :m 0 :d -7} (:remaining-period result)))
              (is (= 0 (:delays result)))
              (is (= 1707429599999 (:deadline result)) "Feb 8, 2024 23:59:59.999 (original deadline)")
              (is (= 0 (:requests result))))))))))

(defn endstamp [zoned]
  (date/timestamp (date/end-of-day zoned)))

(deftest calculate-processing-complexity-deadlines-test
  ;; Tests that different complexities produce correct deadline dates.

  (let [submitted-ts (date/timestamp (test-datetime 2024 1 8 9 0))
        ;; Jan 8 + 1 month = Feb 8 (end of day)
        ;; Jan 8 + 3 months = Apr 8 (end of day)
        ;; Jan 8 + 6 months = Jul 8 (end of day)
        now         (test-datetime 2024 1 8 12 0)
        application {:id               "LP-753-2024-00001"
                     :organization     "753-R"
                     :permitType       "R"
                     :state            "submitted"
                     :submitted        submitted-ts
                     :primaryOperation {:name "pientalo"}}]

    (with-redefs [date/now                             (constantly now)
                  stats-e/delay-ranges                 (constantly [])
                  stats-e/application-open-entry-count (constantly 0)]

      (doseq [[complexity date] [[:special "8.2.2024"] [:small "8.4.2024"]
                                 [:medium "8.4.2024"] [:large "8.4.2024"]
                                 [:very-large "8.7.2024"] [:extra-large "8.7.2024"]]
              :let [result (stats-db/calculate-processing application complexity)]]
        (testing (str complexity
                      " complexity -> deadline at the end of "
                      date)
          (is (= complexity (:complexity result)))
          (is (= (endstamp date) (:deadline result))))))))

(deftest calculate-processing-open-requests-test
  ;; Tests that open request count is correctly included in results.

  (let [submitted-ts (date/timestamp (test-datetime 2024 1 8 9 0))
        now          (test-datetime 2024 1 15 12 0)
        application  {:id               "LP-753-2024-00001"
                      :organization     "753-R"
                      :permitType       "R"
                      :state            "submitted"
                      :submitted        submitted-ts
                      :primaryOperation {:name "pientalo"}}]

    (with-redefs [date/now             (constantly now)
                  stats-e/delay-ranges (constantly [])]

      (testing "Zero open requests"
        (with-redefs [stats-e/application-open-entry-count (constantly 0)]
          (let [result (stats-db/calculate-processing application :medium)]
            (is (= 0 (:requests result))))))

      (testing "Multiple open requests"
        (with-redefs [stats-e/application-open-entry-count (constantly 5)]
          (let [result (stats-db/calculate-processing application :medium)]
            (is (= 5 (:requests result))))))

      (testing "Many open requests"
        (with-redefs [stats-e/application-open-entry-count (constantly 42)]
          (let [result (stats-db/calculate-processing application :medium)]
            (is (= 42 (:requests result)))))))))
