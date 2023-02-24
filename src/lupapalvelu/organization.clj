(ns lupapalvelu.organization
  (:require [lupacrypto.core :as crypt]
            [lupapalvelu.attachment.stamp-schema :as stmp]
            [lupapalvelu.automatic-assignment.schemas :as automatic-schemas]
            [lupapalvelu.bag :refer [bag]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.ident.ad-login-util :refer [parse-certificate]]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.schemas :refer [PateSavedVerdictTemplates Phrase CustomPhraseCategoryMap]]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.permit :as permit :refer [PermitType]]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.sftp.schemas :refer [SftpType]]
            [lupapalvelu.wfs :as wfs]
            [lupapiste-commons.archive-metadata-schema :as archive-schema]
            [lupapiste-commons.attachment-types :as attachment-types]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail!]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.municipality :refer [resolve-municipality]]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :refer [valid-email?]]
            [sade.xml :as sxml]
            [schema.coerce :as coerce]
            [schema.core :as sc]
            [taoensso.timbre :refer [debugf errorf error]]))

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

(sc/defschema AutomaticEmailTemplate
  {:id          ssc/ObjectIdStr
   :title       sc/Str
   :contents    sc/Str
   :operations  [sc/Str]
   :states      [sc/Str]
   :parties     [sc/Str]})

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
  {:docStoreInUse                                        sc/Bool
   :docTerminalInUse                                     sc/Bool
   (sc/optional-key :docDepartmentalInUse)               sc/Bool
   (sc/optional-key :allowedTerminalAttachmentTypes)     [DocTerminalAttachmentType]
   (sc/optional-key :allowedDepartmentalAttachmentTypes) [DocTerminalAttachmentType]
   :documentPrice                                        sssc/Nat ; Total price
   :organizationFee                                      sssc/Nat ; Included in `:documentPrice` (VAT 0%)
   :organizationDescription                              (i18n/lenient-localization-schema sc/Str)
   :documentRequest                                      {:enabled      sc/Bool
                                                          :email        ssc/OptionalEmail
                                                          :instructions (i18n/lenient-localization-schema sc/Any)}})

(def default-docstore-info
  {:docStoreInUse                      false
   :docTerminalInUse                   false
   :allowedTerminalAttachmentTypes     []
   :allowedDepartmentalAttachmentTypes []
   :documentPrice                      0
   :organizationFee                    0
   :organizationDescription            (i18n/supported-langs-map (constantly ""))
   :documentRequest                    {:enabled      false
                                        :email        ""
                                        :instructions (i18n/supported-langs-map (constantly ""))}})

(sc/defschema Scope
  {:permitType                               PermitType
   :municipality                             sc/Str
   :new-application-enabled                  sc/Bool
   :inforequest-enabled                      sc/Bool
   (sc/optional-key :opening)                (sc/maybe ssc/Timestamp)
   (sc/optional-key :open-inforequest)       sc/Bool
   (sc/optional-key :open-inforequest-email) ssc/OptionalEmail
   (sc/optional-key :caseManagement)         {:enabled                   sc/Bool
                                              :version                   sc/Str
                                              (sc/optional-key :ftpUser) sc/Str}
   (sc/optional-key :bulletins)              {:enabled                                            sc/Bool
                                              (sc/optional-key :url)                              sc/Str
                                              (sc/optional-key :notification-email)               sc/Str
                                              (sc/optional-key :descriptions-from-backend-system) sc/Bool}
   (sc/optional-key :pate)                   {:enabled                 sc/Bool
                                              (sc/optional-key :sftp)  sc/Bool
                                              (sc/optional-key :robot) sc/Bool}
   (sc/optional-key :invoicing-enabled)      sc/Bool})

(def permit-types (map keyword (keys (permit/permit-types))))

(def backend-systems #{:facta :kuntanet :louhi :locus :keywinkki :iris :matti :dmcity})

(sc/defschema AuthTypeEnum (sc/enum "basic" "x-header"))

(def endpoint-types #{:application :review :attachments :parties
                      :verdict :building-extinction :conversion})

(sc/defschema EndpointTypesEnum (apply sc/enum endpoint-types))

(sc/defschema KryspHttpConf
  {:url                         (sc/maybe sc/Str)
   ;; Admin batchrun can utilize connection even when disabled. Default: disabled.
   (sc/optional-key :enabled)   sc/Bool
   (sc/optional-key :path)      {EndpointTypesEnum (sc/maybe sc/Str)}
   (sc/optional-key :auth-type) AuthTypeEnum
   (sc/optional-key :username)  sc/Str
   (sc/optional-key :password)  sc/Str
   (sc/optional-key :crypto-iv) sc/Str
   (sc/optional-key :partner)   messages/Partner
   (sc/optional-key :headers)   [{:key sc/Str :value sc/Str}]})

(def krysp-http-conf-validator (sc/validator KryspHttpConf))

(sc/defschema KryspBuildingsConf
  {:url                         sc/Str
   (sc/optional-key :username)  sc/Str
   (sc/optional-key :password)  sc/Str
   (sc/optional-key :crypto-iv) sc/Str})

