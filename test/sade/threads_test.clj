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
