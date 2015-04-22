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


(defn- fetch-prev-application! [{:keys [user] :as command} kuntalupatunnus & {:keys [operation] :or {operation :aiemmalla-luvalla-hakeminen}}]
  (let [organization      (first (:organizations user))
        permit-type       (operations/permit-type-of-operation operation)
        dummy-application {:id kuntalupatunnus
                           :permitType permit-type
                           :organization organization}
        xml (krysp-fetch-api/get-application-xml dummy-application :kuntalupatunnus)]
    (when-not xml
      (fail! :error.no-previous-permit-found-from-backend))

    (let [app-info               (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
          rakennuspaikka-exists? (and (:rakennuspaikka app-info)
                                      (every? (-> app-info :rakennuspaikka keys set) [:x :y :address :propertyId]))
          organizations-match?   (= organization (:id (organization/resolve-organization (:municipality app-info) permit-type)))]

      (cond
        (empty? app-info)            (fail! :error.no-previous-permit-found-from-backend)
        (not rakennuspaikka-exists?) (fail! :error.more-prev-app-info-needed)
        (not organizations-match?)   (fail! :error.previous-permit-found-from-backend-is-of-different-organization)
        :else
          (let [location-info (:rakennuspaikka app-info)
                created-app-id (prev-permit/do-create-application-from-previous-permit command xml app-info location-info)]
            (ok :id created-app-id))))))

(defraw get-lp-id-from-previous-permit
  {:parameters [kuntalupatunnus]
   :input-validators [(partial action/non-blank-parameters [:kuntalupatunnus])]
   :user-roles #{:rest-api}}
  [{:keys [user] :as command}]
  (let [command (update-in command [:data] merge {:operation :aiemmalla-luvalla-hakeminen})
        existing-app (domain/get-application-as {:verdicts {$elemMatch {:kuntalupatunnus kuntalupatunnus}}} user)
        result (if existing-app
                 (merge (ok :id (:id existing-app))
                        {:status :already-existing-application})
                 (merge (fetch-prev-application! command kuntalupatunnus)
                        {:status :created-new-application}))]
    (resp/status 200 (str result))))

(defcommand create-application-from-previous-permit
  {:parameters       [:lang :operation :x :y :address :propertyId :organizationId :kuntalupatunnus]
   :user-roles       #{:authority}
   :input-validators [(partial action/non-blank-parameters [:lang :operation :organizationId]) ;; no :address included
                      ;; the propertyId parameter can be nil
                      (fn [{{propertyId :propertyId} :data :as command}]
                        (when (not (ss/blank? propertyId))
                          (application/property-id-parameters [:propertyId] command)))
                      application/operation-validator]}
  [{{:keys [operation x y address propertyId organizationId kuntalupatunnus]} :data :keys [user] :as command}]
  (let [enough-info-from-parameters (and
                                      (not (ss/blank? address)) (not (ss/blank? propertyId))
                                      (-> x util/->double pos?) (-> y util/->double pos?))
        permit-type (operations/permit-type-of-operation operation)]

    ;; Prevent creating many applications based on the same kuntalupatunnus:
    ;; Check if we have in database an application of same organization that has a verdict with the given kuntalupatunnus.
    (if-let [app-with-verdict (domain/get-application-as
                                {:organization organizationId
                                 :state        {$nin ["canceled"]}
                                 :verdicts     {$elemMatch {:kuntalupatunnus kuntalupatunnus}}}
                                user)]
      ;;Found an application of same organization that has a verdict with the given kuntalupatunnus -> Open it.
      (ok :id (:id app-with-verdict))

      ;; Fetch xml data needed for application creation from backing system with the provided kuntalupatunnus.
      ;; Then extract needed data from it to "app info".
      (let [dummy-application {:id kuntalupatunnus :permitType permit-type :organization organizationId}
            xml (krysp-fetch-api/get-application-xml dummy-application :kuntalupatunnus)]
        ;; Show error if could not receive the verdict message xml for the given kuntalupatunnus
        (when-not xml (fail! :error.no-previous-permit-found-from-backend))

        (let [app-info (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
              rakennuspaikka-exists (and (:rakennuspaikka app-info) (every? (-> app-info :rakennuspaikka keys set) [:x :y :address :propertyId]))]

          ;; Could not extract info from verdict message xml
          (when (empty? app-info)
            (fail! :error.no-previous-permit-found-from-backend))
          ;; Given organization and the organization in the verdict message xml differ from each other
          (when-not (= organizationId (:id (organization/resolve-organization (:municipality app-info) permit-type)))
            (fail! :error.previous-permit-found-from-backend-is-of-different-organization))
          ;; This needs to be last of these error chekcs! Did not get the "rakennuspaikkatieto" element in the xml, so let's ask more needed location info from user.
          (when-not (or rakennuspaikka-exists enough-info-from-parameters)
            (when-not rakennuspaikka-exists
              (info "Prev permit application creation, rakennuspaikkatieto information incomplete:\n " (:rakennuspaikka app-info) "\n"))
            (fail! :error.more-prev-app-info-needed :needMorePrevPermitInfo true))

          (let [location-info (cond
                                rakennuspaikka-exists (:rakennuspaikka app-info)
                                ;; TODO: Pitaisiko kayttaa taman propertyId:ta yms tietoja, kalilta annettujen sijaan (kts alla 'enough-info-from-parameters')?
                                ;(:ensimmainen-rakennus app-info) (:ensimmainen-rakennus app-info)
                                enough-info-from-parameters {:x x :y y :address address :propertyId propertyId})
                created-app-id (prev-permit/do-create-application-from-previous-permit command xml app-info location-info)]
            (ok :id created-app-id)))))))
