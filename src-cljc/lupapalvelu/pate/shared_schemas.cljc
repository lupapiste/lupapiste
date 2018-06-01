(ns lupapalvelu.pate.shared-schemas
  "Schema definitions for Pate schemas."
  (:require [clojure.string :as s]
            [clojure.set :as set]
            [sade.shared-util :as util]
            [schema.core :refer [defschema] :as sc]))

(def phrase-categories #{:paatosteksti :lupaehdot :naapurit
                         :muutoksenhaku :vaativuus :rakennusoikeus
                         :kaava :toimenpide-julkipanoon :yleinen})

(def path-type (sc/conditional
                ;; Joined kw-path (e.g. :one.two.three)
                keyword? sc/Keyword
                ;; Vector path [:one :two :three] or vector of joined
                ;; kw-paths [:one.two.three :four.five], depending on
                ;; the schema.
                :else    [sc/Keyword]))

(def keyword-or-string (sc/conditional
                        keyword? sc/Keyword
                        :else    sc/Str))
(defn only-one-of
  "Only one of the given keys are allowed in the data. Note: do not use
  in a `PateComponent`, since the wrapping constraint breaks Pate
  validation. "
  [allowed-keys schema]
  (sc/constrained schema
                  (fn [data]
                    (< (->> (keys data)
                            (util/intersection-as-kw allowed-keys)
                            count)
                       2))
                  (str "Only one of the keys is allowed: " allowed-keys)))

