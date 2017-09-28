(ns lupapalvelu.document.yleiset-alueet-canonical-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.document.yleiset-alueet-canonical get-handler)

(facts get-handler
  (fact "empty handlers"
    (get-handler {:handlers []}) => "")

  (fact "nil handlers"
    (get-handler {:handlers nil}) => "")

  (fact "no general handler"
    (get-handler {:handlers [{:firstName "first-name" :lastName "last-name"}]}) => "")

  (fact "with general handler"
    (get-handler {:handlers [{:firstName "first-name" :lastName "last-name" :general true}]})
    => {:henkilotieto {:Henkilo {:nimi {:etunimi "first-name", :sukunimi "last-name"}}}})

  (fact "multiple handlers with general"
    (get-handler {:handlers [{:firstName "other-first-name" :lastName "other-last-name"}
                             {:firstName "first-name" :lastName "last-name" :general true}
                             {:firstName "other-first-name-2" :lastName "other-last-name-2"}]})
    => {:henkilotieto {:Henkilo {:nimi {:etunimi "first-name", :sukunimi "last-name"}}}}))
