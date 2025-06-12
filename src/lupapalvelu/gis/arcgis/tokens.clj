(ns lupapalvelu.gis.arcgis.tokens
  "ArcGIS integration requires all queries to be authenticated with a token
  (unless the service is public).
  These tokens are handled here by regenerating them whenever they expire and using
  a cached version otherwise."
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :refer [warnf]]
            [sade.http :as http]))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fetching the token from the service directly

(defn- fetch-arcgis-token
  "Fetches a time-limited authentication token that can be used to access protected services.
  Referer argument must include the server base address in current environment, i.e. the URL that user
  sees in the browser.
  When successful, returns a map:
  {:token   (string, the token itself)
   :expires (timestamp, when the token will become invalid)
   :ssl     (boolean, true if the token must always pass over ssl)}"
  [{:keys [username password crypto-iv token-url]} referer expiration-minutes]
  (when token-url
    (let [resp (http/post token-url
                          {:query-params       {:f "json"}
                           :form-params        {:username   username
                                                :password   (bs-sec/decode-password password crypto-iv)
                                                :expiration expiration-minutes
                                                :client     "referer"
                                                :referer    referer}
                           :socket-timeout     20000
                           :connection-timeout 20000})
          body (-> resp :body (json/decode true))]
      (if-let [error (or (-> body :error)
                         (when (-> body :token not)
                           body))]
        (do (warnf "Could not get ArcGIS token from %s: %s" token-url error)
            {:error error})
        body))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Caching and expiration handling

(def ^{:arglists '([server-config referer expiration-minutes])} fetch-arcgis-token-cached
  "Cached version (30 s TTL) of fetch-arcgis-token to act as lock if multiple threads call this at the same time"
  (memo/ttl fetch-arcgis-token :ttl/threshold 30000))

(def server-token-cache (atom {}))

(defn- coerce-expires-timestamp [{:keys [expires] :as token-map}]
  (cond-> token-map
    (string? expires) (update :expires parse-long)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public functions

(defn token
  "Get a previously fetched ArcGIS token from token cache or retrieve a new token from the token url.
   The fetch of a new token uses memoized functions to prevent multiple threads from hitting the token server
   at the same time when the previous token cache is empty or expired."
  [server-config referer]
  (let [{:keys [expires] :as cached} (get @server-token-cache server-config)]
    (if (and (number? expires)
             (> expires (System/currentTimeMillis)))
      cached
      ; Fetch a token valid for 24 hours
      (let [token-map (fetch-arcgis-token-cached server-config referer 1440)]
        (when (some #{:token :access_token} (keys token-map))
          (->> token-map
               coerce-expires-timestamp
               (swap! server-token-cache assoc server-config)))
        (select-keys token-map [:token :access_token])))))
