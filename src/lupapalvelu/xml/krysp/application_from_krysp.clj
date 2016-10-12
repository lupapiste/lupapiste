(ns lupapalvelu.xml.krysp.application-from-krysp
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.common-reader :as krysp-cr]
            [sade.common-reader :as scr]
            [sade.xml :as sxml]
            [sade.util :refer [fn->>] :as util]
            [sade.core :refer [fail!]]))

(defn- get-lp-tunnus [permit-type xml-without-ns]
  (->> (sxml/select1 xml-without-ns (krysp-cr/get-tunnus-xml-path permit-type :application-id))
       :content
       first))

(defn- get-kuntalupatunnus [permit-type xml-without-ns]
  (->> (sxml/select1 xml-without-ns (krysp-cr/get-tunnus-xml-path permit-type :kuntalupatunnus))
       :content
       first))

(defn- group-content-by [content-fn permit-type xml-without-ns]
  (let [toimituksen-tiedot (sxml/select1 xml-without-ns [:toimituksenTiedot])]
    (->> (:content xml-without-ns)
         (remove (comp #{:toimituksenTiedot} :tag))
         (group-by (partial content-fn permit-type))
         (util/map-values (fn->> (cons toimituksen-tiedot)
                                 (assoc xml-without-ns :content))))))

(defn- not-empty-content [permit-type xml]
  (cond
    (get-lp-tunnus permit-type xml) xml
    (get-kuntalupatunnus permit-type xml) xml))

(defn- fetch-application-xmls [organization permit-type ids search-type raw?]
  (if-let [{url :url credentials :credentials} (if (map? organization)
                                                 (org/resolve-krysp-wfs organization permit-type)
                                                 (org/get-krysp-wfs {:organization organization :permitType permit-type}))]
    (cond->> (permit/fetch-xml-from-krysp permit-type url credentials ids search-type raw?)
      (not raw?) scr/strip-xml-namespaces
      (not raw?) (not-empty-content permit-type))
    (fail! :error.no-legacy-available)))

(defn get-application-xml-by-application-id [{:keys [id organization permitType] :as application} & [raw?]]
  (fetch-application-xmls organization permitType [id] :application-id raw?))

(defn get-application-xml-by-backend-id [{:keys [id organization permitType] :as application} backend-id & [raw?]]
  (when backend-id
    (fetch-application-xmls organization permitType [backend-id] :kuntalupatunnus raw?)))

(defmulti get-application-xmls
  "Get application xmls from krysp"
  {:arglists '([organization permit-type search-type ids])}
  (fn [org-id permit-type search-type & args]
    (keyword search-type)))

(defmethod get-application-xmls :application-id
  [organization permit-type search-type application-ids]
  (->> (fetch-application-xmls organization permit-type application-ids :application-id false)
       (group-content-by get-lp-tunnus permit-type)))

(defmethod get-application-xmls :kuntalupatunnus
  [organization permit-type search-type backend-ids]
  (->> (fetch-application-xmls organization permit-type backend-ids :kuntalupatunnus false)
       (group-content-by get-kuntalupatunnus permit-type)))

(defn- get-application-xmls-in-chunks [organization permit-type search-type ids chunk-size]
  (when-not (empty? ids)
    (->> (partition chunk-size chunk-size nil ids)
         (map (partial get-application-xmls organization permit-type search-type))
         (apply merge))))

(defn- get-application-xmls-by-backend-id [organization permit-type applications chunk-size]
  (let [backend-id->app-id (->> applications
                                (map (fn [app] [(some :kuntalupatunnus (:verdicts app)) (:id app)]))
                                (filter first)
                                (into {}))]
    (-> (get-application-xmls-in-chunks organization permit-type :kuntalupatunnus (keys backend-id->app-id) chunk-size)
        (rename-keys backend-id->app-id))))

(defn fetch-xmls-for-applications [organization permit-type applications]
  (let [chunk-size (get-in organization [:krysp (keyword permit-type) :chunk-size] 10)
        xmls-by-app-id (get-application-xmls-in-chunks organization permit-type :application-id (map :id applications) chunk-size)
        not-found-apps (remove (comp (set (keys xmls-by-app-id)) :id) applications)
        all-xmls (merge (get-application-xmls-by-backend-id organization permit-type not-found-apps chunk-size)
                        xmls-by-app-id)]
    all-xmls))
