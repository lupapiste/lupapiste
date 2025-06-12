(ns lupapalvelu.ui.statistics.summary-test
  "Tests for subscriptions and events in lupapalvelu.ui.statistics.data.summary namespace.

  These tests verify the behavior of re-frame subscriptions by testing them
  in isolation with mock db states."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.ui.statistics.data.summary :as summary]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]))

;; ----------------------------------
;; Test fixtures
;; ----------------------------------

(defn reset-db-fixture
  "Fixture that resets app-db before and after each test."
  [f]
  (let [original-db @rf-db/app-db]
    (reset! rf-db/app-db {})
    (try
      (f)
      (finally
        (reset! rf-db/app-db original-db)))))

(use-fixtures :each reset-db-fixture)

;; ----------------------------------
;; Helper functions
;; ----------------------------------

(defn set-processing-days!
  "Helper to set processing days in the db for testing."
  [processing-days]
  (rf/dispatch-sync [::summary/set-processing-days processing-days]))

(defn get-sub
  "Helper to get subscription value synchronously."
  [sub-vec]
  @(rf/subscribe sub-vec))

;; ----------------------------------
;; ::complexity? subscription tests
;; ----------------------------------

(deftest complexity?-sub-test
  (testing "nil db returns false"
    (is (false? (get-sub [::summary/complexity?]))))

  (testing ":no-complexity status returns false (no :complexity key)"
    (set-processing-days! {:status "no-complexity"})
    (is (false? (get-sub [::summary/complexity?]))))

  (testing "status with complexity returns true"
    (set-processing-days! {:complexity "medium" :status "running"})
    (is (true? (get-sub [::summary/complexity?])))))

;; ----------------------------------
;; ::remaining-days subscription tests
;; ----------------------------------

(deftest remaining-days-sub-test
  (testing "nil db returns nil"
    (is (nil? (get-sub [::summary/remaining-days]))))

  (testing "positive remaining days"
    (set-processing-days! {:complexity "medium"
                           :status     "running"
                           :remaining  42})
    (is (= 42 (get-sub [::summary/remaining-days]))))

  (testing "negative remaining days (overdue)"
    (set-processing-days! {:complexity "medium"
                           :status     "running"
                           :remaining  -10})
    (is (= -10 (get-sub [::summary/remaining-days])))))

;; ----------------------------------
;; ::status subscription tests
;; ----------------------------------

(deftest status-sub-test
  (testing "nil db returns nil"
    (is (nil? (get-sub [::summary/status]))))

  (testing ":no-complexity status"
    (set-processing-days! {:status "no-complexity"})
    (is (= :no-complexity (get-sub [::summary/status]))))

  (testing ":running status"
    (set-processing-days! {:complexity "medium" :status "running"})
    (is (= :running (get-sub [::summary/status]))))

  (testing ":delayed status"
    (set-processing-days! {:complexity "medium" :status "delayed"})
    (is (= :delayed (get-sub [::summary/status]))))

  (testing ":finished status"
    (set-processing-days! {:complexity "medium" :status "finished"})
    (is (= :finished (get-sub [::summary/status])))))

;; ----------------------------------
;; ::set-processing-days event tests
;; ----------------------------------

(deftest set-processing-days-event-test
  (testing "nil input clears processing"
    (set-processing-days! {:complexity "medium" :status "running"})
    ;; Verify it was set
    (is (some? (get-sub [::summary/processing-days])))
    ;; Now clear it
    (set-processing-days! nil)
    (is (nil? (get-sub [::summary/processing-days]))))

  (testing "String status coerced to keyword via ->front-processing"
    (set-processing-days! {:status "running" :complexity "medium"})
    (is (= :running (get-sub [::summary/status]))))

  (testing "Processing days stored and readable via ::processing-days"
    (set-processing-days! {:complexity       "large"
                           :status           "running"
                           :used             10
                           :used-period      {:y 0 :m 0 :d 10}
                           :remaining        50
                           :remaining-period {:y 0 :m 1 :d 19}})
    (let [days (get-sub [::summary/processing-days])]
      (is (= :large (:complexity days)))
      (is (= :running (:status days)))
      (is (= 10 (:used days)))
      (is (= {:y 0 :m 0 :d 10} (:used-period days)))
      (is (= 50 (:remaining days)))
      (is (= {:y 0 :m 1 :d 19} (:remaining-period days))))))

;; ----------------------------------
;; ::editing subscription tests
;; ----------------------------------

(deftest editing-sub-test
  (testing "Initially not editing"
    (is (nil? (get-sub [::summary/editing])))
    (is (false? (get-sub [::summary/editing?]))))

  (testing "After toggle-edit, editing is active"
    (rf/dispatch-sync [::summary/toggle-edit true])
    (is (some? (get-sub [::summary/editing])))
    (is (true? (get-sub [::summary/editing?]))))

  (testing "Toggle-edit with false clears editing"
    (rf/dispatch-sync [::summary/toggle-edit true])
    (is (true? (get-sub [::summary/editing?])))
    (rf/dispatch-sync [::summary/toggle-edit false])
    (is (false? (get-sub [::summary/editing?])))))
