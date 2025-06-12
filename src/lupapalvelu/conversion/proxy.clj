(ns lupapalvelu.conversion.proxy
  "Proxy functionality for QA conversions. Since QA typically cannot access the production
  backing systems directly the KuntaGML messages can be fetched via proxy (production
  Lupapiste endpoint)."
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [noir.response :as resp]
            [sade.date :as date]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.coerce :as sco]
            [schema.core :as sc]
            [taoensso.nippy :as nippy]))

(sc/defschema ^:private ProxyConfig
  (sc/conditional
    :host  {:host   ssc/HttpUrl
            :role   (sc/eq :client)
            :secret ssc/NonBlankStr}
    :else {:role   (sc/eq :proxy)
           :secret ssc/NonBlankStr}))

(def ^:private valid-cfg (sco/coercer ProxyConfig sco/json-coercion-matcher))

(sc/defschema ProxyParams
  {:organization-id ssc/NonBlankStr
   :backend-id      conv-sc/SupportedBackendId})

(def valid-proxy-params (sco/coercer ProxyParams sco/json-coercion-matcher))

(sc/defn ^:private proxy-config-factory :- (sc/maybe ProxyConfig)
  []
  (let [{:keys [error]
         :as   m} (some-> (env/value :conversion :proxy)
                          ss/trimwalk
                          valid-cfg)]
    (if error
      (log/debug "Bad conversion proxy configuration.")
      m)))

(def ^:private proxy-config
  (if (env/dev-mode?)
    proxy-config-factory
    ;; The config is cached for five minutes.
    (memo/ttl proxy-config-factory :ttl/threshold (* 1000 60 5))))

(defn endpoint?
  "The endpoint is always active in dev-mode for the local testing purposes (the same
  instance can act as a client and proxy)."
  []
  (or (= (:role (proxy-config)) :proxy)
      (env/dev-mode?)))

(defn use-proxy? []
  (= (:role (proxy-config)) :client))

(defn- nippy-password
  []
  {:post [%]} ; secret is mandatory
  (some->> (proxy-config)
           :secret
           (conj [:cached])
           (hash-map :password)))

(defn- freeze
  "Returns Nippy-frozen string that is encrypted with `nippy-password`."
  [data]
  (nippy/freeze-to-string data (nippy-password)))

(defn- thaw
  "Thaws string `s` and decrypts it with `nippy-password`."
  [s]
  (nippy/thaw-from-string s (nippy-password)))

(defn- encode-timestamp
  "Somewhat overcautious precaution, where the basic-auth value is an encrypted timestamp
  padded with random uuid. The proxy endpoint does not serve old timestamps."
  []
  (freeze [(date/timestamp (date/now)) (str (random-uuid))]))

(defn- decode-timestamp
  [encoded]
  (some-> encoded thaw first date/zoned-date-time))

(defn valid-basic-auth?
  "Basic-auth in `ring-request` is considered valid if it consists of an encoded (by
  `encode-timestamp`) timestamp that decodes into a timestamp that is not older than 10
  seconds. There is also some tolerance for system clock variances."
  [ring-request]
  (boolean
    (try
      (let [now   (date/now)
            start (date/minus now :seconds 10)
            end   (date/plus now :seconds 5)
            ts    (some-> ring-request
                          http/decode-basic-auth
                          second
                          decode-timestamp)]
        (cond
          (nil? ts)                   (log/error "No timestamp found.")
          (date/before? start ts end) true
          :else                       (log/error "Bad or expired timestamp"
                                                 ts)))
      (catch Exception e
        (log/error "Basic auth validation failed:" (ex-message e))))))

(defn- endpoint-url []
  (when-let [host (:host (proxy-config))]
    (str host
         (when-not (ss/ends-with? host "/")
           "/")
         "conversion-proxy")))

(def ^:private
  http-get "Separate definition that is overridden in itests." http/get)

(defn fetch-xml-via-proxy
  "Client code for fetching KuntaGML message via `conversion-proxy` endpoint. For request to succeed the
  current proxy configuration must be compatible with the proxy host configuration AND the
  proxy be enabled for the organization in the host side."
  [organization-id backend-id]
  (let [nope (fn [err]
               (log/error err)
               {:error err})]
    (if-let [url (endpoint-url)]
      (let [{:keys [status body]} (http-get url
                                            {:query-params     (util/mmap organization-id
                                                                          backend-id)
                                             :basic-auth       ["proxy" (encode-timestamp)]
                                             :throw-exceptions false})]
        (if (= 200 status)
          {:xml body}
          (nope (format "%s %s" status body))))
      (nope "Proxy not configured"))))

(defn proxy-response
  "`conversion-proxy` endpoint functionality implementation. In addition to the endpoint
  configuration (see `endpoint?`), the target organization's `:conversion-proxy-enabled`
  flag must be true. The authentication mechanism is basic-auth that contains an encrypted
  timestamp (see `valid-basic-auth?`)."
  [ring-request params]
  (try
    (let [nope            (fn [status body]
                            (log/error status body)
                            (resp/status status body))
          available?      (endpoint?)
          {:keys [organization-id backend-id
                  error]} (when available?
                            (valid-proxy-params params))]
      (cond
        (not available?) (nope 404 "Not found")

        error            (nope 400 (str error))

        (valid-basic-auth? ring-request)
        (let [{:keys [xml error]} (some-> (mongo/select-one
                                            :organizations
                                            {:_id                      organization-id
                                             :conversion-proxy-enabled true}
                                            [:id])
                                          (conv-util/fetch-xml backend-id))]
          (if xml
            (resp/status 200 (resp/xml xml))
            (nope 400 (or error "Bad request"))))

        :else            (resp/status 401 "Unauthorized")))
    (catch Exception e
      (log/error e)
      (resp/status 500 (ex-message e)))))
