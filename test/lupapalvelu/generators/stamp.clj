(ns lupapalvelu.generators.stamp
  (:require [clojure.test.check.generators :as gen]
            [lupapalvelu.attachment.stamp-schema :as stamp-schema]
            [schema.core :as sc]
            [sade.util :refer [map-values]]
            [sade.schema-generators :as ssg]))

(def simple-tag-gen
  (gen/let [string? gen/boolean
            type (ssg/generator stamp-schema/SimpleTagType)]
    (let [type (if string?
                 (name type)
                 type)]
      {:type type})))

(ssg/register-generator stamp-schema/SimpleTag simple-tag-gen)

(def text-tag-gen
  (gen/let [string? gen/boolean
            type (ssg/generator stamp-schema/TextTagType)
            content (ssg/generator sc/Str)]
    (let [type (if string?
                 (name type)
                 type)]
      {:type type
       :text content})))

(ssg/register-generator stamp-schema/TextTag text-tag-gen)

(def tag-gen
  (gen/frequency [[3 simple-tag-gen]
                  [1 text-tag-gen]]))

(ssg/register-generator stamp-schema/Tag tag-gen)

(def page-gen
  (gen/let [string? gen/boolean
            page (gen/elements stamp-schema/pages)]
    (if string?
      (name page)
      page)))

(def stamp-template-generator
  (let [without-page (dissoc stamp-schema/StampTemplate :page)]
    (gen/let [stamp-template (ssg/generator without-page)
              page page-gen]
      (assoc stamp-template :page page))))

(ssg/register-generator stamp-schema/StampTemplate stamp-template-generator)
