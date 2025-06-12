(ns lupapalvelu.asiointi.authn
  "Authentication for AsiointiAPI"
  (:require [clojure.spec.alpha :as s]
            [lupapalvelu.api-common :as api-common]
            [lupapalvelu.asiointi.http-util :as http-util]
            [lupapalvelu.asiointi.logging :as api-logging]
            [lupapalvelu.logging :as log]
            [lupapalvelu.user :as user]))

;; Where to bind the user
(s/def ::user-key keyword?)
(s/def ::bind-user (s/keys :req-un [::user-key]))

(def authentication-middleware
  "Authenticate user and bind into the request."
  {:name    ::authn
   :spec    (s/keys :req [::bind-user])
   :compile (fn [route-data _router-opts]
              (when-let [{:keys [user-key]} (::bind-user route-data)]
                (fn [handler]
                  (fn [{::api-logging/keys [logger] :as request}]
                    (if-let [user (api-common/basic-authentication request)]
                      (log/with-logging-context {:userId (:id user)}
                        (logger {:user-id (:id user)})
                        (if (user/asiointi-api-user? user)
                          (handler (assoc request user-key user))
                          http-util/unauthorized))
                      http-util/unauthorized)))))})
