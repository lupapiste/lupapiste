(ns lupapalvelu.tiedonohjaus
  (:require [sade.http :as http]
            [sade.env :as env]
            [clojure.core.memoize :as memo]))

(defn- build-url [& path-parts]
  (apply str (env/value :toj :host) path-parts))

(def get-functions-from-toj
  (memo/ttl (fn [organization]
              (try
                (let [response (http/get (build-url "/tiedonohjaus/api/org/" organization "/asiat") {:as :json
                                                                                                     :throw-exceptions false})]
                  (if (= 200 (:status response))
                    (:body response)
                    []))
                (catch Exception e
                  [])))
            :ttl/threshold 10000))

(def get-metadata-for-document-from-toj
  (memo/ttl (fn [organization code doc-id]
              (println organization)
              (println code)
              (println doc-id)
              (try
                (let [response (http/get (build-url "/tiedonohjaus/api/org/" organization "/asiat/" code "/document/" doc-id) {:as :json
                                                                                                                               :throw-exceptions false})]
                  (if (= 200 (:status response))
                    (:body response)
                    []))
                (catch Exception e
                  [])))
            :ttl/threshold 10000))