(sc/defschema KryspConf
  {(sc/optional-key :ftpUser)          (sc/maybe sc/Str)
   (sc/optional-key :url)              sc/Str
   (sc/optional-key :buildings)        KryspBuildingsConf
   (sc/optional-key :username)         sc/Str
   (sc/optional-key :password)         sc/Str
   (sc/optional-key :crypto-iv)        sc/Str
   (sc/optional-key :version)          sc/Str
   (sc/optional-key :fetch-chunk-size) sc/Int
   (sc/optional-key :http)             KryspHttpConf
   (sc/optional-key :backend-system)   (apply sc/enum (map name backend-systems))})

(sc/defschema KryspOsoitteetConf
  (-> KryspConf
      (dissoc (sc/optional-key :ftpUser))
      (assoc  (sc/optional-key :defaultSRS) (sc/maybe sc/Str))))

(sc/defschema InvoiceConstants
  {(sc/optional-key :tilauslaji)           sc/Str
   (sc/optional-key :myyntiorg)            sc/Str
   (sc/optional-key :jakelutie)            sc/Str
   (sc/optional-key :sektori)              sc/Str
   (sc/optional-key :laskuttaja)           sc/Str
   (sc/optional-key :nimike)               sc/Str
   (sc/optional-key :tulosyksikko)         sc/Str})

(sc/defschema InvoicingConfig
  {(sc/optional-key :max-invoice-row-count-in-transferbatch) sc/Num
   (sc/optional-key :integration-url)                        sc/Str
   (sc/optional-key :credentials)                            {:username                    sc/Str
                                                              (sc/optional-key :password)  sc/Str
                                                              (sc/optional-key :crypto-iv) sc/Str}
   (sc/optional-key :invoice-file-prefix)                    sc/Str
   (sc/optional-key :integration-requires-customer-number?)  sc/Bool
   ;; Can the biller download the transferbatch XML
   (sc/optional-key :download?)                              sc/Bool
   ;; If true, transferbatch xml files are stored into Lupapiste sftp server
   (sc/optional-key :local-sftp?)                            sc/Bool
   ;; Local ftpUser. If given, denotes also the folder. Otherwise first ftpUser found
   ;; under scope or krysp.
   (sc/optional-key :local-sftp-user)                        sc/Str
   (sc/optional-key :constants)                              InvoiceConstants
   (sc/optional-key :backend-id?)                            sc/Bool})

(sc/defschema InvoiceBackendIdConfig
  "Automatically generated invoice backend-id configuration. The backend-id format is CODE
   + XXX, where CODE is a prefix code and XXX is a zero-padded integer with fixed
   width. For example, ZY000123. The actual number value is determined by invoices-ORG-ID
   sequence."
  {(sc/optional-key :codes)   [{:id   ssc/ObjectIdStr
                                :code ssc/NonBlankStr
                                ;; Friendly name in UI.
                                :text ssc/NonBlankStr}]
   (sc/optional-key :numbers) (sc/pred pos-int? "Number part width")})

(sc/defschema LocalBulletinsPageTexts (i18n/lenient-localization-schema {:heading1 sc/Str
                                                                         :heading2 sc/Str
                                                                         :caption [sc/Str]}))
(sc/defschema LocalBulletinsPageSettings {:texts LocalBulletinsPageTexts})

(sc/defschema AttachmentEntry
  {:type-id sc/Str
   :title sc/Str
   :type-group sc/Str})

(sc/defschema SuomifiMessagesConfig
  {(sc/optional-key :enabled) sc/Bool
   (sc/optional-key :message) sc/Str
   (sc/optional-key :attachments) [AttachmentEntry]})

(sc/defschema SuomifiSettings
  {(sc/optional-key :authority-id)  sc/Str
   (sc/optional-key :service-id)    sc/Str
   (sc/optional-key :verdict)       SuomifiMessagesConfig
   (sc/optional-key :neighbors)     SuomifiMessagesConfig})

(sc/defschema NoticeFormConfig
  {:enabled                       sc/Bool
   (sc/optional-key :text)        (zipmap (map sc/optional-key i18n/languages)
                                          (repeat sc/Str))
   (sc/optional-key :integration) sc/Bool})

;; Whether a claim for rectification (oikaisuvaatimus) is automatically included in final reviews (loppukatselmus).
(sc/defschema ReviewPdf
  {(sc/optional-key :rectification-enabled) sc/Bool
   (sc/optional-key :rectification-info)    sc/Str ; String in markup format. See `markup.cljc` for details.
   (sc/optional-key :contact)               sc/Str ; Contact text to the PDF footer. Also in markup format.
   })

(sc/defschema ExportFile
  "Info for downloading the file generated by the attachments-export batchrun"
  {:fileId      sssc/FileId
   :filename    ssc/NonBlankStr
   :size        sc/Int
   :contentType ssc/NonBlankStr
   :created     ssc/Timestamp})

