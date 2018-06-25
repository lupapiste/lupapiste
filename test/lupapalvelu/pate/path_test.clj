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
    => "Lupaehdot ja -maaraykset"
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

(fact "pathify"
  (path/pathify :this.is.kw.path) => [:this :is :kw :path]
  (path/pathify "hello") => "hello"
  (path/pathify nil) => nil
  (path/pathify ["one" "two" "three"]) => ["one" "two" "three"])

(testable-privates lupapalvelu.pate.path
                   inclusion-status has-path? truthy?
                   resolve-path parse-path-condition
                   path-truthy? eval-state-condition
                   good? climb-to-condition)

(fact "inclusion-status"
  (inclusion-status [:rep :index :foo] [:rep.foo]) => true
  (inclusion-status [:rep :index] [:rep.foo]) => true
  (inclusion-status [:rep] [:rep.foo]) => true
  (inclusion-status [:rep :index :foo] [:rep]) => false
  (inclusion-status [:rep :index :foo] []) => nil

  (inclusion-status [:foo] [:rep.foo]) => false
  (inclusion-status [:foo] [:foo]) => true)

(fact "has-path"
  (let [state* (atom {:foo "hello"
                      :rep {:index {:bar "world"}}})]
    (has-path? [:foo] state* nil) => true
    (has-path? [:foo] state* [:foo]) => true
    (has-path? [:foo] state* [:foob]) => false
    (has-path? [:foob] state* nil) => false
    (has-path? [:rep] state* nil) => true
    (has-path? [:rep :bad :bar] state* nil) => false
    (has-path? [:rep :bad :bar] state* [:rep.bar]) => true))

(fact "resolve-path"
  (resolve-path {:path [:foo :bar]} :?+.hii.hoo) => [:? :foo :bar :hii :hoo]
  (resolve-path {:path [:foo :bar]} :-.hii.hoo) => [:foo :hii :hoo]
  (resolve-path {:path [:foo :bar]} :-?.hii.hoo) => [:? :foo :hii :hoo]
  (resolve-path {:path [:foo :bar]} :?-.hii.hoo) => [:? :foo :hii :hoo]
  (resolve-path {:path [:foo :bar]} :hii.hoo) => [:hii :hoo])

(defn check-ppc [kw-path expected-path good bad]
  (fact {:midje/description kw-path}
    (let [{:keys [path fun?]} (parse-path-condition kw-path)]
      path => expected-path
      (fun? good) => true
      (fun? bad) => false)))

(fact "parse-path-condition"
  (check-ppc :foo.bar :foo.bar "good" nil)
  (check-ppc :foo.bar=10 :foo.bar "10" "20")
  (check-ppc :foo.bar=10 :foo.bar 10 20)
  (check-ppc :foo.bar!=10 :foo.bar 20 10)
  (check-ppc :foo.bar!=10 :foo.bar "20" "10"))

(fact "path-truthy?"
  (let [options {:state (atom {:foo "hello"
                               :oof "olleh"
                               :rep {:index {:bar "world"
                                             :baz "hii"}}})
                 :path  [:rep :index :bar]}]
    (path-truthy? options :foo) => true
    (path-truthy? options :?.foo) => true
    (path-truthy? options :rep.index.bar) => true
    (path-truthy? options :-.baz) => true
    (path-truthy? options :-.baz=juu) => false
    (path-truthy? options :?.oof) => true
    (path-truthy? options :?.rep.index.baz) => true
    (path-truthy? options :?.rep.index.baz) => true
    (fact "meta"
      (let [options (assoc options :_meta (atom {:top.level 8}))]
        (path-truthy? options :_meta.top) => false
        (path-truthy? options :_meta.hii) => false
        (path-truthy? options :_meta.top.level=8) => true
        (path-truthy? options :_meta.top.level=9) => false
        (path-truthy? options :_meta.top.level!=8) => false
        (path-truthy? options :_meta.top.level!=10) => true
        (path-truthy? options :oof) => true
        (path-truthy? options :rep.index.baz) => true
        (path-truthy? options :rep.index.baz) => true))
    (fact "references"
      (let [options (assoc options
                           :references (atom {:name "Bob"
                                              :items [{:id "1"
                                                       :deleted true}
                                                      {:id "2"}
                                                      {:id "3"
                                                       :deleted false}]
                                              :gone [{:foo 8
                                                      :deleted true}]}))]
        (path-truthy? options :*ref.name) => true
        (path-truthy? options :*ref.name.bad) => false
        (path-truthy? options :*ref.bad) => false
        (path-truthy? options :*ref.items) => true
        (path-truthy? options :*ref.gone) => false))
    (fact "inclusions"
      (let [options (assoc options :info (atom {:inclusions [:foo :rep.bar]})) ]
        (path-truthy? options :foo) => true
        (path-truthy? options :?.foo) => true
        (path-truthy? options :rep.index.bar) => true
        (path-truthy? options :?-.baz) => false
        (path-truthy? options :?.oof) => false
        (path-truthy? options :?.rep.index.baz) => false
        (path-truthy? options :?.rep.index.baz) => false
        (path-truthy? options :oof) => true
        (path-truthy? options :rep.index.baz) => true
        (path-truthy? options :rep.index.baz) => true))))

(let [options {:state      (atom {:foo "hello"
                                  :oof "olleh"
                                  :rep {:index {:bar "world"
                                                :baz "hii"}}})
               :path       [:rep :index :bar]
               :_meta      (atom {:top.level 8})
               :references (atom {:name  "Bob"
                                  :items [{:id      "1"
                                           :deleted true}
                                          {:id "2"}
                                          {:id      "3"
                                           :deleted false}]
                                  :gone  [{:foo     8
                                           :deleted true}]})
               :info       (atom {:inclusions [:foo :rep.bar]})}]
  (fact "eval-state-condition"
    (eval-state-condition options :foo) => true
    (eval-state-condition options [:OR :foo :ASDFASDF]) => true
    (eval-state-condition options [:AND :foo :ASDFASDF]) => false
    (eval-state-condition options [:AND :foo :-.baz
                                   [:AND
                                    [:OR :_meta.hii :_meta.top.level=8]
                                    :?.rep]])
    (eval-state-condition options [:AND :foo :-.baz
                                   [:AND
                                    [:AND :_meta.hii :_meta.top.level=8]
                                    :?.rep]])=> false)
  (fact "good?"
    (good? options :foo :asdfasdf) => true
    (good? options :asdfasdf :foo) => false
    (good? options nil nil) => true
    (good? options :*ref.items :?.oof) => true
    (good? options [:AND :foo :ASDFASDF] :asdfasdf) => false
    (good? options nil :asdfasdf) => true
    (good? options :foo :foo) => false))

(fact "climb-to-condition"
  (let [options {:schema {}
                 :_parent {:schema {:foo? :hello}
                           :_parent {:schema {:bar? :world}}}}]
    (climb-to-condition options :dum) => nil
    (climb-to-condition options :foo?) => :hello
    (climb-to-condition options :bar?) => :world))
