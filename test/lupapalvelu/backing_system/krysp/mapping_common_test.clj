(ns lupapalvelu.backing-system.krysp.mapping-common-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.backing-system.krysp.mapping-common :refer :all]
            [clojure.data.xml :refer [parse-str emit-str]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))


(def yksilointitieto-children [{:tag :yksilointitieto}
                               {:tag :alkuHetki}
                               {:tag :loppuHetki}])

(def result-str "<?xml version='1.0' encoding='UTF-8'?>
<root>
 <yksilointitieto>Uniikki arvo</yksilointitieto>
 <alkuHetki>10.10.2012</alkuHetki>
 <loppuHetki>11.11.2013</loppuHetki>
</root>")

(def result (emit-str (parse-str result-str)))

(fact "data-with-childs, sring comparison"
  (emit-str
    (element-to-xml
      {:root
       {:yksilointitieto "Uniikki arvo"
        :alkuHetki "10.10.2012"
        :loppuHetki "11.11.2013"}}
      {:tag :root :child yksilointitieto-children})) => result)

(facts "update-child-element"
  (let [sample-children [{:tag :a :child [{:tag :b  :child [{:tag :c}]}]}
                         {:tag :other1 :child [{:tag :other2}]}
                         {:tag :other3}]
        small-mapping [{:tag :a :child [{:tag :b :attr 1}]}]]
    (fact "replace leaf"
      (update-child-element sample-children [:a :b :c] {:tag :d}) => [{:tag :a :child [{:tag :b  :child [{:tag :d}]}]}
                                                                      {:tag :other1 :child [{:tag :other2}]}
                                                                      {:tag :other3}])

    (fact "replace in the middle"
      (update-child-element sample-children [:a :b] {:tag :d}) => [{:tag :a :child [{:tag :d}]}
                                                                   {:tag :other1 :child [{:tag :other2}]}
                                                                   {:tag :other3}])

    (fact "replace on top level"
      (update-child-element sample-children [:a] {:tag :d}) => [{:tag :d}
                                                                {:tag :other1 :child [{:tag :other2}]}
                                                                {:tag :other3}]
      (update-child-element sample-children [:other1] {:tag :d}) => [{:tag :a :child [{:tag :b  :child [{:tag :c}]}]}
                                                                     {:tag :d}
                                                                     {:tag :other3}]
      (update-child-element sample-children [:other3] {:tag :d}) => [{:tag :a :child [{:tag :b  :child [{:tag :c}]}]}
                                                                     {:tag :other1 :child [{:tag :other2}]}
                                                                     {:tag :d}])

    (fact "function produces the new value"
      (update-child-element small-mapping [:a :b] #(update-in % [:attr] inc))           => [{:tag :a :child [{:tag :b :attr 2}]}]
      (update-child-element small-mapping [:a :b] (fn [elem] (assoc elem :meta "foo"))) => [{:tag :a :child [{:tag :b :attr 1 :meta "foo"}]}])))

(fact merge-into-coll-after-tag
  (merge-into-coll-after-tag [{:tag :a} {:tag :b} {:tag :c}] :b [{:tag :x}])           => [{:tag :a} {:tag :b} {:tag :x} {:tag :c}]
  (merge-into-coll-after-tag [{:tag :a} {:tag :b} {:tag :c}] :b [{:tag :x} {:tag :y}]) => [{:tag :a} {:tag :b} {:tag :x} {:tag :y} {:tag :c}]
  (merge-into-coll-after-tag [{:tag :a} {:tag :b} {:tag :c}] :z {:tag :x})             => [{:tag :a} {:tag :b} {:tag :c}])

(facts "get-child-element"
  (let [sample-mapping {:tag :top
                        :child [{:tag :a :child [{:tag :b  :child [{:tag :c} {:tag :x}]} {:tag :y}]}
                                {:tag :other1 :child [{:tag :other2}]}
                                {:tag :other3}]}]

    (fact "leaf" (get-child-element sample-mapping [:a :b :c]) => {:tag :c})
    (fact "middle" (get-child-element sample-mapping [:a :b]) => {:tag :b :child [{:tag :c} {:tag :x}]})
    (fact "top" (get-child-element sample-mapping [:other3]) => {:tag :other3})))

(def common-210 "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.0/yhteiset.xsd http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd ")
(def common-213 "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.3/yhteiset.xsd http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd ")

(facts "schemalocation"
  (fact "rakval 2.1.2"
    (schemalocation :R "2.1.2") =>
    (str common-210 "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta/2.1.2/rakennusvalvonta.xsd"))
  (fact "rakval 2.1.4"
    (schemalocation :R "2.1.4") =>
    "http://www.paikkatietopalvelu.fi/gml/yhteiset http://www.paikkatietopalvelu.fi/gml/yhteiset/2.1.2/yhteiset.xsd http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/gml.xsd http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta/2.1.4/rakennusvalvonta.xsd")

  (fact "poikkari 2.1.2"
    (schemalocation :P "2.1.2") =>
    (str common-210 "http://www.paikkatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu http://www.paikkatietopalvelu.fi/gml/poikkeamispaatos_ja_suunnittelutarveratkaisu/2.1.2/poikkeamispaatos_ja_suunnittelutarveratkaisu.xsd"))

  (fact "YA 2.1.2"
    (schemalocation :YA "2.1.2") =>
    (str common-210 "http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus http://www.paikkatietopalvelu.fi/gml/yleisenalueenkaytonlupahakemus/2.1.2/YleisenAlueenKaytonLupahakemus.xsd"))

  (fact "MAL 2.1.2"
    (schemalocation :MAL "2.1.2")
    (str common-213 "http://www.paikkatietopalvelu.fi/gml/ymparisto/maa_ainesluvat http://www.paikkatietopalvelu.fi/gml/ymparisto/maa_ainesluvat/2.1.2/maaAinesluvat.xsd"))

  (fact "YI 2.1.2"
    (schemalocation :YI "2.1.2") =>
    (str common-213 "http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset http://www.paikkatietopalvelu.fi/gml/ymparisto/ilmoitukset/2.1.2/ilmoitukset.xsd"))

  (fact "YL 2.1.2"
    (schemalocation :YL "2.1.2") =>
    (str common-213 "http://www.paikkatietopalvelu.fi/gml/ymparisto/ymparistoluvat http://www.paikkatietopalvelu.fi/gml/ymparisto/ymparistoluvat/2.1.2/ymparistoluvat.xsd"))

  (fact "VVVL 2.1.3"
    (schemalocation :VVVL "2.1.3") =>
    (str common-213 "http://www.paikkatietopalvelu.fi/gml/ymparisto/vesihuoltolaki http://www.paikkatietopalvelu.fi/gml/ymparisto/vesihuoltolaki/2.1.3/vesihuoltolaki.xsd")))

(facts "tag-path->update-path"
  (tag-path->update-path nil [:foo]) => nil
  (tag-path->update-path nil [:foo :bar :baz]) => nil
  (tag-path->update-path {:tag :foo} [:foo]) => nil
  (tag-path->update-path {:tag :foo} []) => []
  (tag-path->update-path {:child [{:tag :foo}]} [:foo]) => [:child 0]
  (tag-path->update-path {:child [{:tag   :foo
                                   :child [{:tag :con}
                                           {:tag :don}
                                           {:tag :bar}]}]}
                         [:foo :bar]) => [:child 0 :child 2])

(facts "insert-elements-before"
  (insert-elements {:tag :a :child [{:tag :x}]} [] {:x {:replace-with [{:tag :b}]}})
  => {:tag :a :child [{:tag :b}]}
  (insert-elements {:tag :a :child [{:tag :b}
                                    {:tag :c :child [{:tag :f} {:tag :h} {:tag :x}]}
                                    {:tag :d}]}
                   ;; Under element :c
                   [:c]
                   ;; Add elements :h and :g before elements :d and :e, respectively
                   {:f {:add-before [{:tag :e}]
                        :add-after  [{:tag :g}]}
                    :x {:replace-with [{:tag :i}]}})
  => {:tag :a :child [{:tag :b}
                      {:tag :c :child [{:tag :e} {:tag :f} {:tag :g} {:tag :h} {:tag :i}]}
                      {:tag :d}]}
  (insert-elements {:tag   :building
                    :child [{:tag   :apartment
                             :child [{:tag   :kitchen
                                      :child [{:tag   :fridge
                                               :child [{:tag :temperature}
                                                       {:tag :size}
                                                       {:tag :wattage}]}]}]}]}
                   [:apartment :kitchen :fridge]
                   {:size {:replace-with [{:tag :volume}]}})
  => {:tag   :building
      :child [{:tag   :apartment
               :child [{:tag   :kitchen
                        :child [{:tag   :fridge
                                 :child [{:tag :temperature}
                                         {:tag :volume} ; updated
                                         {:tag :wattage}]}]}]}]})

(facts "mapper"
  (mapper :foo) => {:tag :foo :ns nil}
  (mapper :foo "bar") => {:tag :foo :ns "bar"}
  (mapper :foo/where) => {:tag :foo :ns "where"}
  (mapper :foo/where "bar") => {:tag :foo :ns "where"}
  (mapper {:foo/where [:bar]}) => {:tag   :foo
                                   :child [{:tag :bar :ns "where"}]}
  (mapper {:foo/where [:bar]} "home") => {:tag   :foo
                                          :ns    "home"
                                          :child [{:tag :bar :ns "where"}]}
  (mapper {:foo/where [:bar/baz]}) => {:tag   :foo
                                       :child [{:tag :bar :ns "baz"}]}
  (mapper {:foo/where {:bar/baz [:hii]}}) => {:tag   :foo
                                              :child [{:tag   :bar :ns "where"
                                                       :child [{:tag :hii
                                                                :ns  "baz"}]}]}
  (mapper [:one :two/here :three]) => [{:tag :one :ns nil}
                                       {:tag :two :ns "here"}
                                       {:tag :three :ns nil}]
  (mapper {:one [:two
                 :three/here
                 {:four [:five
                         {:six/there [:seven
                                      {:tag :eight :child [{:tag :nine}]}
                                      {:tag :ten :ns "away"}]}]}]}
          "home")
  => {:child [{:ns "home" :tag :two}
              {:ns "here" :tag :three}
              {:child [{:ns "home" :tag :five}
                       {:child [{:ns "there" :tag :seven}
                                {:child [{:tag :nine}] :ns "there" :tag :eight}
                                {:ns "away" :tag :ten}]
                        :ns    "home"
                        :tag   :six}]
               :ns    "home"
               :tag   :four}]
      :ns    "home"
      :tag   :one})

(facts "update-children-fn"
  (let [f            (fn [element] (assoc element :seen true))
        no-recursion (update-children-fn f)
        recursion    (update-children-fn f true)
        element      {:tag   :parent
                      :child [{:tag   :c1
                               :child []}
                              {:tag   :c2
                               :child [{:tag   :c4
                                        :child []}
                                       {:tag   :c5
                                        :child [{:tag :c6}]}]}
                              {:tag :c3}]}]
    (fact "can update immediate children"
      (no-recursion element) => {:tag   :parent
                                 :child [{:tag   :c1
                                          :seen  true
                                          :child []}
                                         {:tag   :c2
                                          :seen  true
                                          :child [{:tag   :c4
                                                   :child []}
                                                  {:tag   :c5
                                                   :child [{:tag :c6}]}]}
                                         {:tag  :c3
                                          :seen true}]})
    (fact "can update all descendants"
      (recursion element) => {:tag   :parent
                              :child [{:tag   :c1
                                       :seen  true
                                       :child []}
                                      {:tag   :c2
                                       :seen  true
                                       :child [{:tag   :c4
                                                :seen  true
                                                :child []}
                                               {:tag   :c5
                                                :seen  true
                                                :child [{:tag  :c6
                                                         :seen true}]}]}
                                      {:tag  :c3
                                       :seen true}]})))

(facts "add-children"
  (fact "no existing child tag"
    (add-children {:tag :empty}
                  [{:tag :c1} {:tag :c2}])
    => {:tag   :empty
        :child [{:tag :c1}
                {:tag :c2}]})
  (fact "existing empty child tag"
    (add-children {:tag :empty :child []}
                  [{:tag :c1} {:tag :c2}])
    => {:tag   :empty
        :child [{:tag :c1}
                {:tag :c2}]})
  (fact "existing child elements"
    (add-children {:tag :empty :child [{:tag :existing}]}
                  [{:tag :c1} {:tag :c2}])
    => {:tag   :empty
        :child [{:tag :existing}
                {:tag :c1}
                {:tag :c2}]}))
