(ns sade.schema-generators-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [midje.sweet :refer :all]
            [sade.schema-generators :refer :all]
            [sade.validators :as validators]))

(def email-prop
  (prop/for-all [e email]
    ((every-pred validators/valid-email? #(<= (count %) 255)) e)))

(defspec email-generator-test 40 email-prop)

(def http-url-prop
  (prop/for-all [url http-url]
    (validators/http-url? url)))

(defspec http-url-generator-test 40 http-url-prop)

(def fixed-length-generator-prop
  (prop/for-all [len gen/nat]
   (->> (gen/generate (fixed-length-string len))
        (every-pred string? #(= (count %) len)))))

(defspec fixed-length-string-generator-test 40 fixed-length-generator-prop)

(def min-length-generator-prop
  (prop/for-all [len gen/nat]
    (->> (gen/generate (min-length-string len))
         (every-pred string? #(>= (count %) len)))))

(defspec min-length-string-generator-test 40 min-length-generator-prop)

(def max-length-generator-prop
  (prop/for-all [len gen/nat]
    (->> (gen/generate (max-length-string len))
         (every-pred string? #(<= (count %) len)))))

(defspec max-length-string-generator-test 40 max-length-generator-prop)

(def min-max-length-generator-prop
  (prop/for-all [min-len gen/nat range gen/nat]
    (->> (gen/generate (min-max-length-string min-len (+ min-len range)))
         (every-pred string? #(>= (count %) min-len)  #(<= (count %) (+ min-len range))))))

(defspec min-max-length-string-generator-test 40 min-max-length-generator-prop)
