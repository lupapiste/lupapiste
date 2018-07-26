(ns sade.http
  (:require [taoensso.timbre :refer [warn error]]
            [clj-http.client :as http]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.env :as env]
            [cheshire.core :as json]
            [clojure.walk :as walk])
  (:refer-clojure :exclude [get])
  (:import [sun.security.provider.certpath SunCertPathBuilderException]
           [java.io IOException]))

(def no-cache-headers {"Cache-Control" "no-cache, no-store"
                       "Pragma" "no-cache"})

(defn decode-response [resp]
  (update-in resp [:body] (comp walk/keywordize-keys json/decode)))

(defn- merge-to-defaults [& options]
  (let [fst-opt     (first options)
        options-map (cond (and (= 1 (count options)) (or (map? fst-opt) (nil? fst-opt)))  fst-opt
                      (zero? (mod (count options) 2))  (apply hash-map options)
                      :else (throw (IllegalArgumentException. "uneven number of options")))]
    (merge (env/value :http-client) options-map)))

(defn- log [message options]
  (when-not (:quiet options)
    (error "error.integration -" message)))

(defn wrap-location
  "Sanitizes redirect location field. Some backend systems may
  introduce non-latin1 gargabe into urls."
  [client]
  (letfn [(encode-location [resp]
            (if (-> resp :headers :location)
              (update-in resp
                         [:headers :location]
                         #(new String (.getBytes % "ISO-8859-1")))
              resp))]
    (fn
     ([req]
      (encode-location (client req)))
     ([req respond raise]
      (client req
              #(respond (encode-location %)) raise)))))

(defn- handle-exception [^Throwable e uri connection-error {:keys [throw-fail! throw-exceptions] :as options}]
  (log (str uri " - " (.getMessage e)) options)
  (cond
    throw-fail!               (fail! connection-error :cause (.getMessage e))
    (false? throw-exceptions) {:status 502, :body (str "I/O exception - " (.getMessage e))}
    :else                     (throw e)))

(defn- logged-call [f uri {:keys [throw-fail!] :as options}]
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
      (catch IOException e
        (handle-exception e uri connection-error options))
      (catch SunCertPathBuilderException e
        (handle-exception e uri connection-error options)))))

(defn get [uri & options]
  (http/with-additional-middleware [wrap-location]
    (logged-call http/get uri (assoc (apply merge-to-defaults
                                            options)
                                     ;; Use wrap-redirects middleware
                                     :redirect-strategy :none))))

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

(defn parse-bearer
  "Returns Authorization: Bearer token from request.
   See https://tools.ietf.org/html/rfc6750#page-5"
  [request]
  (when-let [auth (get-in request [:headers "authorization"])]

    ; From RFC 6750:
    ;   The syntax for Bearer credentials is as follows:
    ;     b64token    = 1*( ALPHA / DIGIT /
    ;                       "-" / "." / "_" / "~" / "+" / "/" ) *"="
    ;     credentials = "Bearer" 1*SP b64token

    ; The token is not base64-encoded, it just happens that the allowed
    ; characters are the same.

    (last (re-find #"^Bearer ([a-zA-Z0-9\-._~+/]+[=]*)$" auth))))
