(ns lupapalvelu.organization
  (:import [org.geotools.data FileDataStoreFinder DataUtilities]
           [org.geotools.geojson.feature FeatureJSON]
           [org.geotools.feature.simple SimpleFeatureBuilder SimpleFeatureTypeBuilder]
           [org.geotools.geojson.geom GeometryJSON]
           [org.geotools.geometry.jts JTS]
           [org.geotools.referencing CRS]
           [org.geotools.referencing.crs DefaultGeographicCRS]
           [org.opengis.feature.simple SimpleFeature]
           [java.util ArrayList])

  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error errorf fatal]]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [monger.operators :refer :all]
            [cheshire.core :as json]
            [hiccup.core :as hiccup]
            [clj-rss.core :as rss]
            [schema.core :as sc]
            [sade.core :refer [fail fail!]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.crypt :as crypt]
            [sade.http :as http]
            [sade.xml :as sxml]
            [sade.schemas :as ssc]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.geojson :as geo]
            [me.raynes.fs :as fs]
            [clojure.walk :refer [keywordize-keys]]))

(def scope-skeleton
  {:permitType nil
   :municipality nil
   :inforequest-enabled false
   :new-application-enabled false
   :open-inforequest false
   :open-inforequest-email ""
   :opening nil})

(sc/defschema Tag
  {:id ssc/ObjectIdStr
   :label sc/Str})

(sc/defschema Layer
  {:id sc/Str
   :base sc/Bool
   :name sc/Str})

(sc/defschema Link
  {:url  ssc/OptionalHttpUrl
   :name {:fi sc/Str, :sv sc/Str}})

(sc/defschema Server
  {(sc/optional-key :url)       ssc/OptionalHttpUrl
   (sc/optional-key :username)  (sc/maybe sc/Str)
   (sc/optional-key :password)  (sc/maybe sc/Str)
   (sc/optional-key :crypto-iv) sc/Str})

(sc/defschema Organization
  {:id sc/Str
   :name {:fi sc/Str, :sv sc/Str}
   :scope [{:permitType sc/Str
            :municipality sc/Str
            :new-application-enabled sc/Bool
            :inforequest-enabled sc/Bool
            (sc/optional-key :opening) (sc/maybe ssc/Timestamp)
            (sc/optional-key :open-inforequest) sc/Bool
            (sc/optional-key :open-inforequest-email) ssc/OptionalEmail
            (sc/optional-key :caseManagement) {:enabled sc/Bool
                                               :version sc/Str
                                               (sc/optional-key :ftpUser) sc/Str}}]

   (sc/optional-key :allowedAutologinIPs) sc/Any
   (sc/optional-key :app-required-fields-filling-obligatory) sc/Bool
   (sc/optional-key :areas) sc/Any
   (sc/optional-key :areas-wgs84) sc/Any
   (sc/optional-key :calendars-enabled) sc/Bool
   (sc/optional-key :guestAuthorities) sc/Any
   (sc/optional-key :hadOpenInforequest) sc/Bool ;; TODO legacy flag, migrate away
   (sc/optional-key :kopiolaitos-email) (sc/maybe sc/Str) ;; TODO split emails into an array
   (sc/optional-key :kopiolaitos-orderer-address) (sc/maybe sc/Str)
   (sc/optional-key :kopiolaitos-orderer-email) (sc/maybe sc/Str)
   (sc/optional-key :kopiolaitos-orderer-phone) (sc/maybe sc/Str)
   (sc/optional-key :krysp) sc/Any
   (sc/optional-key :links) [Link]
   (sc/optional-key :map-layers) sc/Any
   (sc/optional-key :notifications) {(sc/optional-key :inforequest-notification-emails) [ssc/Email]
                                     (sc/optional-key :neighbor-order-emails)      [ssc/Email]
                                     (sc/optional-key :submit-notification-emails) [ssc/Email]}
   (sc/optional-key :operations-attachments) sc/Any
   (sc/optional-key :operations-tos-functions) sc/Any
   (sc/optional-key :permanent-archive-enabled) sc/Bool
   (sc/optional-key :permanent-archive-in-use-since) sc/Any
   (sc/optional-key :reservations) sc/Any
   (sc/optional-key :selected-operations) sc/Any
   (sc/optional-key :statementGivers) sc/Any
   (sc/optional-key :suti) {(sc/optional-key :www) ssc/OptionalHttpUrl
                            (sc/optional-key :enabled) sc/Bool
                            (sc/optional-key :server) Server
                            (sc/optional-key :operations) [sc/Str]}
   (sc/optional-key :tags) [Tag]
   (sc/optional-key :validate-verdict-given-date) sc/Bool
   (sc/optional-key :vendor-backend-redirect) {(sc/optional-key :vendor-backend-url-for-backend-id) ssc/OptionalHttpUrl
                                               (sc/optional-key :vendor-backend-url-for-lp-id)      ssc/OptionalHttpUrl}
   (sc/optional-key :use-attachment-links-integration) sc/Bool
   (sc/optional-key :section) {(sc/optional-key :enabled)    sc/Bool
                               (sc/optional-key :operations) [sc/Str]}})

(def permanent-archive-authority-roles [:tos-editor :tos-publisher :archivist])
(def authority-roles
  "Reader role has access to every application within org."
  (concat [:authority :approver :commenter :reader] permanent-archive-authority-roles))

(defn- with-scope-defaults [org]
  (if (:scope org)
    (update-in org [:scope] #(map (fn [s] (util/deep-merge scope-skeleton s)) %))
    org))

(defn- remove-sensitive-data [organization]
  (let [org (dissoc organization :allowedAutologinIPs)]
    (if (:krysp org)
      (update org :krysp #(into {} (map (fn [[permit-type config]] [permit-type (dissoc config :password :crypto-iv)]) %)))
      org)))

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
        (map with-scope-defaults))))

(defn get-autologin-ips-for-organization [org-id]
  (-> (mongo/by-id :organizations org-id [:allowedAutologinIPs])
      :allowedAutologinIPs))

(defn autogin-ip-mongo-changes [ips]
  (when (nil? (sc/check [ssc/IpAddress] ips))
    {$set {:allowedAutologinIPs ips}}))

(defn get-organization [id]
  {:pre [(not (s/blank? id))]}
  (->> (mongo/by-id :organizations id)
       remove-sensitive-data
       with-scope-defaults))

(defn update-organization [id changes]
  {:pre [(not (s/blank? id))]}
  (mongo/update-by-id :organizations id changes))

(defn get-organization-attachments-for-operation [organization {operation-name :name}]
  (get-in organization [:operations-attachments (keyword operation-name)]))

(defn allowed-ip? [ip organization-id]
  (pos? (mongo/count :organizations {:_id organization-id, $and [{:allowedAutologinIPs {$exists true}} {:allowedAutologinIPs ip}]})))

(defn encode-credentials
  [username password]
  (when-not (s/blank? username)
    (let [crypto-iv        (crypt/make-iv-128)
          crypted-password (crypt/encrypt-aes-string password (env/value :backing-system :crypto-key) crypto-iv)
          crypto-iv-s      (-> crypto-iv crypt/base64-encode crypt/bytes->str)]
      {:username username :password crypted-password :crypto-iv crypto-iv-s})))

(defn decode-credentials
  "Decode password that was originally generated (together with the init-vector)
   by encode-credentials. Arguments are base64 encoded."
  [password crypto-iv]
  (crypt/decrypt-aes-string password (env/value :backing-system :crypto-key) crypto-iv))

(defn get-krysp-wfs
  "Returns a map containing :url and :version information for municipality's KRYSP WFS"
  ([{:keys [organization permitType] :as application}]
    (get-krysp-wfs {:_id organization} permitType))
  ([query permit-type]
   (let [organization (mongo/select-one :organizations query [:krysp])
         krysp-config (get-in organization [:krysp (keyword permit-type)])
         crypto-key   (-> (env/value :backing-system :crypto-key) (crypt/str->bytes) (crypt/base64-decode))
         crypto-iv    (:crypto-iv krysp-config)
         password     (when-let [password (and crypto-iv (:password krysp-config))]
                        (decode-credentials password crypto-iv))
         username     (:username krysp-config)]
     (when-not (s/blank? (:url krysp-config))
       (->> (when username {:credentials [username password]})
            (merge (select-keys krysp-config [:url :version])))))))