(sc/defschema Organization
  {:id                                                           OrgId
   :name                                                         (i18n/lenient-localization-schema sc/Str)
   :scope                                                        [Scope]

   (sc/optional-key :_sheriff-notes)                             sc/Any
   (sc/optional-key :sftpType)                                   SftpType
   (sc/optional-key :export-files)                               [ExportFile]
   (sc/optional-key :allowedAutologinIPs)                        sc/Any
   (sc/optional-key :app-required-fields-filling-obligatory)     sc/Bool
   (sc/optional-key :plan-info-disabled)                         sc/Bool
   (sc/optional-key :assignments-enabled)                        sc/Bool
   (sc/optional-key :extended-construction-waste-report-enabled) sc/Bool
   (sc/optional-key :automatic-ok-for-attachments-enabled)       sc/Bool
   (sc/optional-key :krysp-extra-attachment-metadata-enabled)    sc/Bool
   (sc/optional-key :automatic-email-templates)                  [AutomaticEmailTemplate]
   (sc/optional-key :areas)                                      sc/Any
   (sc/optional-key :areas-wgs84)                                sc/Any
   (sc/optional-key :ram)                                        {(sc/optional-key :disabled) sc/Bool ;; RAM-attachments are not disabled by default
                                                                  (sc/optional-key :message)  (i18n/lenient-localization-schema sc/Str)}
   (sc/optional-key :calendars-enabled)                          sc/Bool
   (sc/optional-key :guestAuthorities)                           sc/Any
   (sc/optional-key :hadOpenInforequest)                         sc/Bool           ;; TODO legacy flag, migrate away
   (sc/optional-key :kopiolaitos-email)                          (sc/maybe sc/Str) ;; TODO split emails into an array
   (sc/optional-key :kopiolaitos-orderer-address)                (sc/maybe sc/Str)
   (sc/optional-key :kopiolaitos-orderer-email)                  (sc/maybe sc/Str)
   (sc/optional-key :kopiolaitos-orderer-phone)                  (sc/maybe sc/Str)
   (sc/optional-key :krysp)                                      {(apply sc/enum permit-types) KryspConf
                                                                  (sc/optional-key :osoitteet) KryspOsoitteetConf}
   (sc/optional-key :links)                                      [Link]
   (sc/optional-key :map-layers)                                 sc/Any
   (sc/optional-key :notifications)                              {(sc/optional-key :inforequest-notification-emails) [ssc/Email]
                                                                  (sc/optional-key :neighbor-order-emails)           [ssc/Email]
                                                                  (sc/optional-key :submit-notification-emails)      [ssc/Email]
                                                                  (sc/optional-key :funding-notification-emails)     [ssc/Email]}
   (sc/optional-key :operations-attachments)                     sc/Any
   (sc/optional-key :operations-tos-functions)                   sc/Any
   (sc/optional-key :permanent-archive-enabled)                  sc/Bool
   (sc/optional-key :digitizer-tools-enabled)                    sc/Bool
   (sc/optional-key :automatic-emails-enabled)                   sc/Bool
   (sc/optional-key :filebank-enabled)                           sc/Bool
   (sc/optional-key :reporting-enabled)                          sc/Bool
   (sc/optional-key :foreman-termination-request-enabled)        sc/Bool
   (sc/optional-key :review-officers-list-enabled)               sc/Bool
   (sc/optional-key :rakennusluokat-enabled)                     sc/Bool
   (sc/optional-key :foreman-termination-krysp-enabled)          sc/Bool
   (sc/optional-key :permanent-archive-in-use-since)             sc/Any
   (sc/optional-key :earliest-allowed-archiving-date)            sssc/Nat
   (sc/optional-key :reservations)                               sc/Any
   (sc/optional-key :selected-operations)                        sc/Any
   (sc/optional-key :statementGivers)                            sc/Any
   (sc/optional-key :reviewOfficers)                             sc/Any
   (sc/optional-key :handler-roles)                              [HandlerRole]
   (sc/optional-key :suti)                                       {(sc/optional-key :www)        ssc/OptionalHttpUrl
                                                                  (sc/optional-key :enabled)    sc/Bool
                                                                  (sc/optional-key :server)     Server
                                                                  (sc/optional-key :operations) [sc/Str]}
   (sc/optional-key :tags)                                       [Tag]
   (sc/optional-key :validate-verdict-given-date)                sc/Bool
   ;; If Pate is enabled, will the first review done in Lupapiste automatically transfer the application to
   ;; Construction started state. Default is true.
   (sc/optional-key :automatic-construction-started)             sc/Bool
   (sc/optional-key :automatic-review-fetch-enabled)             sc/Bool
   (sc/optional-key :only-use-inspection-from-backend)           sc/Bool
   (sc/optional-key :vendor-backend-redirect)                    {(sc/optional-key :vendor-backend-url-for-backend-id) ssc/OptionalHttpUrl
                                                                  (sc/optional-key :vendor-backend-url-for-lp-id)      ssc/OptionalHttpUrl}
   (sc/optional-key :use-attachment-links-integration)           sc/Bool
   (sc/optional-key :section)                                    {(sc/optional-key :enabled)    sc/Bool
                                                                  (sc/optional-key :operations) [sc/Str]}
   (sc/optional-key :3d-map)                                     {(sc/optional-key :enabled) sc/Bool
                                                                  (sc/optional-key :server)  Server}
   (sc/optional-key :inspection-summaries-enabled)               sc/Bool
   (sc/optional-key :inspection-summary)                         {(sc/optional-key :templates)            [InspectionSummaryTemplate]
                                                                  (sc/optional-key :operations-templates) sc/Any}
   (sc/optional-key :automatic-assignment-filters)               [automatic-schemas/Filter]
   (sc/optional-key :stamps)                                     [stmp/StampTemplate]
   (sc/optional-key :docstore-info)                              DocStoreInfo
   (sc/optional-key :verdict-templates)                          PateSavedVerdictTemplates
   (sc/optional-key :phrases)                                    [Phrase]
   (sc/optional-key :custom-phrase-categories)                   CustomPhraseCategoryMap
   (sc/optional-key :operation-verdict-templates)                {sc/Keyword sc/Str}
   (sc/optional-key :state-change-msg-enabled)                   sc/Bool
   (sc/optional-key :invoicing-config)                           InvoicingConfig
   (sc/optional-key :invoicing-backend-id-config)                InvoiceBackendIdConfig
   (sc/optional-key :multiple-operations-supported)              sc/Bool
   (sc/optional-key :handling-time)                              {:enabled sc/Bool :days sc/Int}
   (sc/optional-key :conversion-debug)                           sc/Bool
   (sc/optional-key :local-bulletins-page-settings)              LocalBulletinsPageSettings
   (sc/optional-key :default-digitalization-location)            {:x sc/Str :y sc/Str}
   (sc/optional-key :remove-handlers-from-reverted-draft)        sc/Bool
   (sc/optional-key :remove-handlers-from-converted-application) sc/Bool
   (sc/optional-key :state-change-endpoint)                      {:url                                   sc/Str
                                                                  (sc/optional-key :header-parameters)   [{:name  sc/Str
                                                                                                           :value sc/Str}]
                                                                  (sc/optional-key :auth-type)           sc/Str
                                                                  (sc/optional-key :basic-auth-password) sc/Str
                                                                  (sc/optional-key :basic-auth-username) sc/Str
                                                                  (sc/optional-key :crypto-iv-s)         sc/Str}
   (sc/optional-key :suomifi-messages)                           SuomifiSettings
   (sc/optional-key :ad-login)                                   {:enabled                        sc/Bool
                                                                  :idp-cert                       sc/Str
                                                                  :idp-uri                        sc/Str
                                                                  :trusted-domains                [sc/Str]
                                                                  (sc/optional-key :role-mapping) {sc/Keyword sc/Str}}
   (sc/optional-key :ely-uspa-enabled)                           sc/Bool
   ;; List of operations for which the Not needed selection is not
   ;; available for the default attachments.
   (sc/optional-key :default-attachments-mandatory)              [sc/Str]
   ;; Whether the organization has been deactivated. In addition to
   ;; this flag, scopes and applications have been updated as
   ;; well. See `deactivate-organization` for details.
   (sc/optional-key :deactivated)                                sc/Bool
   (sc/optional-key :notice-forms)                               {;; Aloittamisilmoitus
                                                                  (sc/optional-key :construction) NoticeFormConfig
                                                                  ;; Maastoon merkintä
                                                                  (sc/optional-key :terrain)      NoticeFormConfig
                                                                  ;; Sijaintikatselmus
                                                                  (sc/optional-key :location)     NoticeFormConfig}
   (sc/optional-key :review-pdf)                                 ReviewPdf
   (sc/optional-key :buildings-extinct-enabled)                  sc/Bool
   ;; Currently (2022-06-30) used only for allowed attachments, but in future maybe
   ;; also for :default-attachments-mandatory and default-attachments
   (sc/optional-key :operations-attachment-settings)             sc/Any
   (sc/optional-key :no-comment-neighbor-attachment-enabled)     sc/Bool
   })

