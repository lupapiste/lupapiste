(ns sade.strings-test
  (:require [clojure.edn :as edn]
            [midje.sweet :refer :all]
            [sade.strings :as ss]))

(facts "Test last-n"
  (fact "Nil-safe"
    (ss/last-n nil nil) => nil)
  (fact "Empty remains empty"
    (ss/last-n 0 "") => "")
  (fact "Zero lenght returns empty"
    (ss/last-n 0 "foo") => "")
  (fact "One"
    (ss/last-n 1 "1") => "1")
  (fact "Two"
    (ss/last-n 2 "10") => "10")
  (fact "Three"
    (ss/last-n 1 "11") => "1")
  (fact "Four"
    (ss/last-n 5 "Four") => "Four")  )

(facts "limit"
  (ss/limit nil nil) => nil
  (ss/limit "abcdefg" nil) => nil
  (ss/limit nil 2) => nil
  (ss/limit "abcdefg" 2) => "ab"
  (ss/limit "abcdefg" 2 nil) => "ab"
  (ss/limit "abcdefg" 2 "...") => "ab..."
  (ss/limit "abcdefg" 20) => "abcdefg")

(facts "string-split"
  (ss/string-split nil nil) => nil
  (ss/string-split nil nil nil) => nil
  (ss/string-split "hello.world.once.more" ".")
  => ["hello" "world" "once" "more"]
  (ss/string-split "hello.world.once.more" "." 3)
  => ["hello" "world" "once.more"]
  (ss/string-split "hello.world.once.more" "." 1)
  => ["hello.world.once.more"]
  (ss/string-split "hello.world.once.more" "." 0)
  => ["hello" "world" "once" "more"]
  (ss/string-split "hello.world.once.more" "." -1)
  => ["hello" "world" "once" "more"]
  (ss/string-split "hello.world.once.more" "." :not-a-number)
  => ["hello" "world" "once" "more"]
  (ss/string-split "hello.world.once.more" "." 100)
  => ["hello" "world" "once" "more"]
  (ss/string-split "hello.world.once.more" #".+")
  => ["hello.world.once.more"]
  (ss/string-split "hello.world.once.more" "world" nil)
  => ["hello." ".once.more"])

(facts "suffix"
  (ss/suffix nil nil) => nil
  (ss/suffix nil "nil") => nil
  (ss/suffix "nil" nil) => "nil"
  (ss/suffix "nil" "") => "nil"
  (ss/suffix "" "") => ""
  (ss/suffix "" "foo") => ""
  (ss/suffix "foo" "") => "foo"
  (ss/suffix "foo" "foo") => ""
  (ss/suffix "foo.bar" ".") => "bar"
  (ss/suffix "ababa" "b") => "a"
  (ss/suffix "gaah---haag!" "---") => "haag!"
  (ss/suffix "hello" "world") => "hello")

(facts "Test de-accent"
  (fact (ss/de-accent nil) => nil)
  (fact (ss/de-accent "Å") => "A")
  (fact (ss/de-accent "ö") => "o")
  (fact (ss/de-accent "é") => "e")
  (fact (ss/de-accent "ÄöÅ") => "AoA")  )

(facts "normalize"
  (let [umlaut  (char 776)
        baddish (ss/join umlaut "AaOoUux")
        normed  "ÄäÖöÜüx"]
    (ss/normalize nil) => nil
    (ss/normalize "") => ""
    (ss/normalize " ") => " "
    (ss/normalize "Hello World!") => "Hello World!"
    baddish =not=> normed
    (ss/normalize baddish) => normed
    (ss/normalize normed) => normed))

(facts "remove-leading-zeros"
  (fact (ss/remove-leading-zeros nil) => nil)
  (fact (ss/remove-leading-zeros "") => "")
  (fact (ss/remove-leading-zeros "01234") => "1234")
  (fact (ss/remove-leading-zeros "0001234a") => "1234a")
  (fact (ss/remove-leading-zeros "101234") => "101234")
  (fact (ss/remove-leading-zeros "0") => "0")
  (fact (ss/remove-leading-zeros "0000000") => "0")
  (fact (ss/remove-leading-zeros "0000009") => "9"))

(facts "zero-pad"
  (ss/zero-pad 4 "1")     => "0001"
  (ss/zero-pad 4 "12")    => "0012"
  (ss/zero-pad 4 "123")   => "0123"
  (ss/zero-pad 4 "1234")  => "1234"
  (ss/zero-pad 4 "12345") => "12345"
  (ss/zero-pad 4 "")      => "0000"
  (ss/zero-pad 4 nil)     => "0000"
  (ss/zero-pad 4 "  ")    => "00  "
  (ss/zero-pad -4 "a")    => "a")

(facts "starts-with?"
  (fact (ss/starts-with? "foo" "f")    => truthy)
  (fact (ss/starts-with? "foo" "fo")   => truthy)
  (fact (ss/starts-with? "foo" "foo")  => truthy)
  (fact (ss/starts-with? "foo" "fooo") => falsey)
  (fact (ss/starts-with? "foo" "ba")   => falsey)
  (fact (ss/starts-with? "foo" nil)    => falsey)
  (fact (ss/starts-with? nil "ba")     => falsey))

(facts "starts-with-i?"
  (fact (ss/starts-with-i? "Foo" "f")    => truthy)
  (fact (ss/starts-with-i? "foo" "Fo")   => truthy)
  (fact (ss/starts-with-i? "foO" "fOo")  => truthy)
  (fact (ss/starts-with-i? "foo" "fooo") => falsey)
  (fact (ss/starts-with-i? "foo" "ba")   => falsey)
  (fact (ss/starts-with-i? "foo" nil)    => falsey)
  (fact (ss/starts-with-i? nil "ba")     => falsey))

(facts "ends-with?"
  (fact (ss/ends-with? "foo" "o")    => truthy)
  (fact (ss/ends-with? "foo" "oo")   => truthy)
  (fact (ss/ends-with? "foo" "foo")  => truthy)
  (fact (ss/ends-with? "foo" "fooo") => falsey)
  (fact (ss/ends-with? "foo" "ba")   => falsey)
  (fact (ss/ends-with? "foo" nil)    => falsey)
  (fact (ss/ends-with? nil "ba")     => falsey))

(facts "ends-with-i?"
  (fact (ss/ends-with-i? "foo" "O")    => truthy)
  (fact (ss/ends-with-i? "foO" "Oo")   => truthy)
  (fact (ss/ends-with-i? "fOO" "foo")  => truthy)
  (fact (ss/ends-with-i? "foo" nil)    => falsey)
  (fact (ss/ends-with-i? nil "ba")     => falsey))

(facts "numeric?"
  (ss/numeric? ["0"]) => false
  (ss/numeric? nil) => false
  (ss/numeric? "") => false
  (ss/numeric? "0xff") => false
  (ss/numeric? 123) => false
  (ss/numeric? "123") => true
  (ss/numeric? "-123") => true
  (ss/numeric? " -123") => false
  (ss/numeric? "+123") => true
  (ss/numeric? "0000") => true
  (ss/numeric? "+123 ") => false
  (ss/numeric? " 123") => false)

(facts "decimal-number?"
  (fact (ss/decimal-number? "1") => true)
  (fact (ss/decimal-number? "1.1") => true)
  (fact (ss/decimal-number? "a") => false)
  (fact (ss/decimal-number? "") => false)
  (fact (ss/decimal-number? nil) => false)
  (fact (ss/decimal-number? "a.0") => false)
  (fact (ss/decimal-number? ".0") => false)
  (fact (ss/decimal-number? "..0") => false)
  (fact (ss/decimal-number? "123123132.456465465465464") => true))

(fact "optional-string?"
  (fact "nil is optional"
    (ss/optional-string? nil) => true)
  (fact "strings"
    (ss/optional-string? "") => true
    (ss/optional-string? "hello") => true)
  (fact "booleas"
    (ss/optional-string? true) => false
    (ss/optional-string? false) => false)
  (fact "numbers"
    (ss/optional-string? 1) => false
    (ss/optional-string? 3.14) => false)
  (fact "collections"
    (ss/optional-string? {}) => false
    (ss/optional-string? #{}) => false
    (ss/optional-string? []) => false)
  (fact "Object"
    (ss/optional-string? (Object.)) => false))

(facts "other-than-string?"
  (fact "nil is nothing"
    (ss/other-than-string? nil) => false)
  (fact "string is not other than string"
    (ss/other-than-string? "") => false)
  (fact "booleas"
    (ss/other-than-string? true) => true
    (ss/other-than-string? false) => true)
  (fact "numbers"
    (ss/other-than-string? 1) => true
    (ss/other-than-string? 3.14) => true)
  (fact "collections"
    (ss/other-than-string? {}) => true
    (ss/other-than-string? #{}) => true
    (ss/other-than-string? []) => true)
  (fact "Object"
    (ss/other-than-string? (Object.)) => true))

(fact "lower-case"
  (ss/lower-case nil)   => nil
  (ss/lower-case "")    => ""
  (ss/lower-case "a")   => "a"
  (ss/lower-case "A")   => "a")

(facts "in-lower-case?"
  (facts "nil"
    (ss/in-lower-case? nil) => false
    (ss/in-lower-case? (ss/lower-case nil)) => false)

  (facts "empty"
    (ss/in-lower-case? "") => true
    (ss/in-lower-case? (ss/lower-case "")) => true)

  (ss/in-lower-case? "a")   => true
  (ss/in-lower-case? "aaaaaaaaaaaaaaa")  => true

  (ss/in-lower-case? "A")                => false
  (ss/in-lower-case? "AAAAAAAAAAAAAAA")  => false
  (ss/in-lower-case? "AaAaAaAaAaAaAaA")  => false

  (ss/in-lower-case? (ss/lower-case "A"))   => true
  (ss/in-lower-case? (ss/lower-case "AaAaAaAaAaAaAaA"))   => true)

(fact "trim"
  (ss/trim nil)    => nil
  (ss/trim "")     => ""
  (ss/trim "a")    => "a"
  (ss/trim " a")   => "a"
  (ss/trim "a ")   => "a"
  (ss/trim " a ")  => "a")

(fact "triml"
  (ss/triml nil)    => nil
  (ss/triml "")     => ""
  (ss/triml "a")    => "a"
  (ss/triml " a")   => "a"
  (ss/triml "a ")   => "a "
  (ss/triml " a ")  => "a ")

(fact "trimr"
  (ss/trimr nil)    => nil
  (ss/trimr "")     => ""
  (ss/trimr "a")    => "a"
  (ss/trimr " a")   => " a"
  (ss/trimr "a ")   => "a"
  (ss/trimr " a ")  => " a")

(fact "capitalize"
      (ss/capitalize nil) => nil
      (ss/capitalize "")  => ""
      (ss/capitalize "h") => "H"
      (ss/capitalize "H") => "H"
      (ss/capitalize "hello") => "Hello")

(fact "escaped-re-pattern"
  (ss/escaped-re-pattern "^te-st[\\d]$.{2}") => #"\Q^te-st[\d]$.{2}\E"
  (ss/escaped-re-pattern "Testitiedosto 3 {2}{2}(#(#){6}=.zip") => #"\QTestitiedosto 3 {2}{2}(#(#){6}=.zip\E")

(fact "unescape-html-scandinavian-characters"
  (ss/unescape-html-scandinavian-characters "&auml;&Auml;&ouml;&Ouml;&aring;&Aring;")
  => "\u00e4\u00c4\u00f6\u00d6\u00e5\u00c5")

(fact "strip-trailing-slashes"
  (ss/strip-trailing-slashes nil) => nil
  (ss/strip-trailing-slashes "") => ""
  (ss/strip-trailing-slashes "foo") => "foo"
  (ss/strip-trailing-slashes "/foo") => "/foo"
  (ss/strip-trailing-slashes "/foo/") => "/foo"
  (ss/strip-trailing-slashes "/foo///") => "/foo"
  (ss/strip-trailing-slashes "/foo///fofo///") => "/foo///fofo")

(fact "serialize"
  (pr-str (map #(do (println "hello" %)
                    (inc %))
               (range 3))) => "(hello 0\nhello 1\nhello 2\n1 2 3)"
  (ss/serialize (map #(do (println "hello" %)
                          (inc %))
                     (range 3))) => "(1 2 3)"
  (edn/read-string (ss/serialize (map #(do (println "hello" %)
                                           (inc %))
                                      (range 3))))
  => '(1 2 3))

(facts "join-non-blanks"
  (ss/join-non-blanks ["hello" "  " "world" nil "!"]) => "helloworld!"
  (ss/join-non-blanks ", " ["hello" "  " "world" nil "!"]) => "hello, world, !"
  (ss/join-non-blanks nil) => ""
  (ss/join-non-blanks ":" nil) => ""
  (ss/join-non-blanks []) => ""
  (ss/join-non-blanks ":" []) => "")

(facts "stringwalk"
  (ss/stringwalk [] nil) => (throws)
  (ss/stringwalk ss/upper-case []) => []
  (ss/stringwalk ss/upper-case " hello ") => " HELLO "
  (ss/stringwalk ss/upper-case :foo) => :foo
  (ss/stringwalk ss/upper-case 123) => 123
  (ss/stringwalk #(->> (ss/trim %) reverse (apply str) ss/upper-case)
                 {:number 123
                  :string "  string  "
                  :nil    nil
                  :map    {:one   "  one  "
                           :two   [1 2 "  34  " 5]
                           :three :three}})
  => {:number 123
      :string "GNIRTS"
      :nil    nil
      :map    {:one   "ENO"
               :two   [1 2 "43" 5]
               :three :three}})

(facts "trimwalk"
  (ss/trimwalk nil) => nil
  (ss/trimwalk []) => []
  (ss/trimwalk "  hello  ") => "hello"
  (ss/trimwalk :foo) => :foo
  (ss/trimwalk 123) => 123
  (ss/trimwalk {:number 123
                :string "  string  "
                :nil    nil
                :map    {:one   "  one  "
                         :two   [1 2 "  3  " 4]
                         :three :three}})
  => {:number 123
      :string "string"
      :nil    nil
      :map    {:one   "one"
               :two   [1 2 "3" 4]
               :three :three}})

(facts "fuzzy-re"
  (ss/fuzzy-re "test") => "^.*\\Qtest\\E.*$"
  (ss/fuzzy-re "test-osteroni") => "^.*\\Qtest\\E.+\\Qosteroni\\E.*$"
  (ss/fuzzy-re "") => nil
  (ss/fuzzy-re nil) => nil)

(facts "join-file-path"
  (ss/join-file-path nil) => nil
  (ss/join-file-path "") => nil
  (ss/join-file-path ["/"]) => "/"
  (ss/join-file-path "one" "two" "three") => "one/two/three"
  (ss/join-file-path [["one" nil "  " " " ["two" [[["three"]]]]]]) => "one/two/three"
  (ss/join-file-path "////one////two///three////") => "/one/two/three/"
  (ss/join-file-path "/" "/" "/") => "/"
  (ss/join-file-path " one " " two ") => "one / two")

(facts "->inputstream"
  (ss/->inputstream nil) => nil
  (-> "" ss/->inputstream slurp) => ""
  (-> "ääkköset 123" ss/->inputstream slurp) => "ääkköset 123")

(facts "format-fixed"
  (ss/format-fixed "") => ""
  (ss/format-fixed "%.4f" 2.0) => "2.0000"
  (ss/format-fixed "%.1f %.2f %.3f" 1.0 2.0 3.0) => "1.0 2.00 3.000")

(facts "blank-as-nil"
  (ss/blank-as-nil nil) => nil
  (ss/blank-as-nil "") => nil
  (ss/blank-as-nil " ") => nil
  (ss/blank-as-nil "hello") => "hello"
  (ss/blank-as-nil " hello ") => " hello ")

(facts "trim-or-nil"
  (ss/trim-or-nil nil) => nil
  (ss/trim-or-nil "") => nil
  (ss/trim-or-nil " ") => nil
  (ss/trim-or-nil "hello") => "hello"
  (ss/trim-or-nil " hello ") => "hello"
  (ss/trim-or-nil "  hello") => "hello"
  (ss/trim-or-nil "hello  ") => "hello")

(facts "trim-join"
  (ss/trim-join "" ["hello" "  " "world" nil "!"]) => "helloworld!"
  (ss/trim-join " " " hello " "  " "  world  " nil "  !  ") => "hello world !"
  (ss/trim-join ", " ["hello" "  " "world" nil "!"]) => "hello, world, !"
  (ss/trim-join nil nil) => nil
  (ss/trim-join ":" nil) => nil
  (ss/trim-join []) => nil
  (ss/trim-join ":" []) => nil
  (ss/trim-join ":" " " "   " nil) => nil
  (ss/trim-join ":" [[[" " "   " nil]]]) => nil
  (ss/trim-join " - " ["  one  " ["   two " [[[" three "]]]]]) => "one - two - three")

(facts "decimal-formatter-fn"
  (fact "fixed point"
    (let [f (ss/decimal-formatter-fn 1 1)]
      (f nil)       => nil
      (f 0)         => "0.0"
      (f 10)        => "10.0"
      (f 1)         => "1.0"
      (f 1.5)       => "1.5"
      (f 1.55)      => "1.6"
      (f 1.555)     => "1.6"
      (f -0)        => "0.0"
      (f -0.005)    => "-0.0"
      (f -0.05)     => "-0.1"
      (f -0.5)      => "-0.5"
      (f -1)        => "-1.0"
      (f -1.5)      => "-1.5"
      (f -1.55)     => "-1.6"
      (f -1.555)    => "-1.6"
      (f -10)       => "-10.0"
      (f 21212)     => "21212.0"
      (f 21212.0)   => "21212.0"
      (f 21212.1)   => "21212.1"
      (f 21212.5)   => "21212.5"
      (f -21212)    => "-21212.0"
      (f -21212.0)  => "-21212.0"
      (f -21212.1)  => "-21212.1"
      (f -21212.5)  => "-21212.5"))
  (fact "decimals only if needed"
    (let [f (ss/decimal-formatter-fn 0 2)]
      (f nil)       => nil
      (f 0)         => "0"
      (f 10)        => "10"
      (f 1)         => "1"
      (f 1.5)       => "1.5"
      (f 1.55)      => "1.55"
      (f 1.555)     => "1.55"
      (f -0)        => "0"
      (f -0.005)    => "-0.01"
      (f -0.05)     => "-0.05"
      (f -0.5)      => "-0.5"
      (f -1)        => "-1"
      (f -1.5)      => "-1.5"
      (f -1.55)     => "-1.55"
      (f -1.555)    => "-1.55"
      (f -10)       => "-10"
      (f 21212)     => "21212"
      (f 21212.0)   => "21212"
      (f 21212.1)   => "21212.1"
      (f 21212.5)   => "21212.5"
      (f -21212)    => "-21212"
      (f -21212.0)  => "-21212"
      (f -21212.1)  => "-21212.1"
      (f -21212.5)  => "-21212.5")))

(facts "includes?"
  (ss/includes? nil nil) => false
  (ss/includes? "" "") => true
  (ss/includes? "" nil) => false
  (ss/includes? "hello" "") => true
  (ss/includes? "hello" "world") => false
  (ss/includes? "helloworld" "low") => true)

(facts "includes-i?"
  (ss/includes-i? nil nil) => false
  (ss/includes-i? "" "") => true
  (ss/includes-i? "" nil) => false
  (ss/includes-i? "hello" "") => true
  (ss/includes-i? "hello" "world") => false
  (ss/includes-i? "helloworld" "low") => true
  (ss/includes-i? " HelLowOrLD  " "   lOw" ) => true)
