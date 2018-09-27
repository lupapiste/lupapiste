(ns lupapalvelu.pate.verdict-interface-test
  (:require [lupapalvelu.pate.verdict-interface :refer :all]
            [midje.sweet :refer :all]))

(fact "all-verdicts"
  (all-verdicts nil) => []
  (all-verdicts {}) => []
  (all-verdicts {:pate-verdicts ["hello"]}) => ["hello"]
  (all-verdicts {:verdicts ["hello"]}) => ["hello"]
  (all-verdicts {:verdicts ["hello"]
                 :pate-verdicts ["world"]}) => ["hello" "world"])

(fact "published-kuntalupatunnus"
  (let [draft     {:category "r"
                   :legacy?  true
                   :data     {:kuntalupatunnus "one"}}
        modern    {:category  "r"
                   :published {}
                   :data      {:kuntalupatunnus "two"}}
        wrapped   {:category  "r"
                   :legacy?   true
                   :published {}
                   :data      {:kuntalupatunnus {:_value    "three"
                                                 :_user     "hello"
                                                 :_modified 12345}}}
        unwrapped {:category  "r"
                   :legacy?   true
                   :published {}
                   :data      {:kuntalupatunnus "four"}}
        blank     {:category  "r"
                   :legacy?   true
                   :published {}
                   :data      {:kuntalupatunnus ""}}
        backends  [{:kuntalupatunnus "first"} {:kuntalupatunnus "second"}]]
    (published-kuntalupatunnus nil) => nil
    (published-kuntalupatunnus {}) => nil
    (published-kuntalupatunnus {:pate-verdicts [draft modern blank wrapped unwrapped]})
    => "three"
    (published-kuntalupatunnus {:pate-verdicts [draft modern blank unwrapped wrapped]})
    => "four"
    (published-kuntalupatunnus {:pate-verdicts [draft modern blank wrapped unwrapped]
                                :verdicts      backends})
    => "first"))

(fact "verdict-date"
  (verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1536537600000}]}]}]}) => 1536537600000
  (verdict-date {:pate-verdicts [{:data {:verdict-date 1538038800000}}]}) => 1538038800000
  (verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1536537600000}]}]}
                            {:paatokset [{:poytakirjat [{:paatospvm 1536539600000}]}]}
                            {:paatokset [{:poytakirjat [{:paatospvm 1536538600000}]}]}]}) => 1536539600000
  (verdict-date {:pate-verdicts [{:data {:verdict-date 1538036800000}}
                                 {:data {:verdict-date 1538038800000}}
                                 {:data {:verdict-date 1538037800000}}]}) => 1538038800000
  (verdict-date {:pate-verdicts [{:data {:foo :bar}}]}) => nil
  (verdict-date {:verdicts [{:paatokset nil}]}) => nil)
