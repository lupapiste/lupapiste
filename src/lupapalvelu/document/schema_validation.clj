(ns lupapalvelu.document.schema-validation
  (:require [lupapalvelu.document.tools :refer [SchemaFlag]]
            [lupapalvelu.roles :as roles]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :refer [defschema] :as sc]))

(def opt sc/optional-key)

(defn type-pred [& types] (comp (set types) :type))
(defn subtype-pred [& subtypes] (comp (set subtypes) :subtype))

(def input-sizes [:t :s :m :l :xl])

(defschema Auth
  "Authorization model based component state. See docgen-input-model
  for how this is enforced in the frontend. Note: empty lists do not
  affect the state in any way."
  {(opt :disabled) [sc/Keyword]  ;; Disabled if any listed action is allowed.
   (opt :enabled)  [sc/Keyword]  ;; Disabled if any listed action is not allowed.
   })

(defschema FlagConditions
  "Inclusion/exclusion of the element depending on the schema context flags."
  {(opt :schema-include) SchemaFlag
   (opt :schema-exclude) SchemaFlag})

(defschema single-value (sc/conditional string? sc/Str
                                        number? sc/Num
                                        :else   (sc/enum true false nil)))

(defschema field-matcher
  "Definition for target value in another element. Used with
  `:hide-when` and `:show-when`."
  (merge FlagConditions
         {:path           sc/Str ;; Path within document
          :values         #{single-value}
          ;; Target document name. Default is the "current" document. If
          ;; there are multiple documents with the same name, only the first
          ;; one is considered.
          (opt :document) sc/Str}))

(defschema Whitelist
  "Document field/group should be disabled or hidden unless some condition(s) are met.
  Note that if the field's visibility is dependent on dynamic values, such as another form field's value or
  the application's permit subtype, use `:hide-when` or `:show-when` instead.

  Conditions defined as whitelisted/blacklisted are only checked when the app is opened (or page refreshed)
  and are not taken into consideration when omitting elements from the application PDF"
  (sc/constrained {(opt :roles)      [(sc/enum :authority
                                               :applicant
                                               :none)]
                   (opt :permitType) [(sc/enum :ARK)]
                   :otherwise        (sc/enum :disabled :hidden)}
                  #(or (not-empty (:roles %))
                       (not-empty (:permitType %)))
                  "some whitelist conditions required, can't all be empty"))

(defschema Blacklist
  "Document field/group contains sensitive information"
  [(sc/if string?
     (sc/eq "turvakieltoKytkin")
     (sc/enum :neighbor))])

(defschema Emitter
  "Each emitted event has a dedicated handler (within docgen), which decides how to react.
  Typically this means publishing a message through the hub to one or more subscribers."
  (sc/enum :accordionUpdate :buildingDemolition :emailChanged :filterByCode :hetuChanged :lowCarbonAssessmentCriteriaChanged))

