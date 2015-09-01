(ns lupapalvelu.document.validator-test
  (:use [lupapalvelu.document.validator]
        [midje.sweet])
  (:require [sade.util :refer [->int]]))

(defvalidator :too-much-health
  {:doc    "health validator"
   :schemas ["test"]
   :fields [health     [:player :health]
            max-health [:game :max-health]]}
  (and health max-health (> health max-health)))

(def document {:schema-info {:name "invalid"}
               :data {:player {:health     10}
                      :game   {:max-health 10}}})

(facts
  (fact "is valid to begin with"
    (validate document) => empty?)

  (fact "is valid with wrong data and wrong schema"
    (->
      document
      (assoc-in [:schema-info :name] "test")
      (assoc-in [:data :player :health] 11)
      validate) => [{:path [:player :health]
                     :result [:warn "too-much-health"]}
                    {:path [:game :max-health]
                     :result [:warn "too-much-health"]}])

  (fact "is invalid with wrong data and right schema"
    (->
      document
      (assoc-in [:data :player :health] 11)
      validate) => empty?))