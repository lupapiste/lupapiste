(ns sade.util-test
  (:refer-clojure :exclude [pos? neg? zero? max-key])
  (:require [babashka.fs :as fs]
            [lupapalvelu.document.schemas :as schema]
            [midje.sweet :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer :all])
  (:import [org.apache.commons.io.output NullWriter]))

(facts max-key
  (fact "single arity"
    (max-key :foo) => nil?)

  (fact "key not found"
    (max-key :foo {:bar 1} {:bar 3} {:bar 3}) => nil?)

  (fact "one element"
    (max-key :foo {:foo 1}) => {:foo 1})

  (fact "multiple elements"
    (max-key :foo {:foo 1} {:foo 3} {:foo 2}) => {:foo 3})

  (fact "multiple elements - nil included in values"
    (max-key :foo {:foo 1} {:foo 3} {:foo nil}) => {:foo 3})

  (fact "multiple elements - key not found in every map"
    (max-key :foo {:foo 1} {:foo 3} {:bar 9}) => {:foo 3}))

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

(facts "strip-matches"
  (let [m {:a 1 :b {:c 2} :d 3 :e {:f 4}}]
    (strip-matches m #(and (number? %) (odd? %))) => {:b {:c 2} :e {:f 4}}
    (strip-matches m #(and (number? %) (even? %))) => {:a 1 :b {} :d 3 :e {}}))

(facts "strip-blanks"
  (strip-blanks {:a "a" :b {:c "  "} :d nil :e {:f "f" :g ""}})
  => {:a "a" :b {} :e {:f "f"}})

(facts "dissoc-in"
  (fact (dissoc-in {:a {:b \b :c \c}} [:a :b]) => {:a {:c \c}})
  (fact (dissoc-in {:a {:b \b :c \c}} [:a :x]) => {:a {:b \b :c \c}})
  (fact (dissoc-in {:a {:b "hello"}} [:a :b :c]) => {:a {:b "hello"}})
  (fact (dissoc-in {:a {:b ["x" "y" {:foo {:bar "hello"}}]}} [:a :b 2 :foo :bar])
    => {:a {:b ["x" "y"]}})
  (fact (dissoc-in {:a {:b ["x" "y" {:foo {:bar "hello"}}]}} [:a :b 2 :foo :bar :zoom])
    => {:a {:b ["x" "y" {:foo {:bar "hello"}}]}})
  (fact (dissoc-in {:a {:b ["x" "y" {:foo {:bar "hello"}}]}} [:a :b 2])
    => {:a {:b ["x" "y"]}})
  (fact (dissoc-in {:a {:b ["x" "y" {:foo {:bar "hello"}}]}} [:a :b])
    => {})
  (fact (dissoc-in {:a [[{:b "hii"}]]} [:a 0 0 :b]) => {})
  (fact (dissoc-in [{:a [[{:b "hii"}]]}] [0 :a 0 0 :b]) => [])
  (fact (dissoc-in [2 [3 [4 [[5]]]]] [1 1 1 0 0]) => [2 [3 [4]]])
  (fact (dissoc-in [2 [3 [4 [[5]]]]] [1 1 1 0]) => [2 [3 [4]]])
  (fact (dissoc-in [2 [3 [4 [[5]]]]] [1 1 1]) => [2 [3 [4]]])
  (fact (dissoc-in [2 [3 [4 [[5]]]]] [1 1]) => [2 [3]])
  (fact (dissoc-in [2 [3 [4 [[5]]]]] [1]) => [2]))

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

(facts position-by-id
  (fact "found in one item collection"
    (position-by-id ..id.. [{:id ..id..}]) => 0)

  (fact "not found in one item collection"
    (position-by-id ..id.. [{:id ..another-id..}]) => nil)

  (fact "found first in three item collection"
    (position-by-id ..id.. [{:id ..id..} {:id ..another-id..} {:id ..yet-another-id..}]) => 0)

  (fact "found last in three item collection"
    (position-by-id ..id.. [{:id ..another-id..} {:id ..yet-another-id..} {:id ..id..}]) => 2)

  (fact "found middle in three item collection"
    (position-by-id ..id.. [{:id ..another-id..} {:id ..id..} {:id ..yet-another-id..}]) => 1)

  (fact "id is nil"
    (position-by-id nil [{:id ..id..} {:id ..another-id..} {:id ..yet-another-id..}]) => nil)

  (fact "collection is nil"
    (position-by-id ..id.. nil) => nil)

  (fact "collection contains nil"
    (position-by-id ..id.. [nil {:id ..id..}]) => 1))

(facts "mapv-some"
  (mapv-some inc nil) => []
  (mapv-some inc []) => []
  (mapv-some inc [1 2 3]) => [2 3 4]
  (mapv-some (constantly nil) []) => []
  (mapv-some (constantly nil) [1 2 3]) => nil
  (mapv-some identity [1 2 nil]) => nil)

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
  => {:a :a, :c [], :d {}, :e [:e], :f {:f :f}}
  (assoc-when {:a nil :b :b} :a :a, :b nil, :c :c, :d false)
  => {:a :a, :b :b, :c :c})

(facts assoc-when-pred
  (assoc-when-pred {} not-empty-or-nil? :a :a, :b nil, :c [], :d {}, :e [:e], :f {:f :f})
  => {:a :a, :e [:e], :f {:f :f}}
  (assoc-when-pred {:a nil :b :b} not-empty-or-nil? :a :a, :b nil, :c :c, :d false)
  => {:a :a, :b :b, :c :c, :d false})

(facts "mongerify"
  (mongerify {:id :186-R
              :names (list :hemuli :hei)
              :valid? true
              :age 20
              :substructure [{:hip :hep, :hop "laa"}]})
  =>
  {:id "186-R"
   :names ["hemuli" "hei"]
   :valid? true
   :age 20
   :substructure [{:hip "hep", :hop "laa"}]})

(facts upsert
  (fact "one item with match"
    (upsert {:id ..id-1.. :val ..new-value..} [{:id ..id-1.. :val ..bar..}])
    => [{:id ..id-1.. :val ..new-value..}])

  (fact "empty collection"
    (upsert {:id ..id-1.. :val ..new-value..} [])
    => [{:id ..id-1.. :val ..new-value..}])

  (fact "empty item"
    (upsert {} [{:id ..id-1.. :val ..bar..}])
    => [{:id ..id-1.. :val ..bar..}])

  (fact "item without id"
    (upsert {:val ..new-value..} [{:id ..id-1.. :val ..bar..}])
    => [{:id ..id-1.. :val ..bar..}])

  (fact "without match"
    (upsert {:id ..id-2.. :val ..new-value..} [{:id ..id-1.. :val ..bar..}])
    => [{:id ..id-1.. :val ..bar..} {:id ..id-2.. :val ..new-value..}])

  (fact "first item matches"
    (upsert {:id ..id-1.. :val ..new-value..} [{:id ..id-1.. :val ..bar..} {:id ..id-2.. :val ..quu..} {:id ..id-3.. :val ..quz..}])
    => [{:id ..id-1.. :val ..new-value..} {:id ..id-2.. :val ..quu..} {:id ..id-3.. :val ..quz..}])

  (fact "last item matches"
    (upsert {:id ..id-3.. :val ..new-value..} [{:id ..id-1.. :val ..bar..} {:id ..id-2.. :val ..quu..} {:id ..id-3.. :val ..quz..}])
    => [{:id ..id-1.. :val ..bar..} {:id ..id-2.. :val ..quu..} {:id ..id-3.. :val ..new-value..}])

  (fact "match in the middle"
    (upsert {:id ..id-2.. :val ..new-value..} [{:id ..id-1.. :val ..bar..} {:id ..id-2.. :val ..quu..} {:id ..id-3.. :val ..quz..}])
    => [{:id ..id-1.. :val ..bar..} {:id ..id-2.. :val ..new-value..} {:id ..id-3.. :val ..quz..}]))

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

(facts "->long"
  (fact (->long nil) => nil)
  (fact (->long "") => nil)
  (fact (->long true) => nil)
  (fact (->long "1234") => 1234)
  (fact (->long "213asd2") => nil)
  (fact (->long 1234) => 1234))

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

(fact "make-kw"
  (make-kw nil) => nil
  (make-kw "hello") => :hello
  (make-kw 1) => :1
  (make-kw 3.14) => :3.14
  (make-kw [1 2 3]) => nil
  (make-kw "") => (keyword "") )

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
    (fact ":str :str str str"  (=as-kw :str :str "str" "str") => true)

    (fact "numbers"
      (=as-kw 10 :10 "10") => true)

    (fact "empty"
      (=as-kw "" (keyword "")) => true))

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
    (fact ":str :str str str"  (not=as-kw :str :str "str3" "str") => true)

    (fact "numbers"
      (not=as-kw 10 :10 "10") => false))

  (facts "nils"
    (fact "nil"  (not=as-kw nil) => false)
    (fact "str nil"   (not=as-kw "str" nil) => true)
    (fact "nil :str" (not=as-kw nil :str) => true))

  (facts "different values"
    (fact "foo :bar"  (not=as-kw "foo" :bar) => true)
    (fact "foo :bar foo"  (not=as-kw "foo" :bar "foo") => true)
    (fact "foo foo :bar"  (not=as-kw "foo" "foo" :bar) => true)))

