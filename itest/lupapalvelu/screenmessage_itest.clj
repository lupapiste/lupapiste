(ns lupapalvelu.screenmessage_itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(fact "No screenmessages"
  (:screenmessages (query pena :screenmessages)) => empty?)

(fact "Add screenmessages"
  (command admin :screenmessages-add :fi "Moi!" :sv "Hej!")
  =>
  (command admin :screenmessages-add :fi "Moido!" :sv "Hejdå!") => ok?)

(fact "Two screenmessages"
  (:screenmessages (query pena :screenmessages))
  => (just [(just {:added pos? :id string? :fi "Moi!" :sv "Hej!"})
            (just {:added pos? :id string? :fi "Moido!" :sv "Hejdå!"})]
           :in-any-order))

(fact "Remove all screenmessages"
  (command admin :screenmessages-reset) => ok?)

(fact "No screenmessages"
  (:screenmessages (query pena :screenmessages)) => empty?)
