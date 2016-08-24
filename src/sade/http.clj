(ns sade.http
  (:require [taoensso.timbre :as timbre :refer [warn error]]
            [clj-http.client :as http]
            [sade.core :refer :all]
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

(defn- log [message {quiet :quiet :as options}]
  (when-not (false? (:quiet options))
    (error "error.integration -" message)))

(defn- logged-call [f uri {:keys [throw-fail! throw-exceptions] :as options}]
  (let [http-client-opts (update options :throw-exceptions (fn [t?] (if throw-fail! false t?)))
        connection-error (or (:connection-error options) :error.integration.connection)
        http-error       (or (:http-error options) :error.integration.http)]
    (try
      (let [{:keys [status] :as resp} (f uri http-client-opts)]
        (if (or (http/unexceptional-status? status) (not throw-fail!))
          resp
          (do
            (log (str uri " returned " status) options)
            (fail! http-error :status status))))
      (catch java.io.IOException e
        (log (str uri " - " (.getMessage e)) options)
        (cond
          throw-fail!               (fail! connection-error :cause (.getMessage e))
          (false? throw-exceptions) {:status 502, :body (.getMessage e)}
          :else                     (throw e))))))

(defn get [uri & options]
  (logged-call http/get uri (apply merge-to-defaults options)))

(defn post [uri & options]
  (logged-call http/post uri (apply merge-to-defaults options)))

(defn put [uri & options]
  (logged-call http/put uri (apply merge-to-defaults options)))

(defn delete [uri & options]
  (logged-call http/delete uri (apply merge-to-defaults options)))

(def blacklisted #"^((set-)?cookie|server|host|connection|x-.+|access-control-.+)")

(defn dissoc-blacklisted [m]
  (into {} (remove #(re-matches blacklisted (ss/lower-case (first %))) m)))

(defn secure-headers [request-or-response]
  (if (contains? request-or-response :headers)
    (update request-or-response :headers dissoc-blacklisted)
    request-or-response))

(defn client-ip [request]
  (or (get-in request [:headers "x-real-ip"]) (get-in request [:remote-addr])))

(defn decode-basic-auth
  "Returns username and password decoded from Authentication header"
  [request]
  (let [auth (get-in request [:headers "authorization"])
        cred (and auth (ss/base64-decode (last (re-find #"^Basic (.*)$" auth))))]
    (when cred
      (ss/split (str cred) #":" 2))))
