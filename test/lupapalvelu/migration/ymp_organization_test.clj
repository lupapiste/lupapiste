(ns lupapalvelu.migration.ymp-organization-test
    (:require [lupapalvelu.migration.migrations :refer :all]
              [lupapalvelu.organization :as org]
              [sade.schema-generators :as ssg]
              [schema.core :refer [defschema] :as sc]
              [midje.sweet :refer :all]))

(defschema Scope
  {:permitType   (sc/enum "R" "P" "YA" "VVVL" "YI" "FOO")
   :municipality (sc/enum "123")})

(facts add-ym-in-scope
  (fact "ym is added"
    (let [org (add-ym-in-scope {:scope [(ssg/generate Scope)]})]
      (count (:scope org)) => 2
      (:permitType (second (:scope org))) => "YM"
      (:municipality (second (:scope org))) => (:municipality (first (:scope org)))))

  (fact "ym not added if exists"
    (let [scope (ssg/generate Scope)
          ym-scope (assoc scope :permitType "YM")
          org    {:scope [scope ym-scope]}]
      (add-ym-in-scope org) => org))

  (fact "ym is added for every municipality code in scope"
    (let [org (add-ym-in-scope {:scope [(assoc (ssg/generate Scope) :municipality "123")
                                        (assoc (ssg/generate Scope) :municipality "223")
                                        (assoc (ssg/generate Scope) :municipality "323") ; Two elements have same municipality codes
                                        (assoc (ssg/generate Scope) :municipality "323")]})]
      (count (:scope org)) => 7
      (->> (:scope org) (filter (comp #{"YM"} :permitType)) (map :municipality)) => ["123" "223" "323"]))

  (fact "nothing is removed or overwritten"
    (let [org {:scope [(assoc (ssg/generate Scope) :municipality "123")
                       (assoc (ssg/generate Scope) :municipality "223")
                       (assoc (ssg/generate Scope) :municipality "323") ; Two elements have same municipality codes
                       (assoc (ssg/generate Scope) :municipality "323")
                       (assoc (ssg/generate Scope) :municipality "423" :permitType "YM")]}]
      (take 5 (:scope (add-ym-in-scope org))) => (:scope org))))
