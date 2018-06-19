(ns lupapalvelu.attachment.stamp-schema
  (:require [sade.shared-schemas :as sssc]
            [schema.core :as sc]
            [clojure.set :as set]
            [clojure.string :as s]))

(def simple-field-types
  #{:current-date
    :verdict-date
    :backend-id
    :user
    :organization
    :application-id
    :building-id
    :section})

(def text-field-types
  #{:custom-text
    :extra-text})

(def all-field-types
  (set/union simple-field-types text-field-types))

(def transparency-options [0 20 40 60 80])

(def pages [:first :last :all])

(sc/defschema SimpleTagType
  (apply sc/enum simple-field-types))

(sc/defschema TextTagType
  (apply sc/enum text-field-types))

(sc/defschema SimpleTag
              (sc/conditional #(keyword? (:type %))
                              {:type SimpleTagType}
                              #(string? (:type %))
                              {:type (apply sc/enum (map name simple-field-types))}))

(sc/defschema TextTag
              (sc/conditional #(keyword? (:type %))
                              {:type TextTagType
                               :text sc/Str}
                              #(string? (:type %))
                              {:type (apply sc/enum (map name text-field-types))
                               :text sc/Str}))

(sc/defschema Tag
              (sc/conditional #(contains? simple-field-types
                                          (keyword (:type %)))
                              SimpleTag
                              #(contains? text-field-types
                                          (keyword (:type %)))
                              TextTag))

(sc/defschema StampTemplateRow
              [Tag])

(sc/defschema FilledTag
              {:type  (apply sc/enum all-field-types)
               :value sc/Str})

(sc/defschema StampRow
              [FilledTag])

(sc/defschema StampName (sc/constrained sc/Str (comp not s/blank?)))

(sc/defschema StampTemplate
              {:name       StampName
               :id         (sc/maybe sssc/ObjectIdStr)
               :position   {:x sc/Num
                            :y sc/Num}
               :background sssc/Nat
               :page       (sc/if keyword? (apply sc/enum pages) (apply sc/enum (mapv name pages)))
               :qrCode     sc/Bool
               :rows       (sc/constrained [StampTemplateRow] #(ffirst %) 'at-least-one-field-in-first-row)})

(sc/defschema Stamp
              {:name       StampName
               :id         sssc/ObjectIdStr
               :position   {:x sc/Num
                            :y sc/Num}
               :background sssc/Nat
               :page       (apply sc/enum pages)
               :qrCode     sc/Bool
               :rows       [StampRow]})

(sc/defschema JSONStampSchema
  (assoc Stamp
    :page (apply sc/enum (map name pages))
    :rows [[{:type                    (apply sc/enum (map name all-field-types))
             (sc/optional-key :value) (sc/maybe sc/Str)}]]))
