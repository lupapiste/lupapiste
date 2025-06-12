(ns sade.core-test
  (:require [midje.sweet :refer :all]
            [sade.core :refer :all]))

(facts
  (ok)                 => {:ok true}
  (ok :id 1)           => {:ok true :id 1}
  (ok :id 1 :extra)    => (throws Exception)
  (ok :text "success") => {:ok true :text "success"})

(facts
  (fact (fail :kosh)              => {:ok false :text "kosh"})
  (fact (fail "kosh")             => {:ok false :text "kosh"})
  (fact (fail "kosh" :id)         => (throws Exception))
  (fact (fail "kosh" :id "1")     => {:ok false :text "kosh" :id "1"})
  (fact (fail "kosh" {:id "1"})   => {:ok false :text "kosh" :id "1"}))

(facts
  (ok? {:ok true})   => true
  (ok? {:ok "true"}) => false
  (ok? {})           => false
  (ok? {:ok false})  => false)
