(ns lupapalvelu.data-skeleton-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.data-skeleton :refer :all]))

(facts "from-context"
  (fact "allowed path values"
    ((from-context [1]) [:zero ...result...]) => ...result...
    ((from-context [last]) [:other :values ...result...]) => ...result...
    ((from-context [:keyword]) {:keyword ...result...}) => ...result...
    ((from-context [#{...result...}]) ...result...) => ...result...

    ((from-context [{:foo :bar}]) anything) => (throws #"Illegal value")
    ((from-context [-1]) anything) => (throws #"Illegal value"))

  (fact "default value"
    ((from-context []) ...default...) => ...default...
    ((from-context [] ...default...) false) => false
    ((from-context [anything] ...default...) nil) => ...default...
    ((from-context [:foo anything anything] ...default...) {:bar anything}) => ...default...)

  (fact "path with multiple values"
    ((from-context [:foo last 1]) {:foo [anything anything '(anything ...result...)]}) => ...result...))

(facts "build-with-skeleton"
  (fact "simple case"
    (build-with-skeleton {}
                         anything
                         anything)
    => {})
  (fact "unnested map"
    (build-with-skeleton {:foo (access :bar)}
                         {:key :value}
                         {:bar (from-context [:key])})
    => {:foo :value})
  (fact "nested map"
    (build-with-skeleton {:nested {:key (access :access-fn)}}
                         {:values [1 2 3 4]}
                         {:access-fn (from-context [:values count])})
    => {:nested {:key 4}})
  (fact "array-from"
    (build-with-skeleton {:values (array-from :values-fn
                                              {:value (access :value-fn)})}
                         {:values [1 2 3 4]}
                         {:values-fn (from-context [:values])
                          :value-fn (from-context [:context])})
    => {:values [{:value 1} {:value 2} {:value 3} {:value 4}]})
  (fact "nested array-from"
    (build-with-skeleton {:values (array-from :values-fn
                                              {:value (array-from :nested-values-fn
                                                                  (access :nested-value-fn))})}
                         {:values [1 2 3 4]}
                         {:values-fn (from-context [:values])
                          :nested-values-fn (from-context [:context #(range 0 %)])
                          :nested-value-fn (from-context [:context])})
    => {:values [{:value [0]} {:value [0 1]} {:value [0 1 2]} {:value [0 1 2 3]}]}))
