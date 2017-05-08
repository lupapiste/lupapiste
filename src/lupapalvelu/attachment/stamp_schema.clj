(ns lupapalvelu.attachment.stamp-schema
  (:require [sade.schemas :as ssc]
            [schema.core :as sc]
            [clojure.set :as set]))

(def simple-field-types
  #{:current-date
    :verdict-date
    :backend-id
    :username
    :organization
    :agreement-id
    :building-id})

(def text-field-types
  #{:custom-text
    :extra-text})

(def all-field-types
  (set/union simple-field-types text-field-types))

(sc/defschema SimpleTag
              {:type sc/Str})

(sc/defschema TextTag
              {:type sc/Str
               :text sc/Str})

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

(sc/defschema StampName (sc/pred string?))

(sc/defschema StampTemplate
              {:name       StampName
               :id         ssc/ObjectIdStr
               :position   {:x ssc/Nat
                            :y ssc/Nat}
               :background ssc/Nat
               :page       sc/Str
               :qrCode     sc/Bool
               :rows       [StampTemplateRow]})

(sc/defschema Stamp
              {:name       StampName
               :id         ssc/ObjectIdStr
               :position   {:x ssc/Nat
                            :y ssc/Nat}
               :background ssc/Nat
               :page       (sc/enum :first
                                    :last
                                    :all)
               :qrCode     sc/Bool
               :rows       [StampRow]})