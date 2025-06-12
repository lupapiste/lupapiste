(ns lupapalvelu.generators.attachment
  (:require [clojure.test.check.generators :as gen]
            [schema.core :as sc]
            [sade.schema-generators :as ssg]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.states :as states]))

(def make-attachment-generators
  (gen/let [attachment-id           ssg/object-id
            now                     ssg/timestamp
            target                  (ssg/generator att/Target)
            required?               gen/boolean
            requested-by-authority? gen/boolean
            locked?                 gen/boolean
            application-state       (gen/elements states/all-states)
            operation               (ssg/generator att/Operation)
            attachment-type         (ssg/generator att/Type)
            metadata                (ssg/generator {sc/Keyword sc/Str})
            ; Optional parameters
            contents                (ssg/generator (sc/maybe sc/Str))
            read-only?              (ssg/generator (sc/maybe sc/Bool))
            source                  (ssg/generator (sc/maybe att/Source))]
    {:attachment-id attachment-id
     :now now
     :target target
     :required? required?
     :requested-by-authority? requested-by-authority?
     :locked? locked?
     :application-state application-state
     :operation operation
     :attachment-type attachment-type
     :metadata metadata
     ;; Optional parameter
     :contents contents
     :read-only? read-only?
     :source source}))
