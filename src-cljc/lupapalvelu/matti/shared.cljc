(ns lupapalvelu.matti.shared
  (:require [schema.core :refer [defschema] :as sc]))

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

(defschema SchemaType
  {(sc/optional-key :docgen)   sc/Str
   (sc/optional-key :list)     (sc/recursive #'MattiList)
   (sc/optional-key :loc-text) sc/Keyword ;; Localisation term shown as text.
   })

(defschema MattiItem
  (merge MattiBase
         {;; Id is used as a path part within the state. Thus, it is
          ;; mandatory if an explicit resolution is needed.
          (sc/optional-key :id)     sc/Str
          (sc/optional-key :align)  (sc/enum :left :right :center :full)
          (sc/optional-key :schema) SchemaType}))

(defschema MattiList
  (merge MattiBase
         {(sc/optional-key :id) sc/Str  ;; List has label if id is given.
          :items                [MattiItem]}))

(defschema MattiGrid
  (merge MattiBase
         {:columns (apply sc/enum (range 1 13)) ;; Grid size (.matti-grid-n)
          :rows    [[(merge MattiItem
                            {(sc/optional-key :col)    sc/Int ;; Column width (.col-n). Default 1.
                             })]]}))

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

(defn complexity-section [id items]
  {:id (name id)
   :loc-prefix (keyword id)
   :grid  {:columns 4
           :rows (checkbox-rows items)}
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
                       :rows    [[{:id     "giver"
                                   :col    2
                                   :schema {:docgen "matti-verdict-giver"}}
                                  {:align  :full
                                   :col    2
                                   :id     "paatostieto"
                                   :schema {:docgen "matti-verdict-code"}}]
                                 [{:col    5
                                   :id     "paatosteksti"
                                   :align  :full
                                   :schema {:docgen "matti-verdict-text"}}]]}
               :_meta {:can-remove? false}}
              (complexity-section :matti-foremen ["vastaava-tj" "vv-tj" "iv-tj" "erityis-tj"] )
              (complexity-section :matti-plans ["rakenne" "vv" "piha" "ilma"])
              (complexity-section :matti-reviews ["paikka" "sijainti" "aloitus" "pohja" "rakenne" "vv" "iv" "loppu"])
              (text-section :matti-neighbours)
              {:id "matti-appeal"
               :grid {:columns 6
                      :rows [[{:col 6
                               :schema {:docgen "automatic-vs-manual"}}]]}
               :_meta {:can-remove? false}}
              (text-section :matti-collateral)
              (text-section :matti-rights)
              (text-section :matti-purpose)
              (text-section :matti-statements)
              {:id "matti-buildings"
               :grid {:columns 1
                      :rows [[{:schema {:list {:id "info"
                                               :items (mapv (fn [check]
                                                              {:id check
                                                               :schema {:docgen "matti-verdict-check"}
                                                               :css [:matti-condition-box]})
                                                            ["autopaikat" "vss-luokka" "paloluokka"])}}}]]}
               :_meta {:can-remove? true}}]})

(sc/validate MattiVerdict default-verdict-template)

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