(defschema GenInput
  "General leaf element schema. Base element for input elements."
  (merge
    FlagConditions
    {:name                      sc/Str         ;; Element name
     :type                      (sc/enum :text :string :select :checkbox :radioGroup :date :time :textlink :ryhti-identifier)
     (opt :uicomponent)         sc/Keyword     ;; Component name for special components
     (opt :inputType)           (sc/enum :string :checkbox :localized-string :inline-string
                                         :check-string :checkbox-wrapper
                                         :paragraph) ;; Input types for generic docgen-input component
     (opt :labelclass)          sc/Str         ;; Special label style class
     (opt :i18nkey)             sc/Str         ;; Absolute localization key
     (opt :locPrefix)           sc/Str
     (opt :layout)              (sc/enum :full-width :initial-width)
     ;; Element is hidden either explicitly by boolean flag or whether the element value
     ;; is falsey (false, blank, nil).
     (opt :hidden)              (sc/cond-pre sc/Bool (sc/enum :by-value))
     (opt :label)               sc/Bool        ;; Label is shown? (default true)
     (opt :size)                (apply sc/enum input-sizes) ;; Element size (default ?)
     (opt :readonly)            sc/Bool        ;; Element is readonly
     (opt :readonly-after-sent) sc/Bool;; Element is readonly if document state is sent
     (opt :required)            sc/Bool        ;; Required field
     (opt :approvable)          sc/Bool        ;; Authority can apporove/reject field
     (opt :codes)               [sc/Keyword]   ;;
     (opt :validator)           sc/Keyword     ;; Specific validator key for element (see model/validate-element)
     (opt :whitelist)           Whitelist
     (opt :blacklist)           Blacklist
     (opt :emit)                [Emitter]      ;; Change in element emits events
     (opt :listen)              [sc/Keyword]   ;; Events to listen
     (opt :css)                 [(sc/cond-pre sc/Keyword sc/Str)]   ;; CSS classes. Even an empty vector overrides default classes.
     (opt :auth)                Auth
     ;; Value transform. See persistence/transform-value
     (opt :transform)           (sc/enum :upper-case :lower-case :zero-pad-4 :property-id)
     (opt :hide-when)           field-matcher
     (opt :show-when)           field-matcher
     (opt :exclude-from-pdf)    sc/Bool   ;; If true, the component is not printed (included in the pdf)
     (opt :dynamic)             sc/Bool   ;; If true, the value will be dynamically assigned unless overridden by user
     (opt :container-css)       [sc/Keyword]   ;; Adds any css class to the parent container of a element
     (opt :ifc-mapping)         sc/Keyword     ;; IFC → LP field mapping key, used by the Bimmer pipeline
     }))


(defschema Text
  "Text area element. Represented as text-area html element"
  (merge GenInput
         {:type               (sc/eq :text)
          (opt :default)      sc/Str
          (opt :placeholder)  sc/Str
          (opt :min-len)      sc/Int
          (opt :max-len)      sc/Int}))

(defschema Url
  "Url element. Represented as input html element"
  (merge GenInput
         {:type               (sc/eq :url)
          (opt :default)      sc/Str
          (opt :placeholder)  sc/Str}))

(defschema GenString
  "General string type. Represented as text input html element."
  (merge GenInput
         {:type               (sc/eq :string)
          (opt :default)      sc/Str
          (opt :placeholder)  sc/Str
          (opt :min-len)      sc/Int
          (opt :max-len)      sc/Int
          (opt :dummy-test)   sc/Keyword}))

(defschema PlainString
  "String type without subtype"
  (merge GenString
         {;; TODO: should be a subtype?
          (opt :identifier) sc/Bool
          ;; TODO: should have numeric subtype?
          (opt :unit)       (sc/enum :m :m2 :m3 :k-m3 :hehtaaria :y :kuukautta :kpl)}))

(defschema Num
  "Integer string type"
  (merge GenString
         {:subtype            (sc/eq :number)
          (opt :unit)         (sc/enum :cm :m :m2 :m3 :km :k-m3 :tonnia :hehtaaria :y :kuukautta :tuntiaviikko :kpl :hengelle :db)
          (opt :min)          sc/Int
          (opt :max)          sc/Int}))

(defschema Decimal
  "Numeric string type"
  (merge GenString
         {:subtype            (sc/eq :decimal)
          (opt :unit)         (sc/enum :cm :m :m2 :m3 :km :k-m3 :tonnia :hehtaaria :y :kuukautta :tuntiaviikko :kwh)
          (opt :min)          sc/Int
          (opt :max)          sc/Int}))

(defschema RecentYear
  "A valid recent year"
  (merge GenString
         {:subtype            (sc/eq :recent-year)
          :range              sc/Int}))

(defschema Letter
  (merge GenString
         {:subtype            (sc/eq :letter)
          (opt :case)         (sc/enum :lower :upper)}))

(defschema Digit
  (merge GenString
         {:subtype            (sc/eq :digit)}))

(defschema Tel
  (merge GenString
         {:subtype            (sc/eq :tel)}))

