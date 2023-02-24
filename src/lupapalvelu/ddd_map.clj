(ns lupapalvelu.ddd-map
  "External 3D map support. Preferred namespace alias ddd."
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.http :as http]
            [sade.strings :as ss]
            [taoensso.timbre :as timbre]))

(defn three-d-map-enabled
  "Pre-checker for making sure that the organization has the 3D map enabled."
  [{organization :organization}]
  (when-not (and organization (-> @organization :3d-map :enabled))
    (fail :error.3d-map-disabled)))

(defn- post-3d-map-server
  "POST call to 3D map server backend. Returns response or nil.
  basic-auth is [username password] if given."
  [url application-id apikey & [basic-auth]]
  (try
    (timbre/debug "redirect-to-3d-map POSTing to" url)
    (http/post url
               (merge {:throw-exceptions false
                       :follow-redirects false
                       :form-params      {:applicationId application-id
                                          :apikey        apikey}}
                      (when (not-empty basic-auth)
                        {:basic-auth basic-auth})))
    (catch Exception e
      (timbre/errorf "3D map url %s failed: %s" url (.getMessage e)))))

(defn- mark-3d-map-activated [{:keys [created] :as command}]
  (action/update-application
    command
    {:3dMapActivated nil}
    {$set {:3dMapActivated created}}))

(defn redirect-to-3d-map
  "Makes POST request to the 3D map server and returns the Location header value."
  [{:keys [application user organization] :as command}]
  (let [email    (:email user)
        {:keys [url username password crypto-iv]} (-> @organization
                                                      :3d-map
                                                      :server)
        start-ts (System/currentTimeMillis)
        response (post-3d-map-server url
                                     (:id application)
                                     (or (usr/get-apikey email)
                                         (usr/create-apikey email))
                                     (when (ss/not-blank? crypto-iv)
                                       [username
                                        (org/decode-credentials password
                                                                crypto-iv)]))
        _        (timbre/debug "Sova 3D response received in "
                               (- (System/currentTimeMillis) start-ts)
                               " ms: " (pr-str response))
        location (-> response :headers :location)]
    (if (ss/blank? location)
      (do (timbre/error "Sova 3D redirect-to-3d-map redirect failed")
          (fail :error.3d-map-not-found))
      (do (timbre/debug "Activating 3D map in db")
          (mark-3d-map-activated command)
          (timbre/debug "3D map activated, responding with redirect location.")
          (ok :location location)))))