(defn municipality-address-endpoint [^String municipality]
  {:pre [(or (string? municipality) (nil? municipality))]}
  (when (and (ss/not-blank? municipality) (re-matches #"\d{3}" municipality) )
    (let [no-bbox-srs (env/value :municipality-wfs (keyword municipality) :no-bbox-srs)]
      (merge
        (get-krysp-wfs {:scope.municipality municipality, :krysp.osoitteet.url {"$regex" ".+"}} :osoitteet)
        (when no-bbox-srs {:no-bbox-srs true})))))

(defn set-krysp-endpoint
  [id url username password endpoint-type version]
  {:pre [(mongo/valid-key? endpoint-type)]}
  (let [url (ss/trim url)
        updates (->> (encode-credentials username password)
                  (merge {:url url :version version})
                  (map (fn [[k v]] [(str "krysp." endpoint-type "." (name k)) v]))
                  (into {})
                  (hash-map $set))]
    (if (and (ss/not-blank? url) (= "osoitteet" endpoint-type))
      (let [capabilities-xml (wfs/get-capabilities-xml url username password)
            osoite-feature-type (some->> (wfs/feature-types capabilities-xml)
                                         (map (comp :FeatureType sxml/xml->edn))
                                         (filter #(re-matches #"[a-z]*:?Osoite$" (:Name %))) first)
            address-updates (assoc-in updates [$set (str "krysp." endpoint-type "." "defaultSRS")] (:DefaultSRS osoite-feature-type))]
        (if-not osoite-feature-type
          (fail! :error.no-address-feature-type)
          (update-organization id address-updates)))
      (update-organization id updates))))

(defn get-organization-name
  ([organization]
  (let [default (get-in organization [:name :fi] (str "???ORG:" (:id organization) "???"))]
    (get-in organization [:name i18n/*lang*] default)))
  ([organization-id lang]
   (let [organization (get-organization organization-id)
         default (get-in organization [:name :fi] (str "???ORG:" (:id organization) "???"))]
     (get-in organization [:name lang] default))))

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

(defn krysp-integration? [organization permit-type]
  (let [mandatory-keys [:url :version :ftpUser]]
    (when-let [krysp (select-keys (get-in organization [:krysp (keyword permit-type)]) mandatory-keys)]
     (and (= (count krysp) (count mandatory-keys)) (not-any? ss/blank? (vals krysp))))))

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

(defn earliest-archive-enabled-ts [organization-ids]
  (->> (mongo/select :organizations {:_id {$in organization-ids} :permanent-archive-enabled true} {:permanent-archive-in-use-since 1} {:permanent-archive-in-use-since 1})
       (first)
       (:permanent-archive-in-use-since)))

(defn some-organization-has-calendars-enabled? [organization-ids]
  (pos? (mongo/count :organizations {:_id {$in organization-ids} :calendars-enabled true})))

(defn organizations-with-calendars-enabled []
  (map :id (mongo/select :organizations {:calendars-enabled true} {:id 1})))

;;
;; Backend server addresses
;;

(defn- update-organization-server [mongo-path org-id url username password]
  {:pre [mongo-path (ss/not-blank? (name mongo-path))
         (string? org-id)
         (ss/optional-string? url)
         (ss/optional-string? username)
         (ss/optional-string? password)]}
  (let [server (cond
                 (ss/blank? username) {:url url, :username nil, :password nil} ; this should replace the server map (removes password)
                 (ss/blank? password) {:url url, :username username} ; update these keys only, password not changed
                 :else (assoc (encode-credentials username password) :url url)) ; this should replace the server map
        updates (if-not (contains? server :password)
                  (into {} (map (fn [[k v]] [(str (name mongo-path) \. (name k)) v]) server) )
                  {mongo-path server})]
   (update-organization org-id {$set updates})))

;;
;; Organization/municipality provided map support.
;;

(defn query-organization-map-server
  [org-id params headers]
  (when-let [m (-> org-id get-organization :map-layers :server)]
    (let [{:keys [url username password crypto-iv]} m
          base-request {:query-params params
                        :throw-exceptions false
                        :quiet true
                        :headers (select-keys headers [:accept :accept-encoding])
                        :as :stream}
          request (if-not (ss/blank? crypto-iv)
                    (assoc base-request :basic-auth [username (decode-credentials password crypto-iv)])
                    base-request)
          response (http/get url request)]
      (if (= 200 (:status response))
        response
        (do
          (error "error.integration - organization" org-id "wms server" url "returned" (:status response))
          response)))))

(defn organization-map-layers-data [org-id]
  (when-let [{:keys [server layers]} (-> org-id get-organization :map-layers)]
    (let [{:keys [url username password crypto-iv]} server]
      {:server {:url url
                :username username
                :password (if (ss/blank? crypto-iv)
                            password
                            (decode-credentials password crypto-iv))}
       :layers layers})))

(def update-organization-map-server (partial update-organization-server :map-layers.server))

;;
;; Suti
;;

(def update-organization-suti-server (partial update-organization-server :suti.server))


;;
;; Construction waste feeds
;;

(defmulti waste-ads (fn [org-id & [fmt lang]] fmt))

(defn max-modified
  "Returns the max (latest) modified value of the given document part
  or list of parts."
  [m]
  (cond
    (:modified m)   (:modified m)
    (map? m)        (max-modified (vals m))
    (sequential? m) (apply max (map max-modified (cons 0 m)))
    :default        0))

(def max-number-of-ads 100)

(defmethod waste-ads :default [ org-id & _]
  (->>
   ;; 1. Every application that maybe has available materials.
   (mongo/select
    :applications
    {:organization (if (ss/blank? org-id)
                     {$exists true}
                     org-id)
     :documents {$elemMatch {:schema-info.name "rakennusjateselvitys"
                             :data.availableMaterials {$exists true }
                             :data.contact {$nin ["" nil]}}}
     :state {$nin ["draft" "open" "canceled"]}}
    {:documents.schema-info.name 1
     :documents.data.contact 1
     :documents.data.availableMaterials 1})
   ;; 2. Create materials, contact, modified map.
   (map (fn [{docs :documents}]
          (some #(when (= (-> % :schema-info :name) "rakennusjateselvitys")
                   (let [data (select-keys (:data %) [:contact :availableMaterials])
                         {:keys [contact availableMaterials]} (tools/unwrapped data)]
                     {:contact contact
                      ;; Material and amount information are mandatory. If the information
                      ;; is not present, the row is not included.
                      :materials (->> availableMaterials
                                      tools/rows-to-list
                                      (filter (fn [m]
                                                (->> (select-keys m [:aines :maara])
                                                       vals
                                                       (not-any? ss/blank?)))))
                      :modified (max-modified data)}))
                docs)))
   ;; 3. We only check the contact validity. Name and either phone or email
   ;;    must have been provided and (filtered) materials list cannot be empty.
   (filter (fn [{{:keys [name phone email]} :contact
                 materials                  :materials}]
             (letfn [(good [s] (-> s ss/blank? false?))]
               (and (good name) (or (good phone) (good email))
                    (not-empty materials)))))
   ;; 4. Sorted in the descending modification time order.
   (sort-by (comp - :modified))
   ;; 5. Cap the size of the final list
   (take max-number-of-ads)))


(defmethod waste-ads :rss [org-id _ lang]
  (let [ads         (waste-ads org-id)
        columns     (map :name schemas/availableMaterialsRow)
        loc         (fn [prefix term] (if (ss/blank? term)
                                        term
                                        (i18n/with-lang lang (i18n/loc (str prefix term)))))
        col-value   (fn [col-key col-data]
                      (let [k (keyword col-key)
                            v (k col-data)]
                        (case k
                          :yksikko (loc "jateyksikko." v)
                          v)))
        col-row-map (fn [fun]
                      (->> columns (map fun) (concat [:tr]) vec))
        items       (for [{:keys [contact materials]} ads
                          :let [{:keys [name phone email]}  contact
                                html (hiccup/html [:div [:span (ss/join " " [name phone email])]
                                                   [:table
                                                    (col-row-map #(vec [:th (loc "available-materials." %)]))
                                                    (for [m materials]
                                                      (col-row-map #(vec [:td (col-value % m)])))]])]]

                      {:title "Lupapiste"
                       :link "http://www.lupapiste.fi"
                       :author name
                       :description (str "<![CDATA[ " html " ]]>")})]
    (rss/channel-xml {:title (str "Lupapiste:" (i18n/with-lang lang (i18n/loc "available-materials.contact")))
                      :link "" :description ""}
                     items)))

(defmethod waste-ads :json [org-id & _]
  (json/generate-string (waste-ads org-id)))

;; Waste feed enpoint parameter validators

(defn valid-org
  "Empty organization is valid"
  [{{:keys [org]} :data}]
  (when-not (or (ss/blank? org) (-> org ss/upper-case get-organization))
    (fail :error.organization-not-found)))

(defn valid-feed-format [cmd]
  (when-not (->> cmd :data :fmt ss/lower-case keyword (contains? #{:rss :json}) )
    (fail :error.invalid-feed-format)))

(defn valid-ip-addresses [ips]
  (when-let [error (sc/check [ssc/IpAddress] ips)]
    (fail :error.invalid-ip :desc (str error))))

(defn-
  ^org.geotools.data.simple.SimpleFeatureCollection
  transform-crs-to-wgs84
  "Convert feature crs in collection to WGS84"
  [org-id existing-areas ^org.geotools.feature.FeatureCollection collection]
  (let [existing-areas (if-not existing-areas
                         (mongo/by-id :organizations org-id {:areas.features.id 1 :areas.features.properties.nimi 1 :areas.features.properties.NIMI 1})
                         {:areas existing-areas})
        map-of-existing-areas (into {} (map (fn [a]
                                              (let [properties (:properties a)
                                                    nimi (if (contains? properties :NIMI)
                                                           (:NIMI properties)
                                                           (:nimi properties))]
                                                {nimi (:id a)})) (get-in existing-areas [:areas :features])))
        iterator (.features collection)
        list (ArrayList.)
        _ (loop [feature (when (.hasNext iterator)
                           (.next iterator))]
            (when feature
              ; Set CRS to WGS84 to bypass problems when converting to GeoJSON (CRS detection is skipped with WGS84).
              ; Atm we assume only CRS EPSG:3067 is used.
              ; Always give feature the same id if names match, so that search filters continue to work after reloading shp file
              ; with same feature names
              ;
              ; Cheatsheet to understand naming conventions in Geotools (from http://docs.geotools.org/latest/userguide/tutorial/feature/csv2shp.html):
              ; Java  | GeoSpatial
              ; --------------
              ; Object  Feature
              ; Class   FeatureType
              ; Field   Attribute

              (let [feature-type        (DataUtilities/createSubType (.getFeatureType feature) nil DefaultGeographicCRS/WGS84)
                    name-property       (or ; try to get name of feature from these properties
                                          (.getProperty feature "nimi")
                                          (.getProperty feature "NIMI")
                                          (.getProperty feature "Nimi")
                                          (.getProperty feature "name")
                                          (.getProperty feature "NAME")
                                          (.getProperty feature "Name")
                                          (.getProperty feature "id")
                                          (.getProperty feature "ID")
                                          (.getProperty feature "Id"))

                    feature-name        (when name-property
                                          (.getValue name-property))
                    id                  (if (contains? map-of-existing-areas feature-name)
                                          (get map-of-existing-areas feature-name)
                                          (mongo/create-id))

                    type-builder        (doto (SimpleFeatureTypeBuilder.) ; FeatureType builder, 'nimi' property
                                          (.init  feature-type) ; Init with existing subtyped feature (correct CRS, no attributes)
                                          (.add "nimi" (.getClass String))) ; Add the attribute we are interested in
                    new-feature-type    (.buildFeatureType type-builder)

                    builder             (SimpleFeatureBuilder. new-feature-type) ; new FeatureBuilder with changed crs and new attribute
                    builder             (doto builder
                                          (.init feature) ; init builder with original feature
                                          (.set "nimi" feature-name)) ; ensure 'nimi' property exists
                    transformed-feature (.buildFeature builder id)]
                (.add list transformed-feature)))
            (when (.hasNext iterator)
              (recur (.next iterator))))]
    (.close iterator)
    (DataUtilities/collection list)))

(defn- transform-coordinates-to-wgs84 [collection]
  "Convert feature coordinates in collection to WGS84 which is supported by mongo 2dsphere index"
  (let [schema (.getSchema collection)
        crs (.getCoordinateReferenceSystem schema)
        transform (CRS/findMathTransform crs DefaultGeographicCRS/WGS84 true)
        iterator (.features collection)
        feature (when (.hasNext iterator)
                  (.next iterator))
        list (ArrayList.)
        _ (loop [feature (cast SimpleFeature feature)]
            (when feature
              (let [geometry (.getDefaultGeometry feature)
                    transformed-geometry (JTS/transform geometry transform)]
                (.setDefaultGeometry feature transformed-geometry)
                (.add list feature)))
            (when (.hasNext iterator)
              (recur (.next iterator))))]
    (.close iterator)
    (DataUtilities/collection list)))

(defn parse-shapefile-to-organization-areas [org-id tempfile tmpdir file-info]
  (when-not (= (:content-type file-info) "application/zip")
    (fail! :error.illegal-shapefile))
  (let [target-dir (util/unzip (.getPath tempfile) tmpdir)
        shape-file (first (util/get-files-by-regex (.getPath target-dir) #"^.+\.shp$"))
        data-store (FileDataStoreFinder/getDataStore shape-file)
        new-collection (some-> data-store
                               .getFeatureSource
                               .getFeatures
                               ((partial transform-crs-to-wgs84 org-id nil)))
        precision      13 ; FeatureJSON shows only 4 decimals by default
        areas (keywordize-keys (json/parse-string (.toString (FeatureJSON. (GeometryJSON. precision)) new-collection)))
        ensured-areas (geo/ensure-features areas)

        new-collection-wgs84 (some-> data-store
                                     .getFeatureSource
                                     .getFeatures
                                     transform-coordinates-to-wgs84
                                     ((partial transform-crs-to-wgs84 org-id ensured-areas)))
        areas-wgs84 (keywordize-keys (json/parse-string (.toString (FeatureJSON. (GeometryJSON. precision)) new-collection-wgs84)))
        ensured-areas-wgs84 (geo/ensure-features areas-wgs84)]
    (when (geo/validate-features (:features ensured-areas))
      (fail! :error.coordinates-not-epsg3067))
    (update-organization org-id {$set {:areas ensured-areas
                                       :areas-wgs84 ensured-areas-wgs84}})
    (.dispose data-store)
    ensured-areas))

;; Group denotes organization property that has enabled and operations keys.
;; Suti and section are groups.



(defn toggle-group-enabled
  "Toggles enabled flag of a group (e.g., suti, section)."
  [organization-id group flag]
  (update-organization organization-id
                       {$set {(util/kw-path group :enabled) flag}}))

(defn toggle-group-operation
  "Toggles (either adds or removes) an operation of a group (e.g., suti, section)."
  [organization group operation-id flag]
  (let [already (contains? (-> organization group :operations set) operation-id)]
    (when (not= (boolean already) (boolean flag))
      (update-organization (:id organization)
                           {(if flag $push $pull) {(util/kw-path group :operations) operation-id}}))))