(defschema Email
  (merge GenString
         {:subtype            (sc/eq :email)}))

(defschema Rakennustunnus
  (merge GenString
         {:subtype            (sc/eq :rakennustunnus)}))

(defschema Rakennusnumero
  (merge GenString
         {:subtype            (sc/eq :rakennusnumero)}))

(defschema Kiinteistotunnus
  (merge GenString
         {:subtype                      (sc/eq :kiinteistotunnus)
          ;; If true, try to autofill field with application property id
          ;; See `lupapalvelu.application/autofill-property-ids!`
          (sc/optional-key :auto-fill?) sc/Bool}))

(defschema Ytunnus
  (merge GenString
         {:subtype            (sc/eq :y-tunnus)}))

(defschema Ovt
  (merge GenString
         {:subtype            (sc/eq :ovt)}))

(defschema VrkName
  (merge GenString
         {:subtype            (sc/eq :vrk-name)}))

(defschema VrkAddress
  (merge GenString
         {:subtype            (sc/eq :vrk-address)}))

(defschema Zipcode
  (merge GenString
         {:subtype            (sc/eq :zipcode)}))

(defschema RyhtiAddressName
  (merge GenString
         {:subtype            (sc/eq :ryhti-address-name)}))

(defschema Date
  (merge GenInput
         {:type              (sc/eq :date)
          (opt :placeholder) sc/Str}))

(defschema TimeString
  "Time string hh:mm"
  (merge GenInput
         {:type              (sc/eq :time)
          (opt :placeholder) sc/Str}))

(defschema MsDate
  (merge GenInput
         {:type       (sc/eq :msDate)}))

(defschema Checkbox
  (merge GenInput
         {:type              (sc/eq :checkbox)
          (opt :classPrefix) sc/Keyword ;; Class prefix for the checbox wrapper
          (opt :default)     sc/Bool}))

(defschema Option
  "Option for select and radiogroup types.
  Options with `:optgroup true` act as group headers - subsequent options
  until the next optgroup header are grouped under it in the UI."
  (st/merge
    FlagConditions
    {:name           sc/Str
     (opt :i18nkey)  sc/Str       ;; Absolute localization key for element
     (opt :code)     sc/Keyword
     (opt :optgroup) sc/Bool}))   ;; If true, this option is an optgroup header

(def ryhti-id-keys
  #{:addressKey
    :administrativeLocationUnitKey
    :apartmentEquipmentKey
    :apartmentKey
    :areaToBeBuiltForSpecificActivitiesKey
    :assemblyFacilityKey
    :buildingCarbonFootprintKey
    :buildingCarbonHandprintKey
    :buildingKey
    :buildingMaterialOfLoadBearingStructuresKey
    :buildingObjectLocationDataKey
    :buildingObjectOwnerKey
    :buildingPurposeForLowCarbonAssessmentKey
    :buildingSectionKey
    :buildingServicesEngineeringDataKey
    :buildingSiteCarbonFootprintKey
    :buildingSiteCarbonHandprintKey
    :buildingSiteKey
    :calculatoryDeliveredEnergyUsageKey
    :callForDeviationKey
    :chargingPointKey
    :civilDefenceShelterKey
    :climateReportKey
    :contactAddressKey
    :coolingEnergySourceKey
    :coolingMethodKey
    :deviationObjectKey
    :electricalEnergySourceKey
    :elevatorKey
    :energyDataKey
    :engagingPartyKey
    :entranceKey
    :equipmentKey
    :facadeMaterialKey
    :foremanKey
    :heatingEnergySourceKey
    :heatingMethodKey
    :internalNetworkKey
    :networkConnectionKey
    :plannerKey
    :purposeKey
    :reasonForExceedingThresholdLimitKey
    :structureKey
    :structureSectionKey
    :ventilationMethodKey
    })

(defschema RyhtiIdentifier
  "Object-specific UUID identifier as defined in the Ryhti API specification"
  (st/merge GenInput
            {:name (apply sc/enum (map name ryhti-id-keys))
             :type (sc/eq :ryhti-identifier)}))

