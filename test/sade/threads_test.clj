(ns sade.threads-test
  (:require [midje.sweet :refer :all]
            [sade.threads :refer :all]))

(fact "one thread"
  (let [result (atom [])
        pool   (threadpool 1 "lonely worker")
        thread (submit pool (Thread/sleep 100) (swap! result conj "done"))]
    (wait-for-threads [thread])
    @result => ["done"]))

(fact "poolsize 1 - threads finish in original order"
  (let [result  (atom [])
        pool    (threadpool 1 "lonely worker")
        threads (map #(submit pool
                              (Thread/sleep (+ 60 (* 150 (rem % 3))))
                              (swap! result conj %)) (range 6))]
    (wait-for-threads threads)
    @result => [0 1 2 3 4 5]))

(fact "poolsize 3 - result order depends on Thread/sleep"
  (let [result  (atom [])
        pool    (threadpool 3 "some worker")
        threads (map #(submit pool
                              (Thread/sleep (+ 60 (* 150 (rem % 3))))
                              (swap! result conj %))
                     (range 7))]
    (wait-for-threads threads)
    ;; n:      [0    1    2    3    4    5    6]
    ;; worker: [0    1    2    0    0    1    0]
    ;; start:  [0    0    0    60   120  210  330]
    ;; durat:  [60   210  360  60   210  360  60]
    ;; end:    [60   210  360  120  330  570  390]
    @result => [0 3 1 4 2 6 5]))
