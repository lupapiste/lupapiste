(ns lupapalvelu.pate.path-test
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.path :as path]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(fact "state"
  (let [a*   (atom {:baz 8})
        sub* (path/state ["hello" :world] a*)]
    (reset! sub* "foo")
    @sub* => "foo"
    @a* => {:hello {:world "foo"}
            :baz   8}))

(fact "extend"
  (path/extend nil "hello") => [:hello]
  (path/extend [:foo "bar"] :hei [:one "two"])
  => [:foo :bar :hei :one :two]
  (path/extend :foo) => [:foo]
  (path/extend nil) => []
  (path/extend [:one :two] [:three nil :four] nil :five nil)
  => [:one :two :three :four :five]
  (path/extend [:empty] "") => [:empty  (keyword "")])

(fact "loc-extend"
  (path/loc-extend [:old :path] nil) => [:old :path]
  (path/loc-extend [:old :path] {:loc-prefix :new-start}) => [:new-start]
  (path/loc-extend [:old :path] {:id "my-id"}) => [:old :path :my-id])

(fact "id-extend"
  (path/id-extend [:my :id] {}) => [:my :id]
  (path/id-extend nil {:id "hello"}) => [:hello]
  (path/id-extend [:my "id"] {:id "foo"}) => [:my :id :foo]
  (path/id-extend [:my "id"] {:id ["foo" :bar]}) => [:my :id :foo :bar])

(fact "schema-options"
  (path/schema-options {:foo "bar"
                        :id-path [:my :id]
                        :loc-path [:some :loc]}
                       {:loc-prefix "schema-prefix"
                        :id "schema-id"})
  => {:foo "bar"
      :_parent {:foo "bar"
                :id-path [:my :id]
                :loc-path [:some :loc]}
      :id-path [:my :id :schema-id]
      :loc-path ["schema-prefix"]
      :schema {:loc-prefix "schema-prefix"
               :id "schema-id"}})

(fact "dict-options"
  (path/dict-options {:schema     {:dict :hello}
                      :dictionary {:hello {:toggle {}}}
                      :path       []})
  => {:schema     {:toggle {}}
      :dictionary {:hello {:toggle {}}}
      :_parent    {:schema     {:dict :hello}
                   :dictionary {:hello {:toggle {}}}
                   :path       []}
      :loc-path   nil
      :id-path    []
      :path       [:hello]}
  (path/dict-options {:schema     {:dict :hello}
                      :dictionary {:one {:repeating {:hello {:toggle {:foo 8}}}}}
                      :path       [:one :two]})
  => {:schema     {:toggle {:foo 8}}
      :_parent {:schema     {:dict :hello}
                :dictionary {:one {:repeating {:hello {:toggle {:foo 8}}}}}
                :path       [:one :two]}
      :dictionary {:one {:repeating {:hello {:toggle {:foo 8}}}}}
      :loc-path   nil
      :id-path    []
      :path       [:one :two :hello]})

(fact "id"
  (path/id [:one "two" nil :three]) => "one-two-three")


(fact "loc"
  (i18n/with-lang :fi
    (path/loc [:pate nil] "contract" nil [[nil :template]])
    => "Sopimuspohja"
    (path/loc {:i18nkey :pdf
               :loc-path [:pate :contract]
               :schema {:i18nkey :pdf.contract
                        :loc-prefix :phrase
                        :dict :pate-conditions}} "signature")
    => "Allekirjoitus"
    (path/loc {:loc-path [:pate :contract]
               :schema {:i18nkey :pdf.contract
                        :loc-prefix :phrase
                        :dict :pate-conditions}} :case)
    => "Asia"
    (path/loc {:loc-path [:pate :contract]
               :schema {:loc-prefix :phrase
                        :dict :pate-conditions}} [nil "tag"])
    => "Tunniste"
    (path/loc {:loc-path [:pate :contract]
               :schema {:dict :pate-conditions}})
    => "Lupaehdot ja -määräykset"
    (path/loc {:loc-path [:pate :contract]
               :schema {:dict :pate-conditions}} "add")
    => "Lisää lupaehto"
    (path/loc {:loc-path [:pate :contract]} :language)
    => "Sopimuksen kieli"))

(fact "Meta"
  (let [_meta (atom {:one 1
                     :one.hello "hello"
                     :one.two true
                     :one.two.hello "morjens"
                     :one.two.three :foobar
                     :one.two.three.hello "nihao"})]
    (fact "meta-value"
      (path/meta-value {:id-path [:one :two] :_meta _meta} :hello)
      => "morjens"
      (path/meta-value {:id-path [:one :two :foo :bar :baz] :_meta _meta} :hello)
      => "morjens"
      (path/meta-value {:id-path [:one :foo :two] :_meta _meta} :hello)
      => "hello"
      (path/meta-value {:id-path [:one :foo :two] :_meta _meta} :unknown)
      => nil)
    (fact "flip-meta"
      (path/flip-meta {:id-path [:one :two :hii :hoo] :_meta _meta} :two)
      (path/value [:one.two.hii.hoo.two] _meta) => true
      (path/flip-meta {:id-path [:one :two] :_meta _meta} :three)
      (path/value [:one.two.three] _meta) => false)))

(fact "schema-css"
  (path/schema-css {:css [:foo :bar]} :baz nil [:hii [["hoo"]]])
  => ["foo" "bar" "baz" "hii" "hoo"]
  (path/schema-css nil :baz [:hii [["hoo"]]])
  => ["baz" "hii" "hoo"])
