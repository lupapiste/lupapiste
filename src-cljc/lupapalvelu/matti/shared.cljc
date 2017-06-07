(ns lupapalvelu.matti.shared
  (:require [schema.core :refer [defschema] :as sc]))

(declare MattiList)

(def meta-flags (zipmap (map sc/optional-key
                             [:can-edit?
                              :editing?
                              :can-remove?])
                        (repeat sc/Bool)))

(defschema MattiBase
  {(sc/optional-key :_meta) meta-flags
   (sc/optional-key :css)   [sc/Keyword]
   ;; If an schema ancestor has :loc-prefix then localization term is
   ;; loc-prefix + last, where last is the last path part.
   (sc/optional-key :loc-prefix) sc/Keyword})

(defschema SchemaType
  {(sc/optional-key :docgen) sc/Str
   (sc/optional-key :list)   (sc/recursive #'MattiList)})

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

(defn checkbox-list [id checks]
  {:list (merge {:id id
                 :items (map (fn [check]
                               {:id check
                                :schema {:docgen "matti-verdict-check"}
                                :css [:matti-condition-box]})
                             checks)})})

(def default-verdict-template
  {:name     ""
   :sections [{:id    "matti-verdict"
               :grid  {:columns 5
                       :rows    [[{}
                                  {:id     "pykala"
                                   :schema {:docgen "matti-string"}
                                   :css [:pykala-class]}
                                  {}
                                  {:align  :full
                                   :id     "paatostieto"
                                   :schema {:docgen "matti-verdict-code"}}]
                                 [{:col    3
                                   ;;:id "paatosteksti"
                                   :align  :full
                                   :schema {:list {;;:id "paatosteksti"
                                                   :items [{:id     "paatosteksti"
                                                            :align  :full
                                                            :css    [:item-class]
                                                            :schema {:docgen "matti-verdict-text"}}]}}}]]
                       :css     [:grid-class]}
               :css   [:section-class]
               :_meta {:can-remove? false}}
              {:id    "matti-foremen"
               :loc-prefix :matti-foremen
               :grid  {:columns 4
                       :rows    [[{:col    4
                                   :schema (checkbox-list "foremen" ["vastaava-tj" "vv-tj" "iv-tj" "erityis-tj"])}]]}
               :_meta {:can-remove? true}}
              {:id    "matti-conditions"
               :grid  {:columns 4
                       :rows    [[{:col    4
                                   :schema (checkbox-list "foremen" ["vastaava-tj" "vv-tj" "iv-tj" "erityis-tj"])}]
                                 [{:col    4
                                   :schema (checkbox-list "plans" ["rakenne" "vv" "piha" "ilma"])}]
                                 [{:col    4
                                   :schema (checkbox-list "reviews" ["paikka" "sijainti" "aloitus" "pohja" "rakenne" "vv" "iv" "loppu"])}]]}
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
