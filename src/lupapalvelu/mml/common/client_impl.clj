(ns lupapalvelu.mml.common.client-impl
  (:require [lupapalvelu.json :as json]
            [lupapalvelu.logging :as log]
            [sade.async :as async]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]))

(defn- extract-result
  [{:keys [status body]}]
  (when (and (= 200 status)
             (seq body))
    (json/decode body keyword)))

(defn- log-error
  [url query {:keys [status body]}]
  (case status
    200
    (log/error "error.integration - mml response body empty for url" url)

    :mml.client/timeout
    (log/error "error.integration - mml timeout while requesting" url)

    400
    (log/errorf "error.integration -  mml status 400 Bad Request! query='%s', url='%s', body='%s'"
                (-> query str (ss/limit 220 "..."))
                url
                (some-> body str (ss/limit 200 "...")))
    ;; Else
    (log/errorf "error.integration -  mml status %s, url=%s" status url))
  nil)

(defn get!
  [{:keys [api path query throw-on-failure? accept]}]
  (let [url              (str (env/value :mml api :url) path)
        username         (env/value :mml api :username)
        _                (when (ss/blank? username)
                           (log/warn "No username provided for MML"))
        password         (or (env/value :mml api :password) "")
        _                (when (ss/blank? password)
                           (log/warn "No password provided for MML"))
        conn-timeout     (env/value :mml :conn-timeout)
        socket-timeout   (env/value :mml :socket-timeout)
        request          {:basic-auth       [username password]
                          :query-params     query
                          :conn-timeout     conn-timeout
                          :socket-timeout   socket-timeout
                          :accept           (or accept :json)
                          :throw-exceptions false
                          :quiet            true}
        {:keys [status]
         :as   response} (-> (async/future* (http/get url request))
                             (deref (+ conn-timeout socket-timeout) {:status :mml.client/timeout}))]
    (or (extract-result response)
        (log-error url query response)
        (when throw-on-failure?
          (throw (ex-info "Integration error"
                          {:status (if (= 200 status) :ok :error)
                           :data   status}))))))
