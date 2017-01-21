(ns sade.strings-test
  (:require [sade.strings :refer :all]
            [midje.sweet :refer :all])
  (:refer-clojure :exclude [replace contains? empty?]))

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

(facts "limit"
  (limit nil nil) => nil
  (limit "abcdefg" nil) => nil
  (limit nil 2) => nil
  (limit "abcdefg" 2) => "ab"
  (limit "abcdefg" 2 nil) => "ab"
  (limit "abcdefg" 2 "...") => "ab..."
  (limit "abcdefg" 20) => "abcdefg")

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

(facts "zero-pad"
  (zero-pad 4 "1")     => "0001"
  (zero-pad 4 "12")    => "0012"
  (zero-pad 4 "123")   => "0123"
  (zero-pad 4 "1234")  => "1234"
  (zero-pad 4 "12345") => "12345")

(facts
  (fact (starts-with "foo" "f")    => truthy)
  (fact (starts-with "foo" "fo")   => truthy)
  (fact (starts-with "foo" "foo")  => truthy)
  (fact (starts-with "foo" "fooo") => falsey)
  (fact (starts-with "foo" "ba")   => falsey)
  (fact (starts-with "foo" nil)    => falsey)
  (fact (starts-with nil "ba")     => falsey))

(facts
  (fact (starts-with-i "Foo" "f")    => truthy)
  (fact (starts-with-i "foo" "Fo")   => truthy)
  (fact (starts-with-i "foO" "fOo")  => truthy)
  (fact (starts-with-i "foo" "fooo") => falsey)
  (fact (starts-with-i "foo" "ba")   => falsey)
  (fact (starts-with-i "foo" nil)    => falsey)
  (fact (starts-with-i nil "ba")     => falsey))

(facts
  (fact (ends-with "foo" "o")    => truthy)
  (fact (ends-with "foo" "oo")   => truthy)
  (fact (ends-with "foo" "foo")  => truthy)
  (fact (ends-with "foo" "fooo") => falsey)
  (fact (ends-with "foo" "ba")   => falsey)
  (fact (ends-with "foo" nil)    => falsey)
  (fact (ends-with nil "ba")     => falsey))

(facts
  (fact (ends-with-i "foo" "O")    => truthy)
  (fact (ends-with-i "foO" "Oo")   => truthy)
  (fact (ends-with-i "fOO" "foo")  => truthy)
  (fact (ends-with-i "foo" nil)    => falsey)
  (fact (ends-with-i nil "ba")     => falsey))

(fact (numeric? ["0"]) => false)

(facts "decimal-number?"
  (fact (decimal-number? "1") => true)
  (fact (decimal-number? "1.1") => true)
  (fact (decimal-number? "a") => false)
  (fact (decimal-number? "") => false)
  (fact (decimal-number? nil) => false)
  (fact (decimal-number? "a.0") => false)
  (fact (decimal-number? ".0") => false)
  (fact (decimal-number? "..0") => false)
  (fact (decimal-number? "123123132.456465465465464") => true))

(fact "optional-string?"
  (fact "nil is optional"
    (optional-string? nil) => true)
  (fact "strings"
    (optional-string? "") => true
    (optional-string? "hello") => true)
  (fact "booleas"
    (optional-string? true) => false
    (optional-string? false) => false)
  (fact "numbers"
    (optional-string? 1) => false
    (optional-string? 3.14) => false)
  (fact "collections"
    (optional-string? {}) => false
    (optional-string? #{}) => false
    (optional-string? []) => false)
  (fact "Object"
    (optional-string? (Object.)) => false))

(facts "other-than-string?"
  (fact "nil is nothing"
    (other-than-string? nil) => false)
  (fact "string is not other than string"
    (other-than-string? "") => false)
  (fact "booleas"
    (other-than-string? true) => true
    (other-than-string? false) => true)
  (fact "numbers"
    (other-than-string? 1) => true
    (other-than-string? 3.14) => true)
  (fact "collections"
    (other-than-string? {}) => true
    (other-than-string? #{}) => true
    (other-than-string? []) => true)
  (fact "Object"
    (other-than-string? (Object.)) => true))

(fact "lower-case"
  (lower-case nil)   => nil
  (lower-case "")    => ""
  (lower-case "a")   => "a"
  (lower-case "A")   => "a")

(facts "in-lower-case?"
  (facts "nil"
    (in-lower-case? nil) => false
    (in-lower-case? (lower-case nil)) => false)

  (facts "empty"
    (in-lower-case? "") => true
    (in-lower-case? (lower-case "")) => true)

  (in-lower-case? "a")   => true
  (in-lower-case? "aaaaaaaaaaaaaaa")  => true

  (in-lower-case? "A")                => false
  (in-lower-case? "AAAAAAAAAAAAAAA")  => false
  (in-lower-case? "AaAaAaAaAaAaAaA")  => false

  (in-lower-case? (lower-case "A"))   => true
  (in-lower-case? (lower-case "AaAaAaAaAaAaAaA"))   => true)

(fact "trim"
  (trim nil)    => nil
  (trim "")     => ""
  (trim "a")    => "a"
  (trim " a")   => "a"
  (trim "a ")   => "a"
  (trim " a ")  => "a")

(fact "capitalize"
      (capitalize nil) => nil
      (capitalize "")  => ""
      (capitalize "h") => "H"
      (capitalize "H") => "H"
      (capitalize "hello") => "Hello")

(fact "escaped-re-pattern"
  (escaped-re-pattern "^te-st[\\d]$.{2}") => #"\Q^te-st[\d]$.{2}\E"
  (escaped-re-pattern "Testitiedosto 3 {2}{2}(#(#){6}=.zip") => #"\QTestitiedosto 3 {2}{2}(#(#){6}=.zip\E")

(fact "unescape-html-scandinavian-characters"
  (unescape-html-scandinavian-characters "&auml;&Auml;&ouml;&Ouml;&aring;&Aring;")
  => "\u00e4\u00c4\u00f6\u00d6\u00e5\u00c5")
