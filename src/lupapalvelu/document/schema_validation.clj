(ns lupapalvelu.document.schema-validation
  (:require [schema.core :refer [defschema] :as sc]))

(def opt sc/optional-key)

(defn type-pred [& types] (comp (set types) :type))
(defn subtype-pred [& subtypes] (comp (set subtypes) :subtype))

(def input-sizes ["t" "s" "m" "l" "xl"])

(defschema GenInput
  "General leaf element schema. Parent for input elements."
  {:name              sc/Str
   :type              (sc/enum :text :string :select :checkbox :radioGroup :date)
   (opt :uicomponent) sc/Keyword
   (opt :labelclass)  sc/Str
   (opt :i18nkey)     sc/Str
   (opt :locPrefix)   sc/Str
   (opt :layout)      (sc/enum :full-width)
   (opt :hidden)      sc/Bool
   (opt :label)       sc/Bool    ;; default true
   (opt :size)        (apply sc/enum input-sizes)
   (opt :readonly)    sc/Bool
   (opt :required)    sc/Bool
   (opt :codes)       [sc/Keyword]
   (opt :validator)   sc/Keyword
   (opt :whitelist)   {:roles [sc/Keyword] :otherwise (sc/enum :disabled :hidden)}
   (opt :blacklist)   [(sc/if string? (sc/eq "turvakieltoKytkin") sc/Keyword)] ;; WTF turvakieltoKytkin
   (opt :emit)        [sc/Keyword]
   (opt :listen)      [sc/Keyword]})

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
          (opt :identifier)   sc/Bool ;; TODO: should be a subtype?
          (opt :unit)         sc/Str  ;; TODO: should have numeric subtype?
          }))

(defschema Num
  "Integer string type"
  (merge GenString
         {:subtype            (sc/eq :number)
          (opt :unit)         sc/Str ;; (sc/enum "m" "m2" "m3" "k-m3" "hehtaaria" "kuukautta" "tuntiaviikko" "kpl" "hengelle)
          (opt :min)          sc/Int
          (opt :max)          sc/Int}))

(defschema Decimal
  "Numeric string type"
  (merge GenString
         {:subtype            (sc/eq :decimal)
          (opt :unit)         sc/Str ;; (sc/enum "m" "m2" "m3" "k-m3" "hehtaaria" "kuukautta" "tuntiaviikko" "kpl" "hengelle)
          (opt :min)          sc/Int
          (opt :max)          sc/Int}))

(defschema Letter
  (merge GenString
         {:subtype            (sc/eq :letter)
          (opt :case)         (sc/enum :lower :upper)
          (opt :max-len)      (sc/eq 1)})) ;; TODO: Is this really necessary?

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
         {:type               (sc/eq :date)}))

(defschema Checkbox
  (merge GenInput
         {:type               (sc/eq :checkbox)}))

(defschema Option
  "Option for select and radiogroup types."
  {:name                      sc/Str
   (opt :i18nkey)             sc/Str
   (opt :code)                sc/Keyword})

(defschema Select 
  (merge GenInput
         {:type               (sc/eq :select)
          :body               [Option]
          (opt :default)      sc/Str
          (opt :valueAllowUnset) sc/Bool
          (opt :sortBy)       (sc/enum nil :displayname)
          (opt :other-key)    sc/Str}))

(defschema RadioGroup
  (merge GenInput
         {:type               (sc/eq :radioGroup)
          :body               [Option]
          (opt :default)      sc/Str}))

(defschema Str 
  (sc/conditional (subtype-pred :number)           Num
                  (subtype-pred :decimal)          Decimal
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
                  (apply type-pred special-types) Special
                  :else                   {:type (sc/eq nil)})) ; For better error messages

(defschema Table
  "Table element. Represented as html table. Not recursive group type."
  {:name                       sc/Str
   :type                       (sc/eq :table)
   :body                       [Input]
   (opt :i18nkey)              sc/Str
   (opt :group-help)           sc/Str
   (opt :uicomponent)          sc/Keyword
   (opt :approvable)           sc/Bool
   (opt :repeating)            sc/Bool ;; Always repeating
   (opt :repeating-init-empty) sc/Bool
   (opt :copybutton)           sc/Bool
   (opt :validator)            sc/Keyword})

(defschema Group
  "Group type that groups any doc elements."
  {:name                       sc/Str
   :type                       (sc/eq :group)
   :body                       [(sc/conditional (type-pred :group) (sc/recursive #'Group)
                                                (type-pred :table) Table
                                                :else              Input)]
   (opt :i18nkey)              sc/Str
   (opt :group-help)           sc/Str
   (opt :uicomponent)          sc/Keyword
   (opt :layout)               (sc/enum :vertical :horizontal)
   (opt :approvable)           sc/Bool
   (opt :repeating)            sc/Bool
   (opt :repeating-init-empty) sc/Bool
   (opt :removable)            sc/Bool
   (opt :copybutton)           sc/Bool
   (opt :exclude-from-pdf)     sc/Bool
   (opt :validator)            sc/Keyword
   (opt :whitelist)            {:roles [sc/Keyword] :otherwise (sc/enum :disabled :hidden)}
   (opt :blacklist)            [(sc/if string? (sc/eq "turvakieltoKytkin") sc/Keyword)] ;; WTF turvakieltoKytkin
   (opt :listen)               [sc/Keyword]})

(defschema Element
  "Any doc element."
  (sc/conditional (type-pred :table) Table
                  (type-pred :group) Group
                  :else              Input))

(defschema Doc
  {:info {:name                              sc/Str
          (opt :version)                     sc/Int
          (opt :type)                        sc/Keyword ;; TODO: enum type ?
          (opt :subtype)                     sc/Keyword ;; 
          (opt :i18name)                     sc/Str     ;; root localization (inherited by childs)
          (opt :i18nprefix)                  sc/Str     ;; TODO: not used
          (opt :group-help)                  (sc/maybe sc/Str) ;; TODO: remove nils?
          (opt :section-help)                (sc/maybe sc/Str) ;; TODO: remove nils?
          (opt :approvable)                  sc/Bool    ;; 
          (opt :repeating)                   sc/Bool    ;;
          (opt :removable)                   sc/Bool    ;; 
          (opt :deny-removing-last-document) sc/Bool    ;; deny removin last repeating doc
          (opt :no-repeat-button)            sc/Bool    ;;
          (opt :construction-time)           sc/Bool    ;; is a construction time doc
          (opt :exclude-from-pdf)            sc/Bool    ;; 
          (opt :after-update)                sc/Symbol  ;; function
          (opt :accordion-fields)            [[sc/Str]] ;; paths to display in accordion summary
          (opt :order)                       sc/Int}    ;; 
   :body  [Element]})

(defn validate-doc-schema [doc-schema]
  (sc/check Doc doc-schema))

(defn validate-elem-schema [elem-schema]
  (sc/check Element elem-schema))

