(ns lupapalvelu.pdf.html-template-common-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.pdf.html-template-common :refer :all]))

(testable-privates lupapalvelu.pdf.html-template-common flat-rows)

(facts wrap-map
  (fact "simple wrap"
    (wrap-map :div ["value"]) => [{:tag :div, :attrs {}, :content ["value"]}])

  (facts "tag"
    (->> (wrap-map :div [[:tag1]]) (map :tag)) => [:div]
    (->> (wrap-map :div [[:tag1] [:tag2]]) (map :tag)) => [:div :div])

  (facts "content"
    (->> (wrap-map :div [[:tag1]]) (map (comp :tag first :content))) => [:tag1]
    (->> (wrap-map :div [[:tag1] [:tag2]]) (map (comp :tag first :content))) => [:tag1 :tag2]))

(facts flat-rows
  (fact "no need for flattening"
    (flat-rows [[:tag1] [:tag2]]) => [[:tag1] [:tag2]])

  (fact "one row passed"
    (flat-rows [:tag]) => [[:tag]])

  (fact "simple flattening"
    (flat-rows [[[:tag1]]]) => [[:tag1]])

  (fact "deep flattenig"
    (flat-rows [[[[[[[:tag1]]]]]]]) => [[:tag1]])

  (fact "two tags"
    (flat-rows [[[:tag1]] [:tag2]]) => [[:tag1] [:tag2]])

  (fact "with innner tag"
    (flat-rows [[[:tag1 [:tag2]]]]) => [[:tag1 [:tag2]]])

  (fact "tricky"
    (flat-rows [[[:row1 [:inner-tag]] [[:row2]]] [[[[:row3]]]]]) => [[:row1 [:inner-tag]] [:row2] [:row3]]))

(facts page-content
  (fact "one row"
    (page-content [[:tag1]]) => [:div.page-content [:tag1]])

  (fact "two rows"
    (page-content [[:tag1] [:tag2]]) => [:div.page-content [:tag1] [:tag2]])

  (fact "deep hierarchy"
    (page-content [[[[:tag1] [:tag2 [:inner-tag2]]] [:tag3]] [[[:tag4 [:inner-tag4]]]]]) => [:div.page-content [:tag1] [:tag2 [:inner-tag2]] [:tag3] [:tag4 [:inner-tag4]]]))

(facts html-page
  (fact "body given"
    (html-page nil [[:tag1] [[:tag2] [:tag3]]]) => [:html [:head [:meta {:http-equiv "content-type", :content "text/html; charset=UTF-8"}] [:style] nil] [:body [:tag1] [:tag2] [:tag3]]])

  (fact "head is concatenated to default head"
    (html-page [[:htag1] [[:htag2]]] [:tag1]) => [:html [:head [:meta {:http-equiv "content-type", :content "text/html; charset=UTF-8"}] [:style] [:htag1] [:htag2]] [:body [:tag1]]]))
