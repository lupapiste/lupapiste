(ns sade.util-test
  (:refer-clojure :exclude [pos? neg? zero?])
  (:require [sade.util :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as sc])
  (:import [org.apache.commons.io.output NullWriter]))

(facts "strip-nils"
  (fact "Removes the whole key-value pair when value is nil"
    (strip-nils {:a 1 :b nil :c {:d 2 :e nil}}) => {:a 1 :c {:d 2}})
  (fact "Does not remove empty maps from inside sequential structures like list and vector"
    (strip-nils {:a nil
                 :b {:aa nil :bb nil}
                 :c {:aa nil :bb 2}
                 :d '({:a 11} {:b nil} :c nil)
                 :e [{:a "a"} {:b nil} "c" nil]})
    => {:b {}
        :c {:bb 2}
        :d '({:a 11} {} :c nil)
        :e [{:a "a"} {} "c" nil]}))

(facts "strip-empty-maps"
  (fact "Removes the whole key-value pair when value is an empty map"
    (strip-empty-maps {:a 1 :b {} :c {:d 2 :e {}}}) => {:a 1 :c {:d 2}})
  (fact "Does not remove empty maps from inside sequential structures like list and vector"
    (strip-empty-maps {:b {}
                       :c {:bb 2 :cc {}}
                       :d '({:a 11} {} :c nil)
                       :e [{:a "a"} {} "c" nil]})
    => {:c {:bb 2}
        :d '({:a 11} {} :c nil)
        :e [{:a "a"} {} "c" nil]}))

(facts
  (fact (dissoc-in {:a {:b \b :c \c}} [:a :b]) => {:a {:c \c}})
  (fact (dissoc-in {:a {:b \b :c \c}} [:a :x]) => {:a {:b \b :c \c}}))

(facts
  (fact (select {:a \a :b \b :c \c} [:a :c]) => [\a \c])
  (fact (select {:a \a :b \b :c \c} [:c :a]) => [\c \a])
  (fact (select {:a \a :b \b :c \c} [:x :a :y]) => [nil \a nil])
  (fact (select nil [:a :c]) => [nil nil])
  (fact (select {:a \a :b \b :c \c} nil) => nil))

(facts "some-key"
  (some-key nil) => nil
  (some-key nil nil) => nil
  (some-key nil :a) => nil
  (some-key {:a 1} nil) => nil
  (some-key {:a 1} :a) => 1
  (some-key {:a 1, :b 2} :a :b) => 1
  (some-key {:a 1, :b 2} :b :a) => 2
  (some-key {:a nil, :b 2} :a :b) => 2
  (some-key {:a false, :b 2} :a :b) => false)

