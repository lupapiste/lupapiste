(ns lupapalvelu.asiointi.authz
  "Authorization for AsiointiAPI"
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [lupapalvelu.asiointi.http-util :as http-util]
            [lupapalvelu.asiointi.logging :as api-logging]
            [lupapalvelu.permissions :as permissions]
            [sade.util :as util]))


(s/def :lupapiste/auth #{:application/read
                         :application/edit})

;; Required permissions
(s/def ::required (s/coll-of :lupapiste/auth
                             :kind set?
                             :min-count 1))
(s/def ::context-type #{:organization
                        :application})
(s/def ::app-key keyword?)
(s/def ::path-segment keyword?)
(s/def ::path (s/coll-of ::path-segment
                         :kind vector?
                         :min-count 1))
(s/def ::context (s/keys :req-un [::context-type
                                  ::required
                                  ::user-key]
                         :opt-un [::app-key
                                  ::path]))
(s/def ::contexts (s/coll-of ::context
                             :kind vector
                             :min-count 1))

(defn- make-auth-context-checker
  [{:keys [context-type user-key app-key required path]}]
  ;; NOTE: The checks are based solely on `:orgAuthz`.
  (case context-type
    ;; Check that the user has the required permissions to the target organization
    :organization (fn [{::api-logging/keys [logger] :as request}]
                    (let [user   (get request user-key)
                          org-id (get-in request path)]
                      (when org-id
                        (logger {:organization-id org-id}))
                      (and user
                           org-id
                           (empty? (set/difference required
                                                   (-> (util/mmap user org-id)
                                                       permissions/get-integration-organization-permissions))))))
    ;; Check that the user has the required permissions to the target application
    :application  (fn [request]
                    (let [user        (get request user-key)
                          application (get request app-key)]
                      (and user
                           application
                           (empty? (set/difference required
                                                   (-> (util/mmap user application)
                                                       permissions/get-integration-app-permissions))))))))

(def authorization-middleware
  {:name    ::authz
   :spec    (s/keys :req [::contexts])
   :compile (fn [route-data _router-opts]
              (when-let [contexts (::contexts route-data)]
                (let [context-checkers (mapv make-auth-context-checker contexts)]
                  (fn [handler]
                    (fn [request]
                      (if (every? (fn [checker] (checker request)) context-checkers)
                        (handler request)
                        http-util/forbidden))))))})
