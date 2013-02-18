(ns lupapalvelu.perf-mon
  (:require [clojure.string :refer [join]]))

(comment
  ; Prints call hierarchy:
  (defn perf-logger
    ([context]
      (println (with-out-str (perf-logger context ""))))
    ([[[f-name args duration sub-contexts] & r] indent]
      (println indent f-name (format "%.3f ms" (/ duration 1000000.0)))
      (doseq [sub-context sub-contexts]
        (perf-logger [sub-context] (str indent "   ")))
      (when r (perf-logger r indent)))))

(defn- append-summary [summary [f-name args duration sub-contexts]]
  (let [[c v] (get summary f-name [0 0.0])
        summary (assoc summary f-name [(inc c) (+ v duration)])
        summary (reduce append-summary summary sub-contexts)]
    summary))

(defn perf-logger [context]
  (let [summary (reduce append-summary {} context)]
    (println
      (with-out-str
        (let [k (ffirst context)
              [c v] (get summary k)]
          (println (format "%s: %5d:  %9.3f ms (%.3f ms)" k c (/ v 1000000.0) (/ (/ v 1000000.0) c)))
          (doseq [[k [c v]] (dissoc summary k)]
            (println (format "   %-60s: %5d:  %9.3f ms (%.3f ms)" k c (/ v 1000000.0) (/ (/ v 1000000.0) c)))))))))

(def ^:dynamic *perf-context* nil)

(defn- wrap-perf [f f-name]
  (fn [& args]
    (let [context *perf-context*
          sub-context (atom [])]
      (binding [*perf-context* sub-context]
        (let [start (System/nanoTime)
              result (apply f args)
              end (System/nanoTime)]
          (if context
            (swap! context conj [f-name args (- end start) @sub-context])
            (perf-logger [[f-name args (- end start) @sub-context]]))
          result)))))

(defn instrument [v]
  (alter-var-root v wrap-perf (str (. v ns) \/ (. v sym))))

(defn instrument-ns [& namespaces]
  (doseq [n namespaces
          v (filter (comp fn? deref) (vals (ns-publics n)))]
    (instrument v)))


(comment
  
(defn- wrap-perf [f f-name]
  (fn [& args]
    (if-let [content *perf-context*]
      (let [start (System/nanoTime)]
        (try
          (apply f args)
          (finally
            (let [end (System/nanoTime)]
              (println (format "TIME: %d" (- end start)))))))
      (apply f args))))

(defmacro t [& body]
  `(/ (reduce min (for [i# (range 100)]
                    (let [start# (System/nanoTime)]
                      (dotimes [x# 1000]
                        ~@body)
                      (- (System/nanoTime) start#)))) 1000.0))

(defn f0 [])
(defn fm []
  (m/select :applications {:_id "511b80bce5083468a2f384c7"}))

(t (fm))
(instrument (var fm))

(str (. (var f0) ns) \/ (. (var f0) sym))

(defn f1 []
  (f0))

(defn f2 []
  (if-let [context *perf-context*]
    (f0)
    (f0)))

(defn f3 []
  ((fn [] (f0))))

(let [t0 (t (f0))
      t1 (t (f1))
      t2 (t (f2))
      t3 (t (f3))]
  (println (format "%.3f\n%.3f\n%.3f\n%.3f" t0 t1 t2 t3)))

)