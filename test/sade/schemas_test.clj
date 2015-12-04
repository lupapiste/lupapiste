(ns sade.schemas-test
  (:require [sade.schemas :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as schema]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(facts max-length-constraint
  (fact (schema/check (schema/pred (max-len-constraint 1)) []) => nil)
  (fact (schema/check (schema/pred (max-len-constraint 1)) [1]) => nil)
  (fact (schema/check (schema/pred (max-len-constraint 1)) [1 2]) =not=> nil))

(facts max-length-string
  (fact (schema/check (max-length-string 1) "a") => nil)
  (fact (schema/check (max-length-string 1) "ab") =not=> nil)
  (fact (schema/check (max-length-string 1) [1]) =not=> nil))

;; crooked way to test generators with generators
(def fixed-len-generator-prop
  (prop/for-all [n gen/pos-int
                 len gen/pos-int]
    (->> (gen/sample (fixed-len-string-generator len) n)
         (every? #(and (string? %) 
                       (= (count %) len))))))

(fact fixed-len-string-generator
  (tc/quick-check 100 fixed-len-generator-prop) => (contains #{[:result true]}))

(def min-len-generator-prop
  (prop/for-all [n gen/pos-int
                 len gen/pos-int]
    (->> (gen/sample (min-len-string-generator len) n)
         (every? #(and (string? %) 
                       (>= (count %) len))))))

(fact min-len-string-generator
  (tc/quick-check 100 min-len-generator-prop) => (contains #{[:result true]}))

(def max-len-generator-prop
  (prop/for-all [n gen/pos-int
                 len gen/pos-int]
    (->> (gen/sample (max-len-string-generator len) n)
         (every? #(and (string? %) 
                       (<= (count %) len))))))

(fact max-len-string-generator
  (tc/quick-check 100 max-len-generator-prop) => (contains #{[:result true]}))

