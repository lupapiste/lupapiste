(ns lupapalvelu.document.schema-validation
  (:require [schema.core :refer [defschema] :as sc]
            [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.roles :as roles]))

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

(defschema single-value (sc/conditional string? sc/Str
                                        number? sc/Num
                                        :else   (sc/enum true false nil)))

(defschema field-matcher
  "Definition for target value in another element. Used with
  `:hide-when` and `:show-when`."
  {:path           sc/Str ;; Path within document
   :values         #{single-value}
   ;; Target document name. Default is the "current" document. If
   ;; there are multiple documents with the same name, only the first
   ;; one is considered.
   (opt :document) sc/Str})

(defschema GenInput
  "General leaf element schema. Base element for input elements."
  {:name              sc/Str         ;; Element name
   :type              (sc/enum :text :string :select :checkbox :radioGroup :date :time :textlink)
   (opt :uicomponent) sc/Keyword     ;; Component name for special components
   (opt :inputType)   (sc/enum :string :checkbox :localized-string :inline-string
                               :check-string :checkbox-wrapper
                               :paragraph) ;; Input types for generic docgen-input component
   (opt :labelclass)  sc/Str         ;; Special label style class
   (opt :i18nkey)     sc/Str         ;; Absolute localization key
   (opt :locPrefix)   sc/Str         ;;
   (opt :layout)      (sc/enum :full-width :initial-width)
   (opt :hidden)      sc/Bool        ;; Element is hidden (default false)
   (opt :label)       sc/Bool        ;; Label is shown? (default true)
   (opt :size)        (apply sc/enum input-sizes) ;; Element size (default ?)
   (opt :readonly)    sc/Bool        ;; Element is readonly
   (opt :readonly-after-sent) sc/Bool;; Element is readonly if document state is sent
   (opt :required)    sc/Bool        ;; Required field
   (opt :approvable)  sc/Bool        ;; Authority can apporove/reject field
   (opt :codes)       [sc/Keyword]   ;;
   (opt :validator)   sc/Keyword     ;; Specific validator key for element (see model/validate-element)
   (opt :whitelist)   (sc/constrained {(opt :roles) [sc/Keyword]
                                       (opt :permitType) [sc/Keyword]
                                       :otherwise (sc/enum :disabled :hidden)}
                                      #(or (not-empty (:roles %)) (not-empty (:permitType %)))
                                      "roles or permitType require, can't be empty")
   (opt :blacklist)   [(sc/if string? (sc/eq "turvakieltoKytkin") sc/Keyword)] ;; WTF turvakieltoKytkin
   (opt :emit)        [sc/Keyword]   ;; Change in element emits events
   (opt :listen)      [sc/Keyword]   ;; Events to listen
   (opt :css)         [sc/Keyword]   ;; CSS classes. Even an empty vector overrides default classes.
   (opt :auth)        Auth
   (opt :transform)   sc/Keyword     ;; Value transform. See persistence/transform-value
   (opt :hide-when)   field-matcher
   (opt :show-when)   field-matcher
   })

(defschema Text
  "Text area element. Represented as text-area html element"
  (merge GenInput
         {:type               (sc/eq :text)
          (opt :default)      sc/Str
          (opt :placeholder)  sc/Str
          (opt :min-len)      sc/Int
          (opt :max-len)      sc/Int}))

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
         {(opt :identifier)   sc/Bool     ;; TODO: should be a subtype?
          (opt :unit)         sc/Keyword  ;; TODO: should have numeric subtype?
          }))

(defschema Num
  "Integer string type"
  (merge GenString
         {:subtype            (sc/eq :number)
          (opt :unit)         (sc/enum :m :m2 :m3 :km :k-m3 :tonnia :hehtaaria :y :kuukautta :tuntiaviikko :kpl :hengelle :db)
          (opt :min)          sc/Int
          (opt :max)          sc/Int}))

(defschema Decimal
  "Numeric string type"
  (merge GenString
         {:subtype            (sc/eq :decimal)
          (opt :unit)         (sc/enum :m :m2 :m3 :km :k-m3 :tonnia :hehtaaria :y :kuukautta :tuntiaviikko)
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
         {:subtype            (sc/eq :kiinteistotunnus)}))

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
         {:type          (sc/eq :checkbox)
          (opt :default) sc/Bool}))

(defschema Option
  "Option for select and radiogroup types."
  {:name                         sc/Str       ;;
   (opt :i18nkey)                sc/Str       ;; Absolute localization key for element
   (opt :code)                   sc/Keyword}) ;;

