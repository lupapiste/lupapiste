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
    (str common-213 "http://www.paikkatietopalvelu.fi/gml/ymparisto/vesihuoltolaki http://www.paikkatietopalvelu.fi/gml/ymparisto/vesihuoltolaki/2.1.3/vesihuoltolaki.xsd"))
  )
