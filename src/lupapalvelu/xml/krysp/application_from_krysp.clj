(ns lupapalvelu.xml.krysp.application-from-krysp
  (:require [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.kiinteistotoimitus-canonical :refer [operation-name]]
            [sade.core :refer [fail!]]))

(defn- get-application-xml [application id search-type & [raw?]]
  (if-let [{url :url credentials :credentials} (organization/get-krysp-wfs application)]
    (if-let [fetch-fn (fetch-fetch-fn application)]
      (or (permit/fetch-xml-from-krysp (:permitType application) url credentials id search-type raw?)
          (fail! :error.unknown)))
    (fail! :error.no-legacy-available)))

(defn get-application-xml-by-application-id [{:keys [id organization permitType] :as application} & [raw?]]
  (get-application-xml application id :application-id raw?))

(defn get-application-xml-by-backend-id [{:keys [id organization permitType] :as application} backend-id & [raw?]]
  (when backend-id
    (get-application-xml application backend-id :kuntalupatunnus raw?)))