(def PathCondition
  [(sc/one (sc/enum :OR :AND) :OR)
   (sc/conditional
    keyword?    (sc/constrained sc/Keyword
                                ;; util/fn-> does not pass schema
                                ;; validation on the ClojureScript!?
                                #(-> % #{:AND :OR} not))
    sequential? (sc/recursive #'PathCondition))])

(def condition-type
  "Paths with operator (:AND, :OR) and nesting support. A valid value is
  either a keyword or list. The list must start with operator and it
  can include nested lists. Valid values:

  :simple.path

  [:AND :first.path :second.path [:OR :alternative.path :other.path]]"
  (sc/conditional
   keyword?    sc/Keyword
   sequential? PathCondition))


(defschema PateEnabled
  "Component state (enabled/disabled) as defined by paths into the
  global state. Empty strings/collections are interpreted as
  falsey. Default: enabled.  On conflicts, component is disabled. Some
  path prefixes are handled specially:

   :_meta denotes that (the rest of) the path is interpreted as _meta
   query (see path/react-meta).

   :*ref the (remaining) path target is the references value of the
   options instead of state. If the value is sequential, then every
   deleted item is ignored.

  :? True if the path is found within the state (regardless of its
  value)

  If simple falsey/truthy resolution is not enough, the 'good/bad'
  value can be given within a path definition (but not for :? paths):

  :hii.hoo=9
  :_meta.foo.bar!=10

  Note: :_meta.enabled? is always used as prerequisite."
  {(sc/optional-key :enabled?)  condition-type
   (sc/optional-key :disabled?) condition-type})

(defschema PateVisible
  "Similar to PateEnabled, but without any implicit prerequisite
  condition. Default is visible."
  {(sc/optional-key :show?) condition-type
   (sc/optional-key :hide?) condition-type})

(defschema PateCss
  {(sc/optional-key :css) (sc/conditional
                           keyword? sc/Keyword
                           :else    [sc/Keyword])})

(defschema PateBase
  (merge PateEnabled
         PateCss
         {;; If an schema ancestor has :loc-prefix then localization
          ;; term is loc-prefix + id-path, where id-path from
          ;; loc-prefix schema element util current.
          (sc/optional-key :loc-prefix) keyword-or-string
          ;; Absolute localisation terms. Overrides loc-prefix, does not
          ;; affect children.
          (sc/optional-key :i18nkey)    keyword-or-string}))

(defschema PateComponent
  (merge PateBase
         {;; Show label? Default true
          (sc/optional-key :label?)          sc/Bool
          ;; Read-only components cannot be edited and only rendered
          ;; in the viewing mode. Overrides :always-editing?
          (sc/optional-key :read-only?)      sc/Bool
          ;; If true, the component is always rendered as the edit
          ;; component regardless of the container mode.
          (sc/optional-key :always-editing?) sc/Bool}))

(defschema PateReferenceList
  "Component that builds schema from an external source. Each item is
  the id property of the target value or the value itself."
  (merge PateComponent
         ;; Path is interpreted by the implementation. In Pate the
         ;; path typically refers to the settings. Note: if path
         ;; resolves into map, it is implicitly transformed into
         ;; vector of map values associated with :MAP-KEY property.
         {:path                              path-type
          ;; In addition to UI, type also affects validation: :select
          ;; only accepts single values. List is read-only.
          :type                              (sc/enum :select
                                                      :multi-select
                                                      :list)
          ;; By default, an item value is the same as
          ;; source. If :item-key is given, then the corresponding
          ;; source property is used.
          (sc/optional-key :item-key)        sc/Keyword
          ;; Term-path overrides item-loc-prefix. However,
          ;; item-loc-prefix supports item-key.
          (sc/optional-key :item-loc-prefix) sc/Keyword
          ;; Separator string between items when viewed. Not
          ;; applicable for list type. Default ", "
          (sc/optional-key :separator)       sc/Str
          ;; Are items sorted by textual presentation (e.g.,
          ;; label). Default false.
          (sc/optional-key :sort?)           sc/Bool
          ;; Term definition resolves the localization for the value.
          (sc/optional-key :term)
          {;; The path contains sources with corresponding fi, sv and
           ;; en localisations (if not extra-path given). The matching
           ;; is done by :item-key. If term is defined without :path
           ;; then the target value is assumed to have lang
           ;; properties.
           (sc/optional-key :path)       path-type
           ;; Additional path within matched term that contains the
           ;; lang properties.
           (sc/optional-key :extra-path) path-type
           ;; Key for the source property that should match the
           ;; value. For example, the value list might be just ids and
           ;; the match-key could be :id. Default value is the same as
           ;; item-key.
           (sc/optional-key :match-key)  sc/Keyword}}))

(defschema PatePhraseText
  "Textarea with integrated phrase support."
  (merge PateComponent
         {;; Default category.
          :category (apply sc/enum phrase-categories)
          (sc/optional-key :text) sc/Str}))

(defschema PateMultiSelect
  (merge PateComponent
         ;; Sometimes it is useful to finetune item localisations
         ;; separately from the label.
         {(sc/optional-key :item-loc-prefix) sc/Keyword
          :items                             [(sc/conditional
                                               :text  {:value keyword-or-string
                                                       :text  sc/Str}
                                               :else keyword-or-string)]
          ;; Selection sorted by label. Default is true.
          (sc/optional-key :sort?) sc/Bool}))

(def positive-integer (sc/constrained sc/Int (comp not neg?)))

(defschema PateDateDelta
  (merge PateComponent
         {(sc/optional-key :delta) positive-integer
          :unit                    (sc/enum :days :years)}))

(defschema PateReference
  "Displays the referenced value. By default, :path is resolved as a
  regular path into the component state. However, if the path is
  prefixed with :*ref the resolution target (for the rest) is
  references (like in PateEnabled for example)."
  (merge PateComponent
         {:path path-type}))

(defschema PateLink
  "UI component that represents a text with link. The link is part of
  the text-loc, using a special notation: 'Text before [link]
  after.' The click handler for the link is a _meta function."
  (merge PateComponent
         {;; The label text is always determined by the default
          ;; PateComponent mechanisms (loc-prefix, i18nkey), but the
          ;; actual text with link is determined by the :text-loc key.
          :text-loc sc/Keyword
          ;; Must resolve to _meta function. The function receives
          ;; options as arguments.
          :click    sc/Keyword}))

(defschema PateButton
  "Button with an optional icon."
  (only-one-of [:add :remove :click]
               (merge PateComponent
                      ;; Icon class (e.g., :lupicon-save)
                      {(sc/optional-key :icon)   sc/Keyword
                       ;; If false the button shows only icon. Default
                       ;; true.
                       (sc/optional-key :text?)  sc/Bool
                       ;; Keyword must be a sibling repeating dict id.
                       (sc/optional-key :add)    sc/Keyword
                       ;; Keyword is an encompassing repeating dict id
                       (sc/optional-key :remove) sc/Keyword
                       ;; Must resolve to _meta function. The function
                       ;; receives options as arguments.
                       (sc/optional-key :click)  sc/Keyword})))

(defschema PatePlaceholder
  "Placholder for external (filled by backend) data."
  (merge PateComponent
         {:type (sc/enum :neighbors :application-id
                         :building :statements)}))

(defschema KeyMap
  "Map with the restricted set of keys. In other words, no new keys
  after the instantiation can be added. No UI counterpart."
  {sc/Keyword sc/Any})

(defschema PateAttachments
  "Support for adding (via batch editor) attachments and viewing
  attachment list. In addition to schema properties, the component
  depends on two _meta functions:

  filedata: (options filedata & kvs -> filedata) Receives regular
  options (state, _meta, schema and other keys), filedata and extra
  key-values. Returns filedata. Typical use case is to add target
  information.

  include?: (options attachment -> boolean) True if the attachment is
  to be included in the attachments list. Sample use case: list only
  the verdict's attachments."
  (merge PateComponent
         {;; Matching type groups are listed on the type
          ;; selector. Default all type groups.
          (sc/optional-key :type-group) sc/Regex
          ;; Default selection the value is a kw-path
          ;; type-group.type-id. The value must in the filtered
          ;; type-groups or it is ignored.
          (sc/optional-key :default)    sc/Keyword
          ;; Dropzone is jQuery selector for the dropzone. For the
          ;; best visual effect the container should include dropzone
          ;; component. If not given, drag'n'drop is not supported.
          (sc/optional-key :dropzone)   sc/Str
          ;; If true, multiple files can be uploaded at the same
          ;; time. Default false.
          (sc/optional-key :multiple?)  sc/Bool}))

(defschema PateToggle
  (merge (dissoc PateComponent :css)
         {(sc/optional-key :value)  sc/Bool
          ;; Checkbox wrapper class prefix (see components/toggle).
          (sc/optional-key :prefix) keyword-or-string
          ;; By default the toggle text is determined by the
          ;; localisation mechanisms. However, in some cases dynamic
          ;; toggle text might be needed. :text-dict refers to a
          ;; sibling dict that contains the the toggle text.
          (sc/optional-key :text-dict) sc/Keyword}))

(def pate-units
  (sc/enum :days :years :ha :m2 :m3 :kpl :section :eur))

(defschema PateText
  (merge PateComponent
         {(sc/optional-key :value)  sc/Str
          ;; Default type is :text.
          (sc/optional-key :type)   (sc/enum :text :password)
          ;; Before and after are localisation keys for the strings to
          ;; be shown before and after the value and editor.
          (sc/optional-key :before) pate-units
          (sc/optional-key :after)  pate-units
          ;; Items are localization keys. If any items
          ;; are given, then the text field is rendered
          ;; as a combobox.
          (sc/optional-key :items)  [sc/Keyword]
          ;; If :lines is given the field is rendered as textarea.
          (sc/optional-key :lines)  positive-integer}))

(defschema PateDate
  (merge PateComponent
         ;; Timestamp (ms from epoch).
         {(sc/optional-key :value) sc/Int}))

(defschema PateSelect
  "Very simple selection model. Rendered as dropdown. Each item
  coresponds to a value. The option text is resolved via regular
  localisation mechanisms."
  (merge PateComponent
         {:items                          [sc/Keyword]
          ;; If true (default), empty selection (- Choose -) is
          ;; available. For autocomplete, the clear button is shown.
          (sc/optional-key :allow-empty?) sc/Bool
          ;; Value sorting uses natural order, text sorting takes
          ;; locale into account. Default order is the items
          ;; order.
          (sc/optional-key :sort-by)      (sc/enum :value :text)
          ;; How the select is rendered? Select is the default.
          (sc/optional-key :type)         (sc/enum :select :autocomplete)}))

(defschema PateLocText
  "Localisation term shown as text."
  (assoc PateCss
         :loc-text sc/Keyword))

(defschema PateRequired
  {(sc/optional-key :required?) sc/Bool})

(defn- required [m]
  (merge PateRequired m))

(defn schema-types
  "Schema type schema 'factory' function.

  schema-ref: Reference to the encompassing schema
              (e.g., #'SchemaTypes)

  fun: (optional) If given, called for every dict schema
                  (see VerdictSchemaTypes)"
  ([schema-ref fun]
   {sc/Keyword
    (let [fun (or fun identity)]
           (sc/conditional
            :reference-list (fun (required {:reference-list PateReferenceList}))
            :phrase-text    (fun (required {:phrase-text PatePhraseText}))
            :loc-text       (fun PateLocText)
            :date-delta     (fun (required {:date-delta PateDateDelta}))
            :multi-select   (fun (required {:multi-select PateMultiSelect}))
            :reference      (fun (required {:reference PateReference}))
            :link           (fun {:link PateLink})
            :button         (fun {:button PateButton})
            :placeholder    (fun {:placeholder PatePlaceholder})
            :keymap         (fun {:keymap KeyMap})
            :attachments    (fun {:attachments PateAttachments})
            :application-attachments (fun {:application-attachments PateComponent})
            :toggle         (fun {:toggle PateToggle})
            :text           (fun (required {:text PateText}))
            :date           (fun (required {:date PateDate}))
            :select         (fun (required {:select PateSelect}))
            :repeating      (fun {:repeating (sc/recursive schema-ref)
                                  ;; The value is a key in the repeating dictionary.
                                  (sc/optional-key :sort-by) sc/Keyword})))})
  ([schema-ref]
   (schema-types schema-ref nil)))

;; Keys on the left side of the conditional above.
(def schema-type-keys [:reference-list :phrase-text :loc-text :date-delta
                       :multi-select :reference :link :button :placeholder
                       :keymap :attachments :application-attachments
                       :toggle :text :date :select :repeating])

(defschema SchemaTypes
  (schema-types #'SchemaTypes))

(defschema Dictionary
  "Id to schema mapping."
  {:dictionary SchemaTypes})

(defschema PateLayout
  (merge PateBase PateVisible))

(defschema PateMeta
  "Dynamic management. No UI counterpart. Not part of the saved data."
  {(sc/optional-key :_meta) {sc/Keyword sc/Any}})

(defschema PateItem
  (merge PateLayout
         {;; Id is used as a path part for _meta queries.
          (sc/optional-key :id)   keyword-or-string
          ;; Value is a dictionary key
          (sc/optional-key :dict) sc/Keyword}))

(defschema CellConfig
  {(sc/optional-key :col)   sc/Int ;; Column width (.col-n). Default 1.
   (sc/optional-key :align) (sc/enum :left :right :center :full)})

(defschema PateList
  (merge PateLayout
         CellConfig
         {:list (merge PateCss
                       {(sc/optional-key :title)   keyword-or-string
                        ;; By default, items always have labels, even
                        ;; if they are just empty strings. Otherwise
                        ;; the vertical alignment could be
                        ;; off. If :labels? is false, then the labels
                        ;; are not laid out at all. This is useful,
                        ;; when it is known that none of the items
                        ;; have labels, thus avoiding superflous
                        ;; whitespace. Default is true.
                        (sc/optional-key :labels?) sc/Bool
                        :items                     [PateItem]})}))

(defschema PateItemCell
  (merge CellConfig
         PateItem))

(declare NestedGrid)

(defschema PateCell
  (sc/conditional :list PateList
                  :grid (sc/recursive #'NestedGrid)
                  :else PateItemCell))

(defschema PateGrid
  (merge PateLayout
         {:columns (apply sc/enum (range 1 25)) ;; Grid size (.pate-grid-n)
          :rows    [(sc/conditional
                     :row (merge PateVisible
                                 PateCss
                                 {(sc/optional-key :id)         sc/Str
                                  ;; The same semantics as in PateLayout.
                                  (sc/optional-key :loc-prefix) sc/Keyword
                                  :row                          [PateCell]})
                     :else [PateCell])]}))


(defschema NestedGrid
  (merge PateLayout
         CellConfig
         {:grid (assoc PateGrid
                       ;; The :repeating value is a nested dictionary
                       ;; for the repeating grid dicts. The
                       ;; corresponding state paths also include the
                       ;; index part (can be anything, provided by the
                       ;; backend.). For example, for buildings (see
                       ;; the verdict schema below) the state path
                       ;; is [:buildings <id> <dict>] where <id> is
                       ;; the id for the operation containing the
                       ;; building and <dict> is a dict reference to
                       ;; the nested dictionary.
                       (sc/optional-key :repeating) sc/Keyword)}))

(defschema PateSection
  (merge PateLayout
         {:id   sc/Keyword ;; Also title localization key
          :grid PateGrid}))

(defschema PateVerdictTemplateSection
  (assoc PateSection
         ;; By default section dicts are included only if the section
         ;; itself is included. However, sometimes this approach is
         ;; too restrictive (e.g., for extra reviews section
         ;; dicts). If :always-included? is true, then the section
         ;; dicts are never excluded.
         (sc/optional-key :always-included?) sc/Bool))

(def id-modified
  {(sc/optional-key :id)       sc/Str
   (sc/optional-key :modified) sc/Int})

(defschema PateVerdictTemplate
  (merge Dictionary
         PateMeta
         id-modified
         {(sc/optional-key :name) sc/Str ;; Non-localized raw string
          :sections               [PateVerdictTemplateSection]}))

(defschema PateSettings
  (merge Dictionary
         {:title    sc/Str
          :sections [(assoc PateSection
                            ;; A way to to show "required star" on the section title.
                            (sc/optional-key :required?) sc/Bool)]}))

(defschema PateVerdictSchemaTypes
  (schema-types #'PateVerdictSchemaTypes
                (fn [schema]
                  (only-one-of [:template-section :template-dict]
                               (merge schema
                                      {;; If the section is removed in the template, this
                                       ;; dict is excluded in the verdict.
                                       (sc/optional-key :template-section) sc/Keyword
                                       ;; The template-dict provides the initial value to
                                       ;; this verdict dict.
                                       (sc/optional-key :template-dict)    sc/Keyword})))))

(defschema PateVerdictDictionary
  {:dictionary PateVerdictSchemaTypes})

(def section-buttons
  ;; Show edit button? (default true)
  {(sc/optional-key :buttons?) sc/Bool})

(defschema PateVerdictSection
  (merge PateSection
         PateCss
         section-buttons
         {;; The corresponding verdict template section. Needed if the
          ;; template section is removable. If the template section is
          ;; removed then every dict specific to this verdict section
          ;; is also removed. If only parts of the section depend on
          ;; the template section then the correct mechanism is the
          ;; top level template-sections (see below).
          (sc/optional-key :template-section) sc/Keyword}))

(defschema PateVerdict
  (merge PateVerdictDictionary
         PateMeta
         id-modified
         {:version                    sc/Int
          :sections                   [PateVerdictSection]}))

(defschema PateLegacySection
  (merge PateSection
         section-buttons))

(defschema PateLegacyVerdict
  (merge id-modified
         {:legacy?    (sc/enum true)
          :dictionary SchemaTypes
          :sections   [PateLegacySection]}))