(defschema Select
  (merge GenInput
         {:type                  (sc/eq :select)  ;;
          :body                  [Option]         ;;
          (opt :default)         sc/Str           ;; Default option name
          (opt :valueAllowUnset) sc/Bool          ;; Selection is not required (default true)
          (opt :sortBy)          (sc/enum nil :displayname)  ;; (default ?)
          (opt :other-key)       sc/Str}))        ;; Sibling (string) element name for typing other value

(defschema RadioGroup
  (merge GenInput
         {:type                  (sc/eq :radioGroup) ;;
          :body                  [Option]         ;;
          (opt :default)         sc/Str}))        ;; Default option name

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
                  #(not (contains? % :subtype))    PlainString
                  :else                            {:subtype (sc/eq nil)})) ; For better error messages

(defschema Path [sc/Str])

(defschema LinkPermitSelector
  (merge GenInput
         {:type                 (sc/eq :linkPermitSelector)
          :operationsPath        Path}))

(def special-types [:hetu
                    :foremanHistory
                    :fillMyInfoButton
                    :companySelector
                    :buildingSelector
                    :newBuildingSelector
                    :maaraalaTunnus
                    :fundingSelector])

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
         {:type      (sc/eq :allu-drawings)
          :kind      (sc/enum :promotion)
          ;; AlluMap instance name.
          (opt :map) sc/Str}))

(defschema AlluMap
  (merge PseudoInput
         {:type (sc/eq :allu-map)}))

(defschema Input
  (sc/conditional (type-pred :text)       Text
                  (type-pred :string)     Str
                  (type-pred :checkbox)   Checkbox
                  (type-pred :select)     Select
                  (type-pred :radioGroup) RadioGroup
                  (type-pred :date)       Date
                  (type-pred :time)       TimeString
                  (type-pred :msDate)     MsDate
                  (type-pred :linkPermitSelector) LinkPermitSelector
                  (type-pred :personSelector) PersonSelector
                  (apply type-pred special-types) Special
                  (type-pred :textlink) TextLink
                  (type-pred :allu-drawings) AlluDrawings
                  (type-pred :allu-map) AlluMap
                  :else                   {:type (sc/eq nil)})) ; For better error messages

(declare Element)

(defschema Calculation
  "Calculated column for Table."
  {:name    sc/Str
   :type    (sc/eq :calculation)
   :columns [sc/Str]})

