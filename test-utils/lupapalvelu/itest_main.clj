(ns lupapalvelu.itest-main
  "A script that runs chunks of integration tests in parallel in separate processes."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [swiss.arrows :refer [-<>]])
  (:import [java.io File]
           [java.nio.file Files]))

(def ^:private midje-process-count
  "Approx. how many `lein midje` processes to use."
  (.availableProcessors (Runtime/getRuntime)))              ; As good a guess as any small int...

;;;; # File Predicates

(defn- file? [^File fl] (.isFile fl))

(defn- count-lines [^File fl] (.count (Files/lines (.toPath fl))))

(defn- clj-file? [^File fl] (str/ends-with? (.getName fl) ".clj"))

;;;; # File <-> Namespace Name String

(defn- itest-file->ns-name
  "(File. \"itest/foo/bar_itest.clj\") -> \"foo.bar-itest\""
  [^File fl]
  (let [segments (rest (iterator-seq (.. fl toPath iterator))) ; ("foo" "bar_itest.clj")
        joined (str/join "." segments)]                     ; "foo.bar_itest.clj"
    (-> joined
        (subs 0 (- (.length joined) 4))                     ; "foo.bar_itest"
        (str/replace "_" "-"))))                            ; "foo.bar-itest)))

(defn- ns-name->itest-file
  "\"foo.bar-itest\" -> (File. \"itest/foo/bar_itest.clj\")"
  [ns-name]
  (-<> ns-name
       (str/replace "-" "_")                                ; "foo.bar_itest"
       (str/replace "." "/")                                ; "foo/bar_itest"
       (str "itest/" <> ".clj")                             ; "itest/foo/bar_itest.clj"
       File.))

;;;; # Namespace Information Record

(defrecord NSInfo [name line-count])

(defn- ns-info
  ([file] (ns-info (itest-file->ns-name file) file))
  ([ns-name file] (NSInfo. ns-name (count-lines file))))

;;;; Splitting To Per-process Chunks

(defn- take-chunk [size-goal ns-infos]
  (loop [chunk [], chunk-size 0, ns-infos ns-infos]
    (if (and (seq ns-infos) (< chunk-size size-goal))
      (let [{:keys [line-count] :as ns-info} (first ns-infos)]
        (recur (conj chunk ns-info) (+ chunk-size line-count) (rest ns-infos)))
      [chunk ns-infos])))

(defn- leftover-chunk [ns-infos] (mapv :name ns-infos))

(defn- split-by-lines [chunk-count-goal ns-infos]
  (let [total-line-count (reduce (fn [sum {:keys [line-count]}] (+ sum line-count)) 0 ns-infos)
        lines-per-chunk (/ total-line-count chunk-count-goal)]
    (loop [chunks [], ns-infos ns-infos]
      (if (seq ns-infos)
        (let [[chunk ns-infos] (take-chunk lines-per-chunk ns-infos)]
          (recur (conj chunks chunk) ns-infos))
        (if (seq ns-infos)
          (conj chunks (leftover-chunk ns-infos))
          chunks)))))

;;;; Main and Associated Functions

(defn- ns-names->infos [ns-names]
  (map (fn [ns-name] (ns-info ns-name (ns-name->itest-file ns-name)))
       ns-names))

(defn- all-itest-ns-infos []
  (->> (file-seq (io/file "itest"))
       (filter (every-pred clj-file? file?))
       (map ns-info)))

(defn- combined-exit-code
  "Combine exit codes to match non-parallel run of `lein midje`."
  [sh-results]
  (min (transduce (map :exit) + 0 sh-results) 255))         ; Cap to 255 just like lein-midje.

(defn -main [& ns-names]
  (let [ns-infos (if (seq ns-names)
                   (ns-names->infos ns-names)
                   (all-itest-ns-infos))
        sh-results (->> (split-by-lines midje-process-count ns-infos)
                        (pmap (fn [ns-info-chunk]
                                (let [jvm-opts ["-Djava.awt.headless=true" "-Xmx1G"]
                                      ;; HACK: convey `target_server` property to children:
                                      jvm-opts (if-some [target-server (System/getProperty "target_server")]
                                                 (conj jvm-opts (str "-Dtarget_server=" target-server))
                                                 jvm-opts)]
                                  (apply sh "lein"
                                         "update-in" ":" "assoc" ":jvm-opts" (pr-str jvm-opts)
                                         "--"
                                         "trampoline" "with-profile" "dev,itest" "midje" (map :name ns-info-chunk))))))]
    (doseq [{:keys [out err]} sh-results]
      (println err)
      (println out))
    (System/exit (combined-exit-code sh-results))))
