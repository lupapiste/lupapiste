(ns lupapalvelu.xml.krysp.application-from-krysp
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.kiinteistotoimitus-canonical :refer [operation-name]]
            [sade.core :refer [fail!]]))

(defmulti fetch-fetch-fn :permitType)

(defmethod fetch-fetch-fn :default
  [{:keys [permitType]}]
  (permit/get-application-xml-getter permitType))

(defn- get-application-xml [{:keys [permitType organization] :as application} id search-type & [raw?]]
  (if-let [{url :url credentials :credentials} (organization/get-krysp-wfs application)]
    (if-let [fetch-fn (fetch-fetch-fn application)]
      (fetch-fn url credentials id search-type raw?)
      (do
        (error "No fetch function for" permitType organization)
        (fail! :error.unknown)))
    (fail! :error.no-legacy-available)))

(defn get-application-xml-by-application-id [{:keys [id organization permitType] :as application} & [raw?]]
  (get-application-xml application id :application-id raw?))

(defn get-application-xml-by-backend-id [{:keys [organization permitType] :as application} backend-id & [raw?]]
  (when backend-id
    (get-application-xml application backend-id :kuntalupatunnus raw?)))
