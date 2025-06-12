(ns sade.http
  (:refer-clojure :exclude [get])
  (:require [clj-http.client :as http]
            [clojure.set :as set]
            [hato.client :as hc]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :refer [error]]
            [lupapiste-commons.ring.autologin :as commons-autologin]
            [muuntaja.core :as m]
            [sade.core :refer [def- fail!]]
            [sade.env :as env]
            [sade.strings :as ss])
  (:import [clojure.lang ExceptionInfo]
           [java.io IOException]
           [java.security.cert CertPathBuilderException]))

(set! *warn-on-reflection* true)

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
              (update-in resp [:headers :location] #(new String (.getBytes ^String % "ISO-8859-1")))
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
          (handle [^Throwable exn]
            (let [msg (.getMessage exn)]
              (log (str uri " - " msg))
              (if throw-fail!
                (fail! connection-error :cause msg)
                {:status 502, :body (str "I/O exception - " msg)})))]
    (if (or (false? throw-exceptions) throw-fail!)
      (try
        (call-and-log)
        (catch IOException exn (handle exn))
        (catch CertPathBuilderException exn (handle exn)))
      (call-and-log))))

(defn request-json
  "Returns HTTP response with body decoded from JSON to Clojure data structure"
  [method url opts]
  (-> (http/request (merge {:url    url
                            :method method
                            :accept :json}
                           opts))
      (update :body #(when-not (or (nil? %)
                                   (ss/blank? %))
                       (json/decode %)))))

(defn get-json
  "Makes GET request and returns HTTP response with body decoded from JSON to Clojure data structure"
  [url opts]
  (request-json :get url opts))

(defn get-json-body
  "Returns body from json response"
  [url opts]
  (:body (get-json url opts)))

(defn post-json
  "Makes POST request and returns HTTP response with body decoded from JSON to Clojure data structure"
  [url opts]
  (request-json :post url opts))

(defn get [uri & options]
  (http/with-additional-middleware [wrap-location]
    (let [{:keys [redirect-strategy]
           :as   options}   (apply merge-to-defaults options)
          redirect-strategy (or redirect-strategy
                                (if (:throw-exceptions options)
                                  ;; Should we throw (default) if `max-redirects` is exceeded or not (graceful)?
                                  :default
                                  :graceful))]
      (logged-call http/get uri (assoc options :redirect-strategy redirect-strategy )))))

(defn post [uri & options]
  (logged-call http/post uri (apply merge-to-defaults options)))

(defn post-as-json
  "Makes POST request with `body` as a JSON object, returns HTTP response with body decoded from JSON to Clojure data"
  [uri options]
  (post-json uri (-> options (assoc :content-type :json) (update :body json/encode))))

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

(def client-ip-map commons-autologin/client-ip-map)

(defn client-ip [request]
  (commons-autologin/client-ip request (env/feature? :parse-x-forwarded-for)))

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

(def *http-client (delay (hc/build-http-client {:redirect-policy :always
                                                :connect-timeout 10000})))

(defn post-transit
  "HTTP POST request with application/transit+json encoding.
  Encodes given body to transit using muuntaja.core."
  ([url body]
   (post-transit url body {}))
  ([url body opts]
   (try
     (-> (hc/post url
                  (-> opts
                      (merge {:http-client  @*http-client
                              :body         (m/encode "application/transit+json" body)
                              :content-type "application/transit+json"})
                      (update-in [:headers "accept"] (fnil identity "application/transit+json"))))
         (update :headers set/rename-keys {"content-type" "Content-Type"}))
     (catch ExceptionInfo e
       (if-let [new-body (try (some-> e
                                      ex-data
                                      (update :headers set/rename-keys {"content-type" "Content-Type"})
                                      m/decode-response-body)
                              (catch Exception _))]
         (->> (assoc (ex-data e) :body new-body)
              (ex-info (.getMessage e))
              throw)
         (throw e))))))

(defn hato-get
  "Make a GET request using hato client with error logging similar to `get` function."
  [uri & [options]]
  (logged-call hc/get uri options))

(defn hato-post
  "Make a POST request using hato client with error logging similar to `post` function."
  [uri & [options]]
  (logged-call hc/post uri options))

(def decode-response-body m/decode-response-body)
