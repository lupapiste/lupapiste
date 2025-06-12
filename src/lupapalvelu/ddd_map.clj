(ns lupapalvelu.ddd-map
  "External 3D map support. Preferred namespace alias ddd."
  (:require [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.token :as token]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn is-3d-map-enabled?
  [organization]
  (boolean (some-> (force organization) :3d-map :enabled)))

(defn three-d-map-enabled
  "Pre-checker for making sure that the organization has the 3D map enabled."
  [{organization :organization}]
  (when-not (is-3d-map-enabled? organization)
    (fail :error.3d-map-disabled)))

(defn- get-3d-map-server
  [organization]
  (-> (force organization) :3d-map :server))

(defn- post-3d-map-server
  "POST call to 3D map server backend. Returns response or nil.
  basic-auth is [username password] if given."
  [url form-params & [basic-auth]]
  (try
    (log/debug "redirect-to-3d-map POSTing to" url)
    (http/post url
               (util/assoc-when {:throw-exceptions  false
                                 :redirect-strategy :none
                                 :form-params       form-params}
                                :basic-auth basic-auth))
    (catch Exception e
      (log/errorf "3D map url %s failed: %s" url (ex-message e)))))

(defn- mark-3d-map-activated [application-id created]
  (mongo/update-by-query :applications
                         {:_id            application-id
                          :3dMapActivated nil}
                         {$set {:3dMapActivated created}}))

(defn- do-redirect-to-3d-map
  "Makes POST request to the 3D map server and returns the Location header value."
  [{url :url :as cfg} {:keys [applicationId] :as form-params} created & [user]]
  (let [start-ts (now)
        response (post-3d-map-server url
                                     form-params
                                     (bs-sec/get-credentials cfg))
        _        (log/debug "Sova 3D response received in "
                            (- (now) start-ts)
                            " ms: " (pr-str response))
        location (-> response :headers :location)]
    (if (ss/blank? location)
      (do (log/error "Sova 3D redirect-to-3d-map redirect failed")
          (fail :error.3d-map-not-found))
      (do (log/debug "Activating 3D map in db")
          ;; If the user is not a virtual user (e.g. impersonating), mark the 3D map as activated
          (when-not (some-> user usr/virtual-user?)
            (mark-3d-map-activated applicationId created))
          (log/debug "3D map activated, responding with redirect location.")
          (ok :location location)))))

(defn redirect-to-3d-map
  "Redirect authenticated user to the 3D map server."
  [{:keys [application user organization created] :as _command}]
  (do-redirect-to-3d-map (get-3d-map-server organization)
                         {:applicationId (:id application)
                          :apikey        (token/generate-or-refresh-map-token user)}
                         created
                         user))

(defn redirect-neighbor-to-3d-map
  "Redirect anonymous neighbor to the 3D map server."
  [organization application-id neighbor-id token created]
  (do-redirect-to-3d-map (get-3d-map-server organization)
                         {:applicationId application-id
                          :neighborId    neighbor-id
                          :token         token}
                         created))
