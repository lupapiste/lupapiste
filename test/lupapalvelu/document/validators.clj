(ns lupapalvelu.document.validators
  (:use [lupapalvelu.document.model]
        [lupapalvelu.document.tools]
        [midje.sweet])
  (:require [lupapalvelu.document.schemas :as schemas]))

(defn valid? [document]
  (or (fact (validate document) => empty?) true))

(defn valid-against? [schema]
  (fn [document] (or (fact (validate document schema) => empty?) true)))

(defn invalid? [document]
  (or (fact (validate document) => (has some not-empty)) true))

(defn invalid-against? [schema]
  (fn [document] (or (fact (validate document schema) => (has some not-empty)) true)))

(defn invalid-with?
  ([result]
    (invalid-with? nil result))
  ([schema result]
    (fn [document]
      (or (fact (validate document schema) => (has some (contains {:result result}))) true))))

(defn not-invalid-with?
  ([result]
    (not-invalid-with? nil result))
  ([schema result]
    (fn [document]
      (or (fact (validate document schema) => (has not-every? (contains {:result result}))) true))))

(defn dummy-doc [schema-name]
  (let [schema (schemas/get-schema (schemas/get-latest-schema-version) schema-name)
        data   (create-document-data schema dummy-values)]
    {:schema-info (:info schema)
     :data        data}))
