(ns lupapalvelu.pate.shared
  (:require [clojure.string :as s]
            [clojure.set :as set]
            [markdown.core :as markdown]
            [sade.shared-util :as util]
            [schema.core :refer [defschema] :as sc]))

(def supported-languages [:fi :sv :en])

;; identifier - KuntaGML-paatoskoodi (yhteiset.xsd)
(def verdict-code-map
  {:annettu-lausunto            "annettu lausunto"
   :asiakirjat-palautettu       "asiakirjat palautettu korjauskehotuksin"
   :ehdollinen                  "ehdollinen"
   :ei-lausuntoa                "ei lausuntoa"
   :ei-puollettu                "ei puollettu"
   :ei-tiedossa                 "ei tiedossa"
   :ei-tutkittu-1               "ei tutkittu"
   :ei-tutkittu-2               "ei tutkittu (oikaisuvaatimusvaatimus tai lupa pysyy puollettuna)"
   :ei-tutkittu-3               "ei tutkittu (oikaisuvaatimus tai lupa pysyy ev\u00e4ttyn\u00e4)"
   :evatty                      "ev\u00e4tty"
   :hallintopakko               "hallintopakon tai uhkasakkoasian k\u00e4sittely lopetettu"
   :hyvaksytty                  "hyv\u00e4ksytty"
   :ilmoitus-tiedoksi           "ilmoitus merkitty tiedoksi"
   :konversio                   "muutettu toimenpideluvaksi (konversio)"
   :lautakunta-palauttanut      "asia palautettu uudelleen valmisteltavaksi"
   :lautakunta-poistanut        "asia poistettu esityslistalta"
   :lautakunta-poydalle         "asia pantu p\u00f6yd\u00e4lle kokouksessa"
   :maarays-peruutettu          "m\u00e4\u00e4r\u00e4ys peruutettu"
   :muutti-evatyksi             "muutti ev\u00e4tyksi"
   :muutti-maaraysta            "muutti m\u00e4\u00e4r\u00e4yst\u00e4 tai p\u00e4\u00e4t\u00f6st\u00e4"
   :muutti-myonnetyksi          "muutti my\u00f6nnetyksi"
   :myonnetty                   "my\u00f6nnetty"
   :myonnetty-aloitusoikeudella "my\u00f6nnetty aloitusoikeudella"
   :osittain-myonnetty          "osittain my\u00f6nnetty"
   :peruutettu                  "peruutettu"
   :puollettu                   "puollettu"
   :pysytti-evattyna            "pysytti ev\u00e4ttyn\u00e4"
   :pysytti-maarayksen-2        "pysytti m\u00e4\u00e4r\u00e4yksen tai p\u00e4\u00e4t\u00f6ksen"
   :pysytti-myonnettyna         "pysytti my\u00f6nnettyn\u00e4"
   :pysytti-osittain            "pysytti osittain my\u00f6nnettyn\u00e4"
   :siirretty-maaoikeudelle     "siirretty maaoikeudelle"
   :suunnitelmat-tarkastettu    "suunnitelmat tarkastettu"
   :tehty-hallintopakkopaatos-1 "tehty hallintopakkop\u00e4\u00e4t\u00f6s (ei velvoitetta)"
   :tehty-hallintopakkopaatos-2 "tehty hallintopakkop\u00e4\u00e4t\u00f6s (asetettu velvoite)"
   :tehty-uhkasakkopaatos       "tehty uhkasakkop\u00e4\u00e4t\u00f6s"
   :tyohon-ehto                 "ty\u00f6h\u00f6n liittyy ehto"
   :valituksesta-luovuttu-1     "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy puollettuna)"
   :valituksesta-luovuttu-2     "valituksesta on luovuttu (oikaisuvaatimus tai lupa pysyy ev\u00e4ttyn\u00e4)"})

(def review-type-map
  {:muu-katselmus             "muu katselmus"
   :muu-tarkastus             "muu tarkastus"
   :aloituskokous             "aloituskokous"
   :paikan-merkitseminen      "rakennuksen paikan merkitseminen"
   :paikan-tarkastaminen      "rakennuksen paikan tarkastaminen"
   :pohjakatselmus            "pohjakatselmus"
   :rakennekatselmus          "rakennekatselmus"
   :lvi-katselmus             "l\u00e4mp\u00f6-, vesi- ja ilmanvaihtolaitteiden katselmus"
   :osittainen-loppukatselmus "osittainen loppukatselmus"
   :loppukatselmus            "loppukatselmus"
   :ei-tiedossa               "ei tiedossa"})

(def foreman-codes [:vastaava-tj :vv-tj :iv-tj :erityis-tj :tj])

(def verdict-dates [:julkipano :anto :muutoksenhaku :lainvoimainen
                    :aloitettava :voimassa])

(def p-verdict-dates [:julkipano :anto :valitus :lainvoimainen
                      :aloitettava :voimassa])

;; Phrases

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
  "Only one of the given keys are allowed in the data."
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
         {(sc/optional-key :label?) sc/Bool})) ;; Show label? Default true

(defschema PateReferenceList
  "Component that builds schema from an external source. Each item is
  the id property of the target value or the value itself."
  (merge PateComponent
         ;; Path is interpreted by the implementation. In Pate the
         ;; path typically refers to the settings.
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
           ;; is done by :item-key
           :path                         path-type
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
         {(sc/optional-key :delta)   positive-integer
          :unit                      (sc/enum :days :years)}))

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
          (sc/optional-key :prefix) keyword-or-string}))

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
          (sc/optional-key :after)  pate-units}))