(defschema Select
  (merge GenInput
         {:type                  (sc/eq :select)  ;;
          :body                  [Option]         ;;
          (opt :default)         sc/Str           ;; Default option name
          (opt :valueAllowUnset) sc/Bool          ;; Selection is not required (default true)
          (opt :sortBy)          (sc/enum nil :displayname)  ;; (default ?)
          (opt :other-key)       sc/Str}))        ;; Sibling (string) element name for typing other value

(defschema ReviewOfficerDropdown
  (merge GenInput
         {:type              (sc/eq :review-officer-dropdown)
          (opt :placeholder) sc/Str}))

(defschema AutocompleteOption
  {:name          sc/Str
   (opt :i18nkey) sc/Str
   ;; Group is always an absolute localization key.
   (opt :group)   sc/Keyword})


(defschema Autocomplete
  "Keyword values are localization keys."
  (merge GenInput
         {:type              (sc/eq :autocomplete)
          :body              [AutocompleteOption]
          ;; Search field placeholder
          (opt :placeholder) sc/Keyword
          ;; Caption is shown when no value is selected.
          (opt :caption)     sc/Keyword
          ;; CSS style (e.g., "20em")
          (opt :max-height)  sc/Str}))

(defschema RadioOption
  (merge Option
         {;; Class prefix for the radio wrapper Icon class
          (opt :classPrefix) sc/Keyword
          ;; name. If given, the label contents is i + span. Typically useful only
          ;; when :classPrefix denotes a tag class
          (opt :icon)        sc/Keyword}))

(defschema RadioGroup
  (merge GenInput
         {:type              (sc/eq :radioGroup)
          :body              [RadioOption]
          (opt :vertical)    sc/Bool
          (opt :show-label)  sc/Bool
          (opt :aria-label)  sc/Keyword ;; Ltext
          (opt :labelled-by) sc/Str     ;; aria-labelledby for the group.
          (opt :default)     sc/Str}))  ;; Default option name

(defschema Str
  (sc/conditional (subtype-pred :number)           Num
                  (subtype-pred :decimal)          Decimal
                  (subtype-pred :recent-year)      RecentYear
                  (subtype-pred :digit)            Digit
                  (subtype-pred :letter)           Letter
                  (subtype-pred :tel)              Tel
                  (subtype-pred :email)            Email
                  (subtype-pred :rakennustunnus)   Rakennustunnus
                  (subtype-pred :rakennusnumero)   Rakennusnumero
                  (subtype-pred :kiinteistotunnus) Kiinteistotunnus
                  (subtype-pred :y-tunnus)         Ytunnus
                  (subtype-pred :ovt)              Ovt
                  (subtype-pred :vrk-name)         VrkName
                  (subtype-pred :vrk-address)      VrkAddress
                  (subtype-pred :zipcode)          Zipcode
                  (subtype-pred :ryhti-address-name) RyhtiAddressName
                  #(not (contains? % :subtype))    PlainString
                  :else                            {:subtype (sc/eq nil)})) ; For better error messages

(defschema Path [sc/Str])

(defschema LinkPermitSelector
  (merge GenInput
         {:type                 (sc/eq :linkPermitSelector)
          :operationsPath        Path}))

(def special-types [:hetu
                    :foremanHistory
                    :foremanActions
                    :foremanConsent
                    :consent
                    :fillMyInfoButton
                    :companySelector
                    :buildingSelector
                    :rakennuskohdeSelector
                    :maaraalaTunnus
                    :fundingSelector
                    :buildingProductCatalogs])

(defschema Special
  (merge GenInput
         {:type            (apply sc/enum special-types)
          (opt :other-key) sc/Str
          (opt :max-len)   sc/Int}))

(defschema PersonSelector
  (merge Special
         {:type                    (sc/eq :personSelector)
          ;; If true, the company users are NOT included in the
          ;; selector.
          (opt :excludeCompanies) sc/Bool}))

