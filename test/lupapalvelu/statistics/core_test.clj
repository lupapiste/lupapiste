(ns lupapalvelu.statistics.core-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.statistics-test-util :refer [test-datetime test-fixture]]
            [lupapalvelu.statistics.core :as stats]
            [sade.date :as date]
            [sade.env :as env])
  (:import [java.time ZonedDateTime]))

(set! *warn-on-reflection* true)

(use-fixtures :once test-fixture)

(deftest verdict-ts-test
  (testing "Returns nil when no verdict"
    (is (nil? (stats/verdict-ts {})))
    (is (nil? (stats/verdict-ts {:pate-verdicts []})))
    (is (nil? (stats/verdict-ts {:verdicts []}))))

  (testing "Returns timestamp for Lupapiste verdict"
    (let [ts 1704697200000  ;; Some timestamp
          app {:pate-verdicts [{:category "r"
                                :published {:published ts}}]}]
      (is (= ts (stats/verdict-ts app)))))

  (testing "Returns earliest Lupapiste verdict when multiple exist"
    (let [ts1 1704697200000
          ts2 1704783600000
          app {:pate-verdicts [{:category "r"
                                :published {:published ts2}}
                               {:category "r"
                                :published {:published ts1}}]}]
      (is (= ts1 (stats/verdict-ts app)))))

  (testing "Returns timestamp for legacy verdict"
    (let [ts 1704697200000
          app {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm ts}]}]}]}]
      (is (= ts (stats/verdict-ts app)))))

  (testing "Returns earliest legacy verdict when multiple exist"
    (let [ts1 1704697200000
          ts2 1704783600000
          app {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm ts2}
                                                      {:paatospvm ts1}]}
                                       {:poytakirjat [{:paatospvm ts2}]}]}]}]
      (is (= ts1 (stats/verdict-ts app))))))

(deftest cutoff-ts-test
  (testing "Without verdict returns now + minute as timestamp"
    ;; Monday Jan 8 9:30
    (let [now       (test-datetime 2024 1 8 9 30)
          submitted (date/timestamp (test-datetime 2024 1 1 9 0))
          expected  (date/timestamp (date/plus now :minute))]
      (with-redefs [date/now (constantly now)]
        (is (= expected (stats/cutoff-ts {:submitted submitted}))))))

  (testing "With verdict returns verdict timestamp"
    (let [submitted  1704100000000
          verdict-ts 1704697200000
          app        {:submitted     submitted
                      :pate-verdicts [{:category  "r"
                                       :published {:published verdict-ts}}]}]
      (is (= verdict-ts (stats/cutoff-ts app)))))

  (testing "Returns verdict timestamp as-is"
    ;; Verdict at 9:45 should return exactly that time as timestamp
    (let [submitted  (date/timestamp (test-datetime 2024 1 1 9 0))
          verdict-ts (date/timestamp (test-datetime 2024 1 8 9 45))
          app        {:submitted     submitted
                      :pate-verdicts [{:category  "r"
                                       :published {:published verdict-ts}}]}]
      (is (= verdict-ts (stats/cutoff-ts app)))))

  (testing "Cutoff is always a timestamp (number)"
    (let [submitted 1704100000000]
      (with-redefs [date/now (constantly (test-datetime 2024 1 8 9 0))]
        (is (number? (stats/cutoff-ts {:submitted submitted})))
        (is (number? (stats/cutoff-ts {:submitted     submitted
                                       :pate-verdicts [{:category  "r"
                                                        :published {:published 1704697200000}}]}))))))

  (testing "Sanity check: cutoff cannot be before submitted"
    (let [submitted (date/timestamp (test-datetime 2024 1 10 12 0))
          verdict   (date/timestamp (test-datetime 2024 1 8 12 0))]
      ;; Verdict is before submission (should not happen in real life, but logic handles it)
      (is (= submitted (stats/cutoff-ts {:submitted     submitted
                                         :pate-verdicts [{:category  "r"
                                                          :published {:published verdict}}]})))
      ;; Now is before submission
      (with-redefs [date/now (constantly (test-datetime 2024 1 8 12 0))]
        (is (= submitted (stats/cutoff-ts {:submitted submitted})))))))

(deftest original-deadline-test
  (testing "Calculates deadline from submitted date with month offset"
    ;; Submitted on Monday Jan 8, 2024, 9:00 + 2 months = March 8, 2024
    (let [submitted (test-datetime 2024 1 8 9 0)
          app {:submitted (date/timestamp submitted)}
          deadline (stats/original-deadline app 2)]
      ;; Result should be a ZonedDateTime
      (is (instance? ZonedDateTime deadline))
      ;; March 8, 2024 is a Friday (work day)
      (is (= 2024 (.getYear deadline)))
      (is (= 3 (.getMonthValue deadline)))
      (is (= 8 (.getDayOfMonth deadline)))))

  (testing "Forward to next work day if lands on weekend"
    ;; Submitted on Friday Dec 8, 2023, 9:00 + 2 months = Feb 8, 2024
    ;; But let's test a case that lands on Saturday
    ;; Submitted on Saturday Nov 9, 2024 + 3 months = Feb 9, 2025 (Sunday) -> Feb 10 (Monday)
    (let [submitted (test-datetime 2024 11 9 9 0)
          app {:submitted (date/timestamp submitted)}
          deadline (stats/original-deadline app 3)]
      (is (instance? ZonedDateTime deadline))
      ;; Should forward to next work day
      (is (= 2025 (.getYear deadline)))
      (is (= 2 (.getMonthValue deadline)))))

  (testing "Different month offsets"
    (let [submitted (test-datetime 2024 1 15 9 0)
          app {:submitted (date/timestamp submitted)}]
      ;; 1 month offset
      (let [deadline (stats/original-deadline app 1)]
        (is (= 2 (.getMonthValue deadline)))
        (is (= 15 (.getDayOfMonth deadline))))
      ;; 6 month offset
      (let [deadline (stats/original-deadline app 6)]
        (is (= 7 (.getMonthValue deadline)))
        (is (= 15 (.getDayOfMonth deadline)))))))

