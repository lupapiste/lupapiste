(ns lupapalvelu.organization
  (:import [java.util ArrayList]
           [org.geotools.data FileDataStoreFinder DataUtilities]
           [org.geotools.feature.simple SimpleFeatureBuilder SimpleFeatureTypeBuilder]
           [org.geotools.geojson.feature FeatureJSON]
           [org.geotools.geojson.geom GeometryJSON]
           [org.geotools.geometry.jts JTS]
           [org.geotools.referencing CRS]
           [org.geotools.referencing.crs DefaultGeographicCRS]
           [org.opengis.feature.simple SimpleFeature])

  (:require [cheshire.core :as json]
            [clojure.set :as set]
            [clojure.walk :refer [keywordize-keys]]
            [lupapalvelu.attachment.stamp-schema :as stmp]
            [lupapalvelu.geojson :as geo]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.schemas :refer [PateSavedVerdictTemplates Phrase]]
            [lupapalvelu.permissions :refer [defcontext] :as permissions]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.wfs :as wfs]
            [lupapiste-commons.archive-metadata-schema :as archive-schema]
            [lupapiste-commons.attachment-types :as attachment-types]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail!]]
            [sade.crypt :as crypt]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [schema.core :as sc]
            [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error errorf fatal]]
            [schema.coerce :as coerce]))

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
  {:url  (i18n/lenient-localization-schema ssc/OptionalHttpUrl)
   :name (i18n/lenient-localization-schema sc/Str)
   (sc/optional-key :modified) ssc/Timestamp})

(sc/defschema Server
  {(sc/optional-key :url)       ssc/OptionalHttpUrl
   (sc/optional-key :username)  (sc/maybe sc/Str)
   (sc/optional-key :password)  (sc/maybe sc/Str)
   (sc/optional-key :crypto-iv) sc/Str})

(sc/defschema InspectionSummaryTemplate
  {:id ssc/ObjectIdStr
   :name sc/Str
   :modified ssc/Timestamp
   :items [sc/Str]})

(sc/defschema HandlerRole
  {:id                              ssc/ObjectIdStr
   :name                            (zipmap i18n/all-languages (repeat ssc/NonBlankStr))
   (sc/optional-key :general)       sc/Bool
   (sc/optional-key :disabled)      sc/Bool})

(sc/defschema AssignmentTrigger
  {:id ssc/ObjectIdStr
   :targets [sc/Str]
   (sc/optional-key :handlerRole) HandlerRole
   :description sc/Str})

(sc/defschema OrgId
  (sc/pred string?))


;; Allowed archive terminal attachment types for organization

