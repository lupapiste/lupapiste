(ns lupapalvelu.application-bulletins-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [lupapalvelu.application-bulletins :refer :all]
            [lupapalvelu.mongo :refer [create-id]]
            [lupapalvelu.pate.metadata :as metadata]))

(def example-app {:documents [{:schema-info {:type :party} :data {:name "party 1"}}
                              {:schema-info {:type "party"} :data {:name "party 2"}}
                              {:schema-info {:type :location} :data {:name "location" :x 1 :y 2}}
                              {:schema-info {} :data {:name "untyped"}}]
                  :attachments [{:type "test1" :versions [{:id 1} {:id 2}] :latestVersion {:fileId 223 :filename "2.txt"}}
                                {:type "testi2"}
                                nil]
                  :permitType "R"
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


(def timestamp 1503003635780)
(def verdict-id (create-id))
(def kuntalupatunnus "lupatunnus")
(def anto 1503003635781)
(def signed1 1503003635782)
(def signed2 1503003635783)
(def signer-id1 (create-id))
(def signer-id2 (create-id))
(def lainvoimainen 1503003635784)
(def paatospvm 1503003635785)
(def handler "handler")
(def verdict-text "Decisions were made.")
(def section "1")
(def code 2)
(def signatures
  [{:created signed1
    :user {:id signer-id1
           :firstName "First"
           :lastName "Name"}}
   {:created signed2
    :user {:id signer-id2
           :firstName "Second"
           :lastName "Name"}}])
(def test-verdict {:id verdict-id
                   :kuntalupatunnus kuntalupatunnus
                   :draft true
                   :timestamp timestamp
                   :sopimus false
                   :metadata nil
                   :paatokset [{:id "5b7e5772e7d8a1a88e669357"
                                :paivamaarat {:anto anto
                                              :lainvoimainen lainvoimainen}
                                :poytakirjat [{:paatoksentekija handler
                                               :urlHash "5b7e5772e7d8a1a88e669356"
                                               :status code
                                               :paatos verdict-text
                                               :paatospvm paatospvm
                                               :pykala section
                                               :paatoskoodi code}]}]})

(def migrated-signatures
  [{:date    signed1
    :user-id signer-id1
    :name "First Name"}
   {:date    signed2
    :user-id signer-id2
    :name "Second Name"}])

(defn wrap [x]
  (metadata/wrap "Verdict draft Pate migration" timestamp x))

(def pate-test-verdict {:id verdict-id
                        :modified timestamp
                        :category "r"
                        :state (wrap "draft")
                        :data {:handler (wrap handler)
                               :kuntalupatunnus (wrap kuntalupatunnus)
                               :verdict-section (wrap section)
                               :verdict-code    (wrap (str code))
                               :verdict-text    (wrap verdict-text)
                               :anto            (wrap anto)
                               :lainvoimainen   (wrap lainvoimainen)
                               :reviews         {"katselmus-id" {:name (wrap "Tarkastus")
                                                                 :type (wrap "muu-tarkastus")}}
                               :foremen         {"foreman-id" {:role (wrap "Supervising supervisor")}}
                               :conditions      {"condition-id1" {:name (wrap "Muu 1")}
                                                 "condition-id2" {:name (wrap "Muu 2")}}
                               :attachments     [{:type-group "paatoksenteko"
                                                  :type-id     "paatos"
                                                  :amount 1}
                                                 {:type-group "muut"
                                                  :type-id    "paatosote"
                                                  :amount 1}]}
                        :template {:inclusions [:foreman-label :conditions-title :foremen-title :kuntalupatunnus :verdict-section :verdict-text :anto :attachments :foremen.role :foremen.remove :verdict-code :conditions.name :conditions.remove :reviews-title :type-label :reviews.name :reviews.type :reviews.remove :add-review :name-label :condition-label :lainvoimainen :handler :add-foreman :upload :add-condition]}
                            :legacy? true})

(fact "verdict-data-for-bulletin-snapshot"
      (verdict-data-for-bulletin-snapshot test-verdict) => (verdict-data-for-bulletin-snapshot pate-test-verdict))
