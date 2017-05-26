(ns lupapalvelu.matti.shared
  (:require [schema.core :refer [defschema] :as sc]))



(declare MattiList)

(defschema SchemaType
  {(sc/optional-key :docgen) sc/Str
   (sc/optional-key :list)   (sc/recursive #'MattiList)})

(defschema MattiItem
  {;; Id is used as a path part within the state. Thus, it is
   ;; mandatory if an explicit resolution is needed.
   (sc/optional-key :id)     sc/Str
   (sc/optional-key :schema) SchemaType})

(defschema MattiList
  {(sc/optional-key :id) sc/Str  ;; List has label if id is given.
   :items                [MattiItem]})

(defschema MattiGrid
  {:columns (apply sc/enum (range 1 13)) ;; Grid size (.matti-grid-n)
   :rows    [[(merge MattiItem
                     {(sc/optional-key :col)    sc/Int ;; Column width (.col-n). Default 1.
                      (sc/optional-key :align)  (sc/enum :left :right :center :full)
                      })]]})

(defschema MattiVerdictSection
  {:id     sc/Str  ;; Also title localization key
   :grid   MattiGrid}) ;; Data must conform to the grid contents

(defschema MattiVerdict
  {(sc/optional-key :id) sc/Str  ;; Id is created when the verdict is saved the first time
   :name                 sc/Str  ;; Non-localized raw string
   :sections             [MattiVerdictSection]})



(defn checkbox-list [id checks]
  {:list {:id id
          :items (map (fn [check]
                        {:id check
                         :schema {:docgen "matti-verdict-check"}})
                      checks)}})

(def default-verdict-template
  {:name     ""
   :sections [{:id    "matti.verdict"
               :grid  {:columns 4
                       :rows    [[{;;:col    1
                                   :align  :full
                                   :id "paatostieto"
                                   :schema {:docgen "matti-verdict-code"}}
                                  #_{:col    2
                                   :align  :full
                                   ;;:id "select2"
                                     :schema "matti-verdict-code"}
                                  {:id "check1"
                                   :schema {:docgen "matti-verdict-check"}}]]}}
              {:id "matti.conditions"
               :grid {:columns 4
                      :rows [[{:col 4
                               :schema (checkbox-list "foremen" ["vastaava-tj" "vv-tj" "iv-tj" "erityis-tj"])}]
                             [{:col 4
                               :schema (checkbox-list "plans" ["rakenne" "vv" "piha" "ilma"])}]
                             [{:col 4
                               :schema (checkbox-list "reviews" ["paikka" "sijainti" "aloitus" "pohja" "rakenne" "vv" "iv" "loppu"])}]]}}]})

(def default-data {:matti.verdict {:paatostieto "evatty"}})

(sc/validate MattiVerdict default-verdict-template)
