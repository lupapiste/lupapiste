(ns sade.schema-generators-test
  (:require [sade.schema-generators :refer :all]
            [sade.validators :as validators]
            [midje.sweet :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(def email-prop
  (prop/for-all [e email]
    ((every-pred validators/valid-email? #(<= (count %) 255)) e)))

(defspec email-generator-test 40 email-prop)

(def http-url-prop
  (prop/for-all [url http-url]
    (validators/http-url? url)))

(defspec http-url-generator-test 40 http-url-prop)

(def fixed-length-generator-prop
  (prop/for-all [len gen/pos-int]
   (->> (gen/generate (fixed-length-string len))
        (every-pred string? #(= (count %) len)))))

(defspec fixed-length-string-generator-test 40 fixed-length-generator-prop)

(def min-length-generator-prop
  (prop/for-all [len gen/pos-int]
    (->> (gen/generate (min-length-string len))
         (every-pred string? #(>= (count %) len)))))

(defspec min-length-string-generator-test 40 min-length-generator-prop)

(def max-length-generator-prop
  (prop/for-all [len gen/pos-int]
    (->> (gen/generate (max-length-string len))
         (every-pred string? #(<= (count %) len)))))

(defspec max-length-string-generator-test 40 max-length-generator-prop)

(def min-max-length-generator-prop
  (prop/for-all [min-len gen/pos-int range gen/pos-int]
    (->> (gen/generate (min-max-length-string min-len (+ min-len range)))
         (every-pred string? #(>= (count %) min-len)  #(<= (count %) (+ min-len range))))))

(defspec min-max-length-string-generator-test 40 min-max-length-generator-prop)
