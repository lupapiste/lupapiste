(ns lupapalvelu.dev.ui-doc.shadow-hook
  (:require [clojure.java.io :as io]
            [cljs.compiler.api :as cljs-comp]
            [jsonista.core :as json]))

;; TODO: Remove old .stories.json files before build?
;; or something to ensure there are no files for removed namespaces?

(defn hook
  {:shadow.build/stage :flush}
  [build-state & _args]
  (let [output-dir (get-in build-state [:build-options :output-dir])]
    (doseq [[ns-key attrs] (get-in build-state [:compiler-env :cljs.analyzer/namespaces])
            :let [storybook (get attrs :lupapalvelu.dev.ui-doc.util/storybook)]
            :when storybook]
      (json/write-value (io/file output-dir (str (name ns-key) ".stories.json"))
                        (vec (for [story (:stories storybook)]
                               {:type "story"
                                :importPath (str (io/file output-dir (str (name ns-key) ".js")))
                                :exportName (cljs-comp/munge (:name story))
                                :title (:title storybook)
                                ;; TODO: Could be parameter to defstory and added to metadata etc.
                                :tags ["autodocs"]})))))
  build-state)
