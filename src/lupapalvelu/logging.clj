(ns lupapalvelu.logging
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as cs]
            [clojure.string :as s]
            [lupapalvelu.json :as json]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.encore :as encore]
            #_{:clj-kondo/ignore [:discouraged-namespace]}
            [taoensso.timbre :as timbre]
            [timbre-json-appender.core :as json-appender])
  (:import [org.joda.time.format DateTimeFormat DateTimeFormatter]))

(set! *warn-on-reflection* true)

;; Proxy the timbre API to guarantee this namespace is loaded before logging occurs
;; Unfortunately we cannot simply invoke the corresponding macro, as that causes timbre
;; to lose track of the line numbers

(defmacro log* [config level & args] `(timbre/log! ~level :p ~args ~{:loc (encore/get-source &form &env) :config config}))
(defmacro log [level & args] `(timbre/log! ~level :p ~args ~{:loc (encore/get-source &form &env)}))
(defmacro trace [& args] `(timbre/log! :trace :p ~args ~{:loc (encore/get-source &form &env)}))
(defmacro debug [& args] `(timbre/log! :debug :p ~args ~{:loc (encore/get-source &form &env)}))
(defmacro info [& args] `(timbre/log! :info :p ~args ~{:loc (encore/get-source &form &env)}))
(defmacro warn [& args] `(timbre/log! :warn :p ~args ~{:loc (encore/get-source &form &env)}))
(defmacro error [& args] `(timbre/log! :error :p ~args ~{:loc (encore/get-source &form &env)}))
(defmacro fatal [& args] `(timbre/log! :fatal :p ~args ~{:loc (encore/get-source &form &env)}))
(defmacro report [& args] `(timbre/log! :report :p ~args ~{:loc (encore/get-source &form &env)}))

(defmacro logf* [config level & args] `(timbre/log! ~level :f ~args ~{:loc (encore/get-source &form &env) :config config}))
(defmacro logf [level & args] `(timbre/log! ~level :f ~args ~{:loc (encore/get-source &form &env)}))
(defmacro tracef [& args] `(timbre/log! :trace :f ~args ~{:loc (encore/get-source &form &env)}))
(defmacro debugf [& args] `(timbre/log! :debug :f ~args ~{:loc (encore/get-source &form &env)}))
(defmacro infof [& args] `(timbre/log! :info :f ~args ~{:loc (encore/get-source &form &env)}))
(defmacro warnf [& args] `(timbre/log! :warn :f ~args ~{:loc (encore/get-source &form &env)}))
(defmacro errorf [& args] `(timbre/log! :error :f ~args ~{:loc (encore/get-source &form &env)}))
(defmacro fatalf [& args] `(timbre/log! :fatal :f ~args ~{:loc (encore/get-source &form &env)}))
(defmacro reportf [& args] `(timbre/log! :report :f ~args ~{:loc (encore/get-source &form &env)}))

