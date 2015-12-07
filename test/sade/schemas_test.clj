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