(deftest unsupported-change-permit?-test
  (testing "Returns true when change permit source application exists but has no processing status"
    (with-redefs [app-utils/source-application-info   (constantly {:id "LP-123-2026-90001"
                                                                    :kind :change})
                  app-utils/source-application        (constantly nil)
                  app-utils/application-link-permits  (constantly nil)]
      (is (true? (stats/unsupported-change-permit? {:id "LP-123-2026-90010"
                                                    :permitSubtype "muutoslupa"})))))

  (testing "Returns false when change permit source application has processing status"
    (with-redefs [app-utils/source-application-info   (constantly {:id "LP-123-2026-90001"
                                                                    :kind :change})
                  app-utils/source-application        (constantly {:id "LP-123-2026-90001"})
                  app-utils/application-link-permits  (constantly nil)]
      (is (false? (stats/unsupported-change-permit? {:id "LP-123-2026-90010"
                                                     :permitSubtype "muutoslupa"})))))

  (testing "Returns true when source application info is missing and no link permits have processing status"
    (with-redefs [app-utils/source-application-info   (constantly nil)
                  app-utils/source-application        (constantly nil)
                  app-utils/application-link-permits  (constantly nil)]
      (is (true? (stats/unsupported-change-permit? {:id "LP-123-2026-90010"
                                                    :permitSubtype "muutoslupa"})))))

  (testing "Returns false when source application info is missing but a link permit has processing status"
    (with-redefs [app-utils/source-application-info   (constantly nil)
                  app-utils/source-application        (constantly nil)
                  app-utils/application-link-permits  (constantly '({:id "LP-123-2026-90011"}))]
      (is (false? (stats/unsupported-change-permit? {:id "LP-123-2026-90010"
                                                     :permitSubtype "muutoslupa"})))))

  (testing "Returns false for non-change permits"
    (is (false? (stats/unsupported-change-permit? {:permitSubtype "jotain muuta"})))
    (is (false? (stats/unsupported-change-permit? {})))))

(deftest processing-application?-test
  (let [a   {:permitType       "R"
             :state            "submitted"
             :submitted        (date/timestamp "31.12.2025")
             :primaryOperation {:name "pientalo"}}
        pa? stats/processing-application?]
    (testing "Valid processing application"
      (is (true? (pa? a)))
      (is (true? (pa? a :submitted)))
      (is (true? (pa? (-> a (assoc :state "sent") (dissoc :submitted)))))
      (is (true? (pa? (assoc a :primaryOperation {:name "purkaminen"}
                             :secondaryOperations [{:name "sisatila-muutos"}])))))

    (testing "Feature flag required"
      ;; We cannot use `env/with-feature-value` due to the `test-fixture`.
      (with-redefs [env/get-config (constantly (assoc-in (env/get-config)
                                                         [:feature :statistics-processing]
                                                         false))]
        (is (false? (pa? a)))))

    (testing "Only R permit type supported"
      (is (false? (pa? (assoc a :permitType "P"))))
      (is (false? (pa? (assoc a :permitType "YA")))))

    (testing "Foreman applications excluded"
      (is (false? (pa? (assoc-in a [:primaryOperation :name]
                                 "tyonjohtajan-nimeaminen-v2")))))

    (testing "Paper permits excluded"
      (is (false? (pa? (assoc-in a [:primaryOperation :name]
                                 "aiemmalla-luvalla-hakeminen")))))

    (testing "Converted applications excluded"
      (is (false? (pa? (assoc a :facta-imported true)))))

    (testing "Notices excluded"
      (is (false? (pa? (assoc-in a [:primaryOperation :name] "purkamisilmoitus")))))

    (testing "Info requests excluded"
      (is (false? (pa? (assoc a :infoRequest true)))))

    (testing "Pre-submission states excluded"
      (is (false? (pa? (assoc a :state "open"))))
      (is (false? (pa? (assoc a :state "draft")))))

    (testing "Unsupported change permits excluded"
      (with-redefs [stats/unsupported-change-permit? (constantly true)]
        (is (false? (pa? a)))))

    (testing "Submitted requirement with :submitted flag"
      (is (true? (pa? a :submitted)))
      (is (false? (pa? (dissoc a :submitted) :submitted))
          "Must have submitted timestamp when :submitted flag is passed")
      (is (true? (pa? (-> a (assoc :state "sent") (dissoc :submitted))))
          "Post-submission states OK without submitted timestamp")
      (is (false? (pa? (-> a (assoc :state "sent") (dissoc :submitted))
                       :submitted))
          "But fails if :submitted flag is explicitly required"))

    (with-redefs [env/get-config (constantly {:feature               {:statistics-processing true}
                                              :statistics-processing {:start-date "1.1.2026"}})]
      (testing "Submitted before start date"
        (is (false? (pa? a))))
      (testing "Submitted after start date"
        (is (true? (pa? (assoc a :submitted (date/timestamp "2.1.2026"))))))
      (testing "No start date configured"
        (is (true? (pa? (dissoc a :submitted))))))))
