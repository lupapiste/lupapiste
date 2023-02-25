(ns lupapalvelu.pate.verdict-interface-test
  (:require [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict-interface :refer :all]
            [midje.sweet :refer :all]))

(fact "all-verdicts"
  (all-verdicts nil) => []
  (all-verdicts {}) => []
  (all-verdicts {:pate-verdicts ["hello"]}) => ["hello"]
  (all-verdicts {:verdicts ["hello"]}) => ["hello"]
  (all-verdicts {:verdicts ["hello"]
                 :pate-verdicts ["world"]}) => ["hello" "world"])

(fact "latest and earlierst verdicts"
  (let [cmd {:application {:verdicts      [{:id "bs1" :timestamp 100}
                                           {:id "bs2" :timestamp 50}
                                           {:id "bs-draft" :timestamp 200 :draft true}]
                           :pate-verdicts [{:id        "pate1"
                                            :category  "r"
                                            :published {:published 10}
                                            :data      {:foo {:_value "bar" :_user "u" :_modified 1}}}
                                           {:id        "pate2"
                                            :category  "r"
                                            :published {:published 5}
                                            :data      {:hii {:_value "hoo" :_user "u" :_modified 1}}}
                                           {:id          "pate3"
                                            :category    "r"
                                            :published   {:published 1}
                                            :replacement {:replaced-by "some"}}
                                           {:id "pate-draft" :category "r"}
                                           {:id          "pate-replaced"
                                            :category    "r"
                                            :published   {:published 300}
                                            :replacement {:replaced-by "other"}}]}}]
    (latest-published-pate-verdict cmd) => {:id        "pate1"
                                            :category  "r"
                                            :published {:published 10}
                                            :data      {:foo "bar"}}
    (latest-published-verdict cmd) => {:id "bs1" :timestamp 100}
    (earliest-published-verdict cmd) => {:id        "pate2"
                                         :category  "r"
                                         :published {:published 5}
                                         :data      {:hii "hoo"}}))

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
    (kuntalupatunnukset {:pate-verdicts [draft modern blank wrapped unwrapped]
                         :verdicts backends})
    => ["first" "second" "one" "two" "three" "four"]
    (kuntalupatunnukset nil) => []
    (kuntalupatunnukset {}) => []
    (kuntalupatunnukset {:verdicts [{:draft true :kuntalupatunnus "drafty"}]})
    => ["drafty"]
    (kuntalupatunnukset {:pate-verdicts [draft draft draft]})
    => ["one"])))

(fact "published-municipality-permit-ids"
      (published-municipality-permit-ids {:pate-verdicts [{:category "r"
                                                           :legacy?  true
                                                           :data     {:kuntalupatunnus "one"}}]})
      => []
      (published-municipality-permit-ids {:pate-verdicts [{:category  "r"
                                                           :legacy?   true
                                                           :published {}
                                                           :data      {:kuntalupatunnus {:_value    "three"
                                                                                         :_user     "hello"
                                                                                         :_modified 12345}}}]})
      => ["three"]
      (published-municipality-permit-ids {:verdicts [{:kuntalupatunnus "first"}]})
      => ["first"]
      (published-municipality-permit-ids {:pate-verdicts [{:category  "r"
                                                           :legacy?   true
                                                           :published {}
                                                           :data      {:kuntalupatunnus {:_value    "three"
                                                                                         :_user     "hello"
                                                                                         :_modified 12345}}}
                                                          {:category "r"
                                                           :legacy?  true
                                                           :data     {:kuntalupatunnus "one"}}]
                                          :verdicts [{:kuntalupatunnus "first"}]})
      => ["first" "three"]
      (published-municipality-permit-ids {}) => []
      (published-municipality-permit-ids {:verdicts [{:draft true :kuntalupatunnus "drafty"}]})
      => []
      (published-municipality-permit-ids {:verdicts [{:kuntalupatunnus "first"}
                                                     {:kuntalupatunnus "first"}]})
      => ["first"])

(defn- ->iso-8601-date [ts]
  (f/unparse (f/with-zone (:date-time-no-ms f/formatters) (t/time-zone-for-id "Europe/Helsinki")) (c/from-long (long ts))))

(def wrap (partial metadata/wrap "user" 12345))

