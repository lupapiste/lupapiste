(ns sade.http
  (:require [taoensso.timbre :as timbre :refer [warn error]]
            [clj-http.client :as http]
            [sade.env :as env])
  (:refer-clojure :exclude [get]))

(defn- merge-to-defaults [& options]
  (let [fst-opt     (first options)
        options-map (cond (and (= 1 (count options)) (or (map? fst-opt) (nil? fst-opt)))  fst-opt
                      (zero? (mod (count options) 2))  (apply hash-map options)
                      :else (throw (IllegalArgumentException. "uneven number of options")))]
    (merge (env/value :http-client) options-map)))

(defn- logged-call [f uri options]
  (try
    (f uri options)
    (catch Exception e
      (error (str uri " - " (.getMessage e)))
      (throw e))))

(defn get [uri & options]
  (logged-call http/get uri (apply merge-to-defaults options)))

(defn post [uri & options]
  (logged-call http/post uri (apply merge-to-defaults options)))
