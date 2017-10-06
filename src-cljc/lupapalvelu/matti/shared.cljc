(ns lupapalvelu.matti.shared
  (:require [clojure.string :as s]
            [sade.shared-util :as util]
            [schema.core :refer [defschema] :as sc]))

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

(def foreman-codes [:vastaava-tj :vv-tj :iv-tj :erityis-tj])

;; Phrases

(def phrase-categories #{:paatosteksti :lupaehdot :naapurit
                         :muutoksenhaku :vakuus :vaativuus
                         :rakennusoikeus :kaava})

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


(defschema MattiEnabled
  "Component state (enabled/disabled) as defined by paths. Empty
  strings/collections are interpreted as falsey. Default: enabled.  On
  conflicts, component is disabled. Some path prefixes are handled specially:

   :_meta denotes that (the rest of) the path is interpreted as _meta
   query (see path/react-meta).

  :? True if the path is found within the state (regardless of its
  value)

  Note: :_meta.enabled? is always used as prerequisite."
  {(sc/optional-key :enabled?)  condition-type
   (sc/optional-key :disabled?) condition-type})

(defschema MattiVisible
  "Similar to MattiEnabled, but without any implicit prerequisite
  condition. Default is visible."
  {(sc/optional-key :show?)
  condition-type
   (sc/optional-key :hide?) condition-type})

(defschema MattiCss
  {(sc/optional-key :css) (sc/conditional
                           keyword? sc/Keyword
                           :else    [sc/Keyword])})

(defschema MattiBase
  (merge MattiVisible
         MattiEnabled
         MattiCss
         {;; If an schema ancestor has :loc-prefix then localization
          ;; term is loc-prefix + id-path, where id-path from
          ;; loc-prefix schema element util current.
          (sc/optional-key :loc-prefix) keyword-or-string
          ;; Absolute localisation terms. Overrides loc-prefix, does not
          ;; affect children.
          (sc/optional-key :i18nkey)    keyword-or-string}))

(defschema MattiComponent
  (merge MattiBase
         {(sc/optional-key :label?) sc/Bool})) ;; Show label? Default true

