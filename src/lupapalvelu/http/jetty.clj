(ns lupapalvelu.http.jetty
  (:require [integrant.core :as ig]
            [lupapalvelu.http.jetty-schema :as jetty-schema]
            [lupapalvelu.logging :as log]
            [ring.adapter.jetty :as jetty])
  (:import [org.eclipse.jetty.server Server]))

(set! *warn-on-reflection* true)

(defmethod ig/assert-key ::server [_ args]
  (jetty-schema/valid-jetty-args! args))

(defmethod ig/init-key ::server [k {:keys [handler opts]}]
  (log/infof "Starting %s..." k)
  (let [^Server jetty-instance (jetty/run-jetty handler opts)]
    (.setStopTimeout jetty-instance 10000)
    {:server  jetty-instance
     :options (select-keys opts #{:port})}))

(defmethod ig/halt-key! ::server [k {:keys [^Server server]}]
  (log/infof "Stopping %s..." k)
  (assert server)
  (.stop server))
