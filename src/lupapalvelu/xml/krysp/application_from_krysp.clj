(ns lupapalvelu.xml.krysp.application-from-krysp
  (:require [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.kiinteistotoimitus-canonical :refer [operation-name]]
            [sade.core :refer [fail!]]))

(defn- get-application-xml [organization-id permit-type ids search-type raw?]
  (if-let [{url :url credentials :credentials} (organization/get-krysp-wfs {:organization organization-id :permitType permit-type})]
    (or (permit/fetch-xml-from-krysp permit-type url credentials ids search-type raw?)
        (fail! :error.unknown))
    (fail! :error.no-legacy-available)))

(defn get-application-xml-by-application-id [{:keys [id organization permitType] :as application} & [raw?]]
  (get-application-xml organization permitType [id] :application-id raw?))

(defn get-multiple-application-xmls-by-application-ids [organization-id permit-type application-ids & [raw?]]
  (get-application-xml organization-id permit-type application-ids :application-id raw?))

(defn get-application-xml-by-backend-id [{:keys [id organization permitType] :as application} backend-id & [raw?]]
  (when backend-id
    (get-application-xml organization permitType [backend-id] :kuntalupatunnus raw?)))