(def PateDate PateComponent)

(defschema PateSelect
  "Very simple selection model. Rendered as dropdown. Each item
  coresponds to a value. The option text is resolved via regular
  localisation mechanisms."
  (merge PateComponent
         {:items                          [sc/Keyword]
          ;; If true (default), empty selection (- Choose -) is
          ;; available.
          (sc/optional-key :allow-empty?) sc/Bool
          ;; Value sorting uses natural order, text sorting takes
          ;; locale into account. Default order is the items order.
          (sc/optional-key :sort-by)      (sc/enum :value :text)}))

(defschema PateRequired
  {(sc/optional-key :required?) sc/Bool})

(defn- required [m]
  (merge PateRequired m))

(defschema SchemaTypes
  {sc/Keyword (sc/conditional
               :reference-list (required {:reference-list PateReferenceList})
               :phrase-text    (required {:phrase-text PatePhraseText})
               :loc-text       {:loc-text sc/Keyword} ;; Localisation term shown as text.
               :date-delta     (required {:date-delta PateDateDelta})
               :multi-select   (required {:multi-select PateMultiSelect})
               :reference      (required {:reference PateReference})
               :link           {:link PateLink}
               :button         {:button PateButton}
               :placeholder    {:placeholder PatePlaceholder}
               :keymap         {:keymap KeyMap}
               :attachments    {:attachments PateAttachments}
               :application-attachments {:application-attachments PateComponent}
               :toggle         {:toggle PateToggle}
               :text           (required {:text PateText})
               :date           (required {:date PateDate})
               :select         (required {:select PateSelect})
               :repeating      {:repeating (sc/recursive #'SchemaTypes)
                                ;; The value is a key in the repeating dictionary.
                                (sc/optional-key :sort-by) sc/Keyword})})

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
                       {(sc/optional-key :title)   sc/Str
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
         {:id   keyword-or-string ;; Also title localization key
          :grid PateGrid}))

(defschema PateVerdictTemplate
  (merge Dictionary
         PateMeta
         {(sc/optional-key :id)       sc/Str
          (sc/optional-key :modified) sc/Int
          (sc/optional-key :name)     sc/Str ;; Non-localized raw string
          :sections                   [PateSection]}))

(defn reference-list [path extra]
  {:reference-list (merge {:label? false
                           :type   :multi-select
                           :path   path}
                          extra)})

(defn  multi-section [id link-ref]
  {:loc-prefix (str "pate-" (name id))
   :id         (name id)
   :grid       {:columns 1
                :rows    [[{:align      :full
                            :loc-prefix (str "pate-settings." (name id))
                            :hide?      link-ref
                            :dict       :link-to-settings-no-label}
                           {:show? link-ref
                            :dict  id }]]}})

(defn text-section [id]
  {:loc-prefix (str "pate-" (name id))
   :id         (name id)
   :grid       {:columns 1
                :rows    [[{:dict id}]]}})

(defn date-delta-row [kws]
  (->> kws
       (reduce (fn [acc kw]
                 (conj acc
                       (when (seq acc)
                         {:dict :plus
                          :css [:date-delta-plus]})
                       {:col 2 :dict kw :id (name kw)}))
               [])
       (remove nil?)))

(def language-select {:select {:loc-prefix :pate-verdict.language
                               :items supported-languages}})
(def verdict-giver-select {:select {:loc-prefix :pate-verdict.giver
                                    :items      [:lautakunta :viranhaltija]
                                    :sort-by    :text}})
(def complexity-select {:select {:loc-prefix :pate.complexity
                                 :items      [:small :medium :large :extra-large]}})

(def collateral-type-select {:select {:loc-prefix :pate.collateral-type
                                      :items      [:shekki :panttaussitoumus]
                                      :sort-by    :text}})

(defn req [m]
  (assoc m :required? true))

(defmulti default-verdict-template (fn [arg]
                                     arg))

