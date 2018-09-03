(ns sade.http
  (:refer-clojure :exclude [get])
  (:require [taoensso.timbre :refer [warn error]]
            [clj-http.client :as http]
            [sade.core :refer [def- fail!]]
            [sade.strings :as ss]
            [sade.env :as env])
  (:import [sun.security.provider.certpath SunCertPathBuilderException]
           [java.io IOException]))

(def no-cache-headers {"Cache-Control" "no-cache, no-store"
                       "Pragma"        "no-cache"})

(defn- merge-to-defaults [& [fst-opt :as options]]
  (let [options-map (cond (and (= 1 (count options))
                               (or (map? fst-opt) (nil? fst-opt))) fst-opt
                          (zero? (mod (count options) 2)) (apply hash-map options)
                          :else (throw (IllegalArgumentException. "uneven number of options")))]
    (merge (env/value :http-client) options-map)))

(defn- wrap-location
  "Sanitizes redirect location field. Some backend systems may
  introduce non-latin1 gargabe into urls."
  [client]
  (letfn [(encode-location [resp]
            (if (-> resp :headers :location)
              (update-in resp [:headers :location] #(new String (.getBytes % "ISO-8859-1")))
              resp))]
    (fn
      ([req] (encode-location (client req)))
      ([req respond raise] (client req #(respond (encode-location %)) raise)))))

(defn- logged-call [f uri {:keys [throw-fail! throw-exceptions quiet http-error connection-error]
                           :or   {http-error :error.integration.http, connection-error :error.integration.connection}
                           :as   options}]
  (letfn [(log [message]
            (when-not quiet
              (error "error.integration -" message)))
          (call-and-log []
            (let [http-client-opts (update options :throw-exceptions #(and (not throw-fail!) %))
                  {:keys [status] :as resp} (f uri http-client-opts)]
              (if (http/unexceptional-status? status)
                resp
                (if throw-fail!
                  (do (log (str uri " returned " status))
                      (fail! http-error :status status))
                  resp))))
          (handle [exn]
            (let [msg (.getMessage exn)]
              (log (str uri " - " msg))
              (if throw-fail!
                (fail! connection-error :cause msg)
                {:status 502, :body (str "I/O exception - " msg)})))]
    (if (or (false? throw-exceptions) throw-fail!)
      (try
        (call-and-log)
        (catch IOException exn (handle exn))
        (catch SunCertPathBuilderException exn (handle exn)))
      (call-and-log))))

(defn get [uri & options]
  (http/with-additional-middleware [wrap-location]
    (logged-call http/get uri (assoc (apply merge-to-defaults options)
                                ;; Use wrap-redirects middleware
                                :redirect-strategy :none))))

(defn post [uri & options]
  (logged-call http/post uri (apply merge-to-defaults options)))

(defn put [uri & options]
  (logged-call http/put uri (apply merge-to-defaults options)))

(defn delete [uri & options]
  (logged-call http/delete uri (apply merge-to-defaults options)))

(def- blacklisted #"^((set-)?cookie|server|host|connection|x-.+|access-control-.+)")

(defn secure-headers [request-or-response]
  (letfn [(dissoc-blacklisted [headers]
            (into {} (remove (fn [[k _]] (re-matches blacklisted (ss/lower-case k))) headers)))]
    (if (contains? request-or-response :headers)
      (update request-or-response :headers dissoc-blacklisted)
      request-or-response)))

(defn client-ip [request]
  (or (get-in request [:headers "x-real-ip"]) (get-in request [:remote-addr])))

(defn decode-basic-auth
  "Returns username and password decoded from Authentication header"
  [request]
  (when-let [auth (get-in request [:headers "authorization"])]
    (when-let [cred (ss/base64-decode (last (re-find #"^Basic (.*)$" auth)))]
      (ss/split cred #":" 2))))

(defn parse-bearer
  "Returns Authorization: Bearer token from request.
   See https://tools.ietf.org/html/rfc6750#page-5"
  [request]
  (when-let [auth (get-in request [:headers "authorization"])]
    ;; From RFC 6750:
    ;;   The syntax for Bearer credentials is as follows:
    ;;     b64token    = 1*( ALPHA / DIGIT /
    ;;                       "-" / "." / "_" / "~" / "+" / "/" ) *"="
    ;;     credentials = "Bearer" 1*SP b64token
    ;;
    ;; The token is not base64-encoded, it just happens that the allowed
    ;; characters are the same.
    (last (re-find #"^Bearer ([a-zA-Z0-9\-._~+/]+[=]*)$" auth))))
