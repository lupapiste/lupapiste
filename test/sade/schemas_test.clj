(ns sade.schemas-test
  (:require [sade.schemas :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as schema]
            [clojure.test.check.clojure-test :refer [defspec]]
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
  (prop/for-all [len gen/pos-int]
    (->> (gen/generate (fixed-len-string-generator len))
         (every-pred string? #(= (count %) len)))))

(defspec fixed-len-string-generator-test 100 fixed-len-generator-prop)

(def min-len-generator-prop
  (prop/for-all [len gen/pos-int]
                (->> (gen/generate (min-len-string-generator len))
                     (every-pred string? #(>= (count %) len)))))

(defspec min-len-string-generator-test 100 min-len-generator-prop)

(def max-len-generator-prop
  (prop/for-all [len gen/pos-int]
                (->> (gen/generate (max-len-string-generator len))
                     (every-pred string? #(<= (count %) len)))))

(defspec max-len-string-generator-test 100 max-len-generator-prop)


