(ns lupapalvelu.organization
  (:require [clojure.set :as set]
            [lupapalvelu.assurance-schemas :as assurance-schemas]
            [lupapalvelu.attachment.stamp-schema :as stmp]
            [lupapalvelu.automatic-assignment.schemas :as automatic-schemas]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.dmcity.schemas :refer [DmcityIntegration]]
            [lupapalvelu.backing-system.krysp.kuntagml-yht-version :as yht]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.bag :refer [bag]]
            [lupapalvelu.gis.arcgis.arcgis-core :as arcgis]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.invoices.shared.schemas :as invoice-schemas]
            [lupapalvelu.logging :refer [debugf error errorf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.pate.schemas :refer [CustomPhraseCategoryMap PateSettingsByCategory]]
            [lupapalvelu.pate.shared-schemas :refer [Phrase]]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.permit :as permit :refer [PermitType]]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.sftp.schemas :refer [SftpType]]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.xml.validator :as validator]
            [lupapiste-commons.archive-metadata-schema :as archive-schema]
            [lupapiste-commons.attachment-types :as attachment-types]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail! ok]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.http :as http]
            [sade.municipality :refer [resolve-municipality]]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [schema.coerce :as coerce]
            [schema.core :as sc]))

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

(sc/defschema OrgId ssc/NonBlankStr)


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

(def DocumentRequestInstructions
  (->> i18n/all-languages
       (map #(vector (sc/optional-key %) sc/Any))
       (into {})))

(sc/defschema DocStoreInfo
  {:docStoreInUse                                        sc/Bool
   :docTerminalInUse                                     sc/Bool
   (sc/optional-key :loginRequiredForPreviews)           sc/Bool
   (sc/optional-key :docDepartmentalInUse)               sc/Bool
   (sc/optional-key :allowedTerminalAttachmentTypes)     [DocTerminalAttachmentType]
   (sc/optional-key :allowedDepartmentalAttachmentTypes) [DocTerminalAttachmentType]
   :documentPrice                                        sssc/Nat ; Total price
   :organizationFee                                      sssc/Nat ; Included in `:documentPrice` (VAT 0%)
   :organizationDescription                              (i18n/lenient-localization-schema sc/Str)
   :documentRequest                                      {:enabled      sc/Bool
                                                          :email        ssc/OptionalEmail
                                                          :instructions DocumentRequestInstructions}})

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
                                        :instructions {}}})

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
                                              (sc/optional-key :enabled-at)                       ssc/Timestamp
                                              ;; Whether an appeal can be lodged via
                                              ;; bulletin. Default is true.
                                              (sc/optional-key :appeal)                           sc/Bool
                                              (sc/optional-key :notification-email)               sc/Str
                                              (sc/optional-key :descriptions-from-backend-system) sc/Bool
                                              (sc/optional-key :redirect-names)                   [{:lang (sc/enum "sv" "fi")
                                                                                                    :name sc/Str}]}
   (sc/optional-key :pate)                   {:enabled                 sc/Bool
                                              (sc/optional-key :sftp)  sc/Bool}
   (sc/optional-key :invoicing-enabled)      sc/Bool})

(def permit-types-keys (map keyword (keys (permit/permit-types))))

(def reporting-systems #{:bi-app :bi-app2})

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
   (sc/optional-key :version)          (sc/cond-pre (sc/eq "") yht/KryspVersionString)
   (sc/optional-key :fetch-chunk-size) sc/Int})

(sc/defschema KryspOsoitteetConf
  (-> KryspConf
      (dissoc (sc/optional-key :ftpUser))
      (assoc  (sc/optional-key :defaultSRS) (sc/maybe sc/Str))))

(sc/defschema InvoiceConstants
  {(sc/optional-key :tilauslaji)     sc/Str
   (sc/optional-key :myyntiorg)      sc/Str
   (sc/optional-key :myyntitoimisto) sc/Str
   (sc/optional-key :jakelutie)      sc/Str
   (sc/optional-key :sektori)        sc/Str
   (sc/optional-key :laskuttaja)     sc/Str
   (sc/optional-key :nimike)         sc/Str
   (sc/optional-key :tulosyksikko)   sc/Str})

