(ns lupapalvelu.xml.krysp.application-from-krysp
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [sade.core :refer [fail!]]))

(defn get-application-xml [{:keys [id permitType] :as application} search-type & [raw?]]
  (if-let [{url :url username :username password :password} (organization/get-krysp-wfs application)]
    (if-let [fetch-fn (permit/get-application-xml-getter permitType)]
      (fetch-fn url (when (not (empty? username)) [username password]) id search-type raw?)
      (do
        (error "No fetch function for" permitType (:organization application))
        (fail! :error.unknown)))
    (fail! :error.no-legacy-available)))
