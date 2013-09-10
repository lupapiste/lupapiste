(ns lupapalvelu.document.validators
  (:use [lupapalvelu.document.model]
        [lupapalvelu.document.tools]
        [midje.sweet])
  (:require [lupapalvelu.document.schemas :as schemas]))

(defn valid? [document]
  (or (fact (validate document) => '()) true))

(defn invalid? [document]
  (or (fact (validate document) => (has some not-empty)) true))

(defn invalid-with? [result]
  (fn [document]
    (or (fact (validate document) => (has some (contains {:result result}))) true)))

(defn not-invalid-with? [result]
  (fn [document]
    (or (fact (validate document) => (has not-every? (contains {:result result}))) true)))

(defn dummy-doc [schema-name]
  (let [schema (schemas/get-schema (schemas/get-latest-schema-version) schema-name)
        data   (create-document-data schema dummy-values)]
    {:schema-info schema
     :data        data}))
