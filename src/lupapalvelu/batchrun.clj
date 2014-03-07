(ns lupapalvelu.batchrun)


; default pattern is on monday noon
(def ^:dynamic ^:String *send-interval-pattern* "0 12 * * 1")

(defn start-watch-email-scheduler []
  ;; TODO
  (log/info "Starting watch email scheduler with pattern" *send-interval-pattern*)
;  (cron/repeatedly-schedule *send-interval-pattern* send-emails-for-all-watches)
  )