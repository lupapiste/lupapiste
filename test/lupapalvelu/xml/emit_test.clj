(ns lupapalvelu.xml.emit-test
  (:use lupapalvelu.xml.emit
        midje.sweet))

(def simple-data {:test "Test"})
(def simple-model  {:tag :test})
(def simple-result #clojure.data.xml.Element{:tag :test, :attrs {}, :content ("Test")})

(def simple-model-with-custom-key  {:tag :eri :attr {:a "a" :b "b"} :key :test})
(def result-with-custom-key #clojure.data.xml.Element{:tag :eri, :attrs {:a "a", :b "b"}, :content ("")})


(def model-with-childs {:tag :main 
                       :child [{:tag :a :attr{:a "a"}
                                :child[{:tag :ab}]}
                               {:tag :b}]})
(def data-with-childs {:main {
                              :a {
                                  :ab "AB Value"}
                              :b "B value"}})
(def result-with-childs #clojure.data.xml.Element{:tag :main, :attrs {}, :content ((#clojure.data.xml.Element{:tag :a, :attrs {:a "a"}, :content ((#clojure.data.xml.Element{:tag :ab, :attrs {}, :content ("AB Value")}))} #clojure.data.xml.Element{:tag :b, :attrs {}, :content ("B value")}))})

 
(facts
  (fact  (element-to-xml simple-data [] simple-model) => simple-result)
  (fact  (element-to-xml simple-data [] simple-model-with-custom-key) => result-with-custom-key)
  (fact (element-to-xml data-with-childs [] model-with-childs) => result-with-childs)
  )