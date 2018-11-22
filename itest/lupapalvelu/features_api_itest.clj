(ns lupapalvelu.features-api-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(facts "feature? and set-feature"
  (command pena :set-feature :feature "lorem-ipsum-dolor-sit-amet" :value false) => ok?
  (feature? :lorem-ipsum-dolor-sit-amet) => false
  (command pena :set-feature :feature "lorem-ipsum-dolor-sit-amet" :value true) => ok?
  (feature? :lorem-ipsum-dolor-sit-amet) => true)

(facts "anti-csrf"
  (set-anti-csrf! false)
  (anti-csrf?) => false
  (set-anti-csrf! true)
  (anti-csrf?) => true
  (without-anti-csrf
   (anti-csrf?) => false
   (with-anti-csrf
     (anti-csrf?) => true)
   (anti-csrf?) => false)
  (anti-csrf?) => true)
