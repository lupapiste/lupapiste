(ns lupapalvelu.logging
  (:use [clojure.tools.logging]
        [clj-logging-config.log4j])
  (:require [lupapalvelu.env :as env])
  (:import [org.apache.log4j DailyRollingFileAppender EnhancedPatternLayout]))


(def pattern "%-7p %d (%r) %-25c %-4L - %X{sessionId} - %X{applicationId} - %m%n")
(defn daily-rolling-midnight-appender [pattern file]
  (DailyRollingFileAppender. (EnhancedPatternLayout. pattern) file "'.'yyyy-MM-dd"))

(def default {:level env/log-level :pattern pattern})

(set-loggers! "sade"                 default
              "lupapalvelu"          default
              "ontodev.excel"        default
              "org"                  (assoc default :level :info)
              "events"               {:out (daily-rolling-midnight-appender pattern "target/logs/events.log")})

(defn unsecure-log-event [level event]
  (with-logs "events" level level
    (println event)))

(defn log-event [level event]
  (try
    (unsecure-log-event level event)
    (catch Exception e
      (error "can't write to event log:" event))))
