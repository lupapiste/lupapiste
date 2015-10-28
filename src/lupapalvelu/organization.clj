(ns lupapalvelu.organization
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [monger.operators :refer :all]
            [cheshire.core :as json]
            [sade.core :refer [fail]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.crypt :as crypt]
            [sade.http :as http]
            [sade.xml :as xml]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]))

(def scope-skeleton
  {:permitType nil
   :municipality nil
   :inforequest-enabled false
   :new-application-enabled false
   :open-inforequest false
   :open-inforequest-email ""
   :opening nil})

(def permanent-archive-authority-roles [:tos-editor :tos-publisher :archivist])
(def authority-roles (concat [:authority :approver :commenter :reader] permanent-archive-authority-roles))

(defn- with-scope-defaults [org]
  (when (seq org)
    (update-in org [:scope] #(map (fn [s] (util/deep-merge scope-skeleton s)) %))))

(defn- remove-sensitive-data
  [org]
  (when (seq org)
    (->> (:krysp org)
         (map (fn [[permit-type config]] [permit-type (dissoc config :password :crypto-iv)]))
         (into {})
         (assoc org :krysp))))

(defn get-organizations
  ([]
    (get-organizations {}))
  ([query]
   (->> (mongo/select :organizations query)
        (map remove-sensitive-data)
        (map with-scope-defaults)))
  ([query projection]
   (->> (mongo/select :organizations query projection)
        (map remove-sensitive-data)
        (map with-scope-defaults ))))

(defn get-organization [id]
  {:pre [(not (s/blank? id))]}
  (->> (mongo/by-id :organizations id)
       remove-sensitive-data
       with-scope-defaults)) 

(defn update-organization [id changes]
  {:pre [(not (s/blank? id))]}
  (mongo/update-by-id :organizations id changes))

(defn get-organization-attachments-for-operation [organization operation]
  (-> organization :operations-attachments ((-> operation :name keyword))))

(defn get-krysp-wfs
  "Returns a map containing :url and :version information for municipality's KRYSP WFS"
  ([{:keys [organization permitType] :as application}]
    (get-krysp-wfs organization permitType))
  ([organization-id permit-type]
   (let [organization (mongo/by-id :organizations organization-id)
         krysp-config (get-in organization [:krysp (keyword permit-type)])
         crypto-key   (-> (env/value :backing-system :crypto-key) (crypt/str->bytes) (crypt/base64-decode))
         crypto-iv    (when-let [iv (:crypto-iv krysp-config)]
                        (-> iv (crypt/str->bytes) (crypt/base64-decode)))
         password     (when-let [password (and crypto-iv (:password krysp-config))]
                        (->> password
                             (crypt/str->bytes)
                            (crypt/base64-decode)
                             (crypt/decrypt crypto-key crypto-iv :aes)
                             (crypt/bytes->str)))
         username     (:username krysp-config)]
     (when-not (s/blank? (:url krysp-config))
       (->> (when username {:credentials [username password]})
            (merge (select-keys krysp-config [:url :version])))))))

(defn- encode-credentials
  [username password]
  (when-not (s/blank? username)
    (let [crypto-key       (-> (env/value :backing-system :crypto-key) (crypt/str->bytes) (crypt/base64-decode))
          crypto-iv        (crypt/make-iv-128)
          crypted-password (->> password
                                (crypt/str->bytes)
                                (crypt/encrypt crypto-key crypto-iv :aes)
                                (crypt/base64-encode)
                                (crypt/bytes->str))
          crypto-iv        (-> crypto-iv (crypt/base64-encode) (crypt/bytes->str))]
      {:username username :password crypted-password :crypto-iv crypto-iv})))

(defn set-krysp-endpoint
  [id url username password permitType version]
  (->> (encode-credentials username password)
       (merge {:url url :version version})
       (map (fn [[k v]] [(str "krysp." permitType "." (name k)) v]))
       (into {})
       (hash-map $set)
       (update-organization id)))

(defn get-organization-name [organization]
  (let [default (get-in organization [:name :fi] (str "???ORG:" (:id organization) "???"))]
    (get-in organization [:name i18n/*lang*] default)))

(defn resolve-organizations
  ([municipality]
    (resolve-organizations municipality nil))
  ([municipality permit-type]
    (get-organizations {:scope {$elemMatch (merge {:municipality municipality} (when permit-type {:permitType permit-type}))}})))

(defn resolve-organization [municipality permit-type]
  {:pre  [municipality (permit/valid-permit-type? permit-type)]}
  (when-let [organizations (resolve-organizations municipality permit-type)]
    (when (> (count organizations) 1)
      (errorf "*** multiple organizations in scope of - municipality=%s, permit-type=%s -> %s" municipality permit-type (count organizations)))
    (first organizations)))

(defn resolve-organization-scope
  ([municipality permit-type]
    {:pre  [municipality (permit/valid-permit-type? permit-type)]}
    (let [organization (resolve-organization municipality permit-type)]
      (resolve-organization-scope municipality permit-type organization)))
  ([municipality permit-type organization]
    {:pre  [municipality organization (permit/valid-permit-type? permit-type)]}
   (first (filter #(and (= municipality (:municipality %)) (= permit-type (:permitType %))) (:scope organization)))))

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))

(defn has-ftp-user? [organization permit-type]
  (not (ss/blank? (get-in organization [:krysp (keyword permit-type) :ftpUser]))))

(defn allowed-roles-in-organization [organization]
  {:pre [(map? organization)]}
  (if-not (:permanent-archive-enabled organization)
    (remove #(% (set permanent-archive-authority-roles)) authority-roles)
    authority-roles))

(defn filter-valid-user-roles-in-organization [organization roles]
  (let [organization  (if (map? organization) organization (get-organization organization))
        allowed-roles (set (allowed-roles-in-organization organization))]
    (filter (comp allowed-roles keyword) roles)))

(defn create-tag-ids
  "Creates mongo id for tag if id is not present"
  [tags]
  (map
    #(if (:id %)
       %
       (assoc % :id (mongo/create-id)))
    tags))

(defn some-organization-has-archive-enabled? [organization-ids]
  (pos? (mongo/count :organizations {:_id {$in organization-ids} :permanent-archive-enabled true})))


;;
;; Organization/municipality provided map support.

(defn query-organization-map-server
  [org-id params]
  (when-let [m (-> org-id get-organization :map-layers :server)]
    (let [{:keys [url username password]} m]
      (http/get url
                (merge {:query-params params}
                       (when-not (ss/blank? username)
                         {:basic-auth [username password]}))))))

(defmulti layer-info (fn [a]
                       (cond
                         (map? a) :map
                         (sequential? a) :sequential)))

(defmethod layer-info :map
  [a]
  (let [m {:title (:Title a)
           :name (:Name a)}
        layer (:Layer a)]
    (if layer
      (assoc m :layer (layer-info layer))
      m)))

(defmethod layer-info :sequential
  [a]
  (map layer-info a))



#_(defn all-layers-from-map-server
  "Fetches every map layer (title, name) from the organization/municipality map server.
  The result is a layer tree (composite), where layer can have children under :layer key."
  [org-id]
    (when-let [m (-> org-id get-organization :map-layers :server)]
    (let [{:keys [url username password]} m
          req {:query-params {:request "GetCapabilities"}}
          params (if username
                   (assoc req :basic-auth [username password])
                   req)
          _ (debug "Map server query" org-id url params)
          data (-> url (http/get params) :body xml/parse xml/xml->edn)]
      (-> data :WMS_Capabilities :Capability :Layer layer-info))))

(defn all-layers-from-map-server
  "Fetches every map layer (title, name) from the organization/municipality map server.
  The result is a layer tree (composite), where layer can have children under :layer key."
  [org-id]
  (when-let [response (query-organization-map-server org-id {:request "GetCapabilities"})]
    (let [data (-> response :body xml/parse xml/xml->edn)]
    (-> data :WMS_Capabilities :Capability :Layer layer-info))))
