(ns sade.core-test
  (:require [midje.sweet :refer :all]
            [sade.core :refer :all]))

(facts
  (ok)                 => {:ok true}
  (ok :id 1)           => {:ok true :id 1}
  (ok :id 1 :extra)    => (throws Exception)
  (ok :text "success") => {:ok true :text "success"})

(facts
  (fail :kosh)               => {:ok false :text "kosh"}
  (fail "kosh")              => {:ok false :text "kosh"}
  (fail "kosh" "er")         => {:ok false :text "kosh"}
  (fail "kosh%s" "er")       => {:ok false :text "kosher"}
  (fail "kosh%s%s" "er" "!") => {:ok false :text "kosher!"}
  (fail "kosh%s%s" "er")     => (throws Exception))

(facts
  (ok? {:ok true})   => true
  (ok? {:ok "true"}) => false
  (ok? {})           => false
  (ok? {:ok false})  => false)
