(ns lupapalvelu.matti.shared
  (:require [clojure.string :as s]
            [schema.core :refer [defschema] :as sc]))

(declare MattiList)

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
   :ei-tiedossa               "ei tiedossa"
   })

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
               :grid  {:columns 6
                       :rows    [{:css [:row--date-delta-title]
                                  :row [{:col    6
                                         :css    [:matti-label]
                                         :schema {:loc-text :matti-verdict-dates}}]}
                                 [{:id     "julkipano"
                                   :schema {:date-delta {:unit :days}}}
                                  {:id     "anto"
                                   :schema {:date-delta {:unit :days}}}
                                  {:id     "valitus"
                                   :schema {:date-delta {:unit :days}}}
                                  {:id     "lainvoimainen"
                                   :schema {:date-delta {:unit :days}}}
                                  {:id     "aloitettava"
                                   :schema {:date-delta {:unit :years}}}
                                  {:id     "voimassa"
                                   :schema {:date-delta {:unit :years}}}]
                                 [{:id     "giver"
                                   :col    2
                                   :schema {:docgen "matti-verdict-giver"}}
                                  {:align  :full
                                   :col    2
                                   :id     "verdict-code"
                                   :schema {:reference-list {:path       [:settings :verdict :0 :verdict-code]
                                                             :type       :select
                                                             :loc-prefix :matti-r}}}]
                                 [{:col    5
                                   :id     "paatosteksti"
                                   :align  :full
                                   :schema {:docgen "matti-verdict-text"}}]]}
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
                                                          :items      [:annettu-lausunto
                                                                       :asiakirjat-palautettu
                                                                       :ehdollinen
                                                                       :ei-lausuntoa
                                                                       :ei-puollettu
                                                                       :ei-tiedossa
                                                                       :ei-tutkittu-1
                                                                       :ei-tutkittu-2
                                                                       :ei-tutkittu-3
                                                                       :evatty
                                                                       :hallintopakko
                                                                       :hyvaksytty
                                                                       :ilmoitus-tiedoksi
                                                                       :konversio
                                                                       :lautakunta-palauttanut
                                                                       :lautakunta-poistanut
                                                                       :lautakunta-poydalle
                                                                       :maarays-peruutettu
                                                                       :muutti-evatyksi
                                                                       :muutti-maaraysta
                                                                       :muutti-myonnetyksi
                                                                       :myonnetty
                                                                       :myonnetty-aloitusoikeudella
                                                                       :osittain-myonnetty
                                                                       :peruutettu
                                                                       :puollettu
                                                                       :pysytti-evattyna
                                                                       :pysytti-maarayksen-2
                                                                       :pysytti-myonnettyna
                                                                       :pysytti-osittain
                                                                       :siirretty-maaoikeudelle
                                                                       :suunnitelmat-tarkastettu
                                                                       :tehty-hallintopakkopaatos-1
                                                                       :tehty-hallintopakkopaatos-2
                                                                       :tehty-uhkasakkopaatos
                                                                       :tyohon-ehto
                                                                       :valituksesta-luovuttu-1
                                                                       :valituksesta-luovuttu-2]}}}]]}}
              {:id   "foremen"
               :grid {:columns 1
                      :rows    [[{:id     "foremen"
                                  :schema {:multi-select {:label?     false
                                                          :loc-prefix :matti-r
                                                          :items      [:vastaava-tj :vv-tj :iv-tj :erityis-tj]}}}]]}}
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