(defschema MattiReferenceList
  "Component that builds schema from an external source. Each item is
  the id property of the target value or the value itself."
  (merge MattiComponent
         ;; Path is interpreted by the implementation. In Matti the
         ;; path typically refers to the settings.
         {:path                              path-type
          ;; In addition to UI, type also affects validation: :select
          ;; only accepts single values.
          :type                              (sc/enum :select :multi-select)
          ;; By default, an item value is the same as
          ;; source. If :item-key is given, then the corresponding
          ;; source property is used.
          (sc/optional-key :item-key)        sc/Keyword
          ;; Term-path overrides item-loc-prefix. However,
          ;; item-loc-prefix supports item-key.
          (sc/optional-key :item-loc-prefix) sc/Keyword
          ;; Separator string between items when viewed. Default ", "
          (sc/optional-key :separator)       sc/Str
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

(defschema MattiPhraseText
  "Textarea with integrated phrase support."
  (merge MattiComponent
         {;; Default category.
          :category (apply sc/enum phrase-categories)
          (sc/optional-key :text) sc/Str}))

(defschema MattiMultiSelect
  (merge MattiComponent
         ;; Sometimes it is useful to finetune item localisations
         ;; separately from the label.
         {(sc/optional-key :item-loc-prefix) sc/Keyword
          :items                             [(sc/conditional
                                               :text  {:value keyword-or-string
                                                       :text  sc/Str}
                                               :else keyword-or-string)]}))

(defschema MattiDateDelta
  (merge MattiComponent
         {(sc/optional-key :enabled) sc/Bool
          (sc/optional-key :delta)   (sc/constrained sc/Int (comp not neg?))
          :unit                      (sc/enum :days :years)}))

(defschema MattiReference
  "Displays the referenced value."
  (merge MattiComponent
         {:path path-type}))

(defschema MattiDocgen
  "Additional properties for old school docgen definitions. Shortcut
  is to use just the schema name (see below)."
  (merge MattiEnabled
         {:name sc/Str}))

(defschema MattiPlaceholder
  "Placholder for external (filled by backend) data."
  (merge MattiComponent
         {:type (sc/enum :neighbors :application-id :building)}))

(defschema KeyMap
  "Map with the restricted set of keys. In other words, no new keys
  after the instantiation can be added. No UI counterpart."
  {sc/Keyword sc/Any})

(defschema MattiAttachments
  (merge MattiComponent
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

(defschema SchemaTypes
  {sc/Keyword (sc/conditional
               :docgen         {:docgen (sc/conditional
                                         :name MattiDocgen
                                         :else sc/Str)}
               :reference-list {:reference-list MattiReferenceList}
               :phrase-text    {:phrase-text MattiPhraseText}
               :loc-text       {:loc-text sc/Keyword} ;; Localisation term shown as text.
               :date-delta     {:date-delta MattiDateDelta}
               :multi-select   {:multi-select MattiMultiSelect}
               :reference      {:reference MattiReference}
               :placeholder    {:placeholder MattiPlaceholder}
               :keymap         {:keymap KeyMap}
               :attachments    {:attachments MattiAttachments}
               :repeating      {:repeating (sc/recursive #'SchemaTypes)})})

(defschema Dictionary
  "Id to schema mapping."
  {:dictionary SchemaTypes})

(defschema MattiMeta
  "Dynamic management. No UI counterpart. Not part of the saved data."
  {(sc/optional-key :_meta) {sc/Keyword sc/Any}})

(defschema MattiItem
  (merge MattiBase
         {;; Id is used as a path part for _meta queries.
          (sc/optional-key :id)   keyword-or-string
          ;; Value is a dictionary key
          (sc/optional-key :dict) sc/Keyword}))

(defschema CellConfig
  {(sc/optional-key :col)   sc/Int ;; Column width (.col-n). Default 1.
   (sc/optional-key :align) (sc/enum :left :right :center :full)})

(defschema MattiList
  (merge MattiBase
         CellConfig
         {:list (merge MattiCss
                       {(sc/optional-key :title) sc/Str
                        :items                   [MattiItem]})}))

(defschema MattiItemCell
  (merge CellConfig
         MattiItem))

(declare NestedGrid)

(defschema MattiCell
  (sc/conditional :list MattiList
                  :grid (sc/recursive #'NestedGrid)
                  :else MattiItemCell))

(defschema MattiGrid
  (merge MattiBase
         {:columns (apply sc/enum (range 1 13)) ;; Grid size (.matti-grid-n)
          :rows    [(sc/conditional
                     :row (merge MattiVisible
                                 MattiCss
                                 {(sc/optional-key :id)         sc/Str
                                  ;; The same semantics as in MattiBase.
                                  (sc/optional-key :loc-prefix) sc/Keyword
                                  :row                          [MattiCell]})
                     :else [MattiCell])]}))


(defschema NestedGrid
  (merge MattiBase
         CellConfig
         {:grid (assoc MattiGrid
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





(defschema MattiSection
  (merge MattiBase
         {:id   keyword-or-string ;; Also title localization key
          :grid MattiGrid}))

(defschema MattiVerdictTemplate
  (merge Dictionary
         MattiMeta
         {(sc/optional-key :id)       sc/Str
          (sc/optional-key :modified) sc/Int
          (sc/optional-key :name)     sc/Str ;; Non-localized raw string
          :sections                   [MattiSection]}))

(defn reference-list [path extra]
  {:reference-list (merge {:label? false
                           :type   :multi-select
                           :path   path}
                          extra)})

(defn  multi-section [id]
  {:loc-prefix (str "matti-" (name id))
   :id         (name id)
   :grid       {:columns 1
                :rows    [[{:dict id }]]}})

(defn text-section [id]
  {:loc-prefix (str "matti-" (name id))
   :id         (name id)
   :grid       {:columns 1
                :rows    [[{:dict id}]]}})

(defn date-delta-row [kws]
  (map (fn [kw]
         {:col 2 :dict kw :id (name kw)})
       kws))


;; TODO: access via category
(def default-verdict-template
  {:dictionary {;; Verdict section
                :verdict-dates {:loc-text :matti-verdict-dates}
                :julkipano     {:date-delta {:unit :days}}
                :anto          {:date-delta {:unit :days}}
                :valitus       {:date-delta {:unit :days}}
                :lainvoimainen {:date-delta {:unit :days}}
                :aloitettava   {:date-delta {:unit :years}}
                :voimassa      {:date-delta {:unit :years}}
                :giver         {:docgen "matti-verdict-giver"}
                :verdict-code  {:reference-list {:path       :settings.verdict-code
                                                 :type       :select
                                                 :loc-prefix :matti-r.verdict-code}}
                :paatosteksti  {:phrase-text {:category :paatosteksti}}
                ;; The following keys are whole sections
                :foremen       (reference-list :settings.foremen {:item-loc-prefix :matti-r.foremen})
                :plans         (reference-list :settings.plans {:term {:path       [:plans]
                                                                       :extra-path [:name]
                                                                       :match-key  :id}})
                :reviews       (reference-list :settings.reviews {:term {:path       [:reviews]
                                                                         :extra-path [:name]
                                                                         :match-key  :id}})
                :conditions    {:phrase-text {:category :lupaehdot}}
                :neighbors     {:loc-text :matti-neighbors.text}

                :appeal           {:phrase-text {:category :muutoksenhaku
                                                 :i18nkey  :phrase.category.muutoksenhaku}}
                :collateral       {:loc-text :matti-collateral.text}
                ;; Complexity section
                :complexity       {:docgen "matti-complexity"}
                :complexity-text  {:phrase-text {:label?   false
                                                 :category :vaativuus}}
                ;; Text sections
                :rights           {:loc-text :matti-rights.text}
                :purpose          {:loc-text :matti-purpose.text}
                :statements       {:loc-text :matti-statements.text}
                ;; Buildings section
                :autopaikat       {:docgen "matti-verdict-check"}
                :vss-luokka       {:docgen "matti-verdict-check"}
                :paloluokka       {:docgen "matti-verdict-check"}
                ;; Removable sections
                :removed-sections {:keymap (zipmap [:foremen :reviews :plans
                                                    :conditions :neighbors
                                                    :appeal :statements :collateral
                                                    :complexity :rights :purpose
                                                    :buildings]
                                                   (repeat false))}}
   :sections [{:id         "verdict"
               :loc-prefix :matti-verdict
               :grid       {:columns 12
                            :rows    [{:css [:row--date-delta-title]
                                       :row [{:col  12
                                              :css  [:matti-label]
                                              :dict :verdict-dates}]}
                                      (date-delta-row [:julkipano :anto
                                                       :valitus :lainvoimainen
                                                       :aloitettava :voimassa])
                                      [{:col  3
                                        :id   :giver
                                        :dict :giver}
                                       {:align :full
                                        :col   3
                                        :dict  :verdict-code}]
                                      [{:col  12
                                        :dict :paatosteksti}]]}}
              (multi-section :foremen)
              (multi-section :reviews)
              (multi-section :plans)
              {:id         "conditions"
               :loc-prefix :phrase.category.lupaehdot
               :grid       {:columns 1
                            :rows    [[{:dict :conditions}]]}}
              (text-section :neighbors)
              {:id         "appeal"
               :loc-prefix :matti-appeal
               :grid       {:columns 1
                            :rows    [[{:dict :appeal }]]}}
              (text-section :collateral)
              {:id         "complexity"
               :loc-prefix :matti.complexity
               :grid       {:columns 1
                            :rows    [[{:dict :complexity }]
                                      [{:id   "text"
                                        :dict :complexity-text}]]}}
              (text-section :rights)
              (text-section :purpose)
              (text-section :statements)
              {:id         "buildings"
               :loc-prefix :matti-buildings
               :grid       {:columns 1
                            :rows    [[{:loc-prefix :matti-buildings.info
                                        :list       {:title "matti-buildings.info"
                                                     :items (mapv (fn [check]
                                                                    {:dict check
                                                                     :id   check
                                                                     :css  [:matti-condition-box]})
                                                                  [:autopaikat :vss-luokka :paloluokka])}}]]}}]})

(sc/validate MattiVerdictTemplate default-verdict-template)

(defschema MattiSettings
  (merge Dictionary
         {:title    sc/Str
          :sections [MattiSection]}))

(def r-settings
  {:title      "matti-r"
   :dictionary {:verdict-code {:multi-select {:label?     false
                                              :items      (keys verdict-code-map)}}
                :foremen      {:multi-select {:label?     false
                                              :items      foreman-codes}}
                :plans        {:reference-list {:label?   false
                                                :path     [:plans]
                                                :item-key :id
                                                :type     :multi-select
                                                :term     {:path       :plans
                                                           :extra-path :name}}}
                :reviews      {:reference-list {:label?   false
                                                :path     [:reviews]
                                                :item-key :id
                                                :type     :multi-select
                                                :term     {:path       :reviews
                                                           :extra-path :name}}}}
   :sections   [{:id    "verdict"
                 :loc-prefix :matti-settings.verdict
                 :grid  {:columns 1
                         :loc-prefix :matti-r.verdict-code
                         :rows    [[{:dict :verdict-code}]]}}
                {:id    "foremen"
                 :loc-prefix :matti-settings.foremen
                 :grid  {:columns 1
                         :loc-prefix :matti-r.foremen
                         :rows    [[{:dict :foremen}]]}}
                {:id    "plans"
                 :loc-prefix :matti-settings.plans
                 :grid  {:columns 1
                         :rows    [[{:dict :plans}]]}}
                {:id    "reviews"
                 :loc-prefix :matti-settings.reviews
                 :grid  {:columns 1
                         :rows    [[{:dict :reviews}]]}}]})

(def settings-schemas
  {:r r-settings})

(sc/validate MattiSettings r-settings)


(defschema MattiVerdictSection
  (merge MattiBase
         {:id   keyword-or-string ;; Also title localization key
          (sc/optional-key :buttons?) sc/Bool ;; Show edit button? (default true)
          :grid MattiGrid}))

(defschema MattiVerdict
  (merge Dictionary
         MattiMeta
         {(sc/optional-key :id)       sc/Str
          (sc/optional-key :modified) sc/Int
          :sections                   [MattiVerdictSection]}))


;; It is advisable to reuse ids from template when possible. This
;; makes localization work automatically.
(def verdict-schemas
  {:r
   {:dictionary
    (merge
     {:verdict-date            {:docgen "matti-date"}
      :automatic-verdict-dates {:docgen {:name "matti-verdict-check"}}}
     (->> [:julkipano :anto :valitus :lainvoimainen :aloitettava :voimassa]
          (map (fn [kw]
                 [kw {:docgen {:name      "matti-date"
                               :disabled? :automatic-verdict-dates}}]))
          (into {}))
     {:contact-ref      {:reference {:path :contact}}
      :giver            {:docgen "matti-verdict-giver"}
      :contact          {:docgen "matti-verdict-contact"}
      :verdict-section  {:docgen "matti-verdict-section"}
      :verdict-code     {:reference-list {:path       :verdict-code
                                          :type       :select
                                          :loc-prefix :matti-r.verdict-code}}
      :verdict-text     {:phrase-text {:category :paatosteksti}}
      :verdict-text-ref {:reference {:path :verdict-text}}
      :application-id   {:placeholder {:type :application-id}}}
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
                        included
                        {:docgen "required-in-verdict"})))
             {}
             [[:matti-r.foremen :foremen false false]
              [:matti-plans :plans true false]
              [:matti-reviews :reviews true true]])
     {:conditions      {:phrase-text {:i18nkey  :phrase.category.lupaehdot
                                      :category :lupaehdot}}
      :neighbors       {:phrase-text {:i18nkey  :phrase.category.naapurit
                                      :category :naapurit}}
      :neighbor-states {:placeholder {:type :neighbors}}
      :collateral      {:phrase-text {:category :vakuus}}
      :appeal          {:phrase-text {:category :muutoksenhaku}}
      :complexity      {:docgen "matti-complexity"}
      :complexity-text {:phrase-text {:label?   false
                                      :category :vaativuus}}
      :rights          {:phrase-text {:category :rakennusoikeus}}
      :purpose         {:phrase-text {:category :kaava}}
      :buildings       {:repeating {:building-name          {:placeholder {:label? false
                                                                           :type   :building}}
                                    :rakennetut-autopaikat  {:docgen "matti-string"}
                                    :kiinteiston-autopaikat {:docgen "matti-string"}
                                    :autopaikat-yhteensa    {:docgen "matti-string"}
                                    :vss-luokka             {:docgen "matti-string"}
                                    :paloluokka             {:docgen "matti-string"}
                                    :show-building          {:docgen "required-in-verdict"}}}
      :attachments     {:attachments {:i18nkey    :application.verdict-attachments
                                      :type-group #"paatoksenteko"
                                      :default    :paatoksenteko.paatosote
                                      :dropzone   "#application-matti-verdict-tab"
                                      :multiple?  true}}})
    :sections
    [{:id   "matti-dates"
      :grid {:columns 7
             :rows    [[{:id   "verdict-date"
                         :dict :verdict-date}
                        {:id    "automatic-verdict-dates"
                         :col   2
                         :show? :_meta.editing?
                         :dict  :automatic-verdict-dates}]
                       {:id         "deltas"
                        :css        [:matti-date]
                        :loc-prefix :matti-verdict
                        :row        (map (fn [kw]
                                           (let [id (name kw)]
                                             {:show?     (util/kw-path :? kw)
                                              :disabled? :automatic-verdict-dates
                                              :id        id
                                              :dict      kw}))
                                         [:julkipano :anto :valitus
                                          :lainvoimainen :aloitettava :voimassa])}]}}
     {:id   "matti-verdict"
      :grid {:columns 6
             :rows    [[{:loc-prefix :matti-verdict.giver
                         :hide?      :_meta.editing?
                         :dict       :contact-ref}
                        {:col   3
                         :show? :_meta.editing?
                         :list  {:items [{:id   :giver
                                          :dict :giver}
                                         {:id    :contact
                                          :show? :_meta.editing?
                                          :dict  :contact}]}}
                        {:col   2
                         :hide? :_meta.editing?}
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
     {:id   "requirements"
      :grid {:columns 7
             :rows    (concat (map (fn [dict]
                                     (let [check-path (keyword (str (name dict) "-included"))]
                                       {:show? [:OR :_meta.editing? check-path]
                                        :row   [{:col  4
                                                 :dict dict}
                                                {:col   2
                                                 :align :right
                                                 :show? :_meta.editing?
                                                 :id    "included"
                                                 :dict  check-path}]}))
                                   [:foremen :plans :reviews])
                              [{:show? :?.conditions
                                :row   [{:col   6
                                         :id    "other"
                                         :align :full
                                         :dict  :conditions}]}])}}
     {:id    "appeal"
      :show? [:OR :?.appeal :?.collateral]
      :grid  {:columns 7
              :rows    [{:show? :?.appeal
                         :row   [{:col        6
                                  :loc-prefix :verdict.muutoksenhaku
                                  :dict       :appeal}]}
                        {:show? :?.collateral
                         :row   [{:col        6
                                  :loc-prefix :matti-collateral
                                  :dict       :collateral}]}]}}
     {:id    "neighbors"
      :show? :?.neighbors
      :grid  {:columns 12
              :rows    [[{:col  7
                          :id   "neighbor-notes"
                          :dict :neighbors}
                         {:col   4
                          :id    "neighbor-states"
                          :align :right
                          :dict  :neighbor-states}]
                        ]}}
     {:id         "complexity"
      :loc-prefix :matti.complexity
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
                                       :loc-prefix :matti-rights
                                       :dict       :rights}]}
                             {:show? :?.purpose
                              :row   [{:col        6
                                       :loc-prefix :phrase.category.kaava
                                       :dict       :purpose}]}]}}
     {:id    "buildings"
      :show? :?.buildings
      :grid  {:columns 7
              :rows    [[{:col  7
                          :grid {:columns    6
                                 :loc-prefix :matti-buildings.info
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
     {:id       "attachments"
      :buttons? false
      :grid     {:columns 7
                 :rows    [[{:col   6
                             :dict  :attachments}]]}}]}})

(sc/validate MattiVerdict (:r verdict-schemas))

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
  the remaining path (e.g., [:delta] for matti-delta). Note: the
  result is empty map if the path resolves to the repeating schema."
  [path dictionary]
  (loop [[x & xs]   (->> path
                         (remove nil?)
                         (map keyword))
         dictionary dictionary]
    (if-let [schema (get dictionary x)]
      (if (:repeating schema)
        (recur (rest xs) (:repeating schema))
        {:schema schema :path xs})
      {})))
