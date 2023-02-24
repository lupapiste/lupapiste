(ns sade.municipality-test
  (:require [midje.sweet :refer :all]
            [sade.municipality :as muni :refer [resolve-municipality]]))

(facts "resolve-municipality"
  (fact "Sipoo"
    (resolve-municipality "753") => "753")
  (fact "Juankoski is merged into Kuopio"
    (resolve-municipality "174") => "297")
  (doseq [[k v] muni/municipality-mapping]
    (fact {:midje/description (str k " -> " v)}
      (resolve-municipality k) => v))
  (facts "Bad municipalities"
    (resolve-municipality "bad") => nil
    (resolve-municipality nil) => nil
    (resolve-municipality 753) => nil
    (resolve-municipality "123") => nil
    (resolve-municipality " 753 ") => nil))
