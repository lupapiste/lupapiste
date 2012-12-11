(ns lupapalvelu.perf-test-app
  (:require lupapalvelu.perf-test)
  (:use lupapalvelu.perf-mon))

(comment
  
  ; Instrument public fn's from namespace like this:
  
  (perf-mon-instrument-ns 'lupapalvelu.perf-test)

  ; Run like this:
  
  (with-perf-mon
    (lupapalvelu.perf-test/d)
    (lupapalvelu.perf-test/c)))
