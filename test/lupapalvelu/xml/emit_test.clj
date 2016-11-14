(ns lupapalvelu.xml.emit-test
  (:require [clojure.data.xml :as xml]
            [midje.sweet :refer :all]
            [lupapalvelu.xml.emit :refer :all]))

(def simple-data {:test "Test"})
(def simple-model  {:tag :test :ns "ns"})
(def simple-result #clojure.data.xml.Element{:tag  "ns:test", :attrs {}, :content ("Test")})

(def model-with-childs {:tag :main
                       :child [{:tag :a :attr{:a "a"}
                                :child[{:tag :ab}
                                       {:tag :ac}
                                       {:tag :ad :child [{:tag :level3 :child [{:tag :level4}]}]}]}
                               {:tag :b}]})
(def data-with-childs {:main {
                              :a [
                                  {:ab ["AB Value" "AB Value2"]
                                   :ad {:level3 [{:level4 1} {:level4 2}]}
                                   :ac "AB Value3"}
                                  {:ab ["2nd AB Value" "2nd AB Value2"]
                                   :ad {:level3 [{:level4 ["2. L4-1a" "2. L4-1b"]} {:level4 "2. L4-2"}]}
                                   :ac "2nd AB Value3"}
                                 ]
                              :b "B value 2"}})

(def result-with-childs-str "<?xml version='1.0' encoding='UTF-8'?>
<main>
  <a a='a'>
    <ab>AB Value</ab>
    <ab>AB Value2</ab>
    <ac>AB Value3</ac>
    <ad>
      <level3>
        <level4>1</level4>
      </level3>
      <level3>
        <level4>2</level4>
      </level3>
    </ad>
  </a>
  <a a='a'>
    <ab>2nd AB Value</ab>
    <ab>2nd AB Value2</ab>
    <ac>2nd AB Value3</ac>
    <ad>
      <level3>
        <level4>2. L4-1a</level4>
        <level4>2. L4-1b</level4>
      </level3>
      <level3>
        <level4>2. L4-2</level4>
      </level3>
    </ad>
  </a>
  <b>B value 2</b>
</main>
")

(def result-with-childs (xml/emit-str (xml/parse-str result-with-childs-str)))

(fact  (element-to-xml simple-data simple-model) => simple-result)

(fact "data-with-childs, sring comparison"
  (xml/emit-str (element-to-xml data-with-childs model-with-childs)) => result-with-childs)
