(ns sade.strings-test
  (:use sade.strings
        clojure.test
        midje.sweet))

(facts "Test last-n"
  (fact "Nil-safe"
    (last-n nil nil) => nil)
  (fact "Empty remains empty"
    (last-n 0 "") => "")
  (fact "Zero lenght returns empty"
    (last-n 0 "foo") => "")
  (fact "One"
    (last-n 1 "1") => "1")
  (fact "Two"
    (last-n 2 "10") => "10")
  (fact "Three"
    (last-n 1 "11") => "1")
  (fact "Four"
    (last-n 5 "Four") => "Four")  )

(facts "Test suffix"
  (fact (suffix nil nil) => nil)
  (fact (suffix nil "nil") => nil)
  (fact (suffix "nil" nil) => "nil")
  (fact (suffix "nil" "") => "nil")
  (fact (suffix "" "") => "")
  (fact (suffix "" "foo") => "")
  (fact (suffix "foo" "") => "foo")
  (fact (suffix "foo.bar" ".") => "bar")
  (fact (suffix "ababa" "b") => "a")
  (fact (suffix "gaah---haag!" "---") => "haag!")  )

(facts "Test de-accent"
  (fact (de-accent nil) => nil)
  (fact (de-accent "\u00c5") => "A")
  (fact (de-accent "\u00f6") => "o")
  (fact (de-accent "\u00e9") => "e")
  (fact (de-accent "\u00c4\u00f6\u00c5") => "AoA")  )

(facts "remove-leading-zeros"
  (fact (remove-leading-zeros nil) => nil)
  (fact (remove-leading-zeros "") => "")
  (fact (remove-leading-zeros "01234") => "1234")
  (fact (remove-leading-zeros "0001234a") => "1234a")
  (fact (remove-leading-zeros "101234") => "101234")
  (fact (remove-leading-zeros "0") => "0")
  (fact (remove-leading-zeros "0000000") => "0")
  (fact (remove-leading-zeros "0000009") => "9"))

(facts
  (fact (starts-with "foo" "f")    => truthy)
  (fact (starts-with "foo" "fo")   => truthy)
  (fact (starts-with "foo" "foo")  => truthy)
  (fact (starts-with "foo" "fooo") => falsey)
  (fact (starts-with "foo" "ba")   => falsey)
  (fact (starts-with "foo" nil)    => falsey)
  (fact (starts-with nil "ba")     => falsey))
