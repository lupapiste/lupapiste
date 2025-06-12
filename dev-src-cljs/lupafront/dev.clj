(ns lupafront.dev
  (:require [clojure.java.io :as io]
            [shadow.cljs.devtools.api :as shadow]))

(set! *warn-on-reflection* true)

(defn start-process [cmd]
  (let [;; Redirect output to stdout.
        builder (-> (ProcessBuilder. ^String/1 (into-array String cmd))
                    (.redirectError java.lang.ProcessBuilder$Redirect/INHERIT)
                    (.redirectOutput java.lang.ProcessBuilder$Redirect/INHERIT))
        process (.start builder)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (.destroy process))))
    process))

(defn watch
  {:shadow/requires-server true}
  [& _args]
  (doseq [f (-> (io/file "resources" "public" "lp-static" "js")
                file-seq
                rest ;; don't delete the folder itself
                reverse)]
    (io/delete-file f true))
  (start-process ["npm" "run" "sass:dev:watch"])
  (start-process ["npm" "run" "tailwind:dev:watch"])
  (shadow/watch :front)
  (shadow/watch :test-browser)
  (shadow/watch :ui-doc))