(defschema PartyAttachment
  (merge GenInput
         {:type            (sc/eq :partyAttachment)
          :attachment-type-id (sc/enum "suostumus" "patevyystodistus")
          :attachment-target-type (sc/enum "suunnittelija-suostumus" "suunnittelija-patevyystodistus")}))

(defschema PseudoInput
  "Pseudo input that does not have any database value. In other words,
  a view only component."
  (merge GenInput
         {:pseudo? (sc/eq true)}))

(defschema TextLink
  "Text with embedded link and an optional icon. The :text value
  includes a placeholder for link. For example: 'Click [here] to
  proceed', where [here] will be replaced with link (link text is here
  and url is :url value). If url is not given, no substitution is
  done."
  (merge PseudoInput
         {:type       (sc/eq :textlink)
          ;; Localization key
          :text       sc/Keyword
          ;; Also a localization key since the url could be
          ;; language-specific
          (opt :url)  sc/Keyword
          ;; Icon classes (e.g., [:lupicon-warning :negative])
          (opt :icon) [sc/Keyword]}))

(defschema AlluDrawings
  "Combination of Allu-defined fixed locations and user-added
  drawings."
  (merge PseudoInput
         {:type              (sc/eq :allu-drawings)
          (opt :kind)        (sc/conditional
                              :path {;; kw-path within document data (excluding value)
                                     :path           sc/Keyword
                                     ;; Document name
                                     (opt :document) sc/Str}
                              :else (sc/enum :promotion))
          ;; AlluMap instance name.
          (opt :map)         sc/Str
          ;; Fixed vs. custom selector component name (must be sibling).
          (opt :type-select) sc/Str}))

(defschema AlluMap
  (merge PseudoInput
         {:type    (sc/eq :allu-map)}))

(defschema AlluApplicationKind
  (merge GenInput
         {:type            (sc/eq :allu-application-kind)
          :applicationType (sc/enum :short-term-rental)}))

(defschema Input
  (sc/conditional (type-pred :text)                    Text
                  (type-pred :string)                  Str
                  (type-pred :url)                     Url
                  (type-pred :checkbox)                Checkbox
                  (type-pred :select)                  Select
                  (type-pred :review-officer-dropdown) ReviewOfficerDropdown
                  (type-pred :autocomplete)            Autocomplete
                  (type-pred :radioGroup)              RadioGroup
                  (type-pred :date)                    Date
                  (type-pred :time)                    TimeString
                  (type-pred :msDate)                  MsDate
                  (type-pred :linkPermitSelector)      LinkPermitSelector
                  (type-pred :personSelector)          PersonSelector
                  (apply type-pred special-types)      Special
                  (type-pred :textlink)                TextLink
                  (type-pred :allu-drawings)           AlluDrawings
                  (type-pred :allu-map)                AlluMap
                  (type-pred :allu-application-kind)   AlluApplicationKind
                  (type-pred :partyAttachment)         PartyAttachment
                  (type-pred :ryhti-identifier)        RyhtiIdentifier
                  :else                                {:type (sc/eq nil)})) ; For better error messages

(declare Element)


(defschema Calculation
  "Calculated column for Table."
  {:name    sc/Str
   :type    (sc/eq :calculation)
   :columns [sc/Str]})