(sc/defschema InvoicingConfig
  {(sc/optional-key :max-invoice-row-count-in-transfer-batch) ssc/Nat
   (sc/optional-key :integration-url)                         sc/Str
   (sc/optional-key :credentials)                             {:username                    sc/Str
                                                               (sc/optional-key :password)  sc/Str
                                                               (sc/optional-key :crypto-iv) sc/Str}
   (sc/optional-key :invoice-file-prefix)                     sc/Str
   (sc/optional-key :integration-requires-customer-number?)   sc/Bool
   ;; Default is the alpha-3 eg. "FIN". If this is true use "FI"
   (sc/optional-key :use-alpha-2-country-code?)               sc/Bool
   ;; Can the biller download the transfer batch XML
   (sc/optional-key :download?)                               sc/Bool
   ;; If true, transfer batch xml files are stored into Lupapiste sftp server
   (sc/optional-key :local-sftp?)                             sc/Bool
   ;; Local ftpUser. If given, denotes also the folder. Otherwise first ftpUser found
   ;; under scope or krysp.
   (sc/optional-key :local-sftp-user)                         sc/Str
   (sc/optional-key :constants)                               InvoiceConstants
   (sc/optional-key :backend-id?)                             sc/Bool
   ;; If true, Lupapiste integration SSH key is used for authentication instead of password
   (sc/optional-key :ssh-authentication?)                     sc/Bool
   (sc/optional-key :invoicing-system)                        (apply sc/enum invoice-schemas/invoicing-systems)})

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

(def default-bulletin-texts {:fi {:heading1 "",
                                  :heading2 "",
                                  :caption  [""]},
                             :sv {:heading1 "",
                                  :heading2 "",
                                  :caption  [""]},
                             :en {:heading1 "",
                                  :heading2 "",
                                  :caption  [""]}})

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

(sc/defschema CheckingPlans
  "Default checking plan ids for this organization's operations"
  {op/OperationKey [ssc/NonBlankStr]})

(sc/defschema OrgName (i18n/lenient-localization-schema sc/Str))

(sc/defschema ForemanSubmitSettings
  "Settings for defining how foreman-v3 documents are sent to the backend"
  {(sc/optional-key :notification) {(sc/optional-key :give-verdict) sc/Bool
                                    (sc/optional-key :acknowledge)  sc/Bool
                                    (sc/optional-key :approve)      sc/Bool}
   (sc/optional-key :application)  {(sc/optional-key :give-verdict) sc/Bool
                                    (sc/optional-key :approve)      sc/Bool}})

(sc/defschema RyhtiApiConfiguration
  "Setting for defining Ryhti API configurations"
  {:municipality  ssc/NonBlankStr
   :client-id     ssc/NonBlankStr
   :client-secret ssc/NonBlankStr})