(defmacro with-logging-context
  "Merges given logging context with timbres *context*.
   Context is available for appenders in timbre data map."
  [logging-context & body]
  (assert (map? logging-context) "logging-context must be a map")
  `(timbre/with-context+
     (util/strip-nils ~logging-context)
     ~@body))

(defn- output-fn
  "Logging output function"
  ([data] (output-fn {:stacktrace-fonts {}} data))
  ([opts data]
   (let [{:keys [level ?err msg_ ?ns-str timestamp_ context]} data
         {:keys [session-id applicationId userId]} context]
     (str
       (-> level name ss/upper-case)
       \space (force timestamp_) \space
       \[ session-id \] \space
       \[ applicationId \] \space
       \[ userId \] \space
       (or ?ns-str "unknown namespace") " - "
       (force msg_)
       (when ?err (str "\n" (timbre/stacktrace ?err opts)))))))

(def time-format "yyyy-MM-dd HH:mm:ss.SSS")

(defn- log-event?
  [[first-arg :as _vargs]]
  (boolean (and (map? first-arg) (:isEvent first-arg))))

(defn- produce-message
  [[first-arg :as vargs]]
  (let [message (ss/join " " vargs)]
    ;; Put logging args within a separate map, se we don't mix them
    ;; with various timbre fields or the logging context
    [message {:args (zipmap (range)
                            (if (string? first-arg)
                              (rest vargs)
                              vargs))}]))

(defn- combine-args-mw
  "Middleware for formatting log arguments as strings

  The `timbre-json-appender` library eagerly considers logging
  arguments to be data. Logging calls like `(timbre/warn :foo 42)`
  would result in a) no message and b) {:args {:foo 42}}

  See https://github.com/viesti/timbre-json-appender?tab=readme-ov-file#format-of-the-log-invocation

  To work around this, we can use this middleware to produce the
  leading message string (and also include the arguments in a map). "
  [{:keys [vargs msg-type] :as data}]
  (if (or (log-event? vargs) ; Don't alter event messages
          (= :f msg-type))   ; Don't alter infof, warnf, etc
    data
    (update data :vargs produce-message)))

(defn- string-stack-trace-mw
  "Adds a `stack_trace` field when logging errors for GCP.

  The `timbre-json-appender` already creates a nested map from the
  error with `Throwable->map`, but it seems error reporting in GCP
  does not recognize that format."
  [{:keys [^Throwable ?err] :as data}]
  (if (some? ?err)
    (update data :context assoc :stack_trace (with-out-str (cs/print-stack-trace ?err)))
    data))

(defn- rewrite-ns-mw
  "If logging an Event with a namespace, replace the call-site namespace with it"
  [{:keys       [?ns-str]
    [first-arg] :vargs
    :as         data}]
  (if-let [ns (and (map? first-arg) (:isEvent first-arg) (:ns first-arg))]
    (if (not= ns ?ns-str)
      (assoc data :?ns-str ns)
      data)
    data))

(defn- event->message
  [{:keys [event]}]
  (or
    ;; Event contains a literal string description (e.g. from a batchrun)
    (:event event)
    ;; .. or event is generated from a command
    (when (and (:type event) (:action event))
      (format "%s: %s" (ss/capitalize (name (:type event))) (:action event)))))

(defn- append-message-to-event-mw
  [{[first-arg] :vargs
    :as         data}]
  (if (and (map? first-arg) (:isEvent first-arg))
    (if-let [message (event->message first-arg)]
      (update data :vargs (fn [vargs] (into [message] vargs)))
      data)
    data))

(defn- wrap-uncaught-exception-handler
  "Log uncaught exceptions with timbre. Delegates to the original handler, if any.

  Caveats:
  - Does nothing when running `lein -m whatever` or REPL, only when executing a `.jar`
  - Does not handle Clojure futures, agents (CLJ-1272)"
  []
  (let [default-handler (Thread/getDefaultUncaughtExceptionHandler)]
    (Thread/setDefaultUncaughtExceptionHandler
      (reify Thread$UncaughtExceptionHandler
        (uncaughtException [_ thread ex]
          (timbre/errorf ex "Uncaught exception on '%s': %s" (.getName thread) (.getMessage ex))
          (when default-handler
            (.uncaughtException default-handler thread ex)))))))

(defn- ns-filter
  "Returns a Timbre-compatible namespace filter configuration based on the
  :no-log-ns-regex environment property.

  If the property is set, it attempts to compile it into a regular expression
  pattern for the :deny filter. If the pattern is invalid, an error message is
  printed to STDOUT."
  []
  (when-let [prop (env/value :no-log-ns-regex)]
    (try
      {:ns-filter {:deny (re-pattern prop)}}
      (catch Exception e
        (println "----------- Bad no-log-ns-regex property:")
        (println (ex-message e))
        (println "-----------------------------------------")))))

(defn setup-logging!
  ([]
   (setup-logging! {}))
  ([{:keys [pretty? min-log-level]
     :or   {pretty?       false
            min-log-level env/log-level}}]
   (when (env/feature? :log-json)
     (json-appender/install {;; Put arguments (including logging context) in a separate `:args` map
                             :inline-args?        true
                             ;; Also include ns, file, and line in logs
                             :should-log-field-fn (constantly true)
                             :msg-key             :message
                             :level-key           :severity
                             :key-names           {:err :exception}
                             ;; Pretty print when developing
                             :pretty              pretty?}))
   (timbre/set-min-level! min-log-level)
   (timbre/merge-config! (merge {:min-level      [[#{"org.apache.http.*"
                                                     "org.apache.poi.*"
                                                     "org.mongodb.driver.*"}
                                                   :warn]
                                                  [#{"org.apache.pdfbox.*"}
                                                   :error]
                                                  [#{"lupapalvelu.*"
                                                     "sade.*"
                                                     "lupapiste-pubsub.*"}
                                                   min-log-level]
                                                  [#{"*"} :info]]
                                 :middleware     (vec (concat
                                                        (when (env/feature? :log-json)
                                                          [combine-args-mw
                                                           string-stack-trace-mw
                                                           rewrite-ns-mw
                                                           append-message-to-event-mw])))
                                 :timestamp-opts {:pattern  time-format
                                                  :timezone :jvm-default}}
                                (when-not (env/feature? :log-json)
                                  ;; Make sure output to STDOUT follows a specific format,
                                  ;; so that graylog can parse it
                                  {:output-fn output-fn})
                                (ns-filter)))
   (wrap-uncaught-exception-handler)))

;;
;; event log:
;;

(def ^DateTimeFormatter time-fmt (DateTimeFormat/forPattern time-format))
(def ^java.io.Writer event-log-out (when-not (env/feature? :log-json)
                                     (io/writer (io/file (doto (io/file env/log-dir "logs") (.mkdirs)) "events.log") :append true)))

(defn- level->severity
  [level]
  (get {:debug   :debug
        :info    :info
        :warning :warn
        :error   :error} level :warn))

(defn- unsecure-log-event
  ([level event]
   (unsecure-log-event level event nil))
  ([level event {:keys [ns]}]
   (if (env/feature? :log-json)
     (timbre/log
       (level->severity level)
       (-> {:isEvent   true
            :severity  level
            :timestamp (str (System/currentTimeMillis))
            :event     event}
           (util/assoc-when :context timbre/*context*
                            :ns (ss/blank-as-nil ns))))
     (do
       (.write event-log-out (str (output-fn {:level      level
                                              :timestamp_ (delay (.print time-fmt (System/currentTimeMillis)))
                                              :?ns-str    ns
                                              :context    timbre/*context*})
                                  (json/encode event)
                                  \newline))
       (.flush event-log-out)))))

(defn log-event [level event]
  (let [stripped (-> event
                     (dissoc :application :application-assignments :application-bulletins
                             :organization :user-organizations :permissions :document
                             :company :ns :system)
                     (util/dissoc-in [:data :tempfile]) ; Temporary java.io.File set by ring
                     (util/dissoc-in [:web :bearer]) ; Bearer token
                     (update-in [:data :files] (partial map #(if (:tempfile %)
                                                               (dissoc % :tempfile)
                                                               %))) ; data in multipart/form-data w/ POST
                     (update :data (fn [m] (if-not (seq (:files m)) (dissoc m :files) m))) ; strip empty data.files
                     (util/strip-empty-maps))]
    (try
      (unsecure-log-event level stripped {:ns (:ns event)})
      (catch Exception e
        (error e "Can't write to event log:" (str stripped))))))

(defn sanitize
  "Replaces newlines and limits length"
  [limit ^String s]
  (ss/limit (ss/replace (str s) #"(\r?\n|\r)" (s/re-quote-replacement "\\n")) limit "... (truncated)"))

(setup-logging!)