(fact (positions #{2} [1 2 3 4 1 2 3 4]) => [1 5])

(facts "deep-merge-with"
  (fact
    (deep-merge-with + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
                     {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
    => {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}))

(facts "deep-merge"
  (fact "nil" (deep-merge nil) => nil)
  (fact "empty map" (deep-merge {}) => {})
  (fact "empty maps" (deep-merge {} {}) => {})
  (fact "empty & nil" (deep-merge {} nil) => {})
  (fact "nil in the middle" (deep-merge {} nil {}) => {})
  (fact "non-nested maps" (deep-merge {:a 1} {:b 2} {:c 3}) => {:a 1 :b 2 :c 3})
  (fact "non-nested maps, nil in the middle" (deep-merge {:a 1} nil {:b 1}) => {:a 1 :b 1})

  (fact "three deep maps"
    (deep-merge
      (assoc-in {} [:a :b :c] 2)
      (assoc-in {} [:a :b :d] 3)
      (assoc-in {} [:a :b :e] nil)) => {:a {:b {:c 2 :d 3 :e nil}}})

  (fact "last value wins"
    (deep-merge
    (assoc-in {} [:a :b :c] 2)
    (assoc-in {} [:a :b :d] 3)
    (assoc-in {} [:a :b :d] 4)
    (assoc-in {} [:a :b :d] 5)) => {:a {:b {:c 2 :d 5}}}))

(facts "Contains value"
  (fact (contains-value? nil nil) => false)
  (fact (contains-value? [] nil) => false)
  (fact (contains-value? nil true?) => false)
  (fact (contains-value? [] true?) => false)
  (fact (contains-value? [nil] true?) => false)
  (fact (contains-value? [false] true?) => false)
  (fact (contains-value? [true] true?) => true)
  (fact (contains-value? [true false] true?) => true)
  (fact (contains-value? [false true] true?) => true)
  (fact (contains-value? [false [false false [false [false {"false" true}]]]] true?) => true)
  (fact (contains-value? [false [false false [false [false {"true" false}]]]] true?) => false))

(fact "->int"
  (->int "010")  => 10
  (->int "-010") => -10
  (->int :-10)   => -10
  (->int -10)    => -10
  (->int -60/6)  => -10
  (->int "1.2")  => 0
  (->int "1.2")  => 0
  (->int "1.2" nil)  => nil)

(fact "fn->"
  (map (fn-> :a :b even?) [{:a {:b 2}}
                           {:a {:b 3}}
                           {:a {:b 4}}]) => [true false true])

(fact "fn->>"
  (map (fn->> :a (reduce +)) [{:a [1 2 3]}
                              {:a [2 3 4]}
                              {:a [3 4 5]}]) => [6 9 12])

(facts future*
  (binding [*out* (NullWriter.)
            *err* (NullWriter.)]
    (deref (future* (throw (Exception. "bang!")))))
  => (throws java.util.concurrent.ExecutionException "java.lang.Exception: bang!"))

(facts missing-keys
  (missing-keys ...what-ever... nil)          => (throws AssertionError)
  (missing-keys nil [:a :b :c])               => (just [:a :b :c] :in-any-order)
  (missing-keys {} [:a :b :c])                => (just [:a :b :c] :in-any-order)
  (missing-keys {:a 1} [:a :b :c])            => (just [:b :c] :in-any-order)
  (missing-keys {:b 1} [:a :b :c])            => (just [:a :c] :in-any-order)
  (missing-keys {:a 1 :b 1} [:a :b :c])       => [:c]
  (missing-keys {:a 1 :b 1 :c 1} [:a :b :c])  => nil
  (missing-keys {:a false} [:a])              => nil
  (missing-keys {:a nil} [:a])                => [:a])

(facts "Local vs. UTC from timestamp"
  (fact (to-local-datetime 1412092916016) => "30.09.2014 19:01")
  (fact (to-xml-datetime 1412092916016) => "2014-09-30T16:01:56"))

(facts "to-xml-date"
  (fact "nil -> nil" (to-xml-date nil) => nil)
  (fact "0 -> 1970"  (to-xml-date 0) => "1970-01-01"))

(facts "to-xml-datetime"
  (fact "nil -> nil" (to-xml-datetime nil) => nil)
  (fact "0 -> 1970"  (to-xml-datetime 0) => "1970-01-01T00:00:00"))

(facts "to-xml-date-from-string"
  (fact "nil -> nil" (to-xml-date-from-string nil) => nil)
  (fact "'' -> nil" (to-xml-date-from-string "") => nil)
  (fact "valid date" (to-xml-date-from-string "1.1.2013") => "2013-01-01")
  (fact "invalid date" (to-xml-date-from-string "1.2013") => (throws java.lang.IllegalArgumentException)))

(facts "to-xml-datetime-from-string"
  (fact "nil -> nil" (to-xml-datetime-from-string nil) => nil)
  (fact "valid date" (to-xml-datetime-from-string "1.1.2013") => "2013-01-01T00:00:00")
  (fact "invalid date" (to-xml-datetime-from-string "1.2013") => (throws java.lang.IllegalArgumentException)))

(facts "to-millis-from-local-date-string"
  (fact "nil -> nil" (to-millis-from-local-date-string nil) => nil)
  (fact "valid date" (to-millis-from-local-date-string "1.1.2013") => 1356998400000)
  (fact "invalid date" (to-millis-from-local-date-string "1.2013") => (throws java.lang.IllegalArgumentException)))

(facts "to-xml-time-from-string"
  (fact "nil -> nil" (to-xml-time-from-string nil) => nil)
  (fact "valid times"
    (to-xml-time-from-string "12:12") => "12:12:00"
    (to-xml-time-from-string "00:00:01") => "00:00:01"
    (to-xml-time-from-string "12:59:59.9") => "12:59:59.9"
    (to-xml-time-from-string "0:0") => "00:00:00"
    (to-xml-time-from-string "0:10") => "00:10:00"
    (to-xml-time-from-string "1:0") => "01:00:00"))

(facts sequable?
  (sequable? [])        => true
  (sequable? '())       => true
  (sequable? {})        => true
  (sequable? "")        => true
  (sequable? nil)       => true
  (sequable? (.toArray (java.util.ArrayList.))) => true
  (sequable? 1)         => false
  (sequable? true)      => false)

(facts empty-or-nil?
  (empty-or-nil? [])      => true
  (empty-or-nil? [1])     => false
  (empty-or-nil? {})      => true
  (empty-or-nil? {:a :a}) => false
  (empty-or-nil? '())     => true
  (empty-or-nil? false)   => false
  (empty-or-nil? true)    => false
  (empty-or-nil? "")      => true
  (empty-or-nil? nil)     => true)

(facts boolean?
  (boolean? true) => true
  (boolean? false) => true
  (boolean? (Boolean. true)) => true
  (boolean? (Boolean. false)) => true
  (boolean? nil) => false
  (boolean? "") => false
  (boolean? []) => false
  (boolean? {}) => false)

(facts assoc-when
  (assoc-when {} :a :a, :b nil, :c [], :d {}, :e [:e], :f {:f :f})
  => {:a :a, :e [:e], :f {:f :f}}
  (assoc-when {:a nil :b :b} :a :a, :b nil, :c :c)
  => {:a :a, :b :b, :c :c})

(facts finnish-y?
  (fact (finnish-y? nil)           => falsey)
  (fact (finnish-y? "")            => falsey)
  (fact (finnish-y? "foo")         => falsey)
  (fact (finnish-y? "2341529-4")   => falsey)
  (fact (finnish-y? "2341528-4")   => truthy))

(facts finnish-ovt?
  (fact (finnish-ovt? nil)             => falsey)
  (fact (finnish-ovt? "")              => falsey)
  (fact (finnish-ovt? "foo")           => falsey)
  (fact (finnish-ovt? "1234")          => falsey)
  (fact (finnish-ovt? "12345")         => falsey)
  (fact (finnish-ovt? "003712345")     => falsey)
  (fact (finnish-ovt? "003723415284")  => truthy)
  (fact (finnish-ovt? "0037234152841") => truthy)
  (fact (finnish-ovt? "00372341528412") => truthy)
  (fact (finnish-ovt? "003723415284123") => truthy)
  (fact (finnish-ovt? "0037234152841234") => truthy)
  (fact (finnish-ovt? "00372341528412345") => truthy)
  (fact (finnish-ovt? "003723415284123456") => falsey)
  (fact (finnish-ovt? "003701902735") => truthy)
  (fact (finnish-ovt? "003710601555") => truthy)
  (fact "invalid y"
    (finnish-ovt? "003723415294")  => falsey)
  (facts "Alphabetic suffix"
    (fact (finnish-ovt? "003718523029101CZ") => truthy)))

(facts "rakennustunnus?"
  (fact (rakennustunnus? nil) => falsey)
  (fact (rakennustunnus? "") => falsey)
  (fact (rakennustunnus? "foo") => falsey)
  (fact (rakennustunnus? "1a") => falsey)
  (fact (rakennustunnus? "1A") => falsey)
  (fact (rakennustunnus? "903048741J") => truthy)
  (fact "SYKE sample with a fixed checksum" (rakennustunnus? "100012345N") => truthy)
  (fact "VRK sample with a fixed checksum" (rakennustunnus? "1234567892") => truthy))

(facts max-length
  (fact (sc/check (max-length 1) []) => nil)
  (fact (sc/check (max-length 1) [1]) => nil)
  (fact (sc/check (max-length 1) [1 2]) =not=> nil))

(facts max-length-string
  (fact (sc/check (max-length-string 1) "a") => nil)
  (fact (sc/check (max-length-string 1) "ab") =not=> nil)
  (fact (sc/check (max-length-string 1) [1]) =not=> nil))

(facts "comparing history item difficulties"
  (let [values (vec (map :name (:body lupapalvelu.document.schemas/patevyysvaatimusluokka)))]
    (fact "nil and item"          (compare-difficulty :difficulty values nil {:difficulty "A"})                => pos?)
    (fact "item and nil"          (compare-difficulty :difficulty values {:difficulty "A"} nil)                => neg?)
    (fact "old more difficult"    (compare-difficulty :difficulty values {:difficulty "A"} {:difficulty "B"})  => neg?)
    (fact "new more difficult"    (compare-difficulty :difficulty values {:difficulty "B"} {:difficulty "A"})  => pos?)
    (fact "tricky difficulty val" (compare-difficulty :difficulty values {:difficulty "A"} {:difficulty "AA"}) => pos?)
    (fact "equality"              (compare-difficulty :difficulty values {:difficulty "B"} {:difficulty "B"})  => zero?)))

(facts select-values
  (let [m {:foo "foo" :bar "bar" :baz "baz"}]
    (fact (select-values m [])                    => [])
    (fact (select-values m [:foo :bar])           => ["foo" "bar"])
    (fact (select-values m [:bar :foo])           => ["bar" "foo"])
    (fact (select-values m [:foo :unknown :bar])  => ["foo" nil "bar"])
    (fact (select-values m [:unknown1 :unknown2]) => [nil nil])))

(facts "to-long"
  (fact (to-long "1234") => truthy)
  (fact (to-long "213asd2") => nil)
  (fact (to-long "") => nil)
  (fact (to-long 1234) => nil))