(defschema Table
  "Table element. Represented as html table. Not recursive group type."
  {:name                       sc/Str     ;;
   :type                       (sc/eq :table)
   :body                       [(sc/recursive #'Element)] ;;
   (opt :i18nkey)              sc/Str     ;; Absolute localization key
   (opt :group-help)           sc/Str     ;;
   (opt :uicomponent)          sc/Keyword ;; Component name for special components
   (opt :approvable)           sc/Bool    ;;
   (opt :repeating)            sc/Bool    ;; Should be  always repeating -> default true
   (opt :repeating-init-empty) sc/Bool    ;;
   (opt :exclude-from-pdf)     sc/Bool    ;;
   (opt :copybutton)           sc/Bool    ;;
   (opt :validator)            sc/Keyword
   ;; CSS classes apply either to the whole component (keywords) or
   ;; individual columns {columnName class}
   (opt :css)                  [sc/Keyword]
   (opt :columnCss)            {sc/Str [sc/Keyword]}
   (opt :hide-when)            {:path   sc/Str ;; Toggle element visibility by values of another element
                                :values #{single-value}}
   (opt :show-when)            {:path   sc/Str ;; Toggle element visibility by values of another element
                                :values #{single-value}}
   ;; Just column names -> no units
   ;; Map item fields:
   ;; :amount the column name for data
   ;; :unit column for unit information
   ;; :unitKey fixed unit (supported values are :t, :kg, :tonnia
   ;; and :m2).
   ;; :unit and :unitKey are mutually exclusive.
   (opt :footer-sums)          [(sc/conditional
                                 string? sc/Str
                                 :unitKey {:amount sc/Str
                                           :unitKey sc/Keyword}
                                 :unit  {:amount sc/Str
                                         :unit   sc/Str})]})

(defschema Group
  "Group type that groups any doc elements."
  {:name                       sc/Str       ;;
   :type                       (sc/eq :group)
   :body                       [(sc/recursive #'Element)]
   (opt :subtype)              sc/Keyword
   (opt :i18nkey)              sc/Str       ;;
   (opt :group-help)           sc/Str       ;; Localization key for help text displayd right after group label
   (opt :uicomponent)          sc/Keyword   ;; Component name for special components
   (opt :layout)               (sc/enum :vertical :horizontal) ;; Force group layout
   (opt :approvable)           sc/Bool      ;; Approvable by authority
   (opt :repeating)            sc/Bool      ;; Array of groups
   (opt :repeating-init-empty) sc/Bool      ;; Init repeating group as empty array (default false)
   (opt :copybutton)           sc/Bool      ;;
   (opt :exclude-from-pdf)     (sc/cond-pre sc/Bool          ;; The whole group
                                            {:title sc/Bool} ;; Group title
                                            )      ;;
   (opt :validator)            sc/Keyword   ;; Specific validator key for element (see model/validate-element)
   (opt :whitelist)            {:roles [sc/Keyword] :otherwise (sc/enum :disabled :hidden)}
   (opt :blacklist)            [(sc/if string? (sc/eq "turvakieltoKytkin") sc/Keyword)] ;; WTF turvakieltoKytkin
   (opt :listen)               [sc/Keyword] ;; Events to listen
   (opt :hide-when)            {:path  sc/Str ;; Toggle element visibility by values of another element
                                :values #{single-value}}
   (opt :show-when)            {:path  sc/Str ;; Toggle element visibility by values of another element
                                :values #{single-value}}
   (opt :template)             sc/Str       ;; Component template to use
   ;; Row text item format: "<path><cols><css>"
   ;; <path>  path1/path2/...
   ;; <cols>  ::n  where n is 1-4 (default 1)
   ;; <css>   [class1 class2 ...]
   ;; <cols> and <css> are optional, either or both can be omitted.
   (opt :rows)                 [(sc/conditional
                                 :row {:css [sc/Keyword]
                                       :row [sc/Str]}
                                 map?  {sc/Keyword sc/Str}
                                 :else [sc/Str])]
   ;; See pdf-export namespace on how the options are used.
   (opt :pdf-options)           {;; Select name that contains :muu option
                                 ;; The group must have :muu string field as well.
                                 (opt :other-select) sc/Keyword}
   (opt :address-type)           (sc/enum :contact)
   (opt :css)                    [sc/Keyword]})

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
  {:info {:name                              sc/Str     ;;
          (opt :version)                     sc/Int     ;;
          (opt :type)                        sc/Keyword ;; TODO: enum type ?
          (opt :subtype)                     sc/Keyword ;;
          (opt :i18name)                     sc/Str     ;; Root localization key (inherited by childs)
          (opt :i18nprefix)                  sc/Str     ;; TODO: not used
          (opt :group-help)                  (sc/maybe sc/Str) ;; TODO: remove nils?
          (opt :section-help)                (sc/maybe sc/Str) ;; TODO: remove nils?
          (opt :approvable)                  sc/Bool    ;;
          (opt :post-verdict-editable)       sc/Bool    ;; Editable by authority after PATE verdict is given
          (opt :repeating)                   sc/Bool    ;;
          (opt :removable-by)                (sc/enum :authority :all :none)
          (opt :disableable)                 sc/Bool    ;;
          (opt :redraw-on-approval)          sc/Bool    ;;
          (opt :post-verdict-party)          sc/Bool    ;;
          (opt :last-removable-by)           (sc/enum :authority :none) ;; Deny removing last repeating doc
          (opt :user-authz-roles)            #{(apply sc/enum roles/all-authz-roles)}
          (opt :no-repeat-button)            sc/Bool    ;;
          (opt :addable-in-states)           #{sc/Keyword} ;; States where document can be added
          (opt :editable-in-states)          #{sc/Keyword} ;;
          (opt :exclude-from-pdf)            sc/Bool    ;;
          (opt :after-update)                sc/Symbol  ;; Function, triggered on update
          (opt :order)                       sc/Int
          ;; Blacklist only works for :neighbor blacklisting. See neighbors API for details.
          (opt :blacklist)                   [sc/Keyword]
          (opt :accordion-fields)            [AccordionField]
          (opt :copy-action)                 CopyAction
          }
   (opt :rows) [(sc/cond-pre {sc/Keyword sc/Str} [sc/Str])]
   (opt :template) sc/Str
   :body  [Element]})

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

(defn validate-doc-schema [doc-schema]
  (util/assoc-when (sc/check Doc doc-schema) :reference-errors (validate-references doc-schema)))

(defn validate-elem-schema [elem-schema]
  (sc/check Element elem-schema))
