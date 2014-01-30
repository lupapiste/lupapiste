(ns lupapalvelu.xml.krysp.mapping-common-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.xml.krysp.mapping-common :refer :all]
            [clojure.data.xml :refer [parse-str emit-str]]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))


(def yksilointitieto [{:tag :yksilointitieto}
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
      {:tag :root :child yksilointitieto})) => result)

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

(facts "get-child-element"
  (let [sample-mapping {:tag :top
                        :child [{:tag :a :child [{:tag :b  :child [{:tag :c} {:tag :x}]} {:tag :y}]}
                                {:tag :other1 :child [{:tag :other2}]}
                                {:tag :other3}]}]

    (fact "leaf" (get-child-element sample-mapping [:a :b :c]) => {:tag :c})
    (fact "middle" (get-child-element sample-mapping [:a :b]) => {:tag :b :child [{:tag :c} {:tag :x}]})
    (fact "top" (get-child-element sample-mapping [:other3]) => {:tag :other3})))

