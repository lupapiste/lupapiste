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
  (assoc application :linkPermitData (->> (link-permits-with-app-data application)
                                          (map update-backend-id-in-link-permit)
                                          (map #(dissoc % :app-data)))))
