(ns lupapalvelu.html-email.css-test
  (:require [lupapalvelu.html-email.css :as css]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.html-email.css
                   style-fields find-and-gather)


(facts style-fields
  (style-fields {}) => {}
  (style-fields {} {}) => {}
  (style-fields nil nil) => {}
  (style-fields {:foo "bar" :hii 10
                 :dum {}    :dim {:yes :no}}
                {:hii 20 :bar "baz" :goo {}})
  => {:foo "bar" :hii 20 :bar "baz"})

(facts find-and-gather
  (find-and-gather :foo {}) => nil
  (find-and-gather :foo {:foo "bar"}) => nil
  (find-and-gather :foo {:foo "bar"
                         :bar {:one "two"
                               :foo {:hello 1}}}) => {:foo   "bar"
                                                      :one   "two"
                                                      :hello 1}
  (find-and-gather :foo {:one "one"
                         :bar {:one "two"
                               :goo {:three 3}
                               :foo {:one "three"}}
                         :baz {:two 2}})
  => {:one "three"})

(facts find-style

  (fact "Not found, no fallback"
    (css/find-style {:one {:a   "a"
                           :two {:b     "b"
                                 :three {:c    "c"
                                         :d    "d"
                                         :four {:e "e"}}}}}
                    "five" false)
    => nil)

  (fact "Found, no fallback"
    (css/find-style {:one {:a   "a"
                           :two {:b     "b"
                                 :three {:c    "c"
                                         :d    "d"
                                         :four {:e "e"}}}}}
                    "three" false)
    => {:a "a" :b "b" :c "c" :d "d"})

  (fact "Not found, fallback, but no help there"
    (css/find-style {} "this-style-is-nowhere") => nil)

  (fact "Found on fallback"
    (let [result (css/find-style {} "h1" true)]
      result => truthy
      (css/find-style {} :h1) => result
      (css/find-style "h1") => result)))
