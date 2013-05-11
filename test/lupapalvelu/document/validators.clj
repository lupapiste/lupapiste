(ns lupapalvelu.document.validators
  (:use [lupapalvelu.document.model]
        [midje.sweet]))

(defn valid? [document]
  (or (fact (validate document) => '()) true))

(defn invalid? [document]
  (or (fact (validate document) => (has some not-empty)) true))

(defn invalid-with? [result]
  (fn [document]
    (or (fact (validate document) => (has some (contains {:result result}))) true)))
