(ns lupapalvelu.digitizer-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.digitizer :refer :all]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

(testable-privates lupapalvelu.digitizer
                   good-buildings resolve-verdict-updates)

(facts "about digitizer tools"

  (let [v1 {:id 1 :kuntalupatunnus "foo" :timestamp 11 :paatokset []}
        v2 {:id 2 :kuntalupatunnus "bar" :timestamp 22}
        v3 {:id 3 :kuntalupatunnus "three" :timestamp 33
            :paatokset [{:poytakirjat [{:paatospvm 98765}]}]}
        v4 {:id 4 :kuntalupatunnus "four" :timestamp 44
            :paatokset [{:poytakirjat [{:muupvm 543} {:foo "bar"}]}]}]

    (facts "No verdicts"
      (resolve-verdict-updates nil nil) => []
      (resolve-verdict-updates [] []) => []
      (resolve-verdict-updates [] [{:id 1 :kuntalupatunnus "ignored"}]) => []
      (resolve-verdict-updates nil [{:id 1 :kuntalupatunnus "ignored"}]) => [])

    (fact )

    (fact "new verdict ids can be added"
      (resolve-verdict-updates [v1 v2] [v1 v2 {:kuntalupatunnus "baz"}])
      => [v1 v2 {:id "id1234" :kuntalupatunnus "baz" :timestamp nil :paatokset []}]
      (provided (mongo/create-id) => "id1234"))

    (fact "new verdict with id is ignored"
      (resolve-verdict-updates [v1 v2] [v1 v2 {:id 5 :kuntalupatunnus "baz"}])
      => [v1 v2])

    (fact "new verdict with existing kuntalupatunnus is not created"
      (resolve-verdict-updates [v1] [v1 {:kuntalupatunnus "foo"}])
      => [v1]
      (resolve-verdict-updates [v1] [v1 {:kuntalupatunnus "one"} {:kuntalupatunnus "one"
                                                               :msg "not created"}])
      => [v1 {:id "hello" :kuntalupatunnus "one" :timestamp nil :paatokset []}]
      (provided (mongo/create-id) => "hello"))

    (fact "modify kuntalupatunnus in an existing verdict"
      (resolve-verdict-updates [v1 v2] [v1 {:id 2 :kuntalupatunnus "baz"}])
      => [v1 {:id 2 :kuntalupatunnus "baz" :timestamp 22}])

    (fact "Missing kuntalupatunnus is different than nil"
      (resolve-verdict-updates [v1 v2] [v1 {:id 2}])
      => [v1 v2]
      (resolve-verdict-updates [v1 v2] [v1 {:id 2 :kuntalupatunnus nil}])
      => [v1 {:id 2 :kuntalupatunnus nil :timestamp 22}])

    (facts "modify verdict date in an existing verdict"
      (resolve-verdict-updates [v1 v2] [{:id 1 :verdictDate 12345} v2])
      => [{:id 1 :kuntalupatunnus "foo" :timestamp 11
           :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}
          v2]

      (resolve-verdict-updates [v1 v2] [{:id 1} v2])
      => [v1 v2]

      (resolve-verdict-updates [v1 v2] [{:id 1 :verdictDate nil} v2])
      => [{:id 1 :kuntalupatunnus "foo" :timestamp 11
           :paatokset [{:poytakirjat [{:paatospvm nil}]}]}
          v2]

      (resolve-verdict-updates [v1 v2] [v1 {:id 2 :verdictDate 12345}])
      => [v1 {:id 2 :kuntalupatunnus "bar" :timestamp 22
              :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}]

      (resolve-verdict-updates [v3 v1 v2] [v1 v2 {:id 3 :verdictDate 12345}])
      => [v1 v2
          {:id 3 :kuntalupatunnus "three" :timestamp 33
           :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}]

      (resolve-verdict-updates [v1 v4 v2] [v2 {:id 4 :verdictDate 12345} v1])
      => [v2 {:id 4 :kuntalupatunnus "four" :timestamp 44
              :paatokset [{:poytakirjat [{:muupvm 543 :paatospvm 12345}
                                         {:foo "bar"}]}]}
          v1])
    (facts "modify both"
      (resolve-verdict-updates [v1 v2] [{:id 1 :verdictDate 12345
                                         :kuntalupatunnus "one"} v2])
      => [{:id 1 :kuntalupatunnus "one" :timestamp 11
           :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}
          v2]

      (resolve-verdict-updates [v1 v2] [v1 {:id 2 :verdictDate 12345
                                            :kuntalupatunnus "one"}])
      => [v1 {:id 2 :kuntalupatunnus "one" :timestamp 22
              :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}]

      (resolve-verdict-updates [v3 v1 v2] [v1 v2 {:id 3 :verdictDate 12345
                                                  :kuntalupatunnus "one"}])
      => [v1 v2
          {:id 3 :kuntalupatunnus "one" :timestamp 33
           :paatokset [{:poytakirjat [{:paatospvm 12345}]}]}]

      (resolve-verdict-updates [v1 v4 v2] [v2 {:id 4 :verdictDate 12345
                                               :kuntalupatunnus "one"} v1])
      => [v2 {:id 4 :kuntalupatunnus "one" :timestamp 44
              :paatokset [{:poytakirjat [{:muupvm 543 :paatospvm 12345}
                                         {:foo "bar"}]}]}
          v1])

    (facts "Cumulative updates"
      (resolve-verdict-updates [v1 v4 v2]
                               [v2
                                {:id 4 :kuntalupatunnus "one"}
                                {:kuntalupatunnus "one"} ; Not created
                                {:id 4 :verdictDate 12345}
                                {:id 4 :kuntalupatunnus "two"}
                                v1])
      [v2 {:id 4 :kuntalupatunnus "two" :timestamp 44
           :paatokset [{:poytakirjat [{:muupvm 543 :paatospvm 12345}
                                      {:foo "bar"}]}]}
       v1])

    (fact "Verdict is removed if not present in the updates"
      (resolve-verdict-updates [v1 v2] [{:id 1}])
      => [v1]

      (resolve-verdict-updates [v1 v2] [{:id 1}
                                        {:kuntalupatunnus "bar"}])
      => [v1 {:id "new" :kuntalupatunnus "bar" :timestamp nil
              :paatokset []}]
      (provided (mongo/create-id) => "new"))))

(facts "good-buildings"
  (fact "invalid id 2"
    (good-buildings [{:location [395320.093 6697384.603] :nationalId 1}
                     {:location [1 2] :nationalId 2}])
    => [{:location [395320.093 6697384.603] :nationalId 1}])
  (fact "all valid"
    (good-buildings [{:location [395320.093 6697384.603] :nationalId 1}
                     {:location [430109.3125 7210461.375] :nationalId 2}])
    => [{:location [395320.093 6697384.603] :nationalId 1}
        {:location [430109.3125 7210461.375] :nationalId 2}])
  (fact "all bad"
    (good-buildings [{:location [395320.093 6] :nationalId 1}
                     {:location [430109.3125 7210461375] :nationalId 2}])
    => nil)
  (fact "not validated unless there is non-nil :location key"
    (good-buildings [{:nationalId 1}
                     {:nationalId 2 :location nil}])
    => [{:nationalId 1}
        {:nationalId 2 :location nil}]))
