(ns lupapalvelu.document.schema-validation
  (:require [schema.core :refer [defschema] :as sc]
            [lupapalvelu.authorization :as auth]))

(def opt sc/optional-key)

(defn type-pred [& types] (comp (set types) :type))
(defn subtype-pred [& subtypes] (comp (set subtypes) :subtype))

(def input-sizes [:t :s :m :l :xl])

(defschema Auth
  "Authorization model based component state. See docgen-input-model
  for how this is enforced in the frontend. Note: empty lists do not
  affect the state in any way."
  {(opt :disabled) [sc/Keyword]  ;;Disabled if any listed action is allowed.
   (opt :enabled)  [sc/Keyword]  ;; Disabled if any listed action is not allowed.
   })

(defschema single-value (sc/conditional string? sc/Str
                                        number? sc/Num
                                        :else   (sc/enum true false nil)))

(defschema GenInput
  "General leaf element schema. Base element for input elements."
  {:name              sc/Str         ;; Element name
   :type              (sc/enum :text :string :select :checkbox :radioGroup :date)
   (opt :uicomponent) sc/Keyword     ;; Component name for special components
   (opt :inputType)   (sc/enum :string :checkbox :localized-string :inline-string
                               :check-string :checkbox-wrapper
                               :paragraph) ;; Input types for generic docgen-input component
   (opt :labelclass)  sc/Str         ;; Special label style class
   (opt :i18nkey)     sc/Str         ;; Absolute localization key
   (opt :locPrefix)   sc/Str         ;;
   (opt :layout)      (sc/enum :full-width)
   (opt :hidden)      sc/Bool        ;; Element is hidden (default false)
   (opt :label)       sc/Bool        ;; Label is shown? (default true)
   (opt :size)        (apply sc/enum input-sizes) ;; Element size (default ?)
   (opt :readonly)    sc/Bool        ;; Element is readonly
   (opt :readonly-after-sent) sc/Bool;; Element is readonly if document state is sent
   (opt :required)    sc/Bool        ;; Required field
   (opt :approvable)  sc/Bool        ;; Authority can apporove/reject field
   (opt :codes)       [sc/Keyword]   ;;
   (opt :validator)   sc/Keyword     ;; Specific validator key for element (see model/validate-element)
   (opt :whitelist)   {:roles [sc/Keyword] :otherwise (sc/enum :disabled :hidden)}
   (opt :blacklist)   [(sc/if string? (sc/eq "turvakieltoKytkin") sc/Keyword)] ;; WTF turvakieltoKytkin
   (opt :emit)        [sc/Keyword]   ;; Change in element emits events
   (opt :listen)      [sc/Keyword]   ;; Events to listen
   (opt :css)         [sc/Keyword]   ;; CSS classes. Even an empty vector overrides default classes.
   (opt :auth)        Auth
   (opt :transform)   sc/Keyword     ;; Value transform. See persistence/transform-value
   (opt :hide-when)   {:path  sc/Str ;; Toggle element visibility by values of another element
                       :values #{single-value}}
   (opt :show-when)   {:path  sc/Str ;; Toggle element visibility by values of another element
                       :values #{single-value}}
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
          (opt :min-len)      sc/Int
          (opt :max-len)      sc/Int}))

(defschema PlainString
  "String type without subtype"
  (merge GenString
         {(opt :dummy-test)   sc/Keyword
          (opt :identifier)   sc/Bool     ;; TODO: should be a subtype?
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

(defschema Date
  (merge GenInput
         {:type       (sc/eq :date)}))

(defschema Checkbox
  (merge GenInput
         {:type       (sc/eq :checkbox)}))

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
                    :personSelector
                    :companySelector
                    :buildingSelector
                    :newBuildingSelector
                    :maaraalaTunnus])

(defschema Special
  (merge GenInput
         {:type            (apply sc/enum special-types)
          (opt :other-key) sc/Str
          (opt :max-len)   sc/Int}))

(defschema Input
  (sc/conditional (type-pred :text)       Text
                  (type-pred :string)     Str
                  (type-pred :checkbox)   Checkbox
                  (type-pred :select)     Select
                  (type-pred :radioGroup) RadioGroup
                  (type-pred :date)       Date
                  (type-pred :linkPermitSelector) LinkPermitSelector
                  (apply type-pred special-types) Special
                  :else                   {:type (sc/eq nil)})) ; For better error messages

(declare Group)

(defschema Calculation
  "Calculated column for Table."
  {:name    sc/Str
   :type    (sc/eq :calculation)
   :columns [sc/Str]})

(defschema Table
  "Table element. Represented as html table. Not recursive group type."
  {:name                       sc/Str     ;;
   :type                       (sc/eq :table)
   :body                       [(sc/conditional (type-pred :group)       (sc/recursive #'Group)
                                                (type-pred :calculation) Calculation
                                                :else                    Input)]
   (opt :i18nkey)              sc/Str     ;; Absolute localization key
   (opt :group-help)           sc/Str     ;;
   (opt :uicomponent)          sc/Keyword ;; Component name for special components
   (opt :approvable)           sc/Bool    ;;
   (opt :repeating)            sc/Bool    ;; Should be  always repeating -> default true
   (opt :repeating-init-empty) sc/Bool    ;;
   (opt :copybutton)           sc/Bool    ;;
   (opt :validator)            sc/Keyword
   (opt :css)                  [sc/Keyword]
   (opt :hide-when)            {:path   sc/Str ;; Toggle element visibility by values of another element
                                :values #{single-value}}
   (opt :show-when)            {:path   sc/Str ;; Toggle element visibility by values of another element
                                :values #{single-value}}})

(defschema Group
  "Group type that groups any doc elements."
  {:name                       sc/Str       ;;
   :type                       (sc/eq :group)
   :body                       [(sc/conditional (type-pred :group) (sc/recursive #'Group)
                                                (type-pred :table) Table
                                                :else              Input)]
   (opt :subtype)              sc/Keyword
   (opt :i18nkey)              sc/Str       ;;
   (opt :group-help)           sc/Str       ;; Localization key for help text displayd right after group label
   (opt :uicomponent)          sc/Keyword   ;; Component name for special components
   (opt :layout)               (sc/enum :vertical :horizontal) ;; Force group layout
   (opt :approvable)           sc/Bool      ;; Approvable by authority
   (opt :repeating)            sc/Bool      ;; Array of groups
   (opt :repeating-init-empty) sc/Bool      ;; Init repeating group as empty array (default false)
   (opt :removable)            sc/Bool      ;;
   (opt :copybutton)           sc/Bool      ;;
   (opt :exclude-from-pdf)     sc/Bool      ;;
   (opt :validator)            sc/Keyword   ;; Specific validator key for element (see model/validate-element)
   (opt :whitelist)            {:roles [sc/Keyword] :otherwise (sc/enum :disabled :hidden)}
   (opt :blacklist)            [(sc/if string? (sc/eq "turvakieltoKytkin") sc/Keyword)] ;; WTF turvakieltoKytkin
   (opt :listen)               [sc/Keyword] ;; Events to listen
   (opt :hide-when)            {:path  sc/Str ;; Toggle element visibility by values of another element
                                :values #{single-value}}
   (opt :show-when)            {:path  sc/Str ;; Toggle element visibility by values of another element
                                :values #{single-value}}
   (opt :template)             sc/Str       ;; Component template to use
   ;; Row item format: "<path><cols><css>"
   ;; <path>  path1/path2/...
   ;; <cols>  ::n  where n is 1-4 (default 1)
   ;; <css>   [class1 class2 ...]
   ;; <cols> and <css> are optional, either or both can be omitted.
   (opt :rows)                 [(sc/if map?
                                  {sc/Keyword sc/Str}
                                  [sc/Str])]})

(defschema Element
  "Any doc element."
  (sc/conditional (type-pred :table) Table
                  (type-pred :group) Group
                  (type-pred :calculation) Calculation
                  :else              Input))

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
          (opt :repeating)                   sc/Bool    ;;
          (opt :removable)                   sc/Bool    ;;
          (opt :removable-only-by-authority) sc/Bool    ;; Deny removing document by user role
          (opt :deny-removing-last-document) sc/Bool    ;; Deny removing last repeating doc
          (opt :user-authz-roles)            #{(apply sc/enum auth/all-authz-roles)}
          (opt :no-repeat-button)            sc/Bool    ;;
          (opt :construction-time)           sc/Bool    ;; Is a construction time doc
          (opt :exclude-from-pdf)            sc/Bool    ;;
          (opt :after-update)                sc/Symbol  ;; Function, triggered on update
          (opt :accordion-fields)            [[sc/Str]] ;; Paths to display in accordion summary
          (opt :order)                       sc/Int}    ;;
   (opt :rows) [(sc/cond-pre {sc/Keyword sc/Str} [sc/Str])]
   (opt :template) sc/Str
   :body  [Element]})

(defn validate-doc-schema [doc-schema]
  (sc/check Doc doc-schema))

(defn validate-elem-schema [elem-schema]
  (sc/check Element elem-schema))
