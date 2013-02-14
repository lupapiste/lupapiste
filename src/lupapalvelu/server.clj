(ns lupapalvelu.server
  (:use lupapalvelu.log)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.logging]
            [lupapalvelu.web]
            [lupapalvelu.vetuma]
            [lupapalvelu.env :as env]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.fixture.kind]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.action]
            [lupapalvelu.admin]
            [lupapalvelu.application]
            [lupapalvelu.authority-admin]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.commands]
            [lupapalvelu.perf-mon :as perf-mon]
            [lupapalvelu.user]
            [lupapalvelu.operations])
  (:gen-class))

(def custom-content-type {".eot"   "application/vnd.ms-fontobject"
                          ".ttf"   "font/ttf"
                          ".otf"   "font/otf"
                          ".woff"  "application/font-woff"})

(defn apply-custom-content-types
  "Ring middleware.
   If response does not have content-type header, and request uri ends to one of the
   keys in custom-content-type map, set content-type to value from custom-content-type."
  [handler]
  (fn [request]
    (let [resp (handler request)
          ct (get-in resp [:headers "Content-Type"])
          ext (re-find #"\.[^.]+$" (:uri request))
          neue-ct (get custom-content-type ext)]
      (if (and (nil? ct) (not (nil? neue-ct)))
        (assoc-in resp [:headers "Content-Type"] neue-ct)
        resp))))

(defn -main [& _]
  (info "Server starting")
  (info "Running on Java %s %s %s (%s)"
    (System/getProperty "java.vm.vendor")
    (System/getProperty "java.vm.name")
    (System/getProperty "java.runtime.version")
    (System/getProperty "java.vm.info"))
  (info "Running on Clojure %d.%d.%d"
    (:major *clojure-version*)
    (:minor *clojure-version*)
    (:incremental *clojure-version*))
  (when env/perf-mon-on
    (warn "*** Instrumenting performance monitoring")
    (perf-mon/instrument-ns
      'lupapalvelu.action
      'lupapalvelu.application
      'lupapalvelu.attachment
      'lupapalvelu.authority-admin
      'lupapalvelu.core
      'lupapalvelu.domain
      'lupapalvelu.mongo
      'lupapalvelu.security
      'lupapalvelu.tepa))
  (mongo/connect!)
  (server/add-middleware apply-custom-content-types)
  (server/start env/port {:mode env/mode
                          :jetty-options {:ssl? true
                                          :ssl-port 8443
                                          :keystore "./keystore"
                                          :key-password "lupapiste"}
                          :ns 'lupapalvelu.web
                          :session-cookie-attrs (:cookie env/config)})
  (info "Server running")
  (env/in-dev
    (warn "*** Applying test fixture")
    (fixture/apply-fixture "minimal")
    (warn "*** Starting nrepl")
    (nrepl/start-server :port 9000))
  ; Sensible return value for -main for repl use.
  "ready")

(comment
  (-main))
