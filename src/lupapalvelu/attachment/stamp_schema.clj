(ns lupapalvelu.attachment.stamp-schema
  (:require [sade.schemas :as ssc]
            [schema.core :as sc]
            [clojure.set :as set]))

(def simple-tag-types
  #{:current-date
    :verdict-date
    :backend-id
    :username
    :organization
    :agreement-id
    :building-id})

(def text-tag-types
  #{:custom-text
    :extra-text})

(def all-tag-types
  (set/union simple-tag-types text-tag-types))

(sc/defschema SimpleTag
              {:type sc/Str})

(sc/defschema TextTag
              {:type sc/Str
               :text sc/Str})

(sc/defschema Tag
              (sc/conditional #(contains? simple-tag-types
                                          (keyword (:type %)))
                              SimpleTag
                              #(contains? text-tag-types
                                          (keyword (:type %)))
                              TextTag))

(sc/defschema StampTemplateRow
              [Tag])

(sc/defschema FilledTag
              {:type  (apply sc/enum all-tag-types)
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