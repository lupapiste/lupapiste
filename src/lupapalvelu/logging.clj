(ns lupapalvelu.logging
  (:use [clojure.tools.logging]
        [clj-logging-config.log4j]))

(defn daily-appender [pattern file]
  (org.apache.log4j.FileAppender.
    (org.apache.log4j.EnhancedPatternLayout. pattern)
    file
    true))

(set-loggers! ["sade" "lupapiste"]
              {:level :debug
               :pattern "%-5p %d (%r) %c:%L %X{sessionId} %X{applicationId} %m%n"}

              "events"
              {:out (daily-appender "%-5p %d (%r) %c:%L %X{sessionId} %X{applicationId} %m%n" "target/logs/foo.log")})

(defn action-log [& s]
  (with-logs "events" (apply println s)))