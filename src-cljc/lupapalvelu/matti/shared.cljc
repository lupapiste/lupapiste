(ns lupapalvelu.matti.shared
  (:require [clojure.string :as s]
            [sade.shared-util :as util]
            [schema.core :refer [defschema] :as sc]))

(declare MattiList)

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
   :myonnetty-aloitusoikeudella "my\u00f6nnetty aloitusoikeudella "
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

(defschema MattiEnabled
  "Component is enabled/disabled if the path value is truthy. Empty
  strings/collections are interpreted as falsey. Default: enabled.
  Paths are either joined kw-path or vector of joined kw-paths. In
  other words, each vector item denotes full path. If both are given,
  both are taken into account. On conflicts, component is disabled.
  Paths starting with :_meta are interpreted as _meta queries."
  {(sc/optional-key :enabled?)  path-type   ;; True if ANY truthy.
   (sc/optional-key :disabled?) path-type}) ;; True if ANY truthy

(defschema MattiVisible
  "Similar to MattiEnabled."
  {(sc/optional-key :show?) path-type   ;; True if ANY truthy.
   (sc/optional-key :hide?) path-type}) ;; True if ANY truthy.

(defschema MattiBase
  (merge MattiVisible
         MattiEnabled
         {(sc/optional-key :css)        [sc/Keyword]
          ;; If an schema ancestor has :loc-prefix then localization
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
         {:type (sc/enum :neighbors)}))

(defschema KeyMap
  "Map with the restricted set of keys. In other words, no new keys
  after the instantiation can be added. No UI counterpart."
  {sc/Keyword sc/Any})

(def schema-type-alternatives
  {:docgen         (sc/conditional
                    :name MattiDocgen
                    :else sc/Str)
   :reference-list MattiReferenceList
   :phrase-text    MattiPhraseText
   :loc-text       sc/Keyword ;; Localisation term shown as text.
   :date-delta     MattiDateDelta
   :multi-select   MattiMultiSelect
   :reference      MattiReference
   :placeholder    MattiPlaceholder
   :keymap         KeyMap})

(defn make-conditional [m]
  (->> (reduce (fn [a [k v]]
                 (concat a [k (hash-map k v)]))
               []
               m)
       (apply sc/conditional)))

(defschema Dictionary
  "Id to schema mapping."
  {:dictionary {sc/Keyword (make-conditional schema-type-alternatives)}})

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
         {:list {(sc/optional-key :title) sc/Str
                 :items                   [MattiItem]}}))

(defschema MattiItemCell
  (merge CellConfig
         MattiItem))

(defschema MattiCell
  (sc/conditional :list MattiList
                  :else MattiItemCell))

(defschema MattiGrid
  (merge MattiBase
         {:columns (apply sc/enum (range 1 13)) ;; Grid size (.matti-grid-n)
          :rows    [(sc/conditional
                     :row (merge MattiVisible
                                 {(sc/optional-key :id)         sc/Str
                                  ;; The same semantics as in MattiBase.
                                  (sc/optional-key :loc-prefix) sc/Keyword
                                  (sc/optional-key :css)        [sc/Keyword]
                                  :row                          [MattiCell]})
                     :else [MattiCell])]}))

(defschema MattiSection
  (merge MattiBase
         {:id   sc/Str ;; Also title localization key
          :grid MattiGrid}))

#_(defschema MattiVerdictSection
  (merge MattiSection
         {;; Section removed from the template. Note: the data is not cleared.
          (sc/optional-key :removed) sc/Bool}))

