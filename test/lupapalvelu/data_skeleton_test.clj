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
