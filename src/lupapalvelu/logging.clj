(ns lupapalvelu.logging
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal spy with-log-level]]
            [clojure.string :as s]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [org.joda.time.format DateTimeFormat DateTimeFormatter]))

(defmacro with-logging-context
  "Merges given logging context with timbres *context*.
   Context is available for appedners in timbre data map."
  [logging-context & body]
  (assert (map? logging-context) "logging-context must be a map")
  `(timbre/with-context
     (merge timbre/*context* ~logging-context)
     ~@body))

(defn- output-fn
  "Logging output function"
  ([data] (output-fn {:stacktrace-fonts {}} data))
  ([opts data]
    (let [{:keys [level ?err msg_ ?ns-str timestamp_ context]} data
          {:keys [session-id applicationId userId]} context]
      (str
        (-> level name s/upper-case)
        \space (force timestamp_) \space
        \[ session-id \] \space
        \[ applicationId \] \space
        \[ userId \] \space
        (or ?ns-str "unknown namespace") " - "
        (force msg_)
        (when ?err (str "\n" (timbre/stacktrace ?err opts)))))))

(def time-format "yyyy-MM-dd HH:mm:ss.SSS")

(timbre/set-level! env/log-level)
(timbre/merge-config! {:timestamp-opts {:pattern time-format
                                        :timezone :jvm-default}
                       :output-fn output-fn})

;;
;; event log:
;;

(def- ^DateTimeFormatter time-fmt (DateTimeFormat/forPattern time-format))
(def- ^java.io.Writer event-log-out (io/writer (io/file (doto (io/file env/log-dir "logs") (.mkdirs)) "events.log") :append true))

(defn- unsecure-log-event
  ([level event]
    (unsecure-log-event level event nil))
  ([level event opts]
   (.write event-log-out (str (output-fn {:level level
                                          :timestamp_ (delay (.print time-fmt (System/currentTimeMillis)))
                                          :?ns-str (:ns opts)
                                          :context timbre/*context*})
                              event
                              \newline))
   (.flush event-log-out)))

(defn log-event [level event]
  (let [stripped (-> event
                   (dissoc :application :organization :user-organizations :ns)
                   (util/dissoc-in [:data :tempfile]) ; Temporary java.io.File set by ring
                   (update-in [:data :files] (partial map #(if (:tempfile %)
                                                             (dissoc % :tempfile)
                                                             %)))) ; data in multipart/form-data w/ POST
        jsoned   (json/generate-string stripped)]
    (try
      (unsecure-log-event level jsoned {:ns (str (:ns event))})
      (catch Exception e
        (error e "Can't write to event log:" jsoned)))))

(defn sanitize
  "Replaces newlines and limits length"
  [limit ^String s]
  (ss/limit (s/replace (str s) #"(\r?\n|\r)" (s/re-quote-replacement "\\n")) limit "... (truncated)"))
