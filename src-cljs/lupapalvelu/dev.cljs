(ns lupapalvelu.dev
  "Dev tools

  Vs. lupafront.dev this is also enableable on the QA env.

  To enable on QA, call on JS console:
  lupapalvelu.dev.enable();

  To disable:
  lupapalvelu.dev.disable();"
  (:require [shadow.lazy :as lazy]))

(def impl-start
  #_{:clj-kondo/ignore [:unresolved-namespace]}
  (lazy/loadable lupapalvelu.dev.impl/start!))

(defn enabled? []
  (let [mode (.. js/LUPAPISTE -config -mode)]
    (or (= "dev" mode) ;; localhost, www-dev
        (and (= "qa" mode) ;; www-qa
             (some? (.getItem js/localStorage "dev-enabled"))))))

;; If enabled, load the real dev-tools modules
(defn start! []
  (when (enabled?)
    (lazy/load impl-start (fn [f] (f)))))

;; Export so these are callable on QA js-console
(defn ^:export enable []
  (.setItem js/localStorage "dev-enabled" "true")
  (start!))

(defn ^:export disable []
  (.removeItem js/localStorage "dev-enabled"))

;; Shadow module can be loaded once the initial load is ready
(.addEventListener js/window "load"
                   (fn []
                     (start!)))

