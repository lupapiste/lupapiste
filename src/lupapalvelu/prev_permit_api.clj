(ns lupapalvelu.prev-permit-api
  (:require [taoensso.timbre :refer [info]]
            [lupapalvelu.action :as action]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.core :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.application :as application]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch-api]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.action :refer [defcommand defraw]]
            [lupapalvelu.prev-permit :as prev-permit]
            [noir.response :as resp]))


(defn- enough-location-info-from-parameters? [{{:keys [x y address propertyId]} :data}]
  (and
    (not (ss/blank? address)) (not (ss/blank? propertyId))
    (-> x util/->double pos?) (-> y util/->double pos?)))

(defn- fetch-prev-application! [{{:keys [x y address propertyId organizationId kuntalupatunnus]} :data user :user :as command}]
  (let [operation         :aiemmalla-luvalla-hakeminen
        permit-type       (operations/permit-type-of-operation operation)
        dummy-application {:id kuntalupatunnus
                           :permitType permit-type
                           :organization organizationId}
        xml (krysp-fetch-api/get-application-xml dummy-application :kuntalupatunnus)]
    (when-not xml (fail! :error.no-previous-permit-found-from-backend)) ;; Show error if could not receive the verdict message xml for the given kuntalupatunnus

    (let [app-info               (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
          rakennuspaikka-exists? (and (:rakennuspaikka app-info)
                                      (every? (-> app-info :rakennuspaikka keys set) [:x :y :address :propertyId]))
          location-info          (cond
                                   rakennuspaikka-exists?                          (:rakennuspaikka app-info)
                                   (enough-location-info-from-parameters? command) {:x x :y y :address address :propertyId propertyId})
          organizations-match?   (when (seq app-info)
                                   (= organizationId (:id (organization/resolve-organization (:municipality app-info) permit-type))))]

      (cond
        (empty? app-info)            (fail! :error.no-previous-permit-found-from-backend)
        (not organizations-match?)   (fail! :error.previous-permit-found-from-backend-is-of-different-organization)
        (not location-info)          (do
                                       (when-not rakennuspaikka-exists?
                                         (info "Prev permit application creation, rakennuspaikkatieto information incomplete:\n " (:rakennuspaikka app-info) "\n"))
                                       (fail! :error.more-prev-app-info-needed :needMorePrevPermitInfo true))
        :else                        (ok :id (prev-permit/do-create-application-from-previous-permit command operation xml app-info location-info))))))

(defraw get-lp-id-from-previous-permit
  {:parameters [kuntalupatunnus]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus])]
   :user-roles #{:rest-api}}
  [{:keys [user] :as command}]
  (let [command (update-in command [:data] merge {:organizationId (first (:organizations user))})
        existing-app (domain/get-application-as {:state    {$ne "canceled"}
                                                 :verdicts {$elemMatch {:kuntalupatunnus kuntalupatunnus}}} user)]
    (if existing-app
      (resp/status 200 (str (merge (ok :id (:id existing-app))
                                   {:status :already-existing-application})))
      (resp/status 200 (str (merge (fetch-prev-application! command)
                                   {:status :created-new-application}))))))

(defcommand create-application-from-previous-permit
  {:parameters       [:lang :x :y :address :propertyId organizationId kuntalupatunnus]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:lang :organizationId]) ;; no :address included
                      ;; the propertyId parameter can be nil
                      (fn [{{propertyId :propertyId} :data :as command}]
                        (when (not (ss/blank? propertyId))
                          (application/property-id-parameters [:propertyId] command)))]}
  [{:keys [user] :as command}]
    ;; Prevent creating many applications based on the same kuntalupatunnus:
    ;; Check if we have in database an application of same organization that has a verdict with the given kuntalupatunnus.
    (if-let [app-with-verdict (domain/get-application-as
                                {:organization organizationId
                                 :state        {$ne "canceled"}
                                 :verdicts     {$elemMatch {:kuntalupatunnus kuntalupatunnus}}}
                                user)]
      ;;Found an application of same organization that has a verdict with the given kuntalupatunnus -> Open it.
      (ok :id (:id app-with-verdict))
      (fetch-prev-application! command)))
