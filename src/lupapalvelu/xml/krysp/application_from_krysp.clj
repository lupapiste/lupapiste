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

(defn get-application-xml [{:keys [id permitType] :as application} search-type & [raw?]]
  (if-let [{url :url credentials :credentials} (organization/get-krysp-wfs application)]
    (if-let [fetch-fn (fetch-fetch-fn application)]
      (fetch-fn url credentials id search-type raw?)
      (do
        (error "No fetch function for" permitType (:organization application))
        (fail! :error.unknown)))
    (fail! :error.no-legacy-available)))
