(ns lupapalvelu.xml.krysp.application-from-krysp
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [sade.common-reader :as scr]
            [sade.xml :as sxml]
            [sade.util :refer [fn->>] :as util]
            [sade.core :refer [fail!]]))

(defn- get-lp-tunnus [xml-without-ns]
  (->> (sxml/select1 xml-without-ns [:luvanTunnisteTiedot :LupaTunnus :muuTunnustieto :tunnus])
       :content
       first))

(defn- get-kuntalupatunnus [xml-without-ns]
  (->> (sxml/select1 xml-without-ns [:luvanTunnisteTiedot :LupaTunnus :kuntalupatunnus])
       :content
       first))

(defn- split-content-per-application [xml-without-ns]
  (let [toimituksen-tiedot (sxml/select1 xml-without-ns [:toimituksenTiedot])]
    (->> (:content xml-without-ns)
         (remove (comp #{:toimituksenTiedot} :tag))
         (map (fn->> (vector toimituksen-tiedot)
                     (assoc xml-without-ns :content))))))

(defn- not-empty-content [xml raw?]
  (cond
    raw? xml
    (get-lp-tunnus xml) xml
    (get-kuntalupatunnus xml) xml))

(defn- fetch-application-xmls [organization-id permit-type ids search-type raw?]
  (if-let [{url :url credentials :credentials} (organization/get-krysp-wfs {:organization organization-id :permitType permit-type})]
    (-> (permit/fetch-xml-from-krysp permit-type url credentials ids search-type raw?)
        scr/strip-xml-namespaces
        (not-empty-content raw?))
    (fail! :error.no-legacy-available)))

(defn get-application-xml-by-application-id [{:keys [id organization permitType] :as application} & [raw?]]
  (fetch-application-xmls organization permitType [id] :application-id raw?))

(defn get-application-xml-by-backend-id [{:keys [id organization permitType] :as application} backend-id & [raw?]]
  (when backend-id
    (fetch-application-xmls organization permitType [backend-id] :kuntalupatunnus raw?)))

(defmulti get-application-xmls
  "Get application xmls from krysp"
  {:arglists '([organization-id permit-type search-type applications & [raw?]])}
  (fn [org-id permit-type search-type & args]
    (keyword search-type)))

(defmethod get-application-xmls :application-id
  [organization-id permit-type search-type application-ids]
  (->> (fetch-application-xmls organization-id permit-type application-ids :application-id false)
       (split-content-per-application)
       (group-by get-lp-tunnus)
       (util/map-values first)))

(defmethod get-application-xmls :kuntalupatunnus
  [organization-id permit-type search-type backend-ids]
  (->> (fetch-application-xmls organization-id permit-type backend-ids :kuntalupatunnus false)
       (split-content-per-application)
       (group-by get-kuntalupatunnus)
       (util/map-values first)))
