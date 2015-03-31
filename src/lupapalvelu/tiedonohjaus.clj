(ns lupapalvelu.tiedonohjaus
  (:require [sade.http :as http]))

(defn get-functions-from-toj [organization]
  (let [response (http/get (str "http://localhost:8010/tiedonohjaus/api/org/" organization "/asiat") {:as :json})]
    (:body response)))