(fact "latest-published-verdict-date"
  (latest-published-verdict-date {}) => nil
  (latest-published-verdict-date []) => nil
  (latest-published-verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1}]}]}]}) => 1
  (latest-published-verdict-date {:pate-verdicts [{:category  "r"
                                                   :data      {:verdict-date 2}
                                                   :published {:published 1}}]}) => 2
  (latest-published-verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1}]}]}
                                             {:paatokset [{:poytakirjat [{:paatospvm 3}]}]}
                                             {:paatokset [{:poytakirjat [{:paatospvm 2}]}]}]}) => 3
  (latest-published-verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1}]}]}
                                             {:draft     true
                                              :paatokset [{:poytakirjat [{:paatospvm 3}]}]}
                                             {:paatokset [{:poytakirjat [{:paatospvm 2}]}]}]}) => 2
  (latest-published-verdict-date {:pate-verdicts [{:category  "r"
                                                   :data      {:verdict-date 1}
                                                   :published {:published 11}}
                                                  {:category  "r"
                                                   :data      {:verdict-date 3}
                                                   :published {:published 1}}
                                                  {:category  "r"
                                                   :data      {:verdict-date 2}
                                                   :published {:published 22}}]})=> 3
  (latest-published-verdict-date {:pate-verdicts [{:category  "r"
                                                   :data      {:verdict-date (wrap 1)}
                                                   :published {:published 11}}
                                                  {:category  "r"
                                                   :data      {:verdict-date (wrap 3)}
                                                   :published {:published 1}}
                                                  {:category  "r"
                                                   :data      {:verdict-date (wrap 2)}
                                                   :published {:published 22}}]}) => 3
  (latest-published-verdict-date {:pate-verdicts [{:category  "r"
                                                   :data      {:verdict-date (wrap 1)}
                                                   :published {:published 11}}
                                                  {:category "r"
                                                   :data     {:verdict-date (wrap 3)}}
                                                  {:category  "r"
                                                   :data      {:verdict-date (wrap 2)}
                                                   :published {:published 22}}]}) => 2
  (latest-published-verdict-date [{:category  "r"
                                   :data      {:verdict-date (wrap 1)}
                                   :published {:published 11}}
                                  {:category "r"
                                   :data     {:verdict-date (wrap 3)}}
                                  {:category  "r"
                                   :data      {:verdict-date (wrap 2)}
                                   :published {:published 22}}]) => 2
  (latest-published-verdict-date {:verdicts      [{:paatokset [{:poytakirjat [{:paatospvm 1}]}]}
                                                  {:paatokset [{:poytakirjat [{:paatospvm 3}]}]}
                                                  {:paatokset [{:poytakirjat [{:paatospvm 2}]}]}]
                                  :pate-verdicts [{:category  "r"
                                                   :data      {:verdict-date 1}
                                                   :published {:published 11}}
                                                  {:category  "r"
                                                   :data      {:verdict-date 4}
                                                   :published {:published 1}}
                                                  {:category  "r"
                                                   :data      {:verdict-date 2}
                                                   :published {:published 22}}]}) => 4
  (latest-published-verdict-date {:verdicts      [{:paatokset [{:poytakirjat [{:paatospvm 1}]}]}
                                                  {:paatokset [{:poytakirjat [{:paatospvm 3}]}]}
                                                  {:paatokset [{:poytakirjat [{:paatospvm 6}]}]}]
                                  :pate-verdicts [{:category  "r"
                                                   :data      {:verdict-date 1}
                                                   :published {:published 11}}
                                                  {:category  "r"
                                                   :data      {:verdict-date 4}
                                                   :published {:published 1}}
                                                  {:category  "r"
                                                   :data      {:verdict-date 2}
                                                   :published {:published 22}}]}) => 6
  (latest-published-verdict-date [{:paatokset [{:poytakirjat [{:paatospvm 1}]}]}
                                  {:paatokset [{:poytakirjat [{:paatospvm 3}]}]}
                                  {:paatokset [{:poytakirjat [{:paatospvm 2}]}]}
                                  {:category  "r"
                                   :data      {:verdict-date 1}
                                   :published {:published 11}}
                                  {:category "r"
                                   :data     {:verdict-date 4}}
                                  {:category  "r"
                                   :data      {:verdict-date 2}
                                   :published {:published 22}}]) => 3
  (latest-published-verdict-date [{:paatokset [{:poytakirjat [{:paatospvm 1}]}]}
                                  {:paatokset [{:poytakirjat [{:paatospvm 3}]}]}
                                  {:paatokset [{:poytakirjat [{:paatospvm 2}]}]}
                                  {:category  "r"
                                   :data      {:verdict-date 5}
                                   :published {:published 11}}
                                  {:category "r"
                                   :data     {:verdict-date 4}}
                                  {:category  "r"
                                   :data      {:verdict-date 2}
                                   :published {:published 22}}]) => 5
  (latest-published-verdict-date [{:paatokset [{:poytakirjat [{:paatospvm 1}]}]}
                                  {:paatokset [{:poytakirjat [{:paatospvm 3}]}]}
                                  {:paatokset [{:poytakirjat [{:paatospvm 2}]}]}
                                  {:category    "r"
                                   :data        {:verdict-date 10}
                                   :published   {:published 1}
                                   :replacement {:replaced-by "foo"}}
                                  {:category  "r"
                                   :data      {:verdict-date 5}
                                   :published {:published 11}}
                                  {:category  "r"
                                   :data      {:verdict-date 2}
                                   :published {:published 22}}]) => 5
  (latest-published-verdict-date {:pate-verdicts [{:category "r"
                                                   :data     {:foo :bar}}]}) => nil
  (latest-published-verdict-date {:verdicts [{:paatokset nil}]}) => nil
  (latest-published-verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1536537600000}]}]}]}
                ->iso-8601-date) => (contains "2018-09-10")
  (latest-published-verdict-date {:pate-verdicts [{:category  "r"
                                                   :data      {:verdict-date 1538038800000}
                                                   :published {:published 1538038700000}}]}
                ->iso-8601-date) => (contains "2018-09-27"))