(defschema MattiVerdict
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

(def default-verdict-template
  {:dictionary {;; Verdict section
                :verdict-dates   {:loc-text :matti-verdict-dates}
                :julkipano       {:date-delta {:unit :days}}
                :anto            {:date-delta {:unit :days}}
                :valitus         {:date-delta {:unit :days}}
                :lainvoimainen   {:date-delta {:unit :days}}
                :aloitettava     {:date-delta {:unit :years}}
                :voimassa        {:date-delta {:unit :years}}
                :giver           {:docgen "matti-verdict-giver"}
                :verdict-code    {:reference-list {:path       :settings.verdict-code
                                                   :type       :select
                                                   :loc-prefix :matti-r}}
                :paatosteksti    {:phrase-text {:category :paatosteksti}}
                ;; The following keys are whole sections
                :foremen         (reference-list :settings.foremen {:item-loc-prefix :matti-r.foremen})
                :plans           (reference-list :settings.plans {:term {:path       [:plans]
                                                                         :extra-path [:name]
                                                                         :match-key  :id}})
                :reviews         (reference-list :settings.reviews {:term {:path       [:reviews]
                                                                           :extra-path [:name]
                                                                           :match-key  :id}})
                :conditions      {:phrase-text {:category :lupaehdot}}
                :neighbors       {:loc-text :matti-neighbors.text}

                :appeal          {:phrase-text {:category :muutoksenhaku
                                                :i18nkey  :phrase.category.muutoksenhaku}}
                :collateral      {:loc-text :matti-collateral.text}
                ;; Complexity section
                :complexity      {:docgen "matti-complexity"}
                :complexity-text {:phrase-text {:label?   false
                                                :category :vaativuus}}
                ;; Text sections
                :rights          {:loc-text :matti-rights.text}
                :purpose         {:loc-text :matti-purpose.text}
                :statements      {:loc-text :matti-statements.text}
                ;; Buildings section
                :autopaikat      {:docgen "matti-verdict-check"}
                :vss-luokka      {:docgen "matti-verdict-check"}
                :paloluokka      {:docgen "matti-verdict-check"}
                ;; Removable sections
                :removed-sections {:keymap (zipmap [:foremen :reviews :plans
                                                    :conditions :neighbors
                                                    :appeal :statements :collateral
                                                    :complexity :rights :purpose
                                                    :buildings]
                                                   (repeat false))}}
   :sections [{:id    "verdict"
               :loc-prefix :matti-verdict
               :grid  {:columns 12
                       :rows    [{:css [:row--date-delta-title]
                                  :row [{:col  12
                                         :css  [:matti-label]
                                         :dict :verdict-dates}]}
                                 (date-delta-row [:julkipano :anto
                                                  :valitus :lainvoimainen
                                                  :aloitettava :voimassa])
                                 [{:col 3
                                   :id :giver
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
              {:id    "appeal"
               :loc-prefix :matti-appeal
               :grid  {:columns 1
                       :rows    [[{:dict :appeal }]]}}
              (text-section :collateral)
              {:id    "complexity"
               :loc-prefix :matti.complexity
               :grid  {:columns 1
                       :rows    [[{:dict       :complexity }]
                                 [{:id   "text"
                                   :dict :complexity-text}]]}}
              (text-section :rights)
              (text-section :purpose)
              (text-section :statements)
              {:id    "buildings"
               :loc-prefix :matti-buildings
               :grid  {:columns 1
                       :rows    [[{:loc-prefix :matti-buildings.info
                                   :list       {:title "matti-buildings.info"
                                                :items (mapv (fn [check]
                                                               {:dict check
                                                                :id check
                                                                :css  [:matti-condition-box]})
                                                             [:autopaikat :vss-luokka :paloluokka])}}]]}}]})

(sc/validate MattiVerdict default-verdict-template)

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

;; It is advisable to reuse ids from template when possible. This
;; makes localization work automatically.
(def verdict-schemas
  {:r
   {:sections
    [{:id   "matti-dates"
      :grid {:columns 7
             :rows    [[{:id     "verdict-date"
                         :schema {:docgen "matti-date"}}
                        {:id     "automatic-verdict-dates"
                         :col    2
                         :show?  :_meta.editing?
                         :schema {:docgen {:name "matti-verdict-check"}}}]
                       {:id         "deltas"
                        :css        [:matti-date]
                        :loc-prefix :matti-verdict
                        :row        (map (fn [kw]
                                           (let [id (name kw)]
                                             {:show?  (keyword (str "matti-dates.deltas."
                                                                    id))
                                              :id     id
                                              :schema {:docgen {:name      "matti-date"
                                                                :disabled? :matti-dates.0.automatic-verdict-dates}}}))
                                         [:julkipano :anto :valitus
                                          :lainvoimainen :aloitettava :voimassa])}]}}
     {:id   "matti-verdict"
      :grid {:columns 6
             :rows    [[{:loc-prefix :matti-verdict.giver
                         :hide?      :_meta.editing?
                         :schema     {:reference {:path :matti-verdict.0.giver.contact}}}
                        {:id     "giver"
                         :col    2
                         :show?  :_meta.editing?
                         :schema {:list {:title "matti-verdict.giver"
                                         :items [{:id     "giver"
                                                  :schema {:docgen "matti-verdict-giver"}}
                                                 {:id     "contact"
                                                  :show?  :_meta.editing?
                                                  :schema {:docgen "matti-verdict-contact"}}]}}}
                        {:id     "section"
                         :schema {:docgen "matti-verdict-text"}}
                        {:hide? :_meta.editing?}
                        {:col    2
                         :id     "verdict-code"
                         :align  :full
                         :schema {:reference-list {:path       :verdict
                                                   :type       :select
                                                   :loc-prefix :matti-r}} }]
                       [{:col    5
                         :id     "paatosteksti"
                         :show?  :_meta.editing?
                         :schema {:phrase-text {:category :paatosteksti}}}
                        {:col    3
                         :hide?  :_meta.editing?
                         :schema {:reference {:path :matti-verdict.1.paatosteksti}}}
                        {:col    2
                         :id     "application-id"
                         :hide?  :_meta.editing?
                         :schema {:docgen "matti-verdict-id"}}]]}}
     {:id   "requirements"
      :grid {:columns 7
             :rows    (concat (map-indexed (fn [i [loc-prefix path term? separator?]]
                                             (let [check-path (keyword (str "requirements." i ".included"))]
                                               {:show? [:_meta.editing? check-path]
                                                :row   [{:col    4
                                                         :schema {:reference-list
                                                                  (merge {:enabled?   check-path
                                                                          :loc-prefix loc-prefix
                                                                          :path       path
                                                                          :type       :multi-select}
                                                                         (when term?
                                                                           {:item-key :id
                                                                            :term     {:path       path
                                                                                       :extra-path :name
                                                                                       :match-key  :id}})
                                                                         (when separator?
                                                                           {:separator " \u2013 "}))}}
                                                        {:col    2
                                                         :align  :right
                                                         :show?  :_meta.editing?
                                                         :id     "included"
                                                         :schema {:docgen "required-in-verdict"}}]}))
                                           [[:matti-r.foremen :foremen false false]
                                            [:matti-plans :plans true false]
                                            [:matti-reviews :reviews true true]])
                              [[{:col    6
                                 :id     "other"
                                 :align  :full
                                 :schema {:phrase-text {:i18nkey  :phrase.category.lupaehdot
                                                        :category :lupaehdot}}}]])}}
     {:id "neighbors"
      :grid {:columns 12
             :rows [[{:col 7
                      :id "neighbor-notes"
                      :schema {:phrase-text {:i18nkey :phrase.category.naapurit
                                             :category :naapurit}}}
                     {}
                     {:col 3
                      :id "neighbor-states"
                      :align :right
                      :schema {:placeholder {:type :neighbors}}}]
                    ]}}
     {:id   "matti-complexity"
      :grid {:columns 6
             :rows    [[{:col    5
                         :schema {:docgen "matti-complexity"}}]
                       [{:col    5
                         :id     "text"
                         :schema {:phrase-text {:label?   false
                                                :category :vaativuus}}}]]}}]}})

#_(sc/validate MattiVerdict (:r verdict-schemas))

;; Schema utils

(defn child-schema
  ([options child-key parent]
   (assoc-in options [child-key :_parent] parent))
  ([child parent]
   (assoc child :_parent parent)))

(defn parent-value [schema kw]
  (let [v (kw schema)]
    (if (or (nil? schema) v)
      v
      (parent-value (:_parent schema) kw))))

(defn cell-path
  "Path (vector of strings) for the cell with an explicit (non-index)
  cell-id. Returns the first match so it is advisable to make sure
  that ids are unique. Schema must have sections."
  [{sections :sections} cell-id]
  (letfn [(find-in-row [row-path row]
            (when-let [cell (util/find-by-id cell-id (get row :row row))]
              (conj row-path cell-id)))
          (find-in-section [{:keys [id grid]}]
            (loop [i 0
                   [x & xs] (:rows grid)]
              (if-let [path (find-in-row  [id (get x :id (str i))]
                                         x)]
                path
                (when (seq xs)
                  (recur (inc i) xs)))))]
    (some find-in-section sections)))

;; Other utils

(defn permit-type->category [permit-type]
  (when-let [kw (some-> permit-type
                        s/lower-case
                        keyword)]
    (cond
      (#{:r :p :ya} kw)              kw
      (#{:kt :mm} kw)                :kt
      (#{:yi :yl :ym :vvvl :mal} kw) :ymp)))
