(ns lupapalvelu.matti.shared
  (:require [clojure.string :as s]
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

(def meta-flags (zipmap (map sc/optional-key
                             [:can-edit?
                              :editing?
                              :can-remove?])
                        (repeat sc/Bool)))

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
(defschema MattiBase
  {(sc/optional-key :_meta)      meta-flags
   (sc/optional-key :css)        [sc/Keyword]
   ;; If an schema ancestor has :loc-prefix then localization term is
   ;; loc-prefix + last, where last is the last path part.
   (sc/optional-key :loc-prefix) sc/Keyword
   ;; Absolute localisation terms. Overrides loc-prefix, does not
   ;; affect children. When the vector has more than one items, the
   ;; earlier localisations are arguments to the latter.
   (sc/optional-key :i18nkey)    [sc/Keyword]})

(defschema MattiComponent
  (merge MattiBase
         {(sc/optional-key :label?) sc/Bool})) ;; Show label? Default true

(defschema MattiReferenceList
  "Component that builds schema from an external source. Each item is
  the id property of the target value or the the value itself."
  (merge MattiComponent
         ;; Path is interpreted by the implementation. In Matti the
         ;; path typically refers to the settings.
         {:path                              [sc/Keyword]
          :type                              (sc/enum :select :multi-select)
          ;; By default, an item value is the same as
          ;; source. If :item-key is given, then the corresponding
          ;; source property is used.
          (sc/optional-key :item-key)        sc/Keyword
          ;; Term-path overrides item-loc-prefix. However,
          ;; item-loc-prefix supports item-key.
          (sc/optional-key :item-loc-prefix) sc/Keyword
          (sc/optional-key :id)              sc/Str
          ;; Term definition resolves the localization for the value.
          (sc/optional-key :term)
          {;; The path contains sources with corresponding fi, sv and
           ;; en localisations (if not extra-path given). The matching
           ;; is done by :item-key
           :path                         [sc/Keyword]
           ;; Additional path within matched term that contains the
           ;; lang properties.
           (sc/optional-key :extra-path) [sc/Keyword]
           ;; Key for the source property that should match the
           ;; value. For example, the value list might be just ids and
           ;; the match-key could by :id. Default value is the same as
           ;; item-key.
           (sc/optional-key :match-key)  sc/Keyword}}))

(defschema MattiPhraseText
  "Textarea with integrated phrase support."
  (merge MattiComponent
         {;; Default category.
          :category (apply sc/enum phrase-categories)
          (sc/optional-key :text) sc/Str}))

(def keyword-or-string (sc/conditional
                        keyword? sc/Keyword
                        :else    sc/Str))

(defschema MattiMultiSelect
  (merge MattiComponent
         ;; Sometimes it is useful to finetune item localisations
         ;; separately from the label.
         {(sc/optional-key :item-loc-prefix) sc/Keyword
          :items           [(sc/conditional
                             :text  {:value sc/Str
                                     :text  sc/Str}
                             :else keyword-or-string)]}))

(defschema MattiDateDelta
  (merge MattiComponent
         {(sc/optional-key :enabled) sc/Bool
          (sc/optional-key :delta)   (sc/constrained sc/Int (comp not neg?))
          :unit                      (sc/enum :days :years)}))

(def schema-type-alternatives
  {:docgen         sc/Str
   :list           (sc/recursive #'MattiList)
   :reference-list MattiReferenceList
   :phrase-text    MattiPhraseText
   :loc-text       sc/Keyword ;; Localisation term shown as text.
   :date-delta     MattiDateDelta
   :multi-select   MattiMultiSelect})

(defn make-conditional [m]
  (->> (reduce (fn [a [k v]]
                 (concat a [k (hash-map k v)]))
               []
               m)
       (apply sc/conditional)))

(defschema MattiItem
  (merge MattiBase
         {;; Id is used as a path part within the state. Thus, it is
          ;; mandatory if an explicit resolution is needed.
          (sc/optional-key :id)     sc/Str
          (sc/optional-key :schema) (make-conditional schema-type-alternatives)}))

(defschema MattiCell
  (merge MattiItem
         {(sc/optional-key :col) sc/Int ;; Column width (.col-n). Default 1.
          (sc/optional-key :align)  (sc/enum :left :right :center :full)}))

(defschema MattiList
  (merge MattiBase
         {(sc/optional-key :title) sc/Str
          :items                [MattiItem]}))

(defschema MattiGrid
  (merge MattiBase
         {:columns (apply sc/enum (range 1 13)) ;; Grid size (.matti-grid-n)
          :rows    [(sc/conditional
                     :row {(sc/optional-key :id)  sc/Str
                           (sc/optional-key :css) [sc/Keyword]
                           :row                   [MattiCell]}
                     :else [MattiCell])]}))

(defschema MattiSection
  (merge MattiBase
         {:id   sc/Str ;; Also title localization key
          :grid MattiGrid}))

(defschema MattiVerdictSection
  (merge MattiSection
         {;; Section removed from the template. Note: the data is not cleared.
          (sc/optional-key :removed) sc/Bool
          (sc/optional-key :pdf)     sc/Bool ;; Section included in the verdict pdf.
          }))

(defschema MattiVerdict
  {(sc/optional-key :id)       sc/Str ;; Id is created when the verdict is saved the first time
   (sc/optional-key :modified) sc/Int
   :name                       sc/Str ;; Non-localized raw string
   :sections                   [MattiVerdictSection]})

(defn  complexity-section [id settings-path extra]
  {:id    (name id)
   :grid  {:columns 1
           :rows    (mapv (fn [complexity]
                            [{:id (name complexity)
                              :schema {:reference-list (merge {:i18nkey [(->> complexity
                                                                              name
                                                                              (str "matti.complexity.")
                                                                              keyword)
                                                                         :matti.complexity.label]
                                                               :type    :multi-select
                                                               :path    settings-path}
                                                              extra)}}])
                          [:small :medium :large :extra-large])}
   :_meta {:can-remove? true}})

(defn foremen-section [id settings-path loc-prefix]
  (complexity-section id settings-path {:item-loc-prefix loc-prefix}))

(defn reference-section [ref id settings-path]
  (complexity-section id settings-path {:term {:path       [ref]
                                               :extra-path [:name]
                                               :match-key  :id}}))

(defn text-section [id]
  {:id (name id)
   :grid {:columns 1
          :rows    [[{:schema {:loc-text (keyword (str (name id) ".text"))}}]]}
   :_meta {:can-remove? true}})

(def default-verdict-template
  {:name     ""
   :sections [{:id    "matti-verdict"
               :grid  {:columns 12
                       :rows    [{:css [:row--date-delta-title]
                                  :row [{:col    12
                                         :css    [:matti-label]
                                         :schema {:loc-text :matti-verdict-dates}}]}
                                 [{:id     "julkipano"
                                   :col 2
                                   :schema {:date-delta {:unit :days}}}
                                  {:id     "anto"
                                   :col 2
                                   :schema {:date-delta {:unit :days}}}
                                  {:id     "valitus"
                                   :col 2
                                   :schema {:date-delta {:unit :days}}}
                                  {:id     "lainvoimainen"
                                   :col 2
                                   :schema {:date-delta {:unit :days}}}
                                  {:id     "aloitettava"
                                   :col 2
                                   :schema {:date-delta {:unit :years}}}
                                  {:id     "voimassa"
                                   :col 2
                                   :schema {:date-delta {:unit :years}}}]
                                 [{:id     "giver"
                                   :col    3
                                   :schema {:docgen "matti-verdict-giver"}}
                                  {:align  :full
                                   :col    2
                                   :id     "verdict-code"
                                   :schema {:reference-list {:path       [:settings :verdict :0 :verdict-code]
                                                             :type       :select
                                                             :loc-prefix :matti-r}}}]
                                 [{:col    12
                                   :id     "paatosteksti"
                                   :schema {:phrase-text {:category :paatosteksti}}}]]}
               :_meta {:can-remove? false}}
              (foremen-section :matti-foremen [:settings :foremen :0 :foremen] :matti-r.foremen )
              (reference-section :plans :matti-plans [:settings :plans :0 :plans])
              (reference-section :reviews :matti-reviews [:settings :reviews :0 :reviews])
              (text-section :matti-neighbours)
              {:id    "matti-appeal"
               :grid  {:columns 6
                       :rows    [[{:col    6
                                   :id     "type"
                                   :schema {:docgen "automatic-vs-manual"}}]]}
               :_meta {:can-remove? true}}
              (text-section :matti-collateral)
              (text-section :matti-rights)
              (text-section :matti-purpose)
              (text-section :matti-statements)
              {:id    "matti-buildings"
               :grid  {:columns 1
                       :rows    [[{:schema {:list {:title    "matti-buildings.info"
                                                   :loc-prefix :matti-buildings.info
                                                   :items (mapv (fn [check]
                                                                  {:id     check
                                                                   :schema {:docgen "matti-verdict-check"}
                                                                   :css    [:matti-condition-box]})
                                                                ["autopaikat" "vss-luokka" "paloluokka"])}}}]]}
               :_meta {:can-remove? true}}]})

(sc/validate MattiVerdict default-verdict-template)

(defschema MattiSettings
  {:title    sc/Str
   :sections [MattiSection]})

(def r-settings
  {:title    "matti-r"
   :sections [{:id   "verdict"
               :grid {:columns 1
                      :rows    [[{:id     "verdict-code"
                                  :schema {:multi-select {:label?     false
                                                          :loc-prefix :matti-r
                                                          :items (keys verdict-code-map)}}}]]}}
              {:id   "foremen"
               :grid {:columns 1
                      :rows    [[{:id     "foremen"
                                  :schema {:multi-select {:label?     false
                                                          :loc-prefix :matti-r
                                                          :items foreman-codes}}}]]}}
              {:id   "plans"
               :grid {:columns 1
                      :rows    [[{:id     "plans"
                                  :schema {:reference-list {:label?   false
                                                            :path     [:plans]
                                                            :item-key :id
                                                            :type     :multi-select
                                                            :term     {:path       [:plans]
                                                                       :extra-path [:name]}}}}]]}}
              {:id   "reviews"
               :grid {:columns 1
                      :rows    [[{:id     "reviews"
                                  :schema {:reference-list {:label?   false
                                                            :path     [:reviews]
                                                            :item-key :id
                                                            :type     :multi-select
                                                            :term     {:path       [:reviews]
                                                                       :extra-path [:name]}}}}]]}}]})

(def settings-schemas
  {:r r-settings})

(sc/validate MattiSettings r-settings)


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
