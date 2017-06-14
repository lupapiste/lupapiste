(ns lupapalvelu.matti.shared
  (:require [clojure.string :as s]
            [schema.core :refer [defschema] :as sc]))

(declare MattiList)

(def meta-flags (zipmap (map sc/optional-key
                             [:can-edit?
                              :editing?
                              :can-remove?])
                        (repeat sc/Bool)))

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

(defschema MattiFromSettings
  "Component that builds schema from the settings."
  (merge MattiBase
         {:path                 [sc/Keyword]  ;; Path within settings
          :type                 (sc/enum :select :multi-select)
          (sc/optional-key :item-loc-prefix) sc/Keyword
          (sc/optional-key :id) sc/Str}))

(defschema MattiMultiSelect
  (merge MattiBase
         ;; Sometimes is useful to finetune item localisations
         ;; separately from the label.
         {(sc/optional-key :item-loc-prefix) sc/Keyword
          :items           [sc/Keyword]}))

(def schema-type-alternatives
  {:docgen        sc/Str
   :list          (sc/recursive #'MattiList)
   :from-settings MattiFromSettings
   :loc-text      sc/Keyword ;; Localisation term shown as text.
   :date-delta    {(sc/optional-key :enabled) sc/Bool
                   (sc/optional-key :delta)   sc/Int
                   :unit                      (sc/enum :days :years)}
   :multi-select  MattiMultiSelect})

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
          (sc/optional-key :align)  (sc/enum :left :right :center :full)
          (sc/optional-key :schema) (make-conditional schema-type-alternatives)}))

(defschema MattiList
  (merge MattiBase
         {(sc/optional-key :id) sc/Str  ;; List has label if id is given.
          :items                [MattiItem]}))

(def matti-row [(merge MattiItem
                       {(sc/optional-key :col) sc/Int ;; Column width (.col-n). Default 1.
                        })] )

(defschema MattiGrid
  (merge MattiBase
         {:columns (apply sc/enum (range 1 13)) ;; Grid size (.matti-grid-n)
          :rows    [(sc/conditional
                     :css {:css [sc/Keyword]
                           :row matti-row}
                     :else matti-row)]}))

(defschema MattiVerdictSection
  (merge MattiBase
         {:id                        sc/Str ;; Also title localization key
          ;; Section removed from the template. Note: the data is not cleared.
          (sc/optional-key :removed) sc/Bool
          (sc/optional-key :pdf)     sc/Bool ;; Section included in the verdict pdf.
          :grid                      MattiGrid}))

(defschema MattiVerdict
  {(sc/optional-key :id)        sc/Str ;; Id is created when the verdict is saved the first time
   (sc/optional-key :modified) sc/Int
   :name                        sc/Str ;; Non-localized raw string
   :sections                    [MattiVerdictSection]})

(defn checkbox-rows [checks]
  (mapv (fn [complexity]
         [{:col 4
           :schema {:list {:id (name complexity)
                           :i18nkey [(->> complexity name (str "matti.complexity.") keyword)
                                     :matti.complexity.label]
                           :items (mapv (fn [check]
                                         {:id check
                                          :schema {:docgen "matti-verdict-check"}
                                          :css [:matti-condition-box]})
                                       checks)}}}])
       [:small :medium :large :extra-large]))

(defn complexity-section [id settings-path]
  {:id (name id)
   :grid  {:columns 1
           :rows (mapv (fn [complexity]
                         [{:schema {:from-settings {:id (name complexity)
                                                    :i18nkey [(->> complexity name (str "matti.complexity.") keyword)
                                                              :matti.complexity.label]
                                                    :type :multi-select
                                                    :item-loc-prefix (->> settings-path
                                                                          (map name)
                                                                          (s/join ".")
                                                                          keyword)
                                                    :path settings-path}}}])
                       [:small :medium :large :extra-large])}
   :_meta {:can-remove? true}})

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
                                   :schema {:from-settings {:path       [:matti-r :verdict-code]
                                                            :type       :select
                                                            :loc-prefix :matti-r}}}]
                                 [{:col    5
                                   :id     "paatosteksti"
                                   :align  :full
                                   :schema {:docgen "matti-verdict-text"}}]]}
               :_meta {:can-remove? false}}
              (complexity-section :matti-foremen [:matti-r :foremen] )
              #_(complexity-section :matti-plans ["rakenne" "vv" "piha" "ilma"])
              (complexity-section :matti-reviews [:matti-r :reviews])
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
                       :rows    [[{:schema {:list {:id    "info"
                                                   :items (mapv (fn [check]
                                                                  {:id     check
                                                                   :schema {:docgen "matti-verdict-check"}
                                                                   :css    [:matti-condition-box]})
                                                                ["autopaikat" "vss-luokka" "paloluokka"])}}}]]}
               :_meta {:can-remove? true}}]})

(sc/validate MattiVerdict default-verdict-template)

(defschema MattiSettings
  {:id   sc/Str
   :grid MattiGrid})

(def r-settings
  {:id "matti-r"
   :grid {:columns 1
          :rows [[{:id "verdict-code"
                   :schema {:multi-select {:items [:annettu-lausunto
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
                                                   :valituksesta-luovuttu-2]}}}]
                 [{:id "foremen"
                   :schema {:multi-select {:items [:vastaava-tj :vv-tj :iv-tj :erityis-tj]}}}]
                 [{:id "reviews"
                   :schema {:multi-select {:items [:paikka :sijainti :aloitus :pohja :rakenne :vv :iv :loppu]}}}]]}})

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