(defschema Table
  "Table element. Represented as html table. Not recursive group type."
  {:name                           sc/Str     ;;
   :type                           (sc/eq :table)
   :body                           [(sc/recursive #'Element)] ;;
   (opt :i18nkey)                  sc/Str     ;; Absolute localization key
   (opt :group-help)               sc/Str     ;;
   (opt :uicomponent)              sc/Keyword ;; Component name for special components
   (opt :approvable)               sc/Bool    ;;
   (opt :repeating)                sc/Bool    ;; Should be  always repeating -> default true
   (opt :repeating-init-empty)     sc/Bool    ;;
   (opt :repeating-allow-empty)    sc/Bool      ;; By default UI ensures groups have at least one item. This allows removing the last one.
   (opt :exclude-from-pdf)         sc/Bool    ;;
   (opt :copybutton)               sc/Bool    ;;
   (opt :validator)                sc/Keyword
   ;; CSS classes apply either to the whole component (keywords) or
   ;; individual columns {columnName class}
   (opt :css)                      [(sc/cond-pre sc/Keyword sc/Str)]   ;; CSS classes. Even an empty vector overrides default classes.
   (opt :columnCss)                {sc/Str [sc/Keyword]}
   (opt :hide-when)                field-matcher
   (opt :show-when)                field-matcher
   ;; Just column names -> no units
   ;; Map item fields:
   ;; :amount the column name for data
   ;; :unit column for unit information
   ;; :unitKey fixed unit (supported values are :t, :kg, :tonnia
   ;; and :m2).
   ;; :unit and :unitKey are mutually exclusive.
   (opt :footer-sums)              [(sc/conditional
                                      string? sc/Str
                                      :unitKey {:amount  sc/Str
                                                :unitKey (sc/enum :m2 :t)}
                                      :unit  {:amount sc/Str
                                              :unit   sc/Str})]
   (opt :flatten-for-pdf)          sc/Bool
   ;; For huoneistotaulu: allow highlighting changes based on muutostapa?
   (opt :allow-highlight-changes?) sc/Bool
   ;; For huoneistotaulu: allow uploading existing premises as Excel?
   (opt :allow-premise-upload?)    sc/Bool
   (opt :ifc-mapping)              sc/Keyword ;; IFC → LP table mapping key, used by the Bimmer pipeline
   })

(defschema Group
  "Group type that groups any doc elements."
  (merge
    FlagConditions
    {:name                                  sc/Str       ;;
     :type                                  (sc/eq :group)
     :body                                  [(sc/recursive #'Element)]
     (opt :subtype)                         (sc/enum :foreman-tasks)
     (opt :i18nkey)                         sc/Str       ;;
     (opt :group-help)                      sc/Str       ;; Localization key for help text displayd right after group label
     (opt :group-help-css)                  [(sc/cond-pre sc/Keyword sc/Str)] ;; Can be used to style the group-help
     (opt :uicomponent)                     sc/Keyword   ;; Component name for special components
     (opt :layout)                          (sc/enum :vertical :horizontal) ;; Force group layout
     (opt :approvable)                      sc/Bool      ;; Approvable by authority
     (opt :repeating)                       sc/Bool      ;; Array of groups
     (opt :repeating-init-empty)            sc/Bool      ;; Init repeating group as empty array (default false)
     (opt :repeating-allow-empty)           sc/Bool      ;; By default UI ensures groups have at least one item. This allows removing the last one.
     (opt :copybutton)                      sc/Bool      ;;
     (opt :exclude-from-pdf)                (sc/cond-pre sc/Bool          ;; The whole group
                                                         {:title sc/Bool} ;; Group title
                                                         )      ;;
     (opt :validator)                       sc/Keyword   ;; Specific validator key for element (see model/validate-element)
     (opt :whitelist)                       Whitelist
     (opt :blacklist)                       Blacklist
     (opt :listen)                          [(sc/enum :filterByCode :hetuChanged)] ;; Events to listen
     (opt :hide-when)                       field-matcher
     (opt :show-when)                       field-matcher
     (opt :template)                        sc/Str       ;; Component template to use
     ;; Row text item format: "<path><cols><css>"
     ;; <path>  path1/path2/...
     ;; <cols>  ::n  where n is 1-4 (default 1)
     ;; <css>   [class1 class2 ...]
     ;; <cols> and <css> are optional, either or both can be omitted.
     (opt :rows)                            [(sc/conditional
                                               :row {:css [(sc/cond-pre sc/Keyword sc/Str)]
                                                     :row [sc/Str]}
                                               map?  {sc/Keyword sc/Str}
                                               :else [sc/Str])]
     ;; See pdf-export namespace on how the options are used.
     (opt :pdf-options)                     {;; Select name that contains :muu option
                                             ;; The group must have :muu string field as well.
                                             (opt :other-select) sc/Keyword}
     (opt :address-type)                    (sc/enum :contact)
     (opt :css)                             [(sc/cond-pre sc/Keyword sc/Str)]   ;; CSS classes. Even an empty vector overrides default classes.

     ;; Force the group fields disabled in post-verdict edit state
     (opt :post-verdict-editable-disabled?) sc/Bool
     ;; Group contains Ryhti address information
     (opt :ryhti-address?)                  sc/Bool
     ;; Group contains Ryhti building site measurements information
     (opt :ryhti-measurements?)             sc/Bool
     ;; IFC → LP table mapping key, used by the Bimmer pipeline
     (opt :ifc-mapping)                     sc/Keyword}))

(defschema Element
  "Any doc element."
  (sc/conditional (type-pred :table) Table
                  (type-pred :group) Group
                  (type-pred :calculation) Calculation
                  :else              Input))

(defschema AccordionField
  "Accordion fields semantics are interpreted in the accordion service.
  Note: changes also affect lupapalvelu.document.schemas.defschema."
  (sc/conditional
   :type      {:type                (sc/enum :workPeriod :selected :date :text)
               :paths               [[sc/Str]]
               ;; vsprintf-type format for the whole path value list.
               (opt :format)         sc/Str
               ;; Loc format or key for an individual path value.
               ;; Both %s and {0} are supported.
               (opt :localizeFormat) sc/Str}
   :else      [sc/Str])) ;; Shortcut for :type :text, no optional keys.

(defschema CopyAction
  "Determines what to do with the document when the parent application is being copied"
  (sc/enum :copy  ; Copy the document as it is
           :clear ; Replace the document with an empty copy
           ))

(defschema Doc
  {:info           {:name                                   sc/Str
                    (opt :version)                          sc/Int
                    (opt :type)                             (sc/enum :building-product-catalog
                                                                     :climate-report
                                                                     ;; Is this correct? Only used in `yleiset-alueet-hankkeen-kuvaus-kayttolupa`
                                                                     :group
                                                                     :location
                                                                     :party
                                                                     :task)
                    (opt :subtype)                          (sc/enum :foreman
                                                                     :hakija
                                                                     :hakijan-asiamies
                                                                     :hankkeen-kuvaus
                                                                     :maksaja
                                                                     :review
                                                                     :review-backend
                                                                     :suunnittelija
                                                                     :tyomaasta-vastaava)
                    (opt :i18name)                          sc/Str     ;; Root localization key (inherited by childs)
                    (opt :i18nprefix)                       sc/Str     ;; TODO: not used
                    (opt :group-help)                       (sc/maybe sc/Str) ;; TODO: remove nils?
                    (opt :section-help)                     (sc/maybe sc/Str) ;; TODO: remove nils?
                    (opt :approvable)                       sc/Bool
                    (opt :post-verdict-editable)            sc/Bool    ;; Editable by authority after PATE verdict is given
                    (opt :repeating)                        sc/Bool
                    (opt :removable-by)                     (sc/enum :authority :all :none)
                    (opt :disableable)                      sc/Bool
                    (opt :redraw-on-approval)               sc/Bool
                    (opt :only-one-active)                  sc/Bool    ;; Only one document of the same name can at one time have :disabled false
                    (opt :post-verdict-party)               sc/Bool
                    (opt :last-removable-by)                (sc/enum :authority :none) ;; Deny removing last repeating doc
                    (opt :user-authz-roles)                 #{(apply sc/enum roles/all-authz-roles)}
                    (opt :no-repeat-button)                 sc/Bool
                    (opt :addable-in-states)                #{sc/Keyword} ;; States where document can be added
                    (opt :editable-in-states)               #{sc/Keyword}
                    (opt :editable-in-doc-states)           #{(sc/maybe sc/Keyword)}
                    (opt :exclude-from-pdf)                 sc/Bool
                    (opt :after-update)                     sc/Symbol  ;; Function, triggered on update
                    (opt :order)                            sc/Int
                    ;; Blacklist only works for :neighbor blacklisting. See neighbors API for details.
                    (opt :blacklist)                        [(sc/enum :neighbor)]
                    (opt :accordion-fields)                 [AccordionField]
                    (opt :copy-action)                      CopyAction
                    ;; permitted to update :building-ref value for building product catalog catalog document
                    (opt :ref-update-permitted?)            sc/Bool
                    ;; Make certain commands also send the updates authModel on the response, when
                    ;; the document schema has this flag.
                    (opt :refresh-auth-model-on-update-doc) sc/Bool
                    ;; Enable "Hyväksy" button for designer documents.
                    ;; This makes approve-doc to require a valid document and designer consent.
                    ;; Additionally enables pdf-export to include a line about when the approval was given,
                    ;; if the document was approved using "Hyväksy" button.
                    (opt :use-designer-approval)            sc/Bool
                    ;; Document schema contains a building, used to indicate if the document has building info
                    (opt :building?)                        sc/Bool
                    (opt :generates-climate-report)         sc/Bool
                    ;; Which climate-report schema should be used for this document
                    (opt :climate-report-schema)            sc/Str
                    ;; A list of fields to be copied from the building document to the climate-report
                    (opt :copy-to-climate-report)           [[sc/Keyword]]
                    ;; Is the current document a foreman document
                    (opt :foreman-document)                 sc/Bool
                    ;; Demolition notice flag
                    (opt :demolition-notice?)               sc/Bool
                    ;; Is the current document a maalampokaivo document
                    (opt :maalampokaivo?)                   sc/Bool
                    }
   (opt :rows)     [(sc/cond-pre {sc/Keyword sc/Str} [sc/Str])]
   (opt :template) sc/Str
   :body           [Element]})

(defn get-in-schema [schema path]
  (reduce #(util/find-by-key :name (name %2) (:body %1)) schema path))

(defn- build-absolute-path [path target-path-string]
  (let [target-path (-> (re-matches #"([^\[:]+)+(\[.+\])?(::\d+)?" target-path-string) second (ss/split #"/"))]
    (if (ss/blank? (first target-path))
      (vec (rest target-path))
      (vec (concat path target-path)))))

(defn validate-rows [{name :name rows :rows :as schema}]
  (let [invalid-paths (->> (map #(if (vector? %) % (:row %)) rows)
                           (apply concat)
                           (map #(vector % (build-absolute-path [] %)))
                           (util/map-values (partial get-in-schema schema))
                           (filter (comp nil? val))
                           keys)]
    (when (not-empty invalid-paths) {:description "Invalid rows definition" :schema name :errors invalid-paths})))

(defn validate-value-reference [key doc-schema {:keys [path] :as schema}]
  (when (and (get schema key)
             (nil? (get-in schema [key :document]))
             (->> (build-absolute-path (butlast path) (get-in schema [key :path]))
                  (get-in-schema doc-schema)
                  nil?))
    {:description (str "Invalid " (name key) " path") :schema (:name schema) :path path :errors (get-in schema [key :path])}))

(defn validate-references [doc-schema]
  (loop [schemas [(assoc doc-schema :path [])]  errors []]
    (let [errors (concat errors
                         (map validate-rows schemas)
                         (map (partial validate-value-reference :hide-when doc-schema) schemas)
                         (map (partial validate-value-reference :show-when doc-schema) schemas))
          subschemas (mapcat (fn [{:keys [body path]}]
                               (map #(assoc % :path (conj path (keyword (:name %)))) body))
                             schemas)]
      (if (not-empty subschemas)
        (recur subschemas errors)
        (not-empty (remove nil? errors))))))

(def doc-checker (sc/checker Doc))

(defn validate-doc-schema [doc-schema]
  (util/assoc-when (doc-checker doc-schema) :reference-errors (validate-references doc-schema)))