(facts "includes-as-kw?"
    (fact "[s] s: found"
      (includes-as-kw? [:yi "er" :san "si" :wu] "er") => true)
    (fact "[s] kw: found"
      (includes-as-kw? [:yi "er" :san "si" :wu] :si) => true)
    (fact "[kw] kw: found"
      (includes-as-kw? [:yi "er" :san "si" :wu] :san) => true)
    (fact "[kw] s: found"
      (includes-as-kw? [:yi "er" :san "si" :wu] "yi") => true)
    (fact "kw not found"
      (includes-as-kw? [:yi "er" :san "si" :wu] :liu)=> false)
    (fact "s not found"
      (includes-as-kw? [:yi "er" :san "si" :wu] "liu")=> false))

(facts "intersection-as-kw"
  (fact "s and kw"
    (intersection-as-kw ["yi" :er "san"] '(:yi "er" :si) ["liu" :yi :er])
    => ["yi" :er])
  (fact "empty"
    (intersection-as-kw ["yi" :er "san"] '(:yi "er" :si) ["liu"])
    => [])
  (fact "nil safe"
    (intersection-as-kw ["yi" :er "san"] '(:yi "er" :si) ["liu"] nil nil)
    => []))

(facts "difference-as-kw"
  (fact "s and kw"
    (difference-as-kw ["yi" :er "san"] '(:yi "er" :si) ["liu" :yi :er])
    => [:san])
  (fact "empty"
    (difference-as-kw ["yi" :er "san"] '(:yi "er" :si) ["san"])
    => [])
  (fact "nil safe"
    (difference-as-kw ["yi" :er "san"] '(:yi "er" :si) ["liu"] nil nil)
    => [:san]))

(facts "union-as-kw"
  (fact "s, kw, nil"
    (union-as-kw ["yi" :er "san" nil]) => [:yi :er :san])
  (fact "no duplicates"
    (union-as-kw ["yi" :er "san" nil]
                 [:yi :er nil "si"])
    => [:yi :er :san :si])
  (fact "Nil safe"
    (union-as-kw nil) => []
    (union-as-kw nil nil) => []
    (union-as-kw []) => []
    (union-as-kw [] []) => []))

(facts get-in-tree
  (fact "single level"
    (get-in-tree [[:foo :bar] [:baz :quu]] [:foo]) => :bar)

  (fact "not found"
    (get-in-tree [[:foo :bar] [:baz :quu]] [:fuzz :foo]) => nil)

  (fact "incomplete tree"
    (get-in-tree [[:foo] [:bar] [:baz :quu]] [:foo]) => nil)

  (fact "two level tree - one level path"
    (get-in-tree [[:foo [[:bar :bux]]] [:baz :quu]] [:foo]) => [[:bar :bux]])

  (fact "two level tree - two level path"
    (get-in-tree [[:foo [[:bar :bux]]] [:baz :quu]] [:foo :bar]) => :bux)

  (fact "multi level"
    (get-in-tree [[:foo
                   [[:bar
                     [[:bux :biz] [:rip :rap]]]
                    [:hii
                     [[:hoo
                       [[:hei :hou] [:hip :hup]]]]]]]
                  [:baz :quu]]

                 [:foo :hii :hoo :hei]) => :hou))

(facts get-leafs
  (fact "single level"
    (get-leafs [[:foo :bar] [:baz :quu]]) => [:bar :quu])

  (fact "only leaf"
    (get-leafs :foo) => [:foo])

  (fact "only map as leaf"
    (get-leafs {:foo :bar}) => [{:foo :bar}])

  (fact "map as leaf in single level tree"
    (get-leafs [[:hii {:foo :bar}]]) => [{:foo :bar}])

  (fact "only set as leaf"
    (get-leafs #{:foo :bar}) => [#{:foo :bar}])

  (fact "incomplete tree"
    (get-leafs [[:foo] [:bar] [:baz :quu]]) => [nil nil :quu])

  (fact "two level - contains right elements"
    (get-leafs [[:foo [[:bar :bux] [:hii :hoo]]] [:baz :quu] [:hai :hei]]) => (just #{:bux :hoo :quu :hei} :in-any-order :gaps-ok))

  (fact "two level - higher level leafs ordered before lower level leafs"
    (get-leafs [[:foo [[:bar :bux] [:hii :hoo]]] [:baz :quu] [:hai :hei]]) => [:quu :hei :bux :hoo])

  (fact "multi level"
    (get-leafs [[:foo
                 [[:bar
                   [[:bux :biz] [:rip :rap]]]
                  [:hii
                   [[:hoo
                     [[:hei :hou] [:hip :hup]]]]]]]
                [:baz :quu]]) => [:quu :biz :rap :hou :hup]))

(fact distinct-by
  (distinct-by identity []) => '()
  (distinct-by even? [1 2 3 4 5 6]) => '(1 2)
  (distinct-by identity [1 1 2 3 2]) => '(1 2 3)
  (distinct-by :foo [{:foo 1 :bar :a} {:foo 1 :bar :b}]) => '({:foo 1 :bar :a}))

(fact "kw-path"
  (kw-path "a" :b 9) => :a.b.9
  (kw-path ["a" :b 9]) => :a.b.9
  (kw-path "") => (keyword "")
  (kw-path "a" nil "b") => :a.b
  (kw-path nil "foo") => :foo
  (kw-path nil) => nil
  (kw-path) => nil
  (kw-path "a" [:b nil] :c) => :a.b.c)

(fact "split-kw-path"
  (split-kw-path :a.b.9) => [:a :b :9]
  (split-kw-path :a..b) => [:a (keyword "" ):b]
  (split-kw-path nil) => nil)

(facts "safe-update-in"
  (safe-update-in {:a [{:b 2}]} [:a 0 :b] + 2) => {:a [{:b 4}]}
  (safe-update-in {:a [{:b 2}]} [:a :b] + 2) => {:a [{:b 2}]})

(fact edit-distance
  (edit-distance "" "") => 0

  (edit-distance "a" "a") => 0

  (edit-distance "" "a") => 1
  (edit-distance "a" "") => 1

  (edit-distance "a" "b") => 1
  (edit-distance "b" "a") => 1

  (let [test-string-1 "foo\n\tbar"
        test-string-2 "foobar"
        test-string-3 "moodaar"]
    (edit-distance "" test-string-1) => (count test-string-1)
    (edit-distance test-string-1 "") => (count test-string-1)

    (edit-distance test-string-1 test-string-1) => 0
    (edit-distance test-string-2 test-string-2) => 0
    (edit-distance test-string-3 test-string-3) => 0

    (edit-distance test-string-1 test-string-2) => 2
    (edit-distance test-string-2 test-string-1) => 2
    (edit-distance test-string-2 test-string-3) => 3
    (edit-distance test-string-3 test-string-2) => 3
    (edit-distance test-string-1 test-string-3) => 4
    (edit-distance test-string-1 test-string-3) => 4))

(fact "update-values"
  (update-values {:foo 1 :bar 2} [:foo] inc) => {:foo 2 :bar 2}
  (update-values {:foo "str" :bar "str"} [:foo] str "ing") => {:foo "string" :bar "str"})

(fact "find-index"
  (find-index pos? []) => nil
  (find-index pos? [-1 0 1 2]) => 2
  (find-index string? [-1 0 1 2]) => nil
  (find-index (comp zero? second) {:zero 1 :one 2 :two 3 :three 0 :four 4 :five 5}) => 3)

(fact "find-by-keys"
  (find-by-keys {:foo 1 :bar :hello}
                [{:foo 1} {:foo 1 :baz 3 :bar :hello} {:foo 1 :bar :hello}])
  => {:foo 1 :baz 3 :bar :hello}
  (find-by-keys nil nil) => nil
  (find-by-keys nil [{:foo 1}]) => nil
  (find-by-keys {} [{:foo 1}]) => {:foo 1}
  (find-by-keys {:foo 1 :bar nil} [{:foo 1}]) => nil
  (find-by-keys {:foo 1 :bar nil} [{:foo 1} {:foo 1 :bar nil}])
  => {:foo 1 :bar nil})

(facts "emptyish?"
  (facts "true"
    (emptyish? nil) => true
    (fullish? nil) => false
    (emptyish? "") => true
    (emptyish? "  \n\r  \t") => true
    (emptyish? []) => true
    (emptyish? {}) => true
    (emptyish? '()) => true)
  (facts "false"
    (emptyish? 1) => false
    (fullish? 1) => true
    (emptyish? "hello") => false
    (emptyish? [nil]) => false
    (emptyish? {:foo :bar}) => false
    (emptyish? \a) => false
    (emptyish? :foo) => false
    (emptyish? true) => false
    (emptyish? false) => false))

(fact "keyset"
  (keyset {:foo 1 :faa 2}) => #{:foo :faa}
  (keyset {}) => #{}
  (keyset nil) => #{})

(facts "update-by-pred"
  (update-by-pred odd? * [0 1 2 3 4] 2) => [0 2 2 6 4]
  (update-by-pred odd? inc [0 1 2 3 4]) => [0 2 2 4 4]
  (update-by-pred odd? inc nil) => []
  (update-by-pred (comp even? last) conj {:one 1 :two 2 :three 3} 10 100)
  => [[:one 1] [:two 2 10 100] [:three 3]]
  (update-by-pred string? reverse [0 1 2 3 4]) => [0 1 2 3 4])

(facts "update-by-key"
  (update-by-key :type :dot assoc
                 [{:type :dot} {:type :square} {:type :dot}]
                 :connected? true)

  => [{:type :dot :connected? true}
      {:type :square}
      {:type :dot :connected? true}]
  (update-by-key :type :foo assoc [] :connected? true)
  => []
  (update-by-key :type :foo assoc nil :connected? true)
  => []
  (update-by-key :type :foo assoc [0 1 2 3 4] :connected? true)
  => [0 1 2 3 4]
  (update-by-key :reversed? true #(update % :text (comp ss/join  reverse))
                 [{:reversed? false :text "Hello"}
                  {:reversed? true :text "desreveR"}
                  {:text "World"}])
  => [{:reversed? false :text "Hello"}
      {:reversed? true :text "Reversed"}
      {:text "World"}]
  (update-by-key :reversed? true update
                 [{:reversed? false :text "Hello"}
                  {:reversed? true :text "desreveR"}
                  {:text "World"}]
                 :text (comp ss/join  reverse))
  => [{:reversed? false :text "Hello"}
      {:reversed? true :text "Reversed"}
      {:text "World"}])

(facts "update-by-id"
  (update-by-id "b" #(update % :name str " (default)")
                [{:id "a" :name "Alice"}
                 {:id "b" :name "Bob"}
                 {:id "c" :name "Cecil"}])
  => [{:id "a" :name "Alice"}
      {:id "b" :name "Bob (default)"}
      {:id "c" :name "Cecil"}]
  (update-by-id "b" update
                [{:id "a" :name "Alice"}
                 {:id "b" :name "Bob"}
                 {:id "c" :name "Cecil"}]
                 :name str " (default)")
  => [{:id "a" :name "Alice"}
      {:id "b" :name "Bob (default)"}
      {:id "c" :name "Cecil"}]
  (update-by-id "b" update [] :name str " (default)")
  => []
  (update-by-id "b" update nil :name str " (default)")
  => []
  (update-by-id "b" update [0 1 2 3 4] :name str " (default)")
  => [0 1 2 3 4]
  (update-by-id "b" update [{:id "d" :name "Dot"}] :name str " (default)")
  => [{:id "d" :name "Dot"}])

(let [filename (str (fs/create-temp-file {:suffix ".edn"}))
      form     {:one 1
                :two [:three "4" :five {"6" "seven"}]}]
  (against-background
    [(after :contents (fs/delete filename))]
    (facts "EDN"
      (write-edn-file filename form)
      (read-edn-file filename) => form)))
