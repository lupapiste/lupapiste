(ns lupapalvelu.migration.replace-operation-migration-test
  (:require [lupapalvelu.migration.migrations :refer :all]
            [midje.util :refer [testable-privates]]
            [midje.sweet :refer :all]))

(facts "picking correct rakennuspaikka document from required documents"
  (get-correct-rakennuspaikka-doc {:testi-1 ["a" "b" "c"]
                                   :testi-2 ["rakennuspaikka" "d" "e"]
                                   :testi-3 ["rakennuspaikka-ilman-ilmoitusta" "f" "g"]})
  => [:testi-2 "rakennuspaikka"]

  (get-correct-rakennuspaikka-doc {:testi-1 ["a" "rakennuspaikka-ilman-ilmoitusta" "c"]
                                   :testi-2 ["d" "e"]
                                   :testi-3 ["rakennuspaikka" "f" "g"]})
  => [:testi-3 "rakennuspaikka"]

  (get-correct-rakennuspaikka-doc {:testi-1 ["a" "b" "rakennuspaikka-ilman-ilmoitusta" "c"]
                                   :testi-2 ["d" "e"]
                                   :testi-3 ["f" "g"]})
  => [:testi-1 "rakennuspaikka-ilman-ilmoitusta"]

  (get-correct-rakennuspaikka-doc {:testi-1 ["a" "b" "c"]
                                   :testi-2 ["d" "e"]
                                   :testi-3 ["f" "rakennuspaikka-ilman-ilmoitusta" "g"]})
  => [:testi-3 "rakennuspaikka-ilman-ilmoitusta"]

  (get-correct-rakennuspaikka-doc {:testi-1 ["a" "b"]
                                   :testi-2 ["c" "d"]
                                   :testi-3 ["e" "f"]})
  => [nil nil]

  (get-correct-rakennuspaikka-doc {:testi-1 ["a" "rakennuspaikka" "b"]
                                   :testi-2 ["c" "rakennuspaikka" "d"]
                                   :testi-3 ["e" "rakennuspaikka" "f"]})
  => [:testi-1 "rakennuspaikka"])