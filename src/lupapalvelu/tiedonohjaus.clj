(ns lupapalvelu.tiedonohjaus
  (:require [sade.http :as http]
            [sade.env :as env]))

(defn- build-url [& path-parts]
  (apply str (env/value :toj :host) path-parts))

(defn get-functions-from-toj [organization]
  (let [response (http/get (build-url "/tiedonohjaus/api/org/" organization "/asiat") {:as :json})]
    (:body response)))
