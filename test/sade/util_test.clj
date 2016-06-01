(ns sade.util-test
  (:refer-clojure :exclude [pos? neg? zero?])
  (:require [sade.util :refer :all]
            [sade.strings :as ss]
            [sade.env :as env]
            [midje.sweet :refer :all]
            [schema.core :as sc]
            [lupapalvelu.document.schemas :as schema])
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
  (fact (contains-value? [] nil) => false)
  (fact (contains-value? nil nil) => true)
  (fact (contains-value? [nil] nil) => true)
  (fact (contains-value? :a :a) => true)
  (fact (contains-value? #{:a} :a) => true)
  (fact (contains-value? #{:a} :b) => false)
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
  (fact "0 -> 1970"  (to-xml-date 0) => "1970-01-01")
  (fact "UTC midnight"
    (to-xml-date (.getTime #inst "2016-02-03T00:00:00Z")) => "2016-02-03")
  (fact "Local midnight"
    (to-xml-date (.getTime #inst "2016-02-03T00:00:00+02:00")) => "2016-02-03"))

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

(facts "comparing history item difficulties"
  (let [values (vec (map :name (:body schema/patevyysvaatimusluokka)))]
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

(facts "relative-local-url?"
  (relative-local-url? nil) => false
  (relative-local-url? "") => true
  (relative-local-url? "http://localhost") => false
  (relative-local-url? "//localhost") => false
  (relative-local-url? "/localhost") => true
  (relative-local-url? "../localhost") => true
  (relative-local-url? "!/applications/") => true
  (relative-local-url? "#!/applications/") => true
  (relative-local-url? "/../../../app/fi/admin") => true)

(facts "version-is-greater-or-equal"
  (fact "source (evaluated) version"
    (version-is-greater-or-equal "2"     {:major 2 :minor 1 :micro 5}) => false
    (version-is-greater-or-equal "2.1"   {:major 2 :minor 1 :micro 5}) => false
    (version-is-greater-or-equal "2.1.4" {:major 2 :minor 1 :micro 5}) => false
    (version-is-greater-or-equal "2.1.5" {:major 2 :minor 1 :micro 5}) => true
    (version-is-greater-or-equal "2.1.6" {:major 2 :minor 1 :micro 5}) => true

    (version-is-greater-or-equal "2"     {:major 2 :minor 0 :micro 0}) => true
    (version-is-greater-or-equal "2.1"   {:major 2 :minor 1 :micro 0}) => true)
  (fact "target version"
    (version-is-greater-or-equal 2.1     {:major 2 :minor 1 :micro 5}) => (throws AssertionError)
    (version-is-greater-or-equal "2.1.4" "2.1.5")                      => (throws AssertionError)
    (version-is-greater-or-equal "2.1.4" {:major 2 :minor 1})          => (throws AssertionError)))

(fact "is-latest-of?"
  (is-latest-of? 0 [1 2 3]) => false
  (is-latest-of? 1 [1 2 3]) => false
  (is-latest-of? 3 [1 2 4]) => false
  (is-latest-of? 4 [1 2 3]) => true

  (is-latest-of? 2.2 [1 2 3]) => (throws AssertionError)
  (is-latest-of? 2 nil) =>       (throws AssertionError))

(facts "list-jar"
  (let [separator env/file-separator
        path [(System/getProperty "user.home")
              ".m2" "repository" "org" "clojure" "clojure" (clojure-version)
              (str "clojure-"(clojure-version) ".jar")]
        clojure-jar (ss/join separator path)]

    (fact "clojure.jar contains core.clj in 'clojure/' path"
      (let [files (set (list-jar clojure-jar "clojure/"))]
        (files "core.clj") => "core.clj"))

    (fact "clojure.jar contains io.clj in '/clojure/java' path"
      (let [files (set (list-jar clojure-jar "/clojure/java"))]
        (files "io.clj") => "io.clj"))

    (fact "clojure.jar contains manifest"
      (fact "empty filter"
        (let [files (set (list-jar clojure-jar ""))]
         (files "META-INF/MANIFEST.MF") => "META-INF/MANIFEST.MF"))

      (fact "root filter"
        (let [files (set (list-jar clojure-jar "/"))]
          (files "META-INF/MANIFEST.MF") => "META-INF/MANIFEST.MF"))

      (fact "nil filter"
        (let [files (set (list-jar clojure-jar nil))]
          (files "META-INF/MANIFEST.MF") => "META-INF/MANIFEST.MF")))))

(facts "=as-kw"

  (facts "single param"
    (fact "str"  (=as-kw "str") => true)
    (fact ":str" (=as-kw ::str) => true))

  (fact "str str"   (=as-kw "str" "str") => true)
  (fact ":str :str" (=as-kw ::str ::str) => true)

  (facts "type conversions"
    (fact "str :str"  (=as-kw "str" :str) => true)
    (fact ":str str"  (=as-kw :str "str") => true)

    (fact ":str str :str" (=as-kw :str "str" :str) => true)
    (fact "str :str str"  (=as-kw "str" :str "str") => true)

    (fact ":str str :str :str" (=as-kw :str "str" :str :str) => true)
    (fact "str :str str :str"  (=as-kw "str" :str "str" :str) => true)
    (fact ":str :str str str"  (=as-kw :str :str "str" "str") => true))

  (facts "nils"
    (fact "nil"  (=as-kw nil) => true)
    (fact "str nil"   (=as-kw "str" nil) => false)
    (fact "nil :str" (=as-kw nil :str) => false))

  (facts "different values"
    (fact "foo :bar"  (=as-kw "foo" :bar) => false)
    (fact "foo :bar foo"  (=as-kw "foo" :bar "foo") => false)
    (fact "foo foo :bar"  (=as-kw "foo" "foo" :bar) => false)))

(facts "not=as-kw"

  (facts "single param"
    (fact "str"  (not=as-kw "str") => false)
    (fact ":str" (not=as-kw :str) => false))

  (fact "str str"   (not=as-kw "str" "str2") => true)
  (fact ":str :str" (not=as-kw :str :str2) => true)

  (facts "type conversions"
    (fact "str :str"  (not=as-kw "str" :str2) => true)
    (fact ":str str"  (not=as-kw :str "str") => false)

    (fact ":str str :str" (not=as-kw :str "str2" :str3) => true)
    (fact "str :str str"  (not=as-kw "str" :str "str") => false)

    (fact ":str str :str :str" (not=as-kw :str "str" :str :str) => false)
    (fact "str :str str :str"  (not=as-kw "str" :str2 "str" :str) => true)
    (fact ":str :str str str"  (not=as-kw :str :str "str3" "str") => true))

  (facts "nils"
    (fact "nil"  (not=as-kw nil) => false)
    (fact "str nil"   (not=as-kw "str" nil) => true)
    (fact "nil :str" (not=as-kw nil :str) => true))

  (facts "different values"
    (fact "foo :bar"  (not=as-kw "foo" :bar) => true)
    (fact "foo :bar foo"  (not=as-kw "foo" :bar "foo") => true)
    (fact "foo foo :bar"  (not=as-kw "foo" "foo" :bar) => true)))
