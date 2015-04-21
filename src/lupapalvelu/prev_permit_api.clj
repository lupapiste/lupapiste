(ns lupapalvelu.prev-permit-api
  (:require [lupapalvelu.action :as action]
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
            [lupapalvelu.action :refer [defcommand]]
            [lupapalvelu.prev-permit :as prev-permit]))

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
    (if-let [app-with-verdict (domain/get-application-no-access-checking {:organization organizationId
                                                                          :verdicts     {$elemMatch {:kuntalupatunnus kuntalupatunnus}}})]

      ;; Found an application of same organization that has a verdict with the given kuntalupatunnus. Open it if user has rights, otherwise show error.
      (if-let [existing-app (domain/get-application-as (:id app-with-verdict) user)]
        (ok :id (:id existing-app))
        (fail :error.lupapiste-application-already-exists-but-unauthorized-to-access-it :id (:id app-with-verdict)))

      ;; Fetch xml data needed for application creation from backing system with the provided kuntalupatunnus.
      ;; Then extract needed data from it to "app info".
      (let [dummy-application {:id kuntalupatunnus :permitType permit-type :organization organizationId}
            xml (krysp-fetch-api/get-application-xml dummy-application :kuntalupatunnus)]
        (when-not xml (fail! :error.no-previous-permit-found-from-backend)) ;; Show error if could not receive the verdict message xml for the given kuntalupatunnus

        (let [app-info (krysp-reader/get-app-info-from-message xml kuntalupatunnus)
              rakennuspaikka-exists (and (:rakennuspaikka app-info) (every? (-> app-info :rakennuspaikka keys set) [:x :y :address :propertyId]))
              lupapiste-tunnus (:id app-info)]
          ;; Could not extract info from verdict message xml
          (when (empty? app-info)
            (fail! :error.no-previous-permit-found-from-backend))
          ;; Given organization and the organization in the verdict message xml differ from each other
          (when-not (= organizationId (:id (organization/resolve-organization (:municipality app-info) permit-type)))
            (fail! :error.previous-permit-found-from-backend-is-of-different-organization))
          ;; This needs to be last of these error chekcs! Did not get the "rakennuspaikkatieto" element in the xml, so let's ask more needed location info from user.
          (when-not (or rakennuspaikka-exists enough-info-from-parameters)
            (fail! :error.more-prev-app-info-needed :needMorePrevPermitInfo true))

          (if (ss/blank? lupapiste-tunnus)
            ;; NO LUPAPISTE ID FOUND -> create the application
            (let [location-info (cond
                                  rakennuspaikka-exists (:rakennuspaikka app-info)
                                  ;; TODO: Pitaisiko kayttaa taman propertyId:ta yms tietoja, kalilta annettujen sijaan (kts alla 'enough-info-from-parameters')?
                                  ;(:ensimmainen-rakennus app-info) (:ensimmainen-rakennus app-info)
                                  enough-info-from-parameters {:x x :y y :address address :propertyId propertyId})
                  created-app-id (prev-permit/do-create-application-from-previous-permit command xml app-info location-info)]
              (ok :id created-app-id))
            ;; LUPAPISTE ID WAS FOUND -> open it if user has rights, otherwise show error
            (if-let [existing-application (domain/get-application-as lupapiste-tunnus user)]
              (ok :id (:id existing-application))
              (fail :error.lupapiste-application-already-exists-but-unauthorized-to-access-it :id lupapiste-tunnus))))))))