(defn- type-string [[group types]]
  (if group
    [group (mapv #(->> [group %] (map name) (ss/join ".")) types)]
    [group types]))

(def allowed-attachments-by-group
  (->> (concat attachment-types/Rakennusluvat-v2
               [nil (map name archive-schema/document-types)])
       (partition 2)
       (mapv type-string)))

(def allowed-attachments
  (->>  allowed-attachments-by-group
        (mapcat second)
        vec))

(sc/defschema DocTerminalAttachmentType
  (apply sc/enum allowed-attachments))

(sc/defschema DocStoreInfo
  {:docStoreInUse                  sc/Bool
   :docTerminalInUse               sc/Bool
   :allowedTerminalAttachmentTypes [DocTerminalAttachmentType]
   :documentPrice                  sssc/Nat
   :organizationDescription        (i18n/lenient-localization-schema sc/Str)
   :documentRequest {:enabled      sc/Bool
                     :email        ssc/OptionalEmail
                     :instructions (i18n/lenient-localization-schema sc/Str)}})

(def default-docstore-info
  {:docStoreInUse                  false
   :docTerminalInUse               false
   :allowedTerminalAttachmentTypes []
   :documentPrice                  0
   :organizationDescription        (i18n/supported-langs-map (constantly ""))
   :documentRequest {:enabled      false
                     :email        ""
                     :instructions (i18n/supported-langs-map (constantly ""))}})

(sc/defschema PermitType
  (apply sc/enum (keys (permit/permit-types))))

(sc/defschema Scope
  {:permitType PermitType
   :municipality sc/Str
   :new-application-enabled sc/Bool
   :inforequest-enabled sc/Bool
   (sc/optional-key :opening)                (sc/maybe ssc/Timestamp)
   (sc/optional-key :open-inforequest)       sc/Bool
   (sc/optional-key :open-inforequest-email) ssc/OptionalEmail
   (sc/optional-key :caseManagement) {:enabled sc/Bool
                                      :version sc/Str
                                      (sc/optional-key :ftpUser) sc/Str}
   (sc/optional-key :bulletins) {:enabled sc/Bool
                                 :url sc/Str
                                 (sc/optional-key :notification-email) sc/Str
                                 (sc/optional-key :descriptions-from-backend-system) sc/Bool}
   (sc/optional-key :pate-enabled) sc/Bool})

(def permit-types (map keyword (keys (permit/permit-types))))

(def backend-systems #{:facta :kuntanet :louhi :locus :keywinkki :iris :matti})

(sc/defschema AuthTypeEnum (sc/enum "basic" "x-header"))

(def endpoint-types #{:application :review :attachments :parties :verdict})

(sc/defschema KryspHttpConf
  {:url                         (sc/maybe sc/Str)
   (sc/optional-key :path)      {(apply sc/enum endpoint-types) (sc/maybe sc/Str)}
   (sc/optional-key :auth-type) AuthTypeEnum
   (sc/optional-key :username)  sc/Str
   (sc/optional-key :password)  sc/Str
   (sc/optional-key :crypto-iv) sc/Str
   (sc/optional-key :partner)   (apply sc/enum messages/partners)
   (sc/optional-key :headers)   [{:key sc/Str :value sc/Str}]})

(def krysp-http-conf-validator (sc/validator KryspHttpConf))

(sc/defschema KryspConf
  {(sc/optional-key :ftpUser) (sc/maybe sc/Str)
   (sc/optional-key :url) sc/Str
   (sc/optional-key :buildingUrl) sc/Str
   (sc/optional-key :username) sc/Str
   (sc/optional-key :password) sc/Str
   (sc/optional-key :crypto-iv) sc/Str
   (sc/optional-key :version) sc/Str
   (sc/optional-key :fetch-chunk-size) sc/Int
   (sc/optional-key :http) KryspHttpConf
   (sc/optional-key :backend-system) (apply sc/enum (map name backend-systems))})

(sc/defschema KryspOsoitteetConf
  (-> KryspConf
      (dissoc (sc/optional-key :ftpUser))
      (assoc  (sc/optional-key :defaultSRS) (sc/maybe sc/Str))))

(sc/defschema LocalBulletinsPageTexts (i18n/lenient-localization-schema {:heading1 sc/Str
                                                                         :heading2 sc/Str
                                                                         :caption [sc/Str]}))
(sc/defschema LocalBulletinsPageSettings {:texts LocalBulletinsPageTexts})

(sc/defschema Organization
  {:id OrgId
   :name (i18n/lenient-localization-schema sc/Str)
   :scope [Scope]

   (sc/optional-key :allowedAutologinIPs) sc/Any
   (sc/optional-key :app-required-fields-filling-obligatory) sc/Bool
   (sc/optional-key :assignments-enabled) sc/Bool
   (sc/optional-key :extended-construction-waste-report-enabled) sc/Bool
   (sc/optional-key :automatic-ok-for-attachments-enabled) sc/Bool
   (sc/optional-key :areas) sc/Any
   (sc/optional-key :areas-wgs84) sc/Any
   (sc/optional-key :calendars-enabled) sc/Bool
   (sc/optional-key :guestAuthorities) sc/Any
   (sc/optional-key :hadOpenInforequest) sc/Bool ;; TODO legacy flag, migrate away
   (sc/optional-key :kopiolaitos-email) (sc/maybe sc/Str) ;; TODO split emails into an array
   (sc/optional-key :kopiolaitos-orderer-address) (sc/maybe sc/Str)
   (sc/optional-key :kopiolaitos-orderer-email) (sc/maybe sc/Str)
   (sc/optional-key :kopiolaitos-orderer-phone) (sc/maybe sc/Str)
   (sc/optional-key :krysp) {(apply sc/enum permit-types) KryspConf
                             (sc/optional-key :osoitteet) KryspOsoitteetConf}
   (sc/optional-key :links) [Link]
   (sc/optional-key :map-layers) sc/Any
   (sc/optional-key :notifications) {(sc/optional-key :inforequest-notification-emails) [ssc/Email]
                                     (sc/optional-key :neighbor-order-emails)      [ssc/Email]
                                     (sc/optional-key :submit-notification-emails) [ssc/Email]
                                     (sc/optional-key :funding-notification-emails) [ssc/Email]}
   (sc/optional-key :operations-attachments) sc/Any
   (sc/optional-key :operations-tos-functions) sc/Any
   (sc/optional-key :permanent-archive-enabled) sc/Bool
   (sc/optional-key :digitizer-tools-enabled) sc/Bool
   (sc/optional-key :permanent-archive-in-use-since) sc/Any
   (sc/optional-key :earliest-allowed-archiving-date) sssc/Nat
   (sc/optional-key :reservations) sc/Any
   (sc/optional-key :selected-operations) sc/Any
   (sc/optional-key :statementGivers) sc/Any
   (sc/optional-key :handler-roles) [HandlerRole]
   (sc/optional-key :suti) {(sc/optional-key :www) ssc/OptionalHttpUrl
                            (sc/optional-key :enabled) sc/Bool
                            (sc/optional-key :server) Server
                            (sc/optional-key :operations) [sc/Str]}
   (sc/optional-key :tags) [Tag]
   (sc/optional-key :validate-verdict-given-date) sc/Bool
   (sc/optional-key :automatic-review-fetch-enabled) sc/Bool
   (sc/optional-key :only-use-inspection-from-backend) sc/Bool
   (sc/optional-key :vendor-backend-redirect) {(sc/optional-key :vendor-backend-url-for-backend-id) ssc/OptionalHttpUrl
                                               (sc/optional-key :vendor-backend-url-for-lp-id)      ssc/OptionalHttpUrl}
   (sc/optional-key :use-attachment-links-integration) sc/Bool
   (sc/optional-key :section) {(sc/optional-key :enabled)    sc/Bool
                               (sc/optional-key :operations) [sc/Str]}
   (sc/optional-key :3d-map) {(sc/optional-key :enabled) sc/Bool
                              (sc/optional-key :server)  Server}
   (sc/optional-key :inspection-summaries-enabled) sc/Bool
   (sc/optional-key :inspection-summary) {(sc/optional-key :templates) [InspectionSummaryTemplate]
                                          (sc/optional-key :operations-templates) sc/Any}
   (sc/optional-key :assignment-triggers) [AssignmentTrigger]
   (sc/optional-key :stamps) [stmp/StampTemplate]
   (sc/optional-key :docstore-info) DocStoreInfo
   (sc/optional-key :verdict-templates) PateSavedVerdictTemplates
   (sc/optional-key :phrases) [Phrase]
   (sc/optional-key :operation-verdict-templates) {sc/Keyword sc/Str}
   (sc/optional-key :state-change-msg-enabled)      sc/Bool
   (sc/optional-key :multiple-operations-supported) sc/Bool
   (sc/optional-key :local-bulletins-page-settings) LocalBulletinsPageSettings
   (sc/optional-key :default-digitalization-location) {:x sc/Str :y sc/Str}
   (sc/optional-key :remove-handlers-from-reverted-draft) sc/Bool})


(sc/defschema SimpleOrg
  (select-keys Organization [:id :name :scope]))

(def parse-organization (coerce/coercer! Organization coerce/json-coercion-matcher))

(def permanent-archive-authority-roles [:tos-editor :tos-publisher :archivist :digitizer])
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

(def admin-projection
  [:name :scope :allowedAutologinIPs :krysp
   :pate-enabled :permanent-archive-enabled :permanent-archive-in-use-since
   :earliest-allowed-archiving-date :digitizer-tools-enabled :calendars-enabled
   :docstore-info :3d-map :default-digitalization-location
   :kopiolaitos-email :kopiolaitos-orderer-address :kopiolaitos-orderer-email :kopiolaitos-orderer-phone
   :app-required-fields-filling-obligatory :state-change-msg-enabled])

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

(defn get-organization
  ([id] (get-organization id {}))
  ([id projection]
   {:pre [(not (ss/blank? id))]}
   (->> (mongo/by-id :organizations id projection)
        remove-sensitive-data
        with-scope-defaults)))

(defn update-organization [id changes]
  {:pre [(not (ss/blank? id))]}
  (mongo/update-by-id :organizations id changes))

(defn get-organization-attachments-for-operation [organization {operation-name :name}]
  (get-in organization [:operations-attachments (keyword operation-name)]))

(defn allowed-ip? [ip organization-id]
  (pos? (mongo/count :organizations {:_id organization-id, $and [{:allowedAutologinIPs {$exists true}} {:allowedAutologinIPs ip}]})))

(defn pate-org? [org-id]
  (pos? (mongo/count :organizations {:_id org-id :pate-enabled true})))

(defn krysp-urls-not-set?
  "Takes organization as parameter.
  Returns true if organization has 0 non-blank krysp urls set."
  [{krysp :krysp}]
  (every? (fn [[_ conf]] (ss/blank? (:url conf))) krysp))

(def some-krysp-url?
  "Takes organization as parameter.
  Returns true if some of the krysp configs has non-blank url set, else false."
  (complement krysp-urls-not-set?))

(defn encode-credentials
  [username password]
  (when-not (ss/blank? username)
    (let [crypto-iv        (crypt/make-iv-128)
          crypted-password (crypt/encrypt-aes-string password (env/value :backing-system :crypto-key) crypto-iv)
          crypto-iv-s      (-> crypto-iv crypt/base64-encode crypt/bytes->str)]
      {:username username :password crypted-password :crypto-iv crypto-iv-s})))

(defn decode-credentials
  "Decode password that was originally generated (together with the init-vector)
   by encode-credentials. Arguments are base64 encoded."
  [password crypto-iv]
  (crypt/decrypt-aes-string password (env/value :backing-system :crypto-key) crypto-iv))

(defn get-credentials
  [{:keys [username password crypto-iv]}]
  (when (and username crypto-iv password)
    [username (decode-credentials password crypto-iv)]))

(defn resolve-krysp-wfs
  "Returns a map containing information for municipality's KRYSP WFS.
  url-key defines which URL type is of interest (eg :url or :buildingUrl)"
  ([organization permit-type]
   (resolve-krysp-wfs :url organization permit-type))
  ([url-key organization permit-type]
   (let [krysp-config (get-in organization [:krysp (keyword permit-type)])
         creds        (get-credentials krysp-config)]
     (when-not (ss/blank? (get krysp-config url-key))
       (->> (when (first creds) {:credentials creds})
            (merge (select-keys krysp-config [url-key :version])))))))

(defn resolve-building-wfs
  "Resolve :buildingUrl and associated data from organization.
  Renames :buildingUrl to :url before returning a map."
  [organization permit-type]
  (set/rename-keys
    (resolve-krysp-wfs :buildingUrl organization permit-type)
    {:buildingUrl :url}))

(defn get-krysp-wfs
  "Returns a map containing :url and :version information for municipality's KRYSP WFS"
  ([{:keys [organization permitType]}]
    (get-krysp-wfs {:_id organization} permitType))
  ([query permit-type]
   (-> (mongo/select-one :organizations query [:krysp])
       (resolve-krysp-wfs permit-type))))

(defn get-building-wfs
  "Resolves building WFS url for organization.
  Looks for :buildingUrl in organization and fallbacks to :url if not found.
  NOTE: converts :buildingUrl to :url for convenience."
  ([{:keys [organization permitType]}]
   (get-building-wfs {:_id organization} permitType))
  ([query permit-type]
   (when-some [org (mongo/select-one :organizations query [:krysp])]
     (or (resolve-building-wfs org permit-type)
         (resolve-krysp-wfs org permit-type)))))

(defn municipality-address-endpoint [^String municipality]
  {:pre [(or (string? municipality) (nil? municipality))]}
  (when (and (ss/not-blank? municipality) (re-matches #"\d{3}" municipality) )
    (let [no-bbox-srs (env/value :municipality-wfs (keyword municipality) :no-bbox-srs)]
      (some-> (get-krysp-wfs {:scope.municipality municipality, :krysp.osoitteet.url {"$regex" "\\S+"}} :osoitteet)
              (util/assoc-when :no-bbox-srs (boolean no-bbox-srs))))))

(defn dissoc-credentials
  "Returns $unset updates for credentials, if new username is blank."
  [new-username {:keys [credentials]} endpoint-type]
  (when (and (ss/blank? new-username) (ss/not-blank? (first credentials)))
    {$unset {(str "krysp." endpoint-type ".username") 1
             (str "krysp." endpoint-type ".password") 1
             (str "krysp." endpoint-type ".crypto-iv") 1}}))

(defn set-krysp-endpoint
  [id url username password endpoint-type version old-config]
  {:pre [(mongo/valid-key? endpoint-type)]}
  (let [url (ss/trim url)
        updates (->> (encode-credentials username password)
                  (merge {:url url :version version})
                  (map (fn [[k v]] [(str "krysp." endpoint-type "." (name k)) v]))
                  (into {})
                  (hash-map $set)
                  (merge (dissoc-credentials username old-config endpoint-type)))]
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

(defn get-organization-auto-ok [organization-id]
  (:automatic-ok-for-attachments-enabled (get-organization organization-id)))

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

(defn permit-types [{scope :scope :as organization}]
  (map (comp keyword :permitType) scope))

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))

(defn krysp-integration? [organization permit-type]
  (if-let [{:keys [version ftpUser http]} (get-in organization [:krysp (keyword permit-type)])]
    (boolean
      (and (ss/not-blank? version)
           (or (ss/not-blank? ftpUser)
               (and http (ss/not-blank? (:url http))))))
    false))

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

;; 3D Map. See also 3d-map and 3d-map-api namespaces.

(def update-organization-3d-map-server (partial update-organization-server :3d-map.server))

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

(defn permit-type-validator
  "Returns validator for user-organizations permit types"
  [& valid-permit-types]
  (fn [{org :user-organizations}]
    (when (->>  (mapcat permit-types org)
                (not-any? (set (map keyword valid-permit-types))))
      (fail :error.invalid-permit-type))))

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

(defn- transform-coordinates-to-wgs84
  "Convert feature coordinates in collection to WGS84 which is supported by mongo 2dsphere index"
 [collection]
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

(defn parse-shapefile-to-organization-areas [org-id tempfile tmpdir]
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

(defn add-organization-link [organization name url created]
  (update-organization organization
                       {$push {:links {:name     name
                                       :url      url
                                       :modified created}}}))

(defn update-organization-link [organization index name url created]
  (update-organization organization
                       {$set {(str "links." index) {:name     name
                                                    :url      url
                                                    :modified created}}}))

(defn- combine-keys [prefix [k v]]
  [(keyword (str (name prefix) "." (name k))) v])

(defn- mongofy
  "Transform eg. {:outer {:inner :value}} into {:outer.inner :value}"
  [m]
  (into {}
        (mapcat (fn [[k v]]
                  (if (and (keyword? k)
                           (map? v))
                    (map (partial combine-keys k) (mongofy v))
                    [[k v]]))
                m)))

(defn remove-organization-link [organization name url]
  (update-organization organization
                       {$pull {:links (mongofy {:name name
                                                :url  url})}}))

(defn general-handler-id-for-organization [{roles :handler-roles :as organization}]
  (:id (util/find-first :general roles)))

(defn create-handler-role
  ([]
   (create-handler-role nil {:fi "K\u00e4sittelij\u00e4"
                             :sv "Handl\u00e4ggare"
                             :en "Handler"}))
  ([role-id name]
   {:id (or role-id (mongo/create-id))
    :name name}))

(defn upsert-handler-role! [{handler-roles :handler-roles org-id :id} handler-role]
  (let [ind (or (util/position-by-id (:id handler-role) handler-roles)
                (count handler-roles))]
    (update-organization org-id {$set {(util/kw-path :handler-roles ind :id)   (:id handler-role)
                                       (util/kw-path :handler-roles ind :name) (:name handler-role)}})))

(defn disable-handler-role! [org-id role-id]
  (mongo/update :organizations {:_id org-id :handler-roles.id role-id} {$set {:handler-roles.$.disabled true}}))

(defn create-trigger [triggerId target handler description]
  (cond->
    {:id (or triggerId (mongo/create-id))
     :targets target
     :description description}
    (:name handler) (conj {:handlerRole (create-handler-role (:id handler) (:name handler))})))

(defn add-assignment-trigger [{org-id :id} trigger]
  (update-organization org-id {$push {:assignment-triggers trigger}}))

(defn- user-created? [trigger-id]
  (= trigger-id "user-created"))

(defn- update-assignment-descriptions [trigger-id description]
  (when (not (user-created? trigger-id))
    (mongo/update-by-query :assignments
                           {:trigger trigger-id}
                           {$set {:description description}})))

(defn update-assignment-trigger [{org-id :id} {:keys [targets handlerRole description] :as trigger} triggerId]
  (let [query (assoc {:assignment-triggers {$elemMatch {:id triggerId}}} :_id org-id)
        changes {$set (merge {:assignment-triggers.$.targets targets
                              :assignment-triggers.$.description description}
                             (when (:id handlerRole)
                               {:assignment-triggers.$.handlerRole handlerRole}))}
        changes (merge changes (when (nil? (:id handlerRole))
                                 {$unset {:assignment-triggers.$.handlerRole 1}}))
        num-updated (mongo/update-by-query :organizations query changes)]
    ; it is assumed that triggers are not updated very often, so this
    ; description synchronization is done to avoid unnecessary
    ; organization queries elsewhere
    (update-assignment-descriptions triggerId description)
    num-updated))

(defn remove-assignment-trigger [{org-id :id} trigger-id]
  (update-organization org-id {$pull {:assignment-triggers {:id trigger-id}}}))

(defn toggle-handler-role! [org-id role-id enabled?]
  (mongo/update :organizations
                {:_id org-id :handler-roles.id role-id}
                {$set {:handler-roles.$.disabled (not enabled?)}}))

(defn get-duplicate-scopes [municipality permit-types]
  (not-empty (mongo/select :organizations {:scope {$elemMatch {:permitType {$in permit-types} :municipality municipality}}} [:scope])))

(defn new-scope [municipality permit-type & {:keys [inforequest-enabled new-application-enabled open-inforequest open-inforequest-email opening]}]
  (util/assoc-when scope-skeleton
                   :municipality            municipality
                   :permitType              permit-type
                   :inforequest-enabled     inforequest-enabled
                   :new-application-enabled new-application-enabled
                   :open-inforequest        open-inforequest
                   :open-inforequest-email  open-inforequest-email
                   :opening                 (when (number? opening) opening)))

(defn bulletins-enabled?
  [organization permit-type municipality]
  (let [scopes (cond->> (:scope organization)
                 permit-type  (filter (comp #{permit-type} :permitType))
                 municipality (filter (comp #{municipality} :municipality)))]
    (boolean (some (comp :enabled :bulletins) scopes))))

(defn bulletin-settings-for-scope
  [organization permit-type municipality]
  {:pre [(not-any? nil? [permit-type municipality])]}
  (let [scopes (cond->> (:scope organization)
                        permit-type  (filter (comp #{permit-type} :permitType))
                        municipality (filter (comp #{municipality} :municipality)))]
    (some :bulletins scopes)))

(defn statement-giver-in-organization
  "Pre-check that fails if the user is statementGiver but not defined
  in the organization.
  Note: this will reject application-authority, so make sure you use this with some-pre-check."
  [{:keys [user organization application]}]
  (when (and application
             (some #(= {:id   (:id user)
                        :role "statementGiver"}
                       (select-keys % [:id :role]))
                   (:auth application))
             (not (util/find-by-key :email (:email user)
                                   (:statementGivers @organization))))
    (fail :error.not-organization-statement-giver)))

(defn- statement-giver-in-organization? [{user-email :email} {organization-statement-givers :statementGivers}]
  (boolean (util/find-by-key :email user-email organization-statement-givers)))

(defn- statement-giver-in-application? [{user-id :id} {application-auth :auth}]
  (->> (filter (comp #{:statementGiver} keyword :role) application-auth)
       (util/find-by-id user-id)
       boolean))

(defcontext organization-statement-giver-context [{:keys [user organization application]}]
  (when (and application organization
             (statement-giver-in-organization? user @organization)
             (statement-giver-in-application? user application))
    {:context-scope :organization
     :context-roles [:statementGiver]}))

(defn get-docstore-info-for-organization! [org-id]
  (-> (get-organization org-id [:docstore-info])
      :docstore-info))

(defn- type-info [allowed-set attachment-type]
  {:type attachment-type
   :enabled (boolean (allowed-set attachment-type))})

(defn- populate-attachment-structure [docstore-info]
  (fn [[group types]]
    [group
     (mapv (partial type-info
                    (set (:allowedTerminalAttachmentTypes docstore-info)))
           types)]))

(defn- allowed-docterminal-attachment-types-for-organization
  "Returns a structure that contains all possible docterminal attachment types
  grouped by the attachment groups.

  [[<group name> [{:type <attachment type>
                   :enabled <is the type enabled for organization?>}
                  ...]
   ...]
   [<group name> [...]]]"
  [organization-docstore-info]
  (->> allowed-attachments-by-group
       (mapv (populate-attachment-structure organization-docstore-info))))

(defn allowed-docterminal-attachment-types [org-id]
  (-> org-id
      get-docstore-info-for-organization!
      allowed-docterminal-attachment-types-for-organization))

(defn set-allowed-docterminal-attachment-type
  [org-id attachment-type allowed?]
  (if (= attachment-type "all")
    (if allowed?
      (update-organization org-id {$set {:docstore-info.allowedTerminalAttachmentTypes allowed-attachments}})
      (update-organization org-id {$set {:docstore-info.allowedTerminalAttachmentTypes []}}))
    (if allowed?
     (update-organization org-id {$addToSet {:docstore-info.allowedTerminalAttachmentTypes attachment-type}})
     (update-organization org-id {$pull {:docstore-info.allowedTerminalAttachmentTypes attachment-type}}))))

(defn document-request-info [org-id]
  (-> org-id
      get-docstore-info-for-organization!
      (get :documentRequest)))

(defn set-document-request-info
  [org-id enabled email instructions]
  (update-organization org-id
                       {$set {:docstore-info.documentRequest.enabled enabled
                              :docstore-info.documentRequest.instructions instructions
                              :docstore-info.documentRequest.email email}}))

(defn check-docstore-enabled [{user :user}]
  (when-not (-> user
                roles/authority-admins-organization-id
                get-docstore-info-for-organization!
                :docStoreInUse)
    (fail :error.docstore-not-enabled)))

(defn check-docterminal-enabled [{user :user}]
  (when-not (-> user
                roles/authority-admins-organization-id
                get-docstore-info-for-organization!
                :docTerminalInUse)
    (fail :error.docterminal-not-enabled)))
