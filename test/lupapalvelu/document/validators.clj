(ns lupapalvelu.document.validators
  (:require [lupapalvelu.document.model :as model]
            [lupapalvelu.document.tools :refer :all]
            [midje.sweet :refer :all]))

(defn valid? [document]
  (or (fact (model/validate {} document) => empty?) true))

(defn valid-against? [schema]
  (fn [document] (or (fact (model/validate {} document schema) => empty?) true)))

(defn invalid? [document]
  (or (fact (model/validate {} document) => (has some not-empty)) true))

(defn invalid-against? [schema]
  (fn [document] (or (fact (model/validate {} document schema) => (has some not-empty)) true)))

(defn invalid-with?
  ([result]
    (invalid-with? nil result))
  ([schema result]
    (fn [document]
      (or (fact (model/validate {} document schema) => (has some (contains {:result result}))) true))))

(defn not-invalid-with?
  ([result]
    (not-invalid-with? nil result))
  ([schema result]
    (fn [document]
      (or (fact (model/validate {} document schema) => (has not-every? (contains {:result result}))) true))))
