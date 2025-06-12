(ns lupapalvelu.ui.statistics.entries-test
  "Tests for pure functions in lupapalvelu.ui.statistics.data.entries namespace."
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ui.statistics.data.entries :as entries]))

;; ----------------------------------
;; entry-state tests
;; ----------------------------------

(deftest entry-state-test
  (let [now (js/Date.now)]

    (testing ":deleted takes priority over all other fields"
      (is (= :deleted (entries/entry-state {:deleted?   true
                                            :type       :delay
                                            :delay-start (- now 10000)
                                            :answered-ts now
                                            :closed-ts   now}))))

    (testing ":delayed for delay entry with delay-start in past and no closed-ts"
      (is (= :delayed (entries/entry-state {:deleted?    false
                                            :type        :delay
                                            :delay-start (- now 10000)}))))

    (testing ":open for delay entry with delay-start in the future"
      (is (= :open (entries/entry-state {:deleted?    false
                                         :type        :delay
                                         :delay-start (+ now 10000)}))))

    (testing ":closed beats :answered"
      (is (= :closed (entries/entry-state {:deleted?    false
                                           :type        :request
                                           :answered-ts (- now 20000)
                                           :closed-ts   (- now 10000)}))))

    (testing ":delayed not triggered for :request entries (only :delay)"
      ;; A request entry with delay-start in past should not be :delayed
      (is (not= :delayed (entries/entry-state {:deleted?    false
                                               :type        :request
                                               :delay-start (- now 10000)}))))

    (testing ":closed once delay entry has a closed-ts"
      (is (= :closed (entries/entry-state {:deleted?    false
                                           :type        :delay
                                           :delay-start (- now 20000)
                                           :closed-ts   (- now 10000)}))))

    (testing ":answered when only answered-ts is set"
      (is (= :answered (entries/entry-state {:deleted?    false
                                             :type        :request
                                             :answered-ts (- now 10000)}))))

    (testing ":open when no special state applies"
      (is (= :open (entries/entry-state {:deleted? false
                                         :type     :request}))))))

;; ----------------------------------
;; state-order tests
;; ----------------------------------

(deftest state-order-test
  (let [open-entry     {:deleted? false :type :request}
        delayed-entry  {:deleted? false :type :delay :delay-start (- (js/Date.now) 10000)}
        answered-entry {:deleted? false :type :request :answered-ts (- (js/Date.now) 10000)}
        closed-entry   {:deleted? false :type :request :closed-ts (- (js/Date.now) 10000)}
        deleted-entry  {:deleted? true :type :request}]

    (testing "Open < delayed < answered < closed < deleted ordering"
      (is (< (entries/state-order open-entry)
             (entries/state-order delayed-entry)))
      (is (< (entries/state-order delayed-entry)
             (entries/state-order answered-entry)))
      (is (< (entries/state-order answered-entry)
             (entries/state-order closed-entry)))
      (is (< (entries/state-order closed-entry)
             (entries/state-order deleted-entry))))

    (testing "All 5 states produce distinct integer sort keys"
      (let [orders [(entries/state-order open-entry)
                    (entries/state-order delayed-entry)
                    (entries/state-order answered-entry)
                    (entries/state-order closed-entry)
                    (entries/state-order deleted-entry)]]
        ;; All distinct
        (is (= 5 (count (set orders))))
        ;; All integers
        (is (every? integer? orders))))))

;; ----------------------------------
;; process-auth-model tests
;; ----------------------------------

(deftest process-auth-model-test
  (testing "Filters to actions with :ok true"
    (let [input  {"entry-1" {:close {:ok true}
                             :edit  {:ok false}
                             :view  {:ok true}}
                  "entry-2" {:close {:ok true}}}
          result (entries/process-auth-model input)]
      (is (= {:entry-1 {:close true :view true}
              :entry-2 {:close true}}
             result))))

  (testing "Drops entries with no allowed actions"
    (let [input  {"entry-1" {:close {:ok false}
                             :edit  {:ok false}}
                  "entry-2" {:close {:ok true}}}
          result (entries/process-auth-model input)]
      ;; entry-1 should be dropped as all :ok false
      (is (= {:entry-2 {:close true}} result))))

  (testing "Empty input returns empty map"
    (is (= {} (entries/process-auth-model {}))))

  (testing "Nil values are handled"
    (let [input  {"entry-1" {:close {:ok true}
                             :edit  nil}}
          result (entries/process-auth-model input)]
      (is (= {:entry-1 {:close true}} result)))))

;; ----------------------------------
;; request? tests
;; ----------------------------------

(deftest request?-test
  (testing "Returns true for request type entries"
    (is (true? (entries/request? {:type :request}))))

  (testing "Returns false for delay type entries"
    (is (false? (entries/request? {:type :delay}))))

  (testing "Returns false for nil type"
    (is (false? (entries/request? {:type nil}))))

  (testing "Returns false for empty entry"
    (is (false? (entries/request? {})))))
