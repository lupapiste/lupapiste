(ns sade.http
  (:require [taoensso.timbre :as timbre :refer [warn error]]
            [clj-http.client :as http]
            [sade.strings :as ss]
            [sade.env :as env])
  (:refer-clojure :exclude [get]))

(def no-cache-headers {"Cache-Control" "no-cache, no-store"
                       "Pragma" "no-cache"})

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

(defn secure-headers [request-or-response]
  (if (contains? request-or-response :headers)
    (update request-or-response :headers dissoc "cookie" "set-cookie" "server" "host" "connection")
    request-or-response))

(defn decode-basic-auth
  "Returns username and password decoded from Authentication header"
  [request]
  (let [auth (get-in request [:headers "authorization"])
        cred (and auth (ss/base64-decode (last (re-find #"^Basic (.*)$" auth))))]
    (when cred
      (ss/split (str cred) #":" 2))))
