(ns lupapalvelu.asiointi.common
  "Common middleware for populating the request context"
  (:require [clojure.spec.alpha :as s]
            [lupapalvelu.asiointi.control-case :as control-case]
            [lupapalvelu.asiointi.http-util :as http-util]
            [lupapalvelu.asiointi.logging :as api-logging]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :as log]
            [lupapalvelu.operations :as operations]))

;; Where to bind the application
(s/def ::app-key keyword?)
;; Where is the user
(s/def ::user-key keyword?)
;; Path to the application-id in the request
(s/def ::path-segment keyword?)
(s/def ::path (s/coll-of ::path-segment
                         :kind vector?
                         :min-count 1))
(s/def ::control-case (-> control-case/control-cases keys set))
(s/def ::bind-app (s/keys :req-un [::app-key
                                   ::user-key
                                   ::path
                                   ::control-case]))

(def application-middleware
  "Bind target application into the request using the `permanent-permit-id` field."
  {:name    ::application
   :spec    (s/keys :req [::bind-app])
   :compile (fn [route-data _router-opts]
              (when-let [{:keys [app-key user-key path control-case]} (::bind-app route-data)]
                (fn [handler]
                  (fn [{::api-logging/keys [logger] :as request}]
                    (let [user                (get request user-key)
                          permanent-permit-id (get-in request path)]
                      (if permanent-permit-id
                        (log/with-logging-context {:permanentPermitId permanent-permit-id}
                          (if-let [application (domain/get-application-as {:ryhti.permanentPermitIdentifier permanent-permit-id}
                                                                          user
                                                                          :include-canceled-apps? true)]
                            (log/with-logging-context {:applicationId (:id application)}
                              (logger {:permanent-permit-id permanent-permit-id
                                       :application-id      (:id application)})
                              (let [actual-case (operations/application->control-case application)]
                                (if (= control-case actual-case)
                                  (handler (assoc request app-key application))
                                  (http-util/bad-request {:detail  (format "The control case of the application (%s) does not match the endpoint (%s)."
                                                                           (name actual-case) (name control-case))
                                                          :pointer "/"}))))
                            http-util/not-found))
                        http-util/not-found))))))})
