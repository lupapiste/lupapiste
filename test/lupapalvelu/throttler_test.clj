(ns lupapalvelu.throttler-test
  (:require [clojure.test :refer [deftest testing is]]
            [lupapalvelu.throttler :as throttler]))

(set! *warn-on-reflection* true)

(deftest throttler-slow-calls-test
  (testing "Throttler runs scheduled callback between invocations"
    (let [*queue   (atom [])
          *results (atom [])]
      (binding [throttler/schedule (fn [delay-ms f]
                                     (swap! *queue conj [delay-ms f]))]
        (let [delay-ms   20
              throttler  (throttler/make-throttler delay-ms)
              add-result (fn [value]
                           (fn []
                             (swap! *results conj value)))]

          ;; queue first call
          (throttler (add-result :bar))

          ;; no throttling yet => callback invoked immediately
          (is (= [:bar] (deref *results)))

          ;; run scheduled callback
          (let [q     (deref *queue)
                [d f] (nth q 0)]
            (is (= 1 (count q)))
            (is (= delay-ms d))

            ;; check for throttled functions => nothing to do
            (f)

            ;; no change in results
            (is (= [:bar] (deref *results))))

          ;; queue second call
          (throttler (add-result :quuz))

          ;; no throttling => callback invoked immediately
          (is (= [:bar :quuz] (deref *results))))))))

(deftest test-make-throttler
  (testing "Multiple invocations before throttler runs scheduled callback"
    (let [*queue   (atom [])
          *results (atom [])]
      (binding [throttler/schedule (fn [delay-ms f]
                                     (swap! *queue conj [delay-ms f]))]
        (let [delay-ms   20
              throttler  (throttler/make-throttler delay-ms)
              add-result (fn [value]
                           (fn []
                             (swap! *results conj value)))]

          ;; queue four calls
          (throttler (add-result 1))

          ;; no throttling yet => callback invoked immediately
          (is (= [1] (deref *results)))

          ;; invoke multiple times before we run the scheduled callback
          (throttler (add-result 2))
          (throttler (add-result 3))
          (throttler (add-result 4))

          ;; latter calls are now throttled => no yet invoked
          (is (= [1] (deref *results)))

          ;; start consuming the scheduled functions
          (let [q     (deref *queue)
                [d f] (nth q 0)]
            (is (= 1 (count q)))
            (is (= delay-ms d))

            ;; check for throttled functions => invokes callback, queues next check
            (f)

            ;; fourth call executed as it overwrote calls 2 & 3
            (is (= [1 4] (deref *results)))

            ;; run next check
            (let [q     (deref *queue)
                  [d f] (nth q 1)]
              (is (= 2 (count q)))
              (is (= delay-ms d))

              (f)

              ;; no change in results
              (is (= [1 4] (deref *results)))

              ;; nothing more queued
              (is (= 2 (count (deref *queue)))))))))))