(fact "arkistointi-date"
  (latest-published-verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1538082000000}]}]}]}) => 1538082000000
  (latest-published-verdict-date {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm nil}]}]}]}) => nil)

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

(let [verdicts      {:verdicts [{:paatokset [{:poytakirjat [{:paatoksentekija "Viranhaltija"}]}]}]}
      pate-verdicts {:pate-verdicts [{:category  :r
                                      :data      {:handler "Verdict handler"}
                                      :published {:published 123}}
                                     {:category  :r
                                      :data      {:handler "Verdict handler"
                                                  :giver "Verdict giver"}
                                      :published {:published 122}}
                                     {:category  :r
                                      :data      {:handler "Verdict handler"}
                                      :template {:giver "lautakunta"}
                                      :references {:boardname "The Board"}
                                      :published {:published 121}}]}]
  (fact "handler"
    (handler verdicts) => "Viranhaltija"
    (handler pate-verdicts) => "Verdict handler"
    (handler nil) => nil
    (handler {}) => nil
    (handler (merge verdicts pate-verdicts)) => "Verdict handler"
    (handler (assoc verdicts
                    :pate-verdicts [{:data {:handler "Verdict handler"}}]))
    => "Viranhaltija")

  (fact "verdict-giver"
    (verdict-giver verdicts) => "Viranhaltija"
    (verdict-giver pate-verdicts) => "Verdict handler"
    (verdict-giver nil) => nil
    (verdict-giver {}) => nil
    (verdict-giver (merge verdicts pate-verdicts)) => "Verdict handler"
    (verdict-giver (assoc verdicts
                          :pate-verdicts [{:data {:handler "Verdict handler"}}]))
    => "Viranhaltija"
    (verdict-giver (assoc verdicts
                          :pate-verdicts [{:data {:handler "  "}
                                           :published {:published 1234}}]))
    => "Viranhaltija"
    (verdict-giver (update-in pate-verdicts [:pate-verdicts 1 :published :published] + 10))
    => "Verdict giver"
    (verdict-giver (update-in pate-verdicts [:pate-verdicts 2 :published :published] + 10))
    => "The Board"))

(fact "verdicts by backend id"
  (verdicts-by-backend-id {:verdicts [{:kuntalupatunnus "123"}
                                      {:kuntalupatunnus "456"}
                                      {:kuntalupatunnus "789"}]} "456") => [{:kuntalupatunnus "456"}]
  (verdicts-by-backend-id {:verdicts [{:kuntalupatunnus "123"}
                                      {:kuntalupatunnus "456"}
                                      {:foo :bar}
                                      {:kuntalupatunnus "789"}
                                      {:kuntalupatunnus "456"}]} "456") => [{:kuntalupatunnus "456"} {:kuntalupatunnus "456"}]
  (verdicts-by-backend-id {:pate-verdicts [{:category :r :data {:kuntalupatunnus "AAA"}}
                                           {:category :r :data {:kuntalupatunnus "BBB"}}
                                           {:category :r :data {:kuntalupatunnus "CCC"}}]} "BBB") => [{:category :r :data {:kuntalupatunnus "BBB"}}]
  (verdicts-by-backend-id {:pate-verdicts [{:category :r :data {:kuntalupatunnus "AAA"}}
                                           {:category :r :data {:kuntalupatunnus "BBB"}}
                                           {:category :r :data {:kuntalupatunnus "CCC"}}
                                           {:category :r :data {:kuntalupatunnus "BBB"}}
                                           {:category :r :data {:foo :bar}}
                                           {:category :r :data {:kuntalupatunnus "AAA"}}]} "BBB") => [{:category :r :data {:kuntalupatunnus "BBB"}}{:category :r :data {:kuntalupatunnus "BBB"}}]
  (verdicts-by-backend-id {:pate-verdicts [{:category :r :data {:kuntalupatunnus (metadata/wrap "foo" 123 "AAA")}}]
                           :verdicts [{:kuntalupatunnus "AAA"}]}
                          "AAA")
  => [{:kuntalupatunnus "AAA"} {:category :r :data {:kuntalupatunnus "AAA"}}]
  (verdicts-by-backend-id {} "123") => nil)
