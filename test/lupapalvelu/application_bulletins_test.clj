(ns lupapalvelu.application-bulletins-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [lupapalvelu.application-bulletins :refer :all]))

(def example-app {:documents [{:schema-info {:type :party} :data {:name "party 1"}}
                              {:schema-info {:type "party"} :data {:name "party 2"}}
                              {:schema-info {:type :location} :data {:name "location" :x 1 :y 2}}
                              {:schema-info {} :data {:name "untyped"}}]
                  :attachments [{:type "test1" :versions [{:id 1} {:id 2}] :latestVersion {:fileId 223 :filename "2.txt"}}
                                {:type "testi2"}
                                nil]
                  :state :submitted})

(facts "Remove party docs"
  (let [docs (:documents example-app)
        expected-docs [{:schema-info {:type :location} :data {:name "location" :x 1 :y 2}}
                       {:schema-info {} :data {:name "untyped"}}]]
    (remove-party-docs-fn docs) => expected-docs))

(fact "Snapshot"
  (let [snapshot (create-bulletin-snapshot example-app)
        updates (snapshot-updates snapshot {} 123)]
    (fact "attachments with latest versions"
      (:attachments snapshot) => [{:type "test1" :latestVersion {:fileId 223 :filename "2.txt"}}])

    (fact "state"
      (:state snapshot) => :submitted
      (:bulletinState snapshot) => :proclaimed)

    (fact "mongo update clause, versions are pushed"
      updates => {$push {:versions snapshot}
                  $set  {:modified 123}})))

(fact "Bulletin states mapping from app states"
  (bulletin-state :draft) => :proclaimed
  (bulletin-state :submitted) => :proclaimed
  (bulletin-state :sent) => :proclaimed
  (bulletin-state :verdictGiven) => :verdictGiven
  (bulletin-state :final) => :final
  (bulletin-state :appealed) => :verdictGiven
  (fact "default is 'proclaimed'"
    (bulletin-state :foo) => (throws IllegalArgumentException "No matching clause: :foo")))