(defmethod default-verdict-template :r [_]
  {:dictionary {:language                  language-select
                :verdict-dates             {:multi-select {:items           verdict-dates
                                                           :sort?           false
                                                           :i18nkey         :pate-verdict-dates
                                                           :item-loc-prefix :pate-verdict}}
                :giver                     (req verdict-giver-select)
                :verdict-code              {:reference-list {:path       :settings.verdict-code
                                                             :type       :select
                                                             :loc-prefix :pate-r.verdict-code}}
                :paatosteksti              {:phrase-text {:category :paatosteksti}}
                :bulletinOpDescription     {:phrase-text {:category :toimenpide-julkipanoon
                                                          :i18nkey  :phrase.category.toimenpide-julkipanoon}}
                :link-to-settings          {:link {:text-loc :pate.settings-link
                                                   :click    :open-settings}}
                :link-to-settings-no-label {:link {:text-loc :pate.settings-link
                                                   :label?   false
                                                   :click    :open-settings}}
                ;; The following keys are whole sections
                :foremen                   (reference-list :settings.foremen {:item-loc-prefix :pate-r.foremen})
                :plans                     (reference-list :plans {:item-key :id
                                                                   :term     {:path       [:plans]
                                                                              :extra-path [:name]
                                                                              :match-key  :id}})
                :reviews                   (reference-list :reviews {:item-key :id
                                                                     :term     {:path       [:reviews]
                                                                                :extra-path [:name]
                                                                                :match-key  :id}})
                :conditions                {:repeating {:condition        {:phrase-text {:i18nkey  :pate-condition
                                                                                         :category :lupaehdot}}
                                                        :remove-condition {:button {:i18nkey :remove
                                                                                    :label?  false
                                                                                    :icon    :lupicon-remove
                                                                                    :css     [:primary :outline]
                                                                                    :remove  :conditions}}}}
                :add-condition             {:button {:icon    :lupicon-circle-plus
                                                     :i18nkey :pate-conditions.add
                                                     :css     :positive
                                                     :add     :conditions}}
                :neighbors                 {:loc-text :pate-neighbors.text}

                :appeal           {:phrase-text {:category :muutoksenhaku
                                                 :i18nkey  :phrase.category.muutoksenhaku}}
                :collateral       {:loc-text :pate-collateral.text}
                ;; Complexity section
                :complexity       complexity-select
                :complexity-text  {:phrase-text {:label?   false
                                                 :category :vaativuus}}
                ;; Text sections
                :extra-info       {:loc-text :pate-extra-info.text}
                :deviations       {:loc-text :pate-deviations.text}
                :rights           {:loc-text :pate-rights.text}
                :purpose          {:loc-text :pate-purpose.text}
                :statements       {:loc-text :pate-statements.text}
                ;; Buildings section
                :autopaikat       {:toggle {}}
                :vss-luokka       {:toggle {}}
                :paloluokka       {:toggle {}}
                ;; Attachments section
                :upload           {:toggle {}}
                ;; Removable sections
                :removed-sections {:keymap (zipmap [:foremen :reviews :plans
                                                    :conditions :neighbors
                                                    :appeal :statements :collateral
                                                    :extra-info :deviations
                                                    :complexity :rights :purpose
                                                    :buildings :attachments]
                                                   (repeat false))}}
   :sections [{:id         "verdict"
               :loc-prefix :pate-verdict
               :grid       {:columns 12
                            :rows    [[{:col  6
                                        :dict :language}]
                                      [{:col  12
                                        :dict :verdict-dates}]
                                      [{:col  3
                                        :dict :giver}
                                       {:align      :full
                                        :col        3
                                        :loc-prefix :pate-r.verdict-code
                                        :hide?      :*ref.settings.verdict-code
                                        :dict       :link-to-settings}
                                       {:align :full
                                        :col   3
                                        :show? :*ref.settings.verdict-code
                                        :dict  :verdict-code}]
                                      [{:col  12
                                        :dict :paatosteksti}]]}}
              {:id         "bulletin"
               :loc-prefix :bulletin
               :grid       {:columns 1
                            :rows    [[{:col  1
                                        :dict :bulletinOpDescription}]]}}
              (multi-section :foremen :*ref.settings.foremen)
              (multi-section :reviews :*ref.reviews)
              (multi-section :plans :*ref.plans)
              {:id         "conditions"
               :loc-prefix :phrase.category.lupaehdot
               :grid       {:columns 1
                            :rows    [[{:grid {:columns   8
                                               :repeating :conditions
                                               :rows      [[{:col  6
                                                             :dict :condition}
                                                            {}
                                                            {:dict :remove-condition}]]}}]
                                      [{:dict :add-condition}]]}}
              (text-section :neighbors)
              {:id         "appeal"
               :loc-prefix :pate-appeal
               :grid       {:columns 1
                            :rows    [[{:dict :appeal }]]}}
              (text-section :collateral)
              {:id         "complexity"
               :loc-prefix :pate.complexity
               :grid       {:columns 1
                            :rows    [[{:dict :complexity }]
                                      [{:id   "text"
                                        :dict :complexity-text}]]}}
              (text-section :extra-info)
              (text-section :deviations)
              (text-section :rights)
              (text-section :purpose)
              (text-section :statements)
              {:id         "buildings"
               :loc-prefix :pate-buildings
               :grid       {:columns 1
                            :rows    [[{:loc-prefix :pate-buildings.info
                                        :list       {:title   "pate-buildings.info"
                                                     :labels? false
                                                     :items   (mapv (fn [check]
                                                                      {:dict check
                                                                       :id   check
                                                                       :css  [:pate-condition-box]})
                                                                    [:autopaikat :vss-luokka :paloluokka])}}]]}}
              {:id         "attachments"
               :loc-prefix :application.verdict-attachments
               :grid       {:columns 1
                            :rows    [[{:loc-prefix :pate.attachments
                                        :list       {:labels? false
                                                     :items   [{:id   :upload
                                                                :dict :upload}]}}]]}}]})

