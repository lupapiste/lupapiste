(ns lupapalvelu.document.validator-test
  (:use [lupapalvelu.document.validator]
        [midje.sweet])
  (:require [sade.util :refer [->int]]))

;; normal validator

(defvalidator :too-much-health
  {:doc    "health validator"
   :schema "test"
   :fields [health     [:player :health]
            max-health [:game :max-health]]}
  (and health max-health (> health max-health)))

(facts
  (let [document {:schema {:info {:name "invalid"}}
                  :data   {:player {:health     10}
                           :game   {:max-health 10}}}]

    (fact "is valid to begin with"
      (validate document) => empty?)

    (fact "is valid with wrong data and wrong schema"
      (->
        document
        (assoc-in [:schema :info :name] "test")
        (assoc-in [:data :player :health] 11)
        validate) => [{:path [:player :health]
                       :result [:warn "too-much-health"]}
                      {:path [:game :max-health]
                       :result [:warn "too-much-health"]}])

    (fact "is invalid with wrong data and right schema"
      (->
        document
        (assoc-in [:data :player :health] 11)
        validate) => empty?)))

;; child validator

(defvalidator :all-alive
  {:doc    "everyone in team is alive"
   :schema "test2"
   :childs [:team]
   :fields [health [:health]]}
  (> health 0))

(facts
  (let [document {:schema {:info {:name "test2"}}
                  :data   {:team {:0 {:health 8}
                                  :1 {:health 4}}}}]

    (fact "everyone is alive"
      (validate document) => empty?)

   #_ (fact "is valid with wrong data and wrong schema"
      (->
        document
        (assoc-in [:schema :info :name] "test")
        (assoc-in [:data :player :health] 11)
        validate) => [{:path [:player :health]
                       :result [:warn "too-much-health"]}
                      {:path [:game :max-health]
                       :result [:warn "too-much-health"]}])

    #_(fact "is invalid with wrong data and right schema"
      (->
        document
        (assoc-in [:data :player :health] 11)
        validate) => empty?)))
