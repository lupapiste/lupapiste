(ns lupapalvelu.pate.verdict-interface-test
  (:require [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [lupapalvelu.pate.verdict-interface :refer :all]
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
    => "first"

  (fact "all-kuntalupatunnukset"
    (kuntalupatunnukset {:pate-verdicts [draft modern blank wrapped unwrapped]})
    => ["one" "two" "three" "four"]
    (kuntalupatunnukset {:verdicts backends})
    => ["first" "second"]
    (kuntalupatunnukset nil) => nil
    (kuntalupatunnukset {}) => nil)))

(defn- ->iso-8601-date [ts]
  (f/unparse (f/with-zone (:date-time-no-ms f/formatters) (t/time-zone-for-id "Europe/Helsinki")) (c/from-long (long ts))))

(fact "verdict-date"
  (verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1536537600000}]}]}]}) => 1536537600000
  (verdict-date {:pate-verdicts [{:data {:verdict-date 1538038800000}
                                  :published {:published 1538038700000}}]}) => 1538038800000
  (verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1536537600000}]}]}
                            {:paatokset [{:poytakirjat [{:paatospvm 1536539600000}]}]}
                            {:paatokset [{:poytakirjat [{:paatospvm 1536538600000}]}]}]}) => 1536539600000
  (verdict-date {:pate-verdicts [{:data {:verdict-date 1538036800000}
                                  :published {:published 1538031800001}}
                                 {:data {:verdict-date 1538038800000}
                                  :published {:published 1538031800003}}
                                 {:data {:verdict-date 1538037800000}
                                  :published {:published 1538031800002}}]}) => 1538038800000
  (verdict-date {:pate-verdicts [{:data {:foo :bar}}]}) => nil
  (verdict-date {:verdicts [{:paatokset nil}]}) => nil
  (verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1536537600000}]}]}]}
                ->iso-8601-date) => (contains "2018-09-10")
  (verdict-date {:pate-verdicts [{:data {:verdict-date 1538038800000}
                                  :published {:published 1538038700000}}]}
                ->iso-8601-date) => (contains "2018-09-27"))

(fact "arkistointi-date"
  (verdict-date {:verdicts [{:paatokset [{:poytakirjat {:0 {:paatospvm 1538082000000}}}]}]}) => 1538082000000
  (verdict-date {:verdicts [{:paatokset [{:poytakirjat {:0 {:paatospvm nil}}}]}]}) => nil)

(fact "lainvoimainen-date"
  (lainvoimainen
    {:verdicts [{:paatokset [{:paivamaarat {:lainvoimainen 1536537600000}}]}]}) => 1536537600000
  (lainvoimainen
    {:verdicts [{:paatokset [{:paivamaarat {:lainvoimainen 1536537600000}}]}]}
    ->iso-8601-date) => (contains "2018-09-10")
  (lainvoimainen
    {:pate-verdicts [{:data {:lainvoimainen 1538038800000}
                      :published {:published 1538038700000}}]}) => 1538038800000
  (lainvoimainen
    {:pate-verdicts [{:data {:lainvoimainen 1538038800000}
                      :published {:published 1538038700000}}]}
    ->iso-8601-date) => (contains "2018-09-27")
  (lainvoimainen
    {:verdicts [{:paatokset [{:paivamaarat {:lainvoimainen 1536537600000}}]}
                {:paatokset [{:paivamaarat {:lainvoimainen 1536537900000}}]}
                {:paatokset [{:paivamaarat {:lainvoimainen 1536537500000}}]}]}) => 1536537900000
  (lainvoimainen
    {:pate-verdicts [{:data      {:lainvoimainen 1538039200000}
                      :published {:published 1538031800001}}
                     {:data      {:lainvoimainen 1538039100000}
                      :published {:published 1538031800003}}
                     {:data      {:lainvoimainen 1538039000000}
                      :published {:published 1538031800002}}]}) => 1538039100000
  (lainvoimainen {:pate-verdicts [{:data {:foo :bar}}]}) => nil
  (lainvoimainen {:verdicts [{:paatokset nil}]}) => nil)

(fact "handler"
  (handler {:verdicts [{:paatokset [{:poytakirjat [{:paatoksentekija "Viranhaltija"}]}]}]}) => "Viranhaltija"
  (handler {:pate-verdicts [{:data {:handler "Verdict handler"}
                             :published {:published 1538038700000}}]}) => "Verdict handler"
  (handler nil) => nil
  (handler {}) => nil)

(fact "verdicts by backend id"
  (verdicts-by-backend-id {:verdicts [{:kuntalupatunnus "123"}
                                      {:kuntalupatunnus "456"}
                                      {:kuntalupatunnus "789"}]} "456") => [{:kuntalupatunnus "456"}]
  (verdicts-by-backend-id {:verdicts [{:kuntalupatunnus "123"}
                                      {:kuntalupatunnus "456"}
                                      {:foo :bar}
                                      {:kuntalupatunnus "789"}
                                      {:kuntalupatunnus "456"}]} "456") => [{:kuntalupatunnus "456"} {:kuntalupatunnus "456"}]
  (verdicts-by-backend-id {:pate-verdicts [{:data {:kuntalupatunnus "AAA"}}
                                           {:data {:kuntalupatunnus "BBB"}}
                                           {:data {:kuntalupatunnus "CCC"}}]} "BBB") => [{:data {:kuntalupatunnus "BBB"}}]
  (verdicts-by-backend-id {:pate-verdicts [{:data {:kuntalupatunnus "AAA"}}
                                           {:data {:kuntalupatunnus "BBB"}}
                                           {:data {:kuntalupatunnus "CCC"}}
                                           {:data {:kuntalupatunnus "BBB"}}
                                           {:data {:foo :bar}}
                                           {:data {:kuntalupatunnus "AAA"}}]} "BBB") => [{:data {:kuntalupatunnus "BBB"}}{:data {:kuntalupatunnus "BBB"}}]
  (verdicts-by-backend-id {} "123") => nil)
