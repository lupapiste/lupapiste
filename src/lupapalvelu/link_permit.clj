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

(defn- update-backend-id-in-link-permit [{app-id :id :as link-permit}]
  (if-let [backend-id  (get-backend-id (:app-data link-permit))]
    (assoc link-permit :lupapisteId app-id :type "kuntalupatunnus" :id backend-id)
    link-permit))

(defn- link-permit-not-found! [application link-permit-app]
  (info "Not able to get a kuntalupatunnus for the application  " (:id application) " from it's link permit's (" (:id link-permit-app) ") verdict."
        " Associated Link-permit data: " (:linkPermitData application))
  (if (foreman/foreman-app? application)
    (fail! :error.link-permit-app-not-in-post-sent-state)
    (fail! :error.kuntalupatunnus-not-available-from-verdict)))

(defn- check-link-permit-backend-id [application {{link-state :state} :app-data link-type :type :as link-permit-app}]
  (cond
    (= link-type "kuntalupatunnus")                       nil ; backend-id already exists
    (get-backend-id (:app-data link-permit-app))          nil ; backend-id found from verdicts
    (and (foreman/foreman-app? application)
         (states/post-sent-states (keyword link-state)))  nil ; main application for foreman app is submitted
    :else                                                 :not-found))

(defn- link-permits-with-app-data [{link-permit-data :linkPermitData}]
  (let [link-permit-apps (-> (map :id link-permit-data)
                             (domain/get-multiple-applications-no-access-checking {:verdicts true :state true}))]
    (map #(assoc % :app-data (util/find-by-id (:id %) link-permit-apps)) link-permit-data)))

(defn- check-link-permit-count [{{primary-op :name} :primaryOperation app-id :id link-permit-data :linkPermitData}]
  (when (some-> (op/get-operation-metadata primary-op :max-outgoing-link-permits)
                (< (count link-permit-data)))
    (warn "Application " app-id " has more link permits than allowed for operation '" primary-op "'!"))
  (when (some-> (op/get-operation-metadata primary-op :min-outgoing-link-permits)
                (> (count link-permit-data)))
    (warn "Application " app-id " has fewer link permits than required for operation '" primary-op "'!")))

(defn update-backend-ids-in-link-permit-data [application]
  (check-link-permit-count application)
  (let [link-permit-data (link-permits-with-app-data application)]
    (some->> (first link-permit-data)  ; TODO: Find out if should fail when some or every link permit check fails
             (check-link-permit-backend-id application)
             (link-permit-not-found! application))
    (assoc application :linkPermitData (->> (map update-backend-id-in-link-permit link-permit-data)
                                            (map #(dissoc % :app-data))))))
