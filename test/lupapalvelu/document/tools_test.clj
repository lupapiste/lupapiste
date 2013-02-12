(ns lupapalvelu.document.model-test
  (:require
    [lupapalvelu.document.tools :refer :all]
    [lupapalvelu.document.model-test :as model]
    [midje.sweet :refer :all]))

(fact "simple schema"
  (-> schema
    (create nil-values)
    flattened) => {:a {:aa nil
                       :ab nil
                       :b {:ba nil
                           :bb nil}
                       :c nil}})

(fact "repeative schema"
  (-> schema-with-repetition
    (create nil-values)
    flattened) => {:single nil
                   :repeats {:0 {:repeats {:single2 nil
                                           :repeats2 nil}}}})
