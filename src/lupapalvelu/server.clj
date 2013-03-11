(ns lupapalvelu.server
  (:use clojure.tools.logging)
  (:require [noir.server :as server]
            [clojure.tools.nrepl.server :as nrepl]
            [lupapalvelu.logging]
            [lupapalvelu.web :as web]
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
            [lupapalvelu.user]
            [lupapalvelu.operations]
            [lupapalvelu.proxy-services]
            [sade.security-headers :as headers])
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
  (infof "Running on Java %s %s %s (%s) [%s]"
    (System/getProperty "java.vm.vendor")
    (System/getProperty "java.vm.name")
    (System/getProperty "java.runtime.version")
    (System/getProperty "java.vm.info")
    (if (java.awt.GraphicsEnvironment/isHeadless) "headless" "headful"))
  (infof "Running on Clojure %d.%d.%d"
    (:major *clojure-version*)
    (:minor *clojure-version*)
    (:incremental *clojure-version*))
  (mongo/connect!)
  (mongo/ensure-indexes)
  (server/add-middleware headers/session-id-to-mdc)
  (server/add-middleware apply-custom-content-types)
  (server/add-middleware headers/add-security-headers)
  (server/add-middleware web/anti-csrf)
  (server/add-middleware web/apikey-authentication)
  (env/in-dev
    (warn "*** Instrumenting performance monitoring")
    (require 'lupapalvelu.perf-mon)
    ((resolve 'lupapalvelu.perf-mon/init)))
  (env/in-dev
    (warn "*** Starting nrepl")
    (nrepl/start-server :port 9000))
  (with-logs "lupapalvelu"
    (server/start env/port {:mode env/mode
                            :ns 'lupapalvelu.web
                            :jetty-options {:ssl? true
                                            :ssl-port 8443
                                            :keystore "./keystore"
                                            :key-password "lupapiste"}
                            :session-cookie-attrs (:cookie env/config)}))
  "ok")

(comment
  (-main))
