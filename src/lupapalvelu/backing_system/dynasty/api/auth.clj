(ns lupapalvelu.backing-system.dynasty.api.auth
  "Implements basic \"client credentials\" Oauth2 flow for Dynasty 10."
  (:require [lupapalvelu.logging :as log]
            [sade.async :as async]
            [sade.http :as http]))

(defn fetch-token!
  "Fetch an OAuth2 access token (JWT as base64) using the \"client credentials\" flow"
  [{:keys [url client-id client-secret conn-timeout socket-timeout]}]
  (let [request        {:form-params      {:grant_type    "client_credentials"
                                           :client_id     client-id
                                           :client_secret client-secret}
                        :as               :json
                        :accept           :json
                        :conn-timeout     conn-timeout
                        :socket-timeout   socket-timeout
                        :throw-exceptions false
                        :quiet            true}
        _              (log/infof "Fetching token from %s.." url)
        {:keys [status
                body]} (-> (async/future* (http/post url request))
                           (deref (+ conn-timeout socket-timeout) {:status ::timeout}))]
    (or
      (when-let [token (-> body :access_token)]
        (log/info "Token fetched successfully")
        token)
      (throw (ex-info "Failed to fetch Oauth token"
                      {:url    url
                       :status status
                       :body   body})))))