(sc/defschema SimpleOrg
  (select-keys Organization [:id :name :scope]))

(def parse-organization (coerce/coercer! Organization coerce/json-coercion-matcher))

(defn- with-scope-defaults [org]
  (if (:scope org)
    (update-in org [:scope] #(map (fn [s] (util/deep-merge scope-skeleton s)) %))
    org))

(defn- remove-sensitive-data [organization]
  (-> organization
      (dissoc :allowedAutologinIPs)
      (util/safe-update-in [:invoicing-config :credentials] select-keys [:username])
      (util/safe-update-in [:krysp] (util/fn->>
                                      (map (fn [[permit-type config]] [permit-type (dissoc config :password :crypto-iv)]))
                                      (into {})))))

(def admin-projection
  [:name :deactivated :scope :allowedAutologinIPs :krysp
   :permanent-archive-enabled :permanent-archive-in-use-since :filebank-enabled :rakennusluokat-enabled
   :earliest-allowed-archiving-date :digitizer-tools-enabled :automatic-emails-enabled :calendars-enabled
   :docstore-info :3d-map :default-digitalization-location :suomifi-messages
   :kopiolaitos-email :kopiolaitos-orderer-address :kopiolaitos-orderer-email :kopiolaitos-orderer-phone
   :app-required-fields-filling-obligatory :state-change-msg-enabled :ad-login :ely-uspa-enabled
   :handler-roles :invoicing-config :krysp-extra-attachment-metadata-enabled :foreman-termination-krysp-enabled
   :buildings-extinct-enabled :local-bulletins-page-settings :reporting-enabled
   :use-attachment-links-integration :export-files])

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

(defn known-organizations? [orgs]
  (or (-> orgs seq nil?)
      (let [found-orgs (get-organizations {:_id {"$in" orgs}} {:_id true})]
        (= (count orgs)
           (count found-orgs)))))

(defn organization-flag-pre-check
  "Returns a pre-check that checks if the organization has the given flag on"
  [flag value]
  (fn [{:keys [organization]}]
    (when-not (and organization
                   (= value (get @organization flag false)))
      (->> flag name (format "error.%s.incorrect-value") keyword fail))))

(defn orgAuthz-pre-checker
  "If command data has orgAuthz parameter, checks that all organization keys exists in db."
  [{{:keys [orgAuthz]} :data}]
  (when orgAuthz
    (when-not (known-organizations? (keys orgAuthz))
      (fail :error.unknown-organization))))

(defn get-autologin-ips-for-organization [org-id]
  (-> (mongo/by-id :organizations org-id [:allowedAutologinIPs])
      :allowedAutologinIPs))

(defn autologin-ip-mongo-changes [ips]
  (when (nil? (sc/check [ssc/IpAddress] ips))
    {$set {:allowedAutologinIPs ips}}))

(defn get-organization
  ([id] (get-organization id {}))
  ([id projection]
   {:pre [(not (ss/blank? id))]}
   (->> (mongo/by-id :organizations id projection)
        remove-sensitive-data
        with-scope-defaults)))

(defn get-org-from-org-or-id
  "Returns the organization model when given a parameter that is either the model itself or the
  organization id."
  [org-or-id]
  (util/pcond-> org-or-id string? (get-organization)))

(defn get-application-organization
  "Returns the application's organization, if any"
  [application]
  (bag application :organization
       (get-organization (:organization application))))

(defn update-organization
  ([id changes]
    {:pre [(ss/not-blank? id)]}
    (mongo/update-by-id :organizations id changes))
  ([id mongo-query changes]
    {:pre [(not (ss/blank? id))]}
   (mongo/update-by-query :organizations
                          (merge {:_id id} mongo-query)
                          changes)))

(defn get-organization-attachments-for-operation [organization {operation-name :name}]
  (get-in organization [:operations-attachments (keyword operation-name)]))

(defn allowed-ip? [ip organization-id]
  (pos? (mongo/count :organizations {:_id organization-id, $and [{:allowedAutologinIPs {$exists true}} {:allowedAutologinIPs ip}]})))

(defn get-organizations-by-ad-domain
  "Return the organization id and ad-settings for organizations, where :ad-login is enabled
  AND the :ad-login.trusted-domains array contains the provided
  domain (e.g. 'pori.fi'). Returns nil if nothing found."
  [domain]
  (when-let [domain (ss/blank-as-nil (ss/canonize-email domain))]
    (seq (mongo/select :organizations {:ad-login.trusted-domains domain
                                       :ad-login.enabled         true}
                       [:ad-login]))))

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

(defn encode-headers [name value crypto-iv]
  (when-not (ss/blank? name)
    (let [crypted-value (crypt/encrypt-aes-string value (env/value :backing-system :crypto-key) crypto-iv)]
      {:name name :value crypted-value})))

(defn encode [value crypto-iv]
  (when-not (ss/blank? value)
    (crypt/encrypt-aes-string value (env/value :backing-system :crypto-key) crypto-iv)))

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
  "Returns a map containing information for municipality's KRYSP WFS."
  [organization permit-type]
  (let [krysp-config (get-in organization [:krysp (keyword permit-type)])
        creds        (get-credentials krysp-config)]
    (when-not (ss/blank? (get krysp-config :url))
      (->> (when (first creds) {:credentials creds})
           (merge (select-keys krysp-config [:url :version]))))))

