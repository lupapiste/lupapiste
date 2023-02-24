(ns lupapalvelu.screenmessage-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(apply-remote-minimal)

(fact "No screenmessages"
  (:screenmessages (query pena :screenmessages)) => empty?)

(fact "Add screenmessages"
  (command admin :add-screenmessage :products ["lupapiste"] :fi "Moi!" :sv "Hej!")
  => (just {:ok true :message-id truthy})
  (command admin :add-screenmessage :products ["lupapiste" "store"] :fi "Moido!")
  => (just {:ok true :message-id truthy})
  (command admin :add-screenmessage :products ["store" "terminal" "departmental"]
           :fi "Lukeminen kannattaa aina.")
  => (just {:ok true :message-id truthy}))

(fact "Bad additions"
  (command admin :add-screenmessage :products [] :fi "a" :sv "b") => fail?
  (command admin :add-screenmessage :products ["bad"] :fi "a" :sv "b") => fail?
  (command admin :add-screenmessage :products ["lupapiste" "bad" "store"]
           :fi "a" :sv "b") => fail?)

(fact "Two screenmessages"
  (:screenmessages (query pena :screenmessages))
  => (just [{:fi "Moi!" :sv "Hej!"} {:fi "Moido!" :sv "Moido!"}]))

(facts "Admin screenmessages"
  (let [{:keys [screenmessages]} (query admin :admin-screenmessages)
        {moi-id :id}             (util/find-by-key :fi "Moi!" screenmessages)]
    screenmessages
    => (just (just {:id moi-id :added pos? :products ["lupapiste"]
                    :fi "Moi!" :sv    "Hej!"})
             (just {:id truthy :added pos?
                    :products (just "lupapiste" "store" :in-any-order)
                    :fi "Moido!"})
             (just {:id       truthy :added pos?
                    :products (just "store" "terminal" "departmental"
                                    :in-any-order)
                    :fi       "Lukeminen kannattaa aina."})
             :in-any-order)
    (fact "Remove non-existent message"
      (command admin :remove-screenmessage :message-id "no such id") => ok?
      (:screenmessages (query admin :admin-screenmessages))
      => screenmessages)
    (fact "Remove message"
      (command admin :remove-screenmessage :message-id moi-id) => ok?
      (:screenmessages (query admin :admin-screenmessages))
      => (just (just {:id truthy :added pos?
                      :products (just "lupapiste" "store" :in-any-order)
                      :fi "Moido!"})
               (just {:id       truthy :added pos?
                      :products (just "store" "terminal" "departmental"
                                      :in-any-order)
                      :fi       "Lukeminen kannattaa aina."})
               :in-any-order)
      (:screenmessages (query pena :screenmessages))
      => [{:fi "Moido!" :sv "Moido!"}])))