(sc/defschema Organization
  {:id    OrgId
   :name  OrgName
   :scope [Scope]

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
   (sc/optional-key :dmcity-kuntagml-schemas-enabled)            sc/Bool
   (sc/optional-key :ymp-payer-enabled)                          sc/Bool
   (sc/optional-key :states-tab-enabled)                         sc/Bool
   (sc/optional-key :arcgis-map-enabled)                         sc/Bool
   (sc/optional-key :arcgis-config)                              arcgis/ArcGisConfig
   (sc/optional-key :automatic-email-templates)                  [AutomaticEmailTemplate]
   (sc/optional-key :areas)                                      sc/Any
   (sc/optional-key :areas-wgs84)                                sc/Any
   (sc/optional-key :ram)                                        {(sc/optional-key :disabled) sc/Bool ;; RAM-attachments are not disabled by default
                                                                  (sc/optional-key :message)  (i18n/lenient-localization-schema sc/Str)}
   (sc/optional-key :guestAuthorities)                           sc/Any
   (sc/optional-key :hadOpenInforequest)                         sc/Bool           ;; TODO legacy flag, migrate away
   (sc/optional-key :kopiolaitos-email)                          (sc/maybe sc/Str) ;; TODO split emails into an array
   (sc/optional-key :kopiolaitos-orderer-address)                (sc/maybe sc/Str)
   (sc/optional-key :kopiolaitos-orderer-email)                  (sc/maybe sc/Str)
   (sc/optional-key :kopiolaitos-orderer-phone)                  (sc/maybe sc/Str)
   (sc/optional-key :krysp)                                      {(apply sc/enum permit-types-keys) KryspConf
                                                                  (sc/optional-key :osoitteet)      KryspOsoitteetConf}
   (sc/optional-key :dmcity)                                     DmcityIntegration
   (sc/optional-key :links)                                      [Link]
   (sc/optional-key :map-layers)                                 sc/Any
   (sc/optional-key :notifications)                              {(sc/optional-key :inforequest-notification-emails) [ssc/Email]
                                                                  (sc/optional-key :neighbor-order-emails)           [ssc/Email]
                                                                  (sc/optional-key :submit-notification-emails)      [ssc/Email]
                                                                  (sc/optional-key :funding-notification-emails)     [ssc/Email]}
   (sc/optional-key :operations-attachments)                     sc/Any
   (sc/optional-key :operations-tos-functions)                   sc/Any
   (sc/optional-key :permanent-archive-enabled)                  sc/Bool
   ;; Tampere wants to fetch data from sftp to Facta system. This flag enables the Tampere use case
   (sc/optional-key :replace-archive-with-sftp)                  sc/Bool
   (sc/optional-key :digitizer-tools-enabled)                    sc/Bool
   (sc/optional-key :automatic-emails-enabled)                   sc/Bool
   (sc/optional-key :filebank-enabled)                           sc/Bool
   (sc/optional-key :reporting-system)                           (apply sc/enum (map name reporting-systems))
   (sc/optional-key :foreman-termination-request-enabled)        sc/Bool
   (sc/optional-key :review-officers-list-enabled)               sc/Bool
   (sc/optional-key :rakennusluokat-enabled)                     sc/Bool
   (sc/optional-key :foreman-termination-krysp-enabled)          sc/Bool
   (sc/optional-key :foreman-termination-document-enabled)       sc/Bool
   (sc/optional-key :permanent-archive-in-use-since)             sc/Any
   (sc/optional-key :earliest-allowed-archiving-date)            sssc/Nat
   (sc/optional-key :reservations)                               sc/Any ; Deprecated
   (sc/optional-key :selected-operations)                        [(sc/cond-pre sc/Keyword sc/Str)]
   (sc/optional-key :statementGivers)                            sc/Any
   (sc/optional-key :reviewOfficers)                             sc/Any
   (sc/optional-key :handler-roles)                              [HandlerRole]
   (sc/optional-key :suti)                                       {(sc/optional-key :www)        ssc/OptionalHttpUrl
                                                                  (sc/optional-key :enabled)    sc/Bool
                                                                  (sc/optional-key :server)     Server
                                                                  (sc/optional-key :operations) [sc/Str]}
   (sc/optional-key :tags)                                       [Tag]
   ;; When not false, `antoPvm` element is considered when reading verdict KuntaGML
   ;; message: if `antoPvm` exists, it must be today or earlier.
   (sc/optional-key :validate-verdict-given-date)                sc/Bool
   ;; When not false, `julkipanoPvm` element is considered when reading verdict KuntaGML
   ;; message: if `julkipanoPvm` exists, it must be today or earlier. Overrides
   ;; `:validate-verdict-given-date` if both are active.
   (sc/optional-key :validate-verdict-bulletin-date)             sc/Bool
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
                                                                  (sc/optional-key :operations-templates) {sc/Keyword (sc/cond-pre #{ssc/ObjectIdStr}
                                                                                                                                   [ssc/ObjectIdStr])}}
   (sc/optional-key :automatic-assignment-filters)               [automatic-schemas/Filter]
   (sc/optional-key :stamps)                                     [stmp/StampTemplate]
   (sc/optional-key :docstore-info)                              DocStoreInfo
   (sc/optional-key :verdict-settings)                           PateSettingsByCategory
   (sc/optional-key :phrases)                                    [Phrase]
   (sc/optional-key :custom-phrase-categories)                   CustomPhraseCategoryMap
   (sc/optional-key :operation-verdict-templates)                {sc/Keyword sc/Str}
   (sc/optional-key :invoicing-config)                           InvoicingConfig
   (sc/optional-key :invoicing-backend-id-config)                InvoiceBackendIdConfig
   (sc/optional-key :multiple-operations-supported)              sc/Bool
   (sc/optional-key :handling-time)                              {:enabled sc/Bool :days sc/Int}
   (sc/optional-key :conversion-debug)                           sc/Bool
   (sc/optional-key :local-bulletins-page-settings)              LocalBulletinsPageSettings
   (sc/optional-key :local-kuulutus-page-settings)               LocalBulletinsPageSettings
   (sc/optional-key :default-digitalization-location)            {:x sc/Str :y sc/Str}
   (sc/optional-key :tiff-archiving-enabled)                     sc/Bool
   (sc/optional-key :jpeg-archiving-enabled)                     sc/Bool
   (sc/optional-key :remove-handlers-from-reverted-draft)        sc/Bool
   (sc/optional-key :remove-handlers-from-converted-application) sc/Bool
   (sc/optional-key :suomifi-messages)                           SuomifiSettings
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
   (sc/optional-key :integrations)                               [sc/Any]
   (sc/optional-key :nearby-application-markers-enabled)         sc/Bool
   (sc/optional-key :rectification-verdicts-enabled)             sc/Bool
   ;; Whether the organization allows its backing system to be accessed via
   ;; `conversion-proxy` endpoint.
   (sc/optional-key :conversion-proxy-enabled)                   sc/Bool
   (sc/optional-key :force-kuntagml-226-for-pre-2025)            sc/Bool
   ;; LPK-5211: maalampokaivo count for Matti system
   (sc/optional-key :kuntagml-maalampokaivo-count-enabled)      sc/Bool
   ;; Timestamp after which newly created applications begin collecting Ryhti data.
   ;; Does not affect existing applications, even if set in the past retroactively.
   (sc/optional-key :ryhti-data-2026-start-ts)                   sc/Int
   (sc/optional-key :ryhti-api-configuration)                    [RyhtiApiConfiguration]
   (sc/optional-key :bim-checking-plans)                         CheckingPlans
   (sc/optional-key :bim-compliance-check-enabled)               sc/Bool
   (sc/optional-key :application-submit-assurance-templates)     [assurance-schemas/ApplicationSubmitAssuranceTemplate]
   (sc/optional-key :foreman-submit-settings)                    ForemanSubmitSettings
   (sc/optional-key :transfer-application-enabled)               sc/Bool})

