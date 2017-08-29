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

(def path-type (sc/conditional
                ;; Joined kw-path (e.g. :one.two.three)
                keyword? sc/Keyword
                ;; Vector path [:one :two :three] or vector of joined
                ;; kw-paths [:one.two.three :four.five], depending on
                ;; the schema.
                :else    [sc/Keyword]))

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
         {(sc/optional-key :_meta)      meta-flags
          (sc/optional-key :css)        [sc/Keyword]
          ;; If an schema ancestor has :loc-prefix then localization term is
          ;; loc-prefix + last, where last is the last path part.
          (sc/optional-key :loc-prefix) sc/Keyword
          ;; Absolute localisation terms. Overrides loc-prefix, does not
          ;; affect children. When the vector has more than one items, the
          ;; earlier localisations are arguments to the latter.
          (sc/optional-key :i18nkey)    [sc/Keyword]}))

(defschema MattiComponent
  (merge MattiBase
         {(sc/optional-key :label?) sc/Bool})) ;; Show label? Default true

(defschema MattiReferenceList
  "Component that builds schema from an external source. Each item is
  the id property of the target value or the the value itself."
  (merge MattiComponent
         ;; Path is interpreted by the implementation. In Matti the
         ;; path typically refers to the settings.
         {:path                              path-type
          :type                              (sc/enum :select :multi-select)
          ;; By default, an item value is the same as
          ;; source. If :item-key is given, then the corresponding
          ;; source property is used.
          (sc/optional-key :item-key)        sc/Keyword
          ;; Term-path overrides item-loc-prefix. However,
          ;; item-loc-prefix supports item-key.
          (sc/optional-key :item-loc-prefix) sc/Keyword
          (sc/optional-key :id)              sc/Str
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

(def schema-type-alternatives
  {:docgen         (sc/conditional
                    :name MattiDocgen
                    :else sc/Str)
   :list           (sc/recursive #'MattiList)
   :reference-list MattiReferenceList
   :phrase-text    MattiPhraseText
   :loc-text       sc/Keyword ;; Localisation term shown as text.
   :date-delta     MattiDateDelta
   :multi-select   MattiMultiSelect
   :reference      MattiReference
   :placeholder    MattiPlaceholder})

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

(defschema MattiVerdictSection
  (merge MattiSection
         {;; Section removed from the template. Note: the data is not cleared.
          (sc/optional-key :removed) sc/Bool}))

(defschema MattiVerdict
  {(sc/optional-key :id)       sc/Str
   (sc/optional-key :modified) sc/Int
   (sc/optional-key :name)     sc/Str ;; Non-localized raw string
   :sections                   [MattiVerdictSection]})

(defn  multi-section [id settings-path extra]
  {:id    (name id)
   :grid  {:columns 1
           :rows    [[{:schema {:reference-list (merge {:label? false
                                                        :type   :multi-select
                                                        :path   settings-path}
                                                       extra)}}]]}
   :_meta {:can-remove? true}})

(defn foremen-section [id settings-path loc-prefix]
  (multi-section id settings-path {:item-loc-prefix loc-prefix}))

(defn reference-section [ref id settings-path]
  (multi-section id settings-path {:term {:path       [ref]
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
                                   :col    3
                                   :id     "verdict-code"
                                   :schema {:reference-list {:path       :settings.verdict.0.verdict-code
                                                             :type       :select
                                                             :loc-prefix :matti-r}}}]
                                 [{:col    12
                                   :id     "paatosteksti"
                                   :schema {:phrase-text {:category :paatosteksti}}}]]}
               :_meta {:can-remove? false}}
              (foremen-section :matti-foremen [:settings :foremen :0 :foremen] :matti-r.foremen )
              (reference-section :plans :matti-plans [:settings :plans :0 :plans])
              (reference-section :reviews :matti-reviews [:settings :reviews :0 :reviews])
              {:id "matti-conditions"
               :i18nkey [:phrase.category.lupaehdot]
               :loc-prefix :phrase.category.lupaehdot
               :grid {:columns 1
                      :rows [[{:schema {:phrase-text {:category :lupaehdot}}}]]}
               :_meta {:can-remove? true}}
              (text-section :matti-neighbors)
              {:id    "matti-appeal"
               :grid  {:columns 1
                       :rows    [[{:schema {:phrase-text {:category :muutoksenhaku
                                                          :i18nkey [:phrase.category.muutoksenhaku] }}}]]}
               :_meta {:can-remove? true}}
              (text-section :matti-collateral)
              {:id "matti-complexity"
               :grid {:columns 1
                      :rows [[{:loc-prefix :matti-complexity
                               :schema {:docgen "matti-complexity"}}]
                             [{:id "text"
                               :schema {:phrase-text {:label? false
                                                      :category :vaativuus}}}]]}
               :_meta {:can-remove? true}}
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
                                                          :items (keys verdict-code-map)}}}]]}
               :_meta {:editing? true}}
              {:id   "foremen"
               :grid {:columns 1
                      :rows    [[{:id     "foremen"
                                  :schema {:multi-select {:label?     false
                                                          :loc-prefix :matti-r
                                                          :items foreman-codes}}}]]}
               :_meta {:editing? true}}
              {:id   "plans"
               :grid {:columns 1
                      :rows    [[{:id     "plans"
                                  :schema {:reference-list {:label?   false
                                                            :path     [:plans]
                                                            :item-key :id
                                                            :type     :multi-select
                                                            :term     {:path       :plans
                                                                       :extra-path :name}}}}]]}
               :_meta {:editing? true}}
              {:id   "reviews"
               :grid {:columns 1
                      :rows    [[{:id     "reviews"
                                  :schema {:reference-list {:label?   false
                                                            :path     [:reviews]
                                                            :item-key :id
                                                            :type     :multi-select
                                                            :term     {:path       :reviews
                                                                       :extra-path :name}}}}]]}
               :_meta {:editing? true}}]})

(def settings-schemas
  {:r r-settings})

(sc/validate MattiSettings r-settings)

;; It is adivsabled to reuse ids from template when possible. This
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
                                 :schema {:phrase-text {:i18nkey  [:phrase.category.lupaehdot]
                                                        :category :lupaehdot}}}]])}}
     {:id "neighbors"
      :grid {:columns 12
             :rows [[{:col 7
                      :id "neighbor-notes"
                      :schema {:phrase-text {:i18nkey [:phrase.category.naapurit]
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

(sc/validate MattiVerdict (:r verdict-schemas))

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
