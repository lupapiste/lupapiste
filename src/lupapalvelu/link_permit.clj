(ns lupapalvelu.link-permit
  (:require [taoensso.timbre :as timbre :refer [infof info error errorf warn]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.operations :as op]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.util :as util]))

(defn- get-backend-id [application]
  (some :kuntalupatunnus (:verdicts application)))

(defn- update-backend-id-in-link-permit [link-permit-applications {link-permit-app-id :id :as link-permit}]
  (if-let [backend-id  (-> (util/find-by-id link-permit-app-id link-permit-applications)
                           get-backend-id)]
    (assoc link-permit :lupapisteId link-permit-app-id :type "kuntalupatunnus" :id backend-id)
    link-permit))

(defn- link-permit-not-found! [application link-permit-app]
  (info "Not able to get a kuntalupatunnus for the application  " (:id application) " from it's link permit's (" (:id link-permit-app) ") verdict."
        " Associated Link-permit data: " (:linkPermitData application))
  (if (foreman/foreman-app? application)
    (fail! :error.link-permit-app-not-in-post-sent-state)
    (fail! :error.kuntalupatunnus-not-available-from-verdict)))

(defn- check-link-permit-backend-id [application {link-state :state link-type :type :as link-permit-app}]
  (cond
    (= link-type "kuntalupatunnus")                            nil ; backend-id already exists
    (get-backend-id link-permit-app)                           nil ; backend-id found from verdicts
    (and (foreman/foreman-app? application)
         (states/post-submitted-states (keyword link-state)))  nil ; main application for foreman app is submitted
    :else                                                      :not-found))

(defn update-backend-ids-in-link-permit-data [application]
  (let [link-permit-apps (-> (map :id (:linkPermitData application))
                             (domain/get-multiple-applications-no-access-checking {:verdicts true :state true}))]
    (some->> (first link-permit-apps) ; TODO: Find out if should fail when some or every link permit check fails
             (check-link-permit-backend-id application)
             (link-permit-not-found! application))
    (update application :linkPermitData (partial map (partial update-backend-id-in-link-permit link-permit-apps)))))
