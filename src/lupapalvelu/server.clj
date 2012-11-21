(ns lupapalvelu.server
  (:use lupapalvelu.log)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            (lupapalvelu [web]
                         [vetuma]
                         [env :as env]
                         [fixture :as fixture]
                         [fixture.kind]
                         [fixture.minimal]
                         [action]
                         [admin]
                         [authority-admin]
                         [mongo :as mongo]
                         [document.commands]))
  (:gen-class))

(def custom-content-type {".ttf" "font/ttf"})

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

(defn start-server [port mode]
  (server/start port {:mode mode :ns 'lupapalvelu.web})
  (server/add-middleware apply-custom-content-types))

(defn stop-server [server]
  (server/stop server))

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
  (mongo/connect!)
  (env/in-dev
    (warn "*** Applying test fixture")
    (fixture/apply-fixture "minimal")
    (warn "*** Starting nrepl")
    (nrepl/start-server :port 9000))
  (server/add-middleware apply-custom-content-types)
  (server/start env/port {:mode env/mode
                          :jetty-options {:ssl? true
                                          :ssl-port 8443
                                          :keystore "./keystore"
                                          :key-password "lupapiste"}
                          :ns 'lupapalvelu.web})
  (info "Server running"))
