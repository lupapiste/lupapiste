(ns sade.schemas-test
  (:require [sade.schemas :refer :all]
            [sade.schema-utils :as ssu]
            [midje.sweet :refer :all]
            [schema.core :as sc]))

(facts max-length-constraint
  (fact (sc/check (sc/pred (max-length-constraint 1)) []) => nil)
  (fact (sc/check (sc/pred (max-length-constraint 1)) [1]) => nil)
  (fact (sc/check (sc/pred (max-length-constraint 1)) [1 2]) =not=> nil))

(facts max-length-string
  (fact (sc/check (max-length-string 1) "a") => nil)
  (fact (sc/check (max-length-string 1) "ab") =not=> nil)
  (fact (sc/check (max-length-string 1) [1]) =not=> nil))

(facts "schema-utils/keys"
  (let [schema {:key                    sc/Str
                (sc/required-key :rkey) sc/Int
                (sc/optional-key :okey) sc/Any}]
    (ssu/keys schema)) => (just [:key :rkey :okey]))

