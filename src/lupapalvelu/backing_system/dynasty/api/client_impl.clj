(ns lupapalvelu.backing-system.dynasty.api.client-impl
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.backing-system.dynasty.api.auth :as oauth]
            [lupapalvelu.backing-system.dynasty.api.client-proto :as proto]
            [lupapalvelu.logging :as log]
            [ring.util.http-predicates :as http-pred]
            [sade.async :as async]
            [sade.env :as env]
            [sade.http :as http]
            [sade.shared-schemas :as ssc]
            [sade.strings :as ss]
            [schema-tools.core :as st]
            [schema.core :as sc])
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

(def ^:private token-ttl
  "Access token lifetime is 15 minutes, so let's refresh it a bit earlier"
  (-> (Duration/ofMinutes 12) .toMillis))

(def ^:private cached-token-or-fetch!
  (memo/ttl oauth/fetch-token! :ttl/threshold token-ttl))

(sc/defschema CommonOptions
  "Timeout values are in milliseconds"
  {:conn-timeout   ssc/Nat
   :socket-timeout ssc/Nat})

(defn- common-options
  []
  {:conn-timeout   (or (env/value :dynasty :conn-timeout)
                       20000)
   :socket-timeout (or (env/value :dynasty :socket-timeout)
                       20000)})

(sc/defschema Token ssc/NonBlankStr)

(sc/defn obtain-token! :- Token
  [options :- (st/merge proto/TokenOptions
                        CommonOptions)]
  (try
    (cached-token-or-fetch! options)
    (catch Exception e
      (log/error e "Failed to fetch token")
      (throw (ex-info "Failed to fetch token" (select-keys options [:url]))))))

(defn- do-request!
  [operation url {:keys [conn-timeout socket-timeout] :as request}]
  (-> (async/future* (operation url request))
      (deref (+ conn-timeout socket-timeout) {:status ::timeout})))

(defn- exec!
  [operation {:keys [auth url headers form-params multipart]}]
  (let [common-opts      (common-options)
        token            (when (some? auth)
                           (obtain-token! (merge common-opts auth)))
        request          (merge common-opts
                                {:as               :json
                                 :accept           :json
                                 :throw-exceptions false
                                 :quiet            true}
                                (when headers
                                  {:headers headers})
                                (when form-params
                                  {:form-params  form-params
                                   :content-type :json})
                                (when multipart
                                  {:multipart multipart})
                                (when token
                                  {:oauth-token token}))
        {:keys [body status headers]
         :as   response} (do-request! operation url request)]
    (cond
      (http-pred/success? response)      body
      (http-pred/client-error? response) (let [auth-err (get headers "WWW-Authenticate")]
                                           (if (ss/includes? auth-err "error")
                                             (log/errorf "Token authentication error: %s" auth-err)
                                             (log/errorf "Bad request (%s): '%s'" status body))
                                           (throw (ex-info "Dynasty client error" (select-keys response [:status]))))
      (= ::timeout status)               (do
                                           (log/errorf "Timeout when connecting to '%s'" url)
                                           (throw (ex-info "Dynasty timeout" {:url url})))
      :else                              (do
                                           (log/errorf "Unknown response (%s): '%s'" status body)
                                           (throw (ex-info "Unknown Dynasty response" (select-keys response [:status])))))))

(defrecord HttpClient []
  proto/DynastyClient
  (-get [_ opts]
    (exec! http/get opts))
  (-post [_ opts]
    (exec! http/post opts )))

(defn make-client
  []
  (->HttpClient))