(defn resolve-building-wfs
  "Resolve buildings query url and related credentials. Nil of not defined."
  [organization permit-type]
  (let [{:keys [version buildings]
         :as   krysp-config} (get-in organization
                                     [:krysp (keyword permit-type)])]
    (when-let [url (some-> buildings
                           :url
                           ss/trim
                           not-empty)]
      (util/assoc-when {:url     url
                        :version version}
                       :credentials (get-credentials buildings)))))

(defn get-krysp-wfs
  "Returns a map containing :url and :version information for municipality's KRYSP WFS"
  ([{:keys [organization permitType]}]
    (get-krysp-wfs {:_id organization} permitType))
  ([query permit-type]
   (-> (mongo/select-one :organizations query [:krysp])
       (resolve-krysp-wfs permit-type))))

(defn get-building-wfs
  "Resolves building WFS url for organization.
  Looks for :buildings in organization and fallbacks to :url if not found."
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

(defn dmcity-backend
  "Pre-check that succeeds if the backend system is either dmCity or compatible (Matti)."
  [{data :data}]
  (when-let [org-id (:organizationId data)]
    (when-not (some->> (get-organization org-id  [:krysp])
                       :krysp
                       vals
                       (some (util/fn->> :backend-system (util/includes-as-kw? [:dmcity :matti]))))
      (fail :error.unsupported-organization))))

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

(defn set-state-change-endpoint [id url headers auth-type basic-creds]
  (let [old-crypto-iv-s  (get-in (get-organization id) [:state-change-endpoint :crypto-iv-s])
        old-crypto-iv    (if (some? old-crypto-iv-s) (-> old-crypto-iv-s crypt/str->bytes crypt/base64-decode))
        crypto-iv        (or old-crypto-iv (crypt/make-iv-128))
        crypted-headers  (map (fn [header] (encode-headers (:name header) (:value header) crypto-iv)) headers)
        crypted-password (when (:password basic-creds) (encode (:password basic-creds) crypto-iv))
        crypto-iv-s      (-> crypto-iv crypt/base64-encode crypt/bytes->str)
        updates          (hash-map $set (merge {"state-change-endpoint.url" url
                                                "state-change-endpoint.header-parameters" crypted-headers}
                                               (when (not-empty crypto-iv-s)
                                                 {"state-change-endpoint.crypto-iv-s" crypto-iv-s})
                                               (when (not-empty auth-type)
                                                 {"state-change-endpoint.auth-type" auth-type})
                                               (when (not-empty (:username basic-creds))
                                                 {"state-change-endpoint.basic-auth-username" (:username basic-creds)})
                                               (when (not-empty crypted-password)
                                                 {"state-change-endpoint.basic-auth-password" crypted-password})
                                               (when (= auth-type "other")
                                                 {"state-change-endpoint.basic-auth-username" ""
                                                  "state-change-endpoint.basic-auth-password" "" })))]
    (update-organization id updates)))

(defn get-organization-name
  "Organization name either in the given language or the one within `i18n/with-lang` binding.
   Resolution order: 1) `lang` 2) Finnish 3) Error string"
  ([organization-or-id lang]
   (let [organization (get-org-from-org-or-id organization-or-id)
         default (get-in organization [:name :fi] (str "???ORG:" (:id organization) "???"))]
     (get-in organization [:name (util/make-kw lang)] default)))
  ([organization-or-id]
   (get-organization-name organization-or-id i18n/*lang*)))

(defn get-organization-auto-ok [organization-id]
  (:automatic-ok-for-attachments-enabled (get-organization organization-id)))

(defn resolve-organizations
  ([municipality]
    (resolve-organizations municipality nil))
  ([municipality permit-type]
   (get-organizations {:scope {$elemMatch (merge {:municipality (resolve-municipality municipality)}
                                                 (when permit-type {:permitType permit-type}))}})))

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
   (->> (:scope organization)
        (filter #(and (= (resolve-municipality municipality) (:municipality %))
                      (= permit-type (:permitType %))))
        first)))

(defn pate-scope? [application]
  (some->> (bag application :organization
                (mongo/by-id :organizations (:organization application)))
           (resolve-organization-scope (:municipality application) (:permitType application))
           :pate
           :enabled))

(defn state-change-msg-enabled? [org-id]
  (boolean (and (ss/not-blank? org-id)
                (:state-change-msg-enabled (mongo/by-id :organizations org-id
                                                        {:state-change-msg-enabled 1})))))

(defn permit-types [{:keys [scope]}]
  (map (comp keyword :permitType) scope))

(defn has-permit-type? [org permit-type]
  (some #{permit-type} (permit-types org)))

(defn with-organization [id function]
  (if-let [organization (get-organization id)]
    (function organization)
    (do
      (debugf "organization '%s' not found with id." id)
      (fail :error.organization-not-found))))

(defn krysp-write-integration? [organization permit-type]
  (if-let [{:keys [version ftpUser http]} (get-in organization [:krysp (keyword permit-type)])]
    (boolean
      (and (ss/not-blank? version)
           (or (ss/not-blank? ftpUser)
               (and http (ss/not-blank? (:url http))))))
    false))

(defn- remove-permanent-archive-authority-roles [organization roles]
  (if (:permanent-archive-enabled organization)
    roles
    (remove roles/permanent-archive-authority-roles roles)))

(defn- remove-invoicing-roles [organization roles]
  (let [scopes (:scope organization)
        has-invoicing? (some :invoicing-enabled scopes)]
    (if has-invoicing?
      roles
      (remove roles/invoicing-roles roles))))

(defn- remove-reporting-roles [organization roles]
  (if (:reporting-enabled organization)
    roles
    (remove roles/reporting-roles roles)))

(defn- remove-departmental-roles [organization roles]
  (cond->> roles
    (not (some-> organization :docstore-info :docDepartmentalInUse))
    (remove roles/departmental-roles)))

(defn allowed-roles-in-organization [organization]
  {:pre [(map? organization)]}
  (reduce (fn [roles remove-func]
            (remove-func organization roles))
          roles/all-org-authz-roles
          [remove-permanent-archive-authority-roles
           remove-invoicing-roles
           remove-reporting-roles
           remove-departmental-roles]))

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

(defn organizations-with-ad-login-enabled []
  (map :id (mongo/select :organizations {:ad-login.enabled true} {:id 1})))

(defn ad-login-data-by-domain
  "Takes a username (= email), checks to which organization its domain belongs to and return the organization id.
  Returns nil if it's not found in any organizations."
  [username]
  {:pre [(valid-email? username)]}
  (let [domain (last (ss/split username #"@"))]
    (mongo/select :organizations {:ad-login.trusted-domains domain} {:id 1 :ad-login 1})))

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

(defn general-handler-id-for-organization [{roles :handler-roles}]
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

(defn toggle-handler-role! [org-id role-id enabled?]
  (mongo/update :organizations
                {:_id org-id :handler-roles {$elemMatch {:id role-id}}}
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

(defn- populate-attachment-structure [key docstore-info]
  (fn [[group types]]
    [group
     (mapv (partial type-info
                    (set (get docstore-info key)))
           types)]))

(defn- allowed-docterminal-attachment-types-for-organization
  "Returns a structure that contains all possible docterminal attachment types
  grouped by the attachment groups.

  [[<group name> [{:type <attachment type>
                   :enabled <is the type enabled for organization?>}
                  ...]
   ...]
   [<group name> [...]]]"
  [organization-docstore-info key]
  (->> allowed-attachments-by-group
       (mapv (populate-attachment-structure key organization-docstore-info))))

(defn allowed-docterminal-attachment-types [key org-id]
  (-> org-id
      get-docstore-info-for-organization!
      (allowed-docterminal-attachment-types-for-organization key)))

(defn check-docstore-enabled [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (-> organizationId
                  get-docstore-info-for-organization!
                  :docStoreInUse)
      (fail :error.docstore-not-enabled))))

(defn check-docterminal-enabled [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (-> organizationId
                  get-docstore-info-for-organization!
                  :docTerminalInUse)
      (fail :error.docterminal-not-enabled))))

(defn check-docdepartmental-enabled [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (-> organizationId
                  get-docstore-info-for-organization!
                  :docDepartmentalInUse)
      (fail :error.docdepartmental-not-enabled))))

(defn set-allowed-attachment-type
  [org-id attachment-type key allowed?]
  (if (= attachment-type "all")
    (if allowed?
      (update-organization org-id {$set {key allowed-attachments}})
      (update-organization org-id {$set {key []}}))
    (if allowed?
     (update-organization org-id {$addToSet {key attachment-type}})
     (update-organization org-id {$pull {key attachment-type}}))))

(defn set-allowed-docterminal-attachment-type
  [org-id attachment-type allowed?]
  (set-allowed-attachment-type org-id attachment-type :docstore-info.allowedTerminalAttachmentTypes allowed?))

(defn set-allowed-docdepartmental-attachment-type
  [org-id attachment-type allowed?]
  (set-allowed-attachment-type org-id attachment-type :docstore-info.allowedDepartmentalAttachmentTypes allowed?))

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

(defn set-ad-login-settings [org-id enabled trusted-domains idp-uri idp-cert]
  (update-organization org-id
                       {$set {:ad-login.enabled enabled
                              :ad-login.trusted-domains trusted-domains
                              :ad-login.idp-uri idp-uri
                              :ad-login.idp-cert (parse-certificate idp-cert)}}))

(defn check-ad-login-enabled [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (-> organizationId
                  (get-organization [:ad-login])
                  :ad-login
                  :enabled)
      (fail :error.ad-login-not-enabled))))

(defn update-ad-login-role-mapping [role-map user]
  (let [org-id (-> user :orgAuthz keys first name)
        org (get-organization org-id)
        updated-role-map (-> org
                             :ad-login
                             :role-mapping
                             (merge role-map))
        changes (into {} (for [[k v] updated-role-map]
                           [(keyword (str "ad-login.role-mapping." (name k))) v]))]
    (update-organization org-id {$set changes})))


;; Suomi.fi-messages

(defn suomifi-messages-enabled?
  [organizationId section]
  (boolean
    (some-> (mongo/by-id :organizations organizationId {:suomifi-messages 1})
            (get-in [:suomifi-messages (keyword section) :enabled]))))

(defn toggle-applications-read-only [org-id read-only?]
  (mongo/update-by-query :applications
                         {:organization org-id}
                         {(if read-only? $set $unset) {:readOnly true}}))

(defn deactivate-organization
  "When an organization is deactivated:
  1. Organization deactivated flag true
  2. Inforequests and applications for scopes disabled
  3. Applications into read-only mode."
  [org-id]
  (let [{:keys [scope]} (get-organization org-id {:scope.permitType 1})]
    (let [org-update (->> (range (count scope))
                          (map (fn [index]
                                 {(util/kw-path :scope index :new-application-enabled) false
                                  (util/kw-path :scope index :inforequest-enabled)     false
                                  (util/kw-path :scope index :open-inforequest)        false}))
                          (apply merge)
                          (merge {:deactivated true}))]
      (update-organization org-id {$set org-update})
      (toggle-applications-read-only org-id true))))

;;
;; Review officers
;;

(defn fetch-organization-review-officers
  "Returns the list of review officers.
  Accepts either organization model or id as parameter."
  [org-or-id]
  (let [organization (get-org-from-org-or-id org-or-id)
        review-officers (sort-by (juxt (comp ss/trim ss/lower-case :name)
                                       (comp ss/trim ss/lower-case :code))
                                 (or (:reviewOfficers organization) []))]
    (ok :data review-officers)))

(defn fetch-organization-review-officers-list-enabled
  "Returns true if the dropdown selection for review officers is in use by the given organization.
  Accepts either organization model or id as parameter."
  [org-or-id]
  (let [organization (get-org-from-org-or-id org-or-id)
        list-enabled (:review-officers-list-enabled organization)]
    (ok :data list-enabled)))


;; Matti

(defn update-matti-config [data]
  (let [{:keys [organizationId url username password vault
                buildingUrl]} (util/map-values ss/trim data)
        credentials           (encode-credentials username password)
        backend-systems       (->> (get-organization organizationId [:krysp])
                                   :krysp
                                   (map (fn [[permit-type {:keys [backend-system]}]]
                                          (when-not (ss/blank? backend-system)
                                            [permit-type backend-system])))
                                   (remove nil?)
                                   (into {}))
        http-fn               (fn [permit-type endpoint]
                                (->> (merge {:url       url
                                             :partner   (permit-type backend-systems
                                                                     (some-> backend-systems vals first))
                                             :auth-type "x-header"
                                             :headers   [{:key "x-vault" :value vault}]
                                             :path      (cond-> {:review      "Katselmustieto"
                                                                 :application endpoint
                                                                 :verdict     endpoint}
                                                          (= permit-type :R)
                                                          (assoc :building-extinction endpoint
                                                                 :conversion "RakennuslupienKonversio"))}
                                            credentials)
                                     (sc/validate KryspHttpConf)
                                     (util/map-keys (partial util/kw-path :krysp permit-type :http))))
        r-http                (http-fn :R "Rakennusvalvontapaatos")
        p-http                (http-fn :P "Poikkeamispaatos")
        building-fn           (fn [permit-type]
                                ;; buildingUrl + its credentials updates
                                (->> (assoc credentials :url buildingUrl)
                                     (sc/validate KryspBuildingsConf)
                                     (util/map-keys (partial util/kw-path :krysp permit-type :buildings))))]
    ;; State change
    (set-state-change-endpoint organizationId
                               (str url
                                    (if (ss/ends-with url "/") "" "/")
                                    "HakemuksenTilapaivitys")
                               [{:name "x-vault" :value vault}
                                {:name "x-username" :value username}
                                {:name "x-password" :value password}]
                               "other"
                               nil)
    ;; krysp.R and krysp.P
    (mongo/update-by-id :organizations
                        organizationId
                        {$set (merge (building-fn :R)
                                     (building-fn :P)
                                     r-http
                                     p-http)})))

(defn toggle-matti-functionality
  "Toggles state-change notifications or P/R HTTP integration."
  [{:keys [organizationId function enabled]}]
  (mongo/update-by-id :organizations
                      organizationId
                      {$set {(case (keyword function)
                               :stateChange :state-change-msg-enabled
                               :R :krysp.R.http.enabled
                               :P :krysp.P.http.enabled
                               (fail! :error.bad-matti-function))
                             enabled}}))

(defn get-invoicing-config [organization-id]
  (let [organization (mongo/by-id :organizations organization-id {:invoicing-config 1})]
    (util/safe-update-in (:invoicing-config organization) [:credentials] (fn [{:keys [username password crypto-iv]}]
                                                                           {:username username
                                                                            :password (if (ss/blank? crypto-iv)
                                                                                        password
                                                                                        (decode-credentials password crypto-iv))}))))

(defn is-pure-ymp-org-user? [user-organizations]
  (let [YMP-permit-types #{"YI" "YM" "VVVL" "MAL" "YL"}]
    (some->> (seq user-organizations)
             (mapcat :scope)
             (every? #(contains? YMP-permit-types (:permitType %))))))
