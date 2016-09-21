(ns lupapalvelu.ddd-map
  "External 3D map support. Preferred namespace alias ddd."
  (:require [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [sade.http :as http]))

(defn three-d-map-enabled
  "Pre-checker for making sure that the organization has the 3D map enabled."
  [{organization :organization}]
  (when-not (and organization (-> @organization :3d-map :enabled))
    (fail :error.3d-map-disabled)))

(defn redirect-to-3d-map
  "Makes POST request to the 3D map server and returns the Location header value."
  [{email :email} {app-id :id} organization]
  (let [{:keys [url username password crypto-iv]} (-> organization
                                                      :3d-map
                                                      :server)
        response (http/post url
                   (merge {:throw-exceptions false
                           :follow-redirects false
                           :form-params {:applicationId app-id
                                         :apikey (or (usr/get-apikey email)
                                                     (usr/create-apikey email))}}
                          (when (ss/not-blank? crypto-iv)
                            {:basic-auth [username
                                          (org/decode-credentials password
                                                                  crypto-iv)]})))
        location (-> response :headers :location)]
    (if (ss/blank? location)
      (fail :error.3d-map-not-found)
      (ok :location location))))