(defmethod default-verdict-template :p [_]
  {:dictionary {:language                  language-select
                :verdict-dates             {:multi-select {:items           p-verdict-dates
                                                           :sort?           false
                                                           :i18nkey         :pate-verdict-dates
                                                           :item-loc-prefix :pate-verdict}}
                :giver                     (req verdict-giver-select)
                :verdict-code              {:reference-list {:path       :settings.verdict-code
                                                             :type       :select
                                                             :loc-prefix :pate-r.verdict-code}}
                :paatosteksti              {:phrase-text {:category :paatosteksti}}
                :link-to-settings          {:link {:text-loc :pate.settings-link
                                                   :click    :open-settings}}
                :link-to-settings-no-label {:link {:text-loc :pate.settings-link
                                                   :label?   false
                                                   :click    :open-settings}}
                ;; The following keys are whole sections
                :add-condition             {:button {:icon    :lupicon-circle-plus
                                                     :i18nkey :pate-conditions.add
                                                     :css     :positive
                                                     :add     :conditions}}
                :neighbors                 {:loc-text :pate-neighbors.text}

                :appeal           {:phrase-text {:category :muutoksenhaku
                                                 :i18nkey  :phrase.category.muutoksenhaku}}
                :collateral       {:loc-text :pate-collateral.text}
                ;; Complexity section
                :complexity       complexity-select
                :complexity-text  {:phrase-text {:label?   false
                                                 :category :vaativuus}}
                ;; Text sections
                :extra-info       {:loc-text :pate-extra-info.text}
                :deviations       {:loc-text :pate-deviations.text}
                :rights           {:loc-text :pate-rights.text}
                :purpose          {:loc-text :pate-purpose.text}
                :statements       {:loc-text :pate-statements.text}
                ;; Buildings section
                :autopaikat       {:toggle {}}
                :vss-luokka       {:toggle {}}
                :paloluokka       {:toggle {}}
                ;; Attachments section
                :upload           {:toggle {}}
                ;; Removable sections
                :removed-sections {:keymap (zipmap [:foremen :reviews :plans
                                                    :conditions :neighbors
                                                    :appeal :statements :collateral
                                                    :extra-info :deviations
                                                    :complexity :rights :purpose
                                                    :buildings :attachments]
                                                   (repeat false))}}
   :sections [{:id         "verdict"
               :loc-prefix :pate-verdict
               :grid       {:columns 12
                            :rows    [[{:col  6
                                        :dict :language}]
                                      [{:col  12
                                        :dict :verdict-dates}]
                                      [{:col  3
                                        :dict :giver}
                                       {:align      :full
                                        :col        3
                                        :loc-prefix :pate-r.verdict-code
                                        :hide?      :*ref.settings.verdict-code
                                        :dict       :link-to-settings}
                                       {:align :full
                                        :col   3
                                        :show? :*ref.settings.verdict-code
                                        :dict  :verdict-code}]
                                      [{:col  12
                                        :dict :paatosteksti}]]}}
              (text-section :neighbors)
              {:id         "appeal"
               :loc-prefix :pate-appeal
               :grid       {:columns 1
                            :rows    [[{:dict :appeal }]]}}
              (text-section :collateral)
              {:id         "complexity"
               :loc-prefix :pate.complexity
               :grid       {:columns 1
                            :rows    [[{:dict :complexity }]
                                      [{:id   "text"
                                        :dict :complexity-text}]]}}
              (text-section :extra-info)
              (text-section :deviations)
              (text-section :rights)
              (text-section :purpose)
              (text-section :statements)
              {:id         "attachments"
               :loc-prefix :application.verdict-attachments
               :grid       {:columns 1
                            :rows    [[{:loc-prefix :pate.attachments
                                        :list       {:labels? false
                                                     :items   [{:id   :upload
                                                                :dict :upload}]}}]]}}]})

(sc/validate PateVerdictTemplate (default-verdict-template :r))
(sc/validate PateVerdictTemplate (default-verdict-template :p))

(defschema PateSettings
  (merge Dictionary
         {:title    sc/Str
          :sections [(assoc PateSection
                            ;; A way to to show "required star" on the section title.
                            (sc/optional-key :required?) sc/Bool)]}))

(def r-settings
  {:title      "pate-r"
   :dictionary {:verdict-dates            {:loc-text :pate-verdict-dates}
                :plus                     {:loc-text :plus}
                :julkipano                (req {:date-delta {:unit :days}})
                :anto                     (req {:date-delta {:unit :days}})
                :muutoksenhaku            (req {:date-delta {:unit :days}})
                :lainvoimainen            (req {:date-delta {:unit :days}})
                :aloitettava              (req {:date-delta {:unit :years}})
                :voimassa                 (req {:date-delta {:unit :years}})
                :verdict-code             (req {:multi-select {:label? false
                                                               :items  (keys verdict-code-map)}})
                :lautakunta-muutoksenhaku (req {:date-delta {:unit :days}})
                :boardname                (req {:text {}})
                :foremen                  {:multi-select {:label? false
                                                          :items  foreman-codes}}
                :plans                    {:reference-list {:label?   false
                                                            :path     [:plans]
                                                            :item-key :id
                                                            :type     :list
                                                            :sort?    true
                                                            :term     {:path       :plans
                                                                       :extra-path :name}}}
                :reviews                  {:reference-list {:label?   false
                                                            :path     [:reviews]
                                                            :item-key :id
                                                            :type     :list
                                                            :sort?    true
                                                            :term     {:path       :reviews
                                                                       :extra-path :name}}}}
   :sections   [{:id         "verdict-dates"
                 :loc-prefix :pate-verdict-dates
                 :grid       {:columns    17
                              :loc-prefix :pate-verdict
                              :rows       [(date-delta-row verdict-dates)]
                              }}
                {:id         "verdict"
                 :required?  true
                 :loc-prefix :pate-settings.verdict
                 :grid       {:columns    1
                              :loc-prefix :pate-r.verdict-code
                              :rows       [[{:dict :verdict-code}]]}}
                {:id         "board"
                 :loc-prefix :pate-verdict.giver.lautakunta
                 :grid       {:columns 4
                              :rows    [[{:loc-prefix :pate-verdict.muutoksenhaku
                                          :dict       :lautakunta-muutoksenhaku}]
                                        [{:col        1
                                          :align      :full
                                          :loc-prefix :pate-settings.boardname
                                          :dict       :boardname}]]}}
                {:id         "foremen"
                 :loc-prefix :pate-settings.foremen
                 :grid       {:columns    1
                              :loc-prefix :pate-r.foremen
                              :rows       [[{:dict :foremen}]]}}
                {:id         "plans"
                 :loc-prefix :pate-settings.plans
                 :grid       {:columns 1
                              :rows    [[{:dict :plans}]]}}
                {:id         "reviews"
                 :loc-prefix :pate-settings.reviews
                 :grid       {:columns 1
                              :rows    [[{:dict :reviews}]]}}]})

