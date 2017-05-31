(ns lupapalvelu.matti.shared
  (:require [schema.core :refer [defschema] :as sc]))



(declare MattiList)

(def meta-flags (zipmap (map sc/optional-key
                             [:can-edit?
                              :editing?
                              :can-remove?
                              :removed?])
                        (repeat sc/Bool)))

(defschema MattiMeta
  {(sc/optional-key :_meta) (merge meta-flags
                                   {(sc/optional-key :css) [sc/Keyword]})})

(defschema SchemaType
  (merge MattiMeta
         {(sc/optional-key :docgen) sc/Str
          (sc/optional-key :list)   (sc/recursive #'MattiList)}))

(defschema MattiItem
  (merge MattiMeta
         {;; Id is used as a path part within the state. Thus, it is
          ;; mandatory if an explicit resolution is needed.
          (sc/optional-key :id)     sc/Str
          (sc/optional-key :align)  (sc/enum :left :right :center :full)
          (sc/optional-key :schema) SchemaType}))

(defschema MattiList
  (merge MattiMeta
         {(sc/optional-key :id) sc/Str  ;; List has label if id is given.
          :items                [MattiItem]}))

(defschema MattiGrid
  (merge MattiMeta
         {:columns (apply sc/enum (range 1 13)) ;; Grid size (.matti-grid-n)
          :rows    [[(merge MattiItem
                            {(sc/optional-key :col)    sc/Int ;; Column width (.col-n). Default 1.
                             })]]}))

(defschema MattiVerdictSection
  (merge MattiMeta
         {:id     sc/Str     ;; Also title localization key
          :grid   MattiGrid
          (sc/optional-key :_meta) {sc/Keyword sc/Bool}}))

(defschema MattiVerdict
  {(sc/optional-key :id) sc/Str  ;; Id is created when the verdict is saved the first time
   :name                 sc/Str  ;; Non-localized raw string
   :sections             [MattiVerdictSection]})



(defn checkbox-list [id checks]
  {:list {:id id
          :items (map (fn [check]
                        {:id check
                         :schema {:docgen "matti-verdict-check"}
                         :_meta {:css [:matti-condition-box]}})
                      checks)}})

(def default-verdict-template
  {:name     ""
   :sections [{:id    "matti.verdict"
               :grid  {:columns 5
                       :rows    [[{}
                                  {:id "pykala"
                                   :schema {:docgen "matti-string"}}
                                  {}
                                  {:align  :full
                                   :id "paatostieto"
                                   :schema {:docgen "matti-verdict-code"}}]
                                 [{:col 3
                                   ;;:id "paatosteksti"
                                   :align :full
                                   :schema {:list {;;:id "paatosteksti"
                                                   :items [{:id "paatosteksti"
                                                            :align :full
                                                            :schema {:docgen "matti-verdict-text"}}]}}
                                   }]]}
               :_meta {:can-remove? true}}
              {:id "matti.conditions"
               :grid {:columns 4
                      :rows [[{:col 4
                               :schema (checkbox-list "foremen" ["vastaava-tj" "vv-tj" "iv-tj" "erityis-tj"])}]
                             [{:col 4
                               :schema (checkbox-list "plans" ["rakenne" "vv" "piha" "ilma"])}]
                             [{:col 4
                               :schema (checkbox-list "reviews" ["paikka" "sijainti" "aloitus" "pohja" "rakenne" "vv" "iv" "loppu"])}]]}
               :_meta {:can-remove? true}}]})

(def default-data {:matti.verdict {:paatostieto "evatty"}
                   :_meta         {:can-edit? true}})

(sc/validate MattiVerdict default-verdict-template)