(def valid-organization! (sc/validator Organization))
(def organization-checker (sc/checker Organization))

(sc/defschema SimpleOrg
  (select-keys Organization [:id :name :scope]))

(def parse-organization (coerce/coercer! Organization coerce/json-coercion-matcher))

(defn- with-scope-defaults [org]
  (if (:scope org)
    (update-in org [:scope] #(mapv (fn [s] (util/deep-merge scope-skeleton s)) %))
    org))

(defn- remove-sensitive-data [organization]
  (-> organization
      (dissoc :allowedAutologinIPs)
      (util/safe-update-in [:invoicing-config :credentials] select-keys [:username])
      (util/safe-update-in [:arcgis-config :server] dissoc :password :crypto-iv)
      (util/safe-update-in [:krysp] (util/fn->>
                                      (map (fn [[permit-type config]] [permit-type (dissoc config :password :crypto-iv)]))
                                      (into {})))))

(def admin-projection
  [:name :deactivated :scope :allowedAutologinIPs :krysp
   :permanent-archive-enabled :replace-archive-with-sftp :permanent-archive-in-use-since :filebank-enabled :rakennusluokat-enabled
   :earliest-allowed-archiving-date :digitizer-tools-enabled :automatic-emails-enabled
   :docstore-info :3d-map :default-digitalization-location :suomifi-messages
   :kopiolaitos-email :kopiolaitos-orderer-address :kopiolaitos-orderer-email :kopiolaitos-orderer-phone
   :app-required-fields-filling-obligatory :ely-uspa-enabled
   :handler-roles :invoicing-config :krysp-extra-attachment-metadata-enabled :dmcity-kuntagml-schemas-enabled
   :foreman-termination-krysp-enabled :foreman-termination-document-enabled :buildings-extinct-enabled
   :local-bulletins-page-settings :local-kuulutus-page-settings :reporting-system :states-tab-enabled :arcgis-map-enabled
   :use-attachment-links-integration :export-files :conversion-proxy-enabled
   :bim-compliance-check-enabled :reactivate-application-guard :ymp-payer-enabled :ryhti-data-2026-start-ts])

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
        (map with-scope-defaults)))
  ([query projection options]
   (->> (mongo/select-options :organizations query (update options :projection #(or projection %)))
        (map remove-sensitive-data)
        (map with-scope-defaults))))

(defn known-organizations? [orgs]
  (or (-> orgs seq nil?)
      (let [found-orgs (get-organizations {:_id {"$in" orgs}} {:_id true})]
        (= (count orgs)
           (count found-orgs)))))

(defn get-autologin-ips-for-organization [org-id]
  (-> (mongo/by-id :organizations org-id [:allowedAutologinIPs])
      :allowedAutologinIPs))

(def ip-addresses-checker (sc/checker [ssc/IpAddress]))

(defn autologin-ip-mongo-changes [ips]
  (when (nil? (ip-addresses-checker ips))
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

(defn krysp-urls-not-set?
  "Takes organization as parameter.
  Returns true if organization has 0 non-blank krysp urls set."
  [{krysp :krysp}]
  (every? (fn [[_ conf]] (ss/blank? (:url conf))) krysp))

(def some-krysp-url?
  "Takes organization as parameter.
  Returns true if some of the krysp configs has non-blank url set, else false."
  (complement krysp-urls-not-set?))

(defn resolve-krysp-wfs
  "Returns a map containing information for municipality's KRYSP WFS."
  [organization permit-type]
  (let [krysp-config (get-in organization [:krysp (keyword permit-type)])
        creds        (bs-sec/get-credentials krysp-config)]
    (when-not (ss/blank? (get krysp-config :url))
      (->> (when (first creds) {:credentials creds})
           (merge (select-keys krysp-config [:url :version]))))))

(defn resolve-building-wfs
  "Resolve buildings query url and related credentials. Nil of not defined."
  [organization permit-type]
  (let [{:keys [version buildings]} (get-in organization
                                            [:krysp (keyword permit-type)])]
    (when-let [url (some-> buildings
                           :url
                           ss/trim
                           not-empty)]
      (util/assoc-when {:url     url
                        :version version}
                       :credentials (bs-sec/get-credentials buildings)))))

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
   (when-some [org (mongo/select-one :organizations query [:dmcity :krysp])]
     (or (dm-cfg/building-wfs org)
         (resolve-building-wfs org permit-type)
         (resolve-krysp-wfs org permit-type)))))

(defn municipality-address-endpoint [^String municipality]
  {:pre [(or (string? municipality) (nil? municipality))]}
  (when (and (ss/not-blank? municipality) (re-matches #"\d{3}" municipality) )
    (or (let [no-bbox-srs (env/value :municipality-wfs (keyword municipality) :no-bbox-srs)]
          (some-> (get-krysp-wfs {:scope.municipality municipality
                                  :krysp.osoitteet.url {"$regex" "\\S+"}}
                                 :osoitteet)
                  (util/assoc-when :no-bbox-srs (boolean no-bbox-srs))))
        (dm-cfg/address-endpoint municipality))))

(defn dissoc-credentials
  "Returns $unset updates for credentials, if new username is blank."
  [new-username {:keys [credentials]} endpoint-type]
  (when (and (ss/blank? new-username) (ss/not-blank? (first credentials)))
    {$unset {(str "krysp." endpoint-type ".username") 1
             (str "krysp." endpoint-type ".password") 1
             (str "krysp." endpoint-type ".crypto-iv") 1}}))

(defn organization-flag-pre-check
  "Returns a pre-check that fails if the organization does not have the given flag set to the given value"
  [flag value]
  (fn [{:keys [organization] {:keys [organizationId]} :data}]
    (when (some-> (or (force organization)
                      (some-> organizationId (get-organization [flag])))
                  (get flag false)
                  (not= value))
      (->> flag name (format "error.%s.incorrect-value") keyword fail))))

(defn organization-exists [{data :data}]
  (when-let [organizationId (:organizationId data)]
    (when-not (get-organization organizationId [:id])
      (fail :error.organization-not-found))))

(defn orgAuthz-pre-checker
  "If command data has orgAuthz parameter, checks that all organization keys exists in db."
  [{{:keys [orgAuthz]} :data}]
  (when orgAuthz
    (when-not (known-organizations? (keys orgAuthz))
      (fail :error.unknown-organization))))

(defn orgAuthz-roles-pre-checker-fn
  "If a command data has the `orgAuthz` parameter, checks that all the roles match the expected values."
  [allowed-roles]
  {:pre [(set? allowed-roles)]}
  (fn [{{:keys [orgAuthz]} :data}]
    (let [roles (->> orgAuthz
                     (mapcat val)
                     (map keyword)
                     (into #{}))]
      (when-not (empty? (set/difference roles allowed-roles))
        (fail :error.forbidden-orgAuthz-roles)))))

(defn krysp-version-ok?
  "Name refers to organization's MongoDB 'krysp' field; not strictly limited to KRYSP (KuntaGML) versions"
  [version permit-type org-id]
  (or (ss/blank? version) ; itests disable the integration by setting version to ""
      (and (string? version)
           (-> permit-type keyword validator/supported-versions-by-permit-type set (contains? version))
           (or (-> version (ss/ends-with? "-dmcity-lp") not)
               (-> (mongo/by-id :organizations org-id [:dmcity-kuntagml-schemas-enabled])
                   :dmcity-kuntagml-schemas-enabled)))))

(defn set-krysp-endpoint
  "Name refers to organization's MongoDB 'krysp' field; not strictly limited to KRYSP (KuntaGML) versions"
  [id url username password endpoint-type version old-config]
  {:pre [(mongo/valid-key? endpoint-type)]}
  (let [url (ss/trim url)
        updates (->> (bs-sec/encode-credentials username password)
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

(defn kuntagml-read-integration? [organization permit-type]
  (let [{:keys [version url]} (get-in organization [:krysp (keyword permit-type)])]
    (and (ss/not-blank? version) (ss/not-blank? url))))

(defn krysp-write-integration? [organization permit-type]
  (if-let [{:keys [version ftpUser]} (get-in organization [:krysp (keyword permit-type)])]
    (boolean
      (and (ss/not-blank? version)
           (or (ss/not-blank? ftpUser)
               (dm-cfg/target-enabled? organization permit-type))))
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
  (if (:reporting-system organization)
    roles
    (remove roles/reporting-roles roles)))

(defn- remove-departmental-roles [organization roles]
  (cond->> roles
    (not (some-> organization :docstore-info :docDepartmentalInUse))
    (remove roles/departmental-roles)))

(defn remove-inactive-roles [organization roles remove-fns]
  {:pre [(map? organization)]}
  (reduce (fn [roles remove-func]
            (remove-func organization roles))
          roles
          remove-fns))

(defn allowed-roles-in-organization [organization]
  (remove-inactive-roles organization
                         roles/authority-managed-org-authz-roles
                         [remove-permanent-archive-authority-roles
                          remove-invoicing-roles]))

(defn allowed-oauth-roles-in-organization [organization]
  (remove-inactive-roles organization
                         roles/oauth-roles-without-admin
                         [remove-reporting-roles
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

(defn check-organization-flag
  "Returns true if the organization has the given flag enabled"
  [organization-or-id flag-kw]
  (let [org-or-id (force organization-or-id)]
    (cond
      (string? org-or-id) (mongo/any? :organizations {:_id org-or-id flag-kw true})
      (map? org-or-id)    (-> org-or-id flag-kw true?)
      :else               (ex-info "Not an organization" (util/mmap organization-or-id)))))

(defn check-org-flag-by-app-id
  "Returns true if the organization of the given application has the given flag enabled.
  Use `check-organization-flag` above directly if you have the organization or organization id"
  [application-id flag-kw]
  (-> (mongo/by-id :applications application-id [:organization])
      :organization
      (check-organization-flag flag-kw)))

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
                 :else (assoc (bs-sec/encode-credentials username password) :url url)) ; this should replace the server map
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
                    (assoc base-request :basic-auth [username (bs-sec/decode-password password crypto-iv)])
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
                            (bs-sec/decode-password password crypto-iv))}
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

(defn valid-ip-addresses [ips]
  (when-let [error (ip-addresses-checker ips)]
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
  "Returns true if bulletins are currently enabled for the given scope's bulletin config or organization.
  If the timestamp given is null, any enabled date is ignored"
  ([{:keys [enabled enabled-at]} now-ts]
   (boolean (and enabled
                 (or (nil? now-ts)
                     (nil? enabled-at)
                     (< enabled-at now-ts)))))
  ([organization permit-type municipality now-ts]
   (->> (cond->> (:scope organization)
          permit-type  (filter (comp #{permit-type} :permitType))
          municipality (filter (comp #{municipality} :municipality)))
        (some #(bulletins-enabled? (:bulletins %) now-ts))
        boolean)))

(defn bulletin-settings-for-scope
  [organization permit-type municipality]
  {:pre [(not-any? nil? [permit-type municipality])]}
  (let [scopes (cond->> (:scope organization)
                        permit-type  (filter (comp #{permit-type} :permitType))
                        municipality (filter (comp #{municipality} :municipality)))]
    (some :bulletins scopes)))

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

(defn check-login-required-for-previews
  "Pre-check for the login-required pseudoquery that checks if users are required to log in to see
  document preview images in the Docstore (Kauppa)."
  [{{:keys [organizationId]} :data}]
  (when organizationId
    (when-not (-> organizationId
                  (get-organization [:docstore-info.loginRequiredForPreviews])
                  :docstore-info
                  :loginRequiredForPreviews)
      (fail :error.login-required-for-previews-not-enabled))))

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
  (let [{:keys [scope]} (get-organization org-id {:scope.permitType 1})
        org-update      (->> (range (count scope))
                             (map (fn [index]
                                    {(util/kw-path :scope index :new-application-enabled) false
                                     (util/kw-path :scope index :inforequest-enabled)     false
                                     (util/kw-path :scope index :open-inforequest)        false}))
                             (apply merge)
                             (merge {:deactivated true}))]
    (update-organization org-id {$set org-update})
    (toggle-applications-read-only org-id true)))

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


(defn get-invoicing-config [organization-id]
  (let [organization (mongo/by-id :organizations organization-id {:invoicing-config 1})]
    (util/safe-update-in (:invoicing-config organization) [:credentials] (fn [{:keys [username password crypto-iv]}]
                                                                           {:username username
                                                                            :password (if (ss/blank? crypto-iv)
                                                                                        password
                                                                                        (bs-sec/decode-password password crypto-iv))}))))

(defn is-pure-ymp-org-user? [user-organizations]
  (let [YMP-permit-types #{"YI" "YM" "VVVL" "MAL" "YL"}]
    (some->> (seq user-organizations)
             (mapcat :scope)
             (every? #(contains? YMP-permit-types (:permitType %))))))

(defn ryhti-data-2026?
  "Returns true when creating a new application if the given timestamp (typically current time)
   is >= the organization's :ryhti-data-2026-start-ts. I.e. organization is set to collect ryhti data"
  [organization ts]
  (boolean (some->> (force organization) :ryhti-data-2026-start-ts (date/after? ts))))
