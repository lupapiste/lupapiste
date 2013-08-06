(ns lupapalvelu.logging
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy with-log-level)]
            [sade.env :as env]
            [sade.util :as util]
            [cheshire.core :as json]
            [clojure.java.io :as io]))


(timbre/set-level! env/log-level)
(timbre/set-config! [:timestamp-pattern] "yyyy-MM-dd HH:mm:ss")

(defn unsecure-log-event [level event]
  ; FIXME: timbre 
  (println event)
  #_(with-logs "events" level level
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

; FIXME: timbre 
(comment
  
  (def pattern "%-7p %d (%r) [%X{sessionId}] [%X{applicationId}] [%X{userId}] %c:%L - %m%n")

  (defn to-file [file] (FileAppender. (EnhancedPatternLayout. pattern) file true))
  
  (def default {:level env/log-level :pattern pattern})
  
  (set-loggers!
    "sade"                 default
    "lupapalvelu"          default
    "ontodev.excel"        default
    "org"                  (assoc default :level :info)
    "events"               {:out (to-file (.getPath (io/file env/log-dir "logs" "events.log")))})

  )