(def settings-schemas
  {:r r-settings
   :p r-settings})

(sc/validate PateSettings r-settings)


(defschema PateVerdictSection
  (merge PateLayout
         PateCss
         {:id   keyword-or-string ;; Also title localization key
          (sc/optional-key :buttons?) sc/Bool ;; Show edit button? (default true)
          :grid PateGrid}))

(defschema PateVerdict
  (merge Dictionary
         PateMeta
         {(sc/optional-key :id)       sc/Str
          (sc/optional-key :modified) sc/Int
          :sections                   [PateVerdictSection]}))


(def required-in-verdict {:toggle {:i18nkey :pate.template-removed}})
(def verdict-handler (req {:text {:loc-prefix :pate-verdict.handler}}))
(def app-id-placeholder {:placeholder {:loc-prefix :pate-verdict.application-id
                                       :type :application-id}})

;; It is advisable to reuse ids from template when possible. This
;; makes localization work automatically.
(def verdict-schemas
  {:r
   {:dictionary
    (merge
     {:language                (req language-select)
      :verdict-date            (req {:date {}})
      :automatic-verdict-dates {:toggle {}}}
     (->> [:julkipano :anto :muutoksenhaku :lainvoimainen :aloitettava :voimassa]
          (map (fn [kw]
                 [kw (req {:date {:disabled? :automatic-verdict-dates}})]))
          (into {}))
     {:boardname             {:reference {:path :*ref.boardname}}
      :handler               verdict-handler
      :verdict-section       (req {:text {:before :section}})
      :verdict-code          (req {:reference-list {:path       :verdict-code
                                                    :type       :select
                                                    :loc-prefix :pate-r.verdict-code}})
      :verdict-text          (req {:phrase-text {:category :paatosteksti}})
      :bulletinOpDescription {:phrase-text {:category :toimenpide-julkipanoon
                                            :i18nkey  :phrase.category.toimenpide-julkipanoon}}
      :verdict-text-ref      (req {:reference {:path :verdict-text}})
      :application-id        app-id-placeholder}
     (reduce (fn [acc [loc-prefix kw term? separator?]]
               (let [included (keyword (str (name kw) "-included"))
                     path     kw]
                 (assoc acc
                        (util/kw-path kw)
                        {:reference-list
                         (merge {:enabled?   included
                                 :loc-prefix loc-prefix
                                 :path       path
                                 :type       :multi-select}
                                (when term?
                                  {:item-key :id
                                   :term     {:path       path
                                              :extra-path :name
                                              :match-key  :id}})
                                (when separator?
                                  {:separator " \u2013 "}))}
                        ;; Included toggle
                        included required-in-verdict)))
             {}
             [[:pate-r.foremen :foremen false false]
              [:pate-plans :plans true false]
              [:pate-reviews :reviews true true]])
     {:conditions-title {:loc-text :phrase.category.lupaehdot}
      :conditions       {:repeating {:condition        {:phrase-text {:label?   false
                                                                      :category :lupaehdot}}
                                     :remove-condition {:button {:i18nkey :remove
                                                                 :label?  false
                                                                 :icon    :lupicon-remove
                                                                 :css     :secondary
                                                                 :remove  :conditions}}}}
      :add-condition    {:button {:icon    :lupicon-circle-plus
                                  :i18nkey :pate-conditions.add
                                  :css     :positive
                                  :add     :conditions}}
      :statements       {:placeholder {:type :statements}}
      :neighbors        {:phrase-text {:i18nkey  :phrase.category.naapurit
                                       :category :naapurit}}
      :neighbor-states  {:placeholder {:type :neighbors}}
      :collateral       {:text {:after :eur}}
      :collateral-flag  {:toggle {:loc-prefix :pate-collateral.flag}}
      :collateral-date  {:date {}}
      :collateral-type  collateral-type-select
      :appeal           {:phrase-text {:category :muutoksenhaku}}
      :complexity       complexity-select
      :complexity-text  {:phrase-text {:label?   false
                                       :category :vaativuus}}
      :extra-info       {:phrase-text {:category :yleinen}}
      :deviations       {:phrase-text {:category :yleinen}}
      :rights           {:phrase-text {:category :rakennusoikeus}}
      :purpose          {:phrase-text {:category :kaava}}
      :buildings        {:repeating {:building-name          {:placeholder {:label? false
                                                                            :type   :building}}
                                     :rakennetut-autopaikat  {:text {}}
                                     :kiinteiston-autopaikat {:text {}}
                                     :autopaikat-yhteensa    {:text {}}
                                     :vss-luokka             {:text {}}
                                     :paloluokka             {:text {}}
                                     :show-building          required-in-verdict}
                         :sort-by   :order}
      :upload           {:attachments {:i18nkey    :application.verdict-attachments
                                       :label?     false
                                       :type-group #"paatoksenteko"
                                       :default    :paatoksenteko.paatosote
                                       :dropzone   "#application-pate-verdict-tab"
                                       :multiple?  true}}
      :attachments      {:application-attachments {:i18nkey :application.verdict-attachments}}})
    :sections
    [{:id   "pate-dates"
      :grid {:columns 7
             :rows    [[{:col   7
                         :dict  :language
                         :hide? :_meta.published?}]
                       [{:col  2
                         :dict :handler}
                        {}
                        {:col  2
                         :hide? :_meta.editing?
                         :dict :application-id}]
                       [{:id   "verdict-date"
                         :dict :verdict-date}
                        {:id    "automatic-verdict-dates"
                         :col   2
                         :show? [:AND :_meta.editing?
                                 (cons :OR (map #(util/kw-path :? %) verdict-dates))]
                         :dict  :automatic-verdict-dates}]
                       {:id         "deltas"
                        :css        [:pate-date]
                        :loc-prefix :pate-verdict
                        :row        (map (fn [kw]
                                           (let [id (name kw)]
                                             {:show?     (util/kw-path :? kw)
                                              :disabled? :automatic-verdict-dates
                                              :id        id
                                              :dict      kw}))
                                         verdict-dates)}]}}
     {:id   "pate-verdict"
      :grid {:columns 7
             :rows    [[{:col        2
                         :loc-prefix :pate-verdict.giver
                         :hide?      :_meta.editing?
                         :show?      :*ref.boardname
                         :dict       :boardname}
                        {:col        1
                         :show?      [:OR :*ref.boardname :verdict-section]
                         :loc-prefix :pate-verdict.section
                         :dict       :verdict-section}
                        {:show? [:AND :_meta.editing? :*ref.boardname]}
                        {:col   2
                         :align :full
                         :dict  :verdict-code}]
                       [{:col   5
                         :id    "paatosteksti"
                         :show? :_meta.editing?
                         :dict  :verdict-text}
                        {:col   5
                         :hide? :_meta.editing?
                         :dict  :verdict-text-ref}]]}}
     {:id         "bulletin"
      :loc-prefix :bulletin
      :show?      :?.bulletin-op-description
      :grid       {:columns 1
                   :rows    [[{:col  1
                               :id   "toimenpide-julkipanoon"
                               :dict :bulletinOpDescription}]]}}
     {:id   "requirements"
      :grid {:columns 7
             :rows    (map (fn [dict]
                             (let [check-path (keyword (str (name dict) "-included"))]
                               {:show? [:OR :_meta.editing? check-path]
                                :row   [{:col  4
                                         :dict dict}
                                        {:col   2
                                         :align :right
                                         :show? :_meta.editing?
                                         :id    "included"
                                         :dict  check-path}]}))
                           [:foremen :plans :reviews])}}
     {:id   "conditions"
      :grid {:columns 1
             :show?   :?.conditions
             :rows    [[{:css  :pate-label
                         :dict :conditions-title}]
                       [{:grid {:columns   9
                                :repeating :conditions
                                :rows      [[{:col  7
                                              :dict :condition}
                                             {:align :right
                                              :dict  :remove-condition}]]}}]
                       [{:dict :add-condition}]]}}

     {:id    "appeal"
      :show? [:OR :?.appeal :?.collateral]
      :grid  {:columns 7
              :rows    [{:show? :?.appeal
                         :row   [{:col        6
                                  :loc-prefix :verdict.muutoksenhaku
                                  :dict       :appeal}]}
                        {:show? [:AND :?.collateral :_meta.editing?]
                         :css   [:row--tight]
                         :row   [{:col  3
                                  :dict :collateral-flag}]}
                        {:show?      [:AND :?.collateral :collateral-flag]
                         :loc-prefix :pate
                         :row        [{:col  2
                                       :id   :collateral-date
                                       :dict :collateral-date}
                                      {:col  2
                                       :id   :collateral
                                       :dict :collateral}
                                      {:col  2
                                       :dict :collateral-type}]}]}}
     {:id         "statements"
      :show?      :?.statements
      :loc-prefix :pate-statements
      :buttons?   false
      :grid       {:columns 1
                   :rows    [[{:dict :statements}]]}}
     {:id    "neighbors"
      :show? :?.neighbors
      :grid  {:columns 12
              :rows    [[{:col  7
                          :id   "neighbor-notes"
                          :dict :neighbors}
                         {:col   4
                          :hide? :_meta.published?
                          :id    "neighbor-states"
                          :align :right
                          :dict  :neighbor-states}]
                        ]}}
     {:id         "complexity"
      :loc-prefix :pate.complexity
      :show?      [:OR :?.complexity :?.rights :?.purpose]
      :grid       {:columns 7
                   :rows    [{:show? :?.complexity
                              :row   [{:col  3
                                       :dict :complexity}]}
                             {:show? :?.complexity
                              :row   [{:col  6
                                       :id   "text"
                                       :dict :complexity-text}]}
                             {:show? :?.rights
                              :row   [{:col        6
                                       :loc-prefix :pate-rights
                                       :dict       :rights}]}
                             {:show? :?.purpose
                              :row   [{:col        6
                                       :loc-prefix :phrase.category.kaava
                                       :dict       :purpose}]}]}}
     {:id         "extra-info"
      :loc-prefix :pate-extra-info
      :show?      :?.extra-info
      :grid       {:columns 7
                   :rows    [[{:col  6
                               :dict :extra-info}]]}}
     {:id         "deviations"
      :loc-prefix :pate-deviations
      :show?      :?.extra-info
      :grid       {:columns 7
                   :rows    [[{:col  6
                               :dict :deviations}]]}}
     {:id    "buildings"
      :show? :?.buildings
      :grid  {:columns 7
              :rows    [[{:col  7
                          :grid {:columns    6
                                 :loc-prefix :pate-buildings.info
                                 :repeating  :buildings
                                 :rows       [{:css   [:row--tight]
                                               :show? [:OR :_meta.editing? :+.show-building]
                                               :row   [{:col  6
                                                        :dict :building-name}]}
                                              {:show? [:OR :_meta.editing? :+.show-building]
                                               :css   [:row--indent]
                                               :row   [{:col  5
                                                        :list {:css   :list--sparse
                                                               :items (map #(hash-map :id %
                                                                                      :dict %
                                                                                      :show? (util/kw-path :?+ %)
                                                                                      :enabled? :-.show-building)
                                                                           [:rakennetut-autopaikat
                                                                            :kiinteiston-autopaikat
                                                                            :autopaikat-yhteensa
                                                                            :vss-luokka
                                                                            :paloluokka])}}
                                                       {:dict  :show-building
                                                        :show? :_meta.editing?}]}
                                              ]}}]]}}
     {:id   "attachments"
      :grid {:columns 7
             :rows    [[{:col  6
                         :dict :attachments}]]}}
     {:id       "upload"
      :hide?    :_meta.published?
      :css      :pate-section--no-border
      :buttons? false
      :grid     {:columns 7
                 :rows    [[{:col  6
                             :dict :upload}]]}}]}
   :p {:dictionary
       (merge
        {:language                (req language-select)
         :verdict-date            (req {:date {}})
         :automatic-verdict-dates {:toggle {}}}
        (->> [:julkipano :anto :valitus :lainvoimainen :aloitettava :voimassa]
             (map (fn [kw]
                    [kw (req {:date {:disabled? :automatic-verdict-dates}})]))
             (into {}))
        {:boardname             {:reference {:path :*ref.boardname}}
         :contact               (req {:text {}})
         :verdict-section       (req {:text {:before :section}})
         :verdict-code          (req {:reference-list {:path       :verdict-code
                                                       :type       :select
                                                       :loc-prefix :pate-r.verdict-code}})
         :verdict-text          (req {:phrase-text {:category :paatosteksti}})
         :bulletinOpDescription {:phrase-text {:category :toimenpide-julkipanoon
                                               :i18nkey  :phrase.category.toimenpide-julkipanoon}}
         :verdict-text-ref      (req {:reference {:path :verdict-text}})
         :application-id        app-id-placeholder}
        (reduce (fn [acc [loc-prefix kw term? separator?]]
                  (let [included (keyword (str (name kw) "-included"))
                        path     kw]
                    (assoc acc
                           (util/kw-path kw)
                           {:reference-list
                            (merge {:enabled?   included
                                    :loc-prefix loc-prefix
                                    :path       path
                                    :type       :multi-select}
                                   (when term?
                                     {:item-key :id
                                      :term     {:path       path
                                                 :extra-path :name
                                                 :match-key  :id}})
                                   (when separator?
                                     {:separator " \u2013 "}))}
                           ;; Included checkbox
                           included required-in-verdict)))
                {}
                [[:pate-r.foremen :foremen false false]
                 [:pate-plans :plans true false]
                 [:pate-reviews :reviews true true]])
        {:conditions-title {:loc-text :phrase.category.lupaehdot}
         :conditions       {:repeating {:condition        {:phrase-text {:label?   false
                                                                         :category :lupaehdot}}
                                        :remove-condition {:button {:i18nkey :remove
                                                                    :label?  false
                                                                    :icon    :lupicon-remove
                                                                    :css     :secondary
                                                                    :remove  :conditions}}}}
         :add-condition    {:button {:icon    :lupicon-circle-plus
                                     :i18nkey :pate-conditions.add
                                     :css     :positive
                                     :add     :conditions}}
         :statements       {:placeholder {:type :statements}}
         :neighbors        {:phrase-text {:i18nkey  :phrase.category.naapurit
                                          :category :naapurit}}
         :neighbor-states  {:placeholder {:type :neighbors}}
         :collateral       {:text {:after :eur}}
         :collateral-date  {:date {}}
         :collateral-type  collateral-type-select
         :appeal           {:phrase-text {:category :muutoksenhaku}}
         :complexity       complexity-select
         :complexity-text  {:phrase-text {:label?   false
                                          :category :vaativuus}}
         :extra-info       {:phrase-text {:category :yleinen}}
         :deviations       {:phrase-text {:category :yleinen}}
         :rights           {:phrase-text {:category :rakennusoikeus}}
         :purpose          {:phrase-text {:category :kaava}}
         :upload           {:attachments {:i18nkey    :application.verdict-attachments
                                          :label?     false
                                          :type-group #"paatoksenteko"
                                          :default    :paatoksenteko.paatosote
                                          :dropzone   "#application-pate-verdict-tab"
                                          :multiple?  true}}
         :attachments      {:application-attachments {:i18nkey :application.verdict-attachments}}})
       :sections
       [{:id   "pate-dates"
         :grid {:columns 7
                :rows    [[{:col   7
                            :dict  :language
                            :hide? :_meta.published?}]
                          [{:id   "verdict-date"
                            :dict :verdict-date}
                           {:id    "automatic-verdict-dates"
                            :col   2
                            :show? [:AND :_meta.editing?
                                    (cons :OR (map #(util/kw-path :? %) p-verdict-dates))]
                            :dict  :automatic-verdict-dates}]
                          {:id         "deltas"
                           :css        [:pate-date]
                           :loc-prefix :pate-verdict
                           :row        (map (fn [kw]
                                              (let [id (name kw)]
                                                {:show?     (util/kw-path :? kw)
                                                 :disabled? :automatic-verdict-dates
                                                 :id        id
                                                 :dict      kw}))
                                            p-verdict-dates)}]}}
        {:id   "pate-verdict"
         :grid {:columns 7
                :rows    [[{:col        2
                            :loc-prefix :pate-verdict.giver
                            :hide?      :*ref.boardname
                            :dict       :contact}
                           {:col        2
                            :loc-prefix :pate-verdict.giver
                            :hide?      :_meta.editing?
                            :show?      :*ref.boardname
                            :dict       :boardname}
                           {:col        1
                            :show?      [:OR :*ref.boardname :verdict-section]
                            :loc-prefix :pate-verdict.section
                            :dict       :verdict-section}
                           {:hide? :verdict-section}
                           {:col   2
                            :align :full
                            :dict  :verdict-code}]
                          [{:col   5
                            :id    "paatosteksti"
                            :show? :_meta.editing?
                            :dict  :verdict-text}
                           {:col   3
                            :hide? :_meta.editing?
                            :dict  :verdict-text-ref}
                           {:col   2
                            :id    "application-id"
                            :hide? :_meta.editing?
                            :dict  :application-id}]]}}

        {:id    "appeal"
         :show? [:OR :?.appeal :?.collateral]
         :grid  {:columns 7
                 :rows    [{:show? :?.appeal
                            :row   [{:col        6
                                     :loc-prefix :verdict.muutoksenhaku
                                     :dict       :appeal}]}
                           {:show?      :?.collateral
                            :loc-prefix :pate
                            :row        [{:col  2
                                          :id   :collateral-date
                                          :dict :collateral-date}
                                         {:col  2
                                          :id   :collateral
                                          :dict :collateral}
                                         {:col  2
                                          :dict :collateral-type}]}]}}
        {:id         "statements"
         :show?      :?.statements
         :loc-prefix :pate-statements
         :buttons?   false
         :grid       {:columns 1
                      :rows    [[{:dict :statements}]]}}
        {:id    "neighbors"
         :show? :?.neighbors
         :grid  {:columns 12
                 :rows    [[{:col  7
                             :id   "neighbor-notes"
                             :dict :neighbors}
                            {:col   4
                             :hide? :_meta.published?
                             :id    "neighbor-states"
                             :align :right
                             :dict  :neighbor-states}]
                           ]}}
        {:id         "complexity"
         :loc-prefix :pate.complexity
         :show?      [:OR :?.complexity :?.rights :?.purpose]
         :grid       {:columns 7
                      :rows    [{:show? :?.complexity
                                 :row   [{:col  3
                                          :dict :complexity}]}
                                {:show? :?.complexity
                                 :row   [{:col  6
                                          :id   "text"
                                          :dict :complexity-text}]}
                                {:show? :?.rights
                                 :row   [{:col        6
                                          :loc-prefix :pate-rights
                                          :dict       :rights}]}
                                {:show? :?.purpose
                                 :row   [{:col        6
                                          :loc-prefix :phrase.category.kaava
                                          :dict       :purpose}]}]}}
        {:id         "extra-info"
         :loc-prefix :pate-extra-info
         :show?      :?.extra-info
         :grid       {:columns 7
                      :rows    [[{:col  6
                                  :dict :extra-info}]]}}
        {:id         "deviations"
         :loc-prefix :pate-deviations
         :show?      :?.extra-info
         :grid       {:columns 7
                      :rows    [[{:col  6
                                  :dict :deviations}]]}}
        {:id   "attachments"
         :grid {:columns 7
                :rows    [[{:col  6
                            :dict :attachments}]]}}
        {:id       "upload"
         :hide?    :_meta.published?
         :css      :pate-section--no-border
         :buttons? false
         :grid     {:columns 7
                    :rows    [[{:col  6
                                :dict :upload}]]}}]}})

(sc/validate PateVerdict (:r verdict-schemas))

;; Other utils

(defn permit-type->category [permit-type]
  (when-let [kw (some-> permit-type
                        s/lower-case
                        keyword)]
    (cond
      (#{:r :p :ya} kw)              kw
      (#{:kt :mm} kw)                :kt
      (#{:yi :yl :ym :vvvl :mal} kw) :ymp)))

(defn dict-resolve
  "Path format: [repeating index repeating index ... value-dict].
 Repeating denotes :repeating schema, index is arbitrary repeating
  index (skipped during resolution) and value-dict is the final dict
  for the item schema.

  Returns map with :schema and :path keys. The path is
  the remaining path (e.g., [:delta] for pate-delta). Note: the
  result is empty map if the path resolves to the repeating schema.

  Returns nil when the resolution fails."
  [path dictionary]
  (loop [[x & xs]   (->> path
                         (remove nil?)
                         (map keyword))
         dictionary dictionary]
    (when dictionary
      (if x
        (when-let [schema (get dictionary x)]
          (if (:repeating schema)
            (recur (rest xs) (:repeating schema))
            {:schema schema :path xs}))
        {}))))

(defn repeating-subpath
  "Subpath that resolves to a repeating named repeating. Nil if not
  found. Note that the actual existence of the path within data is not
  checked."
  [repeating path dictionary]
  (loop [path path]
    (cond
      (empty? path)           nil
      (= (last path)
         repeating) (when (= (dict-resolve path dictionary)
                             {})
                      path)
      :else                   (recur (butlast path)))))

(defn markdown->html
  "Translates markdown string into HTML. Used both in the UI and
  PDF."
  [markdown]
  (-> markdown
      (markdown/md-to-html-string* nil)
      :html))
