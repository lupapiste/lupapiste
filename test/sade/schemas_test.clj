(ns sade.schemas-test
  (:require [sade.schemas :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as sc]))

(facts max-length-constraint
  (fact (sc/check (sc/pred (max-len-constraint 1)) []) => nil)
  (fact (sc/check (sc/pred (max-len-constraint 1)) [1]) => nil)
  (fact (sc/check (sc/pred (max-len-constraint 1)) [1 2]) =not=> nil))

(facts max-length-string
  (fact (sc/check (max-length-string 1) "a") => nil)
  (fact (sc/check (max-length-string 1) "ab") =not=> nil)
  (fact (sc/check (max-length-string 1) [1]) =not=> nil))

