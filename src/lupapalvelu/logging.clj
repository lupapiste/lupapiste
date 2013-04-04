(ns lupapalvelu.logging
  (:use [clojure.tools.logging]
        [clj-logging-config.log4j])
  (:require [sade.env :as env]
            [sade.util :as util]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [org.apache.log4j FileAppender EnhancedPatternLayout]))

(def pattern "%-7p %d (%r) [%X{sessionId}] [%X{applicationId}] [%X{userId}] %c:%L - %m%n")

(defn to-file [file] (FileAppender. (EnhancedPatternLayout. pattern) file true))

(def default {:level env/log-level :pattern pattern})

(set-loggers! "sade"                 default
              "lupapalvelu"          default
              "ontodev.excel"        default
              "org"                  (assoc default :level :info)
              "events"               {:out (to-file (.getPath (io/file env/log-dir "logs" "events.log")))})

(defn unsecure-log-event [level event]
  (with-logs "events" level level
    (println event)))

(defn log-event [level event]
  (let [stripped (-> event
                   (dissoc :application)
                   (util/dissoc-in [:data :tempfile])) ; Temporary java.io.File set by ring
        jsoned   (json/generate-string stripped)]
    (try
      (unsecure-log-event level jsoned)
      (catch Exception e
        (error "can't write to event log:" stripped)))))
