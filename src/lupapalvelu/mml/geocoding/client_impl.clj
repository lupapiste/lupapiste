(ns lupapalvelu.mml.geocoding.client-impl
  (:require [lupapalvelu.json :as json]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :as log]))

(defn- extract-result
  [{:keys [status body]}]
  (when (and (= 200 status)
             (seq body))
    (json/decode body keyword)))

(defn- log-error
  [url query {:keys [status]}]
  (case status
    200
    (log/error "error.integration - mml response body empty for url" url)

    :mml.geocoding/timeout
    (log/error "error.integration - mml timeout while requesting" url)

    400
    (log/errorf "error.integration -  mml status 400 Bad Request '%s', url=%s"
                (-> query str (ss/limit 220 "..."))
                url)
    ;; Else
    (log/errorf "error.integration -  mml status %s, url=%s" status url))
  nil)

(defn get!
  [{:keys [path query throw-on-failure?]}]
  (let [url              (str (env/value :mml :geocoding :url) path)
        username         (env/value :mml :geocoding :username)
        password         (or (env/value :mml :geocoding :password) "")
        conn-timeout     (env/value :mml :conn-timeout)
        socket-timeout   (env/value :mml :socket-timeout)
        request          {:basic-auth       [username password]
                          :query-params     query
                          :conn-timeout     conn-timeout
                          :socket-timeout   socket-timeout
                          :accept           :json
                          :throw-exceptions false
                          :quiet            true}
        {:keys [status]
         :as   response} (-> (util/future* (http/get url request))
                             (deref (+ conn-timeout socket-timeout) {:status :mml.geocoding/timeout}))]
    (or (extract-result response)
        (log-error url query response)
        (when throw-on-failure?
          (throw (ex-info "Integration error"
                          {:status (if (= 200 status) :ok :error)
                           :data   status}))))))
