(ns lupapalvelu.migration.pate-verdict-migration-test
  (:require [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.migration.pate-verdict-migration :refer :all]
            [lupapalvelu.pate.metadata :as metadata]))

(def timestamp 1234)

(defn wrap [x]
  (metadata/wrap "Verdict draft Pate migration" timestamp x))

(defn tasks-for-verdict [verdict-id]
  [{:id "katselmus-id"
    :schema-info {:name "task-katselmus"}
    :taskname "AloituskoKKous"
    :data {:katselmuksenLaji {:value "aloituskokous"}}
    :source {:id verdict-id}}
   {:id "foreman-id"
    :schema-info {:name "task-vaadittu-tyonjohtaja"}
    :taskname "Supervising supervisor"
    :source {:id verdict-id}}
   {:id "condition-id1"
    :schema-info {:name "task-lupamaarays"}
    :taskname "Muu 1"
    :source {:id verdict-id}}
   {:id "condition-id2"
    :schema-info {:name "task-lupamaarays"}
    :taskname "Muu 2"
    :source {:id verdict-id}}])

(def verdict-id "verdict-id")
(def kuntalupatunnus "lupatunnus")
(def anto 2345)
(def signed1 4321)
(def signed2 5432)
(def signer-id1 "signer")
(def signer-id2 "singer")
(def lainvoimainen 3456)
(def paatospvm 4567)
(def handler "handler")
(def verdict-text "Decisions were made.")
(def section "1")
(def code 2)
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
                                               :status 2
                                               :paatos verdict-text
                                               :paatospvm paatospvm
                                               :pykala section
                                               :paatoskoodi code}]}]
                   :signatures [{:created signed1
                                 :user {:id signer-id1
                                        :firstName "First"
                                        :lastName "Name"}}
                                {:created signed2
                                 :user {:id signer-id2
                                        :firstName "Second"
                                        :lastName "Name"}}]})
(def migrated-test-verdict {:id verdict-id
                            :modified 1234
                            :category :r
                            :data {:handler (wrap handler)
                                   :kuntalupatunnus (wrap kuntalupatunnus)
                                   :verdict-section (wrap section)
                                   :verdict-code    (wrap (str code))
                                   :verdict-text    (wrap verdict-text)
                                   :anto            (wrap anto)
                                   :lainvoimainen   (wrap lainvoimainen)
                                   :reviews         {"katselmus-id" {:name (wrap "AloituskoKKous")
                                                                     :type (wrap "aloituskokous")}}
                                   :foremen         {"foreman-id" {:role (wrap "Supervising supervisor")}}
                                   :conditions      {"condition-id1" {:name (wrap "Muu 1")}
                                                     "condition-id2" {:name (wrap "Muu 2")}}
                                   :attachments     [{:type-group "paatoksenteko"
                                                      :type-id     "paatos"
                                                      :amount 1}]}
                            :template {:inclusions [:foreman-label :conditions-title :foremen-title :kuntalupatunnus :verdict-section :verdict-text :anto :attachments :foremen.role :foremen.remove :verdict-code :conditions.name :conditions.remove :reviews-title :type-label :reviews.name :reviews.type :reviews.remove :add-review :name-label :condition-label :lainvoimainen :handler :add-foreman :upload :add-condition]}
                            :signatures [{:date    signed1
                                          :user-id signer-id1
                                          :name "First Name"}
                                         {:date    signed2
                                          :user-id signer-id2
                                          :name "Second Name"}]
                            :legacy? true})
(def migrated-test-verdict-no-tasks
  (-> migrated-test-verdict
      (update :data dissoc :reviews :foremen :conditions)))
(def test-application {:id "app-id"
                       :verdicts [test-verdict]
                       :permitType "R"
                       :tasks (tasks-for-verdict (:id test-verdict))
                       :attachments [{:latestVersion {:fileId "attachment-id"}
                                      :target {:id verdict-id
                                               :type "verdict"}
                                      :id "attachment1"
                                      :type {:type-id    "paatos"
                                             :type-group "paatoksenteko"}}]})

(def app-one-verdict-no-tasks (dissoc test-application :tasks))
(def app-one-verdict-with-tasks test-application)

(facts "->pate-legacy-verdict"
  (fact "base case"
    (->pate-legacy-verdict test-application
                           test-verdict
                           timestamp)
    =>
    migrated-test-verdict)

  (fact "the gategory sijoitussopimus is contract"
    (->pate-legacy-verdict (assoc test-application :permitSubtype "sijoitussopimus")
                           test-verdict
                           timestamp)
    => (contains {:category :contract}))

  (fact "only tasks related to given verdict affect the migration"
    (->pate-legacy-verdict (assoc test-application
                                  :tasks
                                  [{:id "condition-id"
                                    :schema-info {:name "task-lupamaarays"}
                                    :taskname "Muu"
                                    :source {:id "This is not the verdict you're looking for"}}])
                           test-verdict
                           timestamp)
    =not=> (contains {:reviews    anything
                      :foremen    anything
                      :conditions anything}))

  (fact "published verdict has published timestamp and archive data"
    (->pate-legacy-verdict (update test-application
                                   :verdicts
                                   #(map (fn [v] (assoc v :draft false)) %))
                           (assoc test-verdict :draft false)
                           timestamp)
    => (contains {:published {:published anto
                              :attachment-id "attachment1"}
                  :archive {:verdict-giver handler
                            :lainvoimainen lainvoimainen}})))

(facts "migration-updates"
  (fact "one draft verdict, no tasks"
    (migration-updates app-one-verdict-no-tasks timestamp)
    => {$unset {:verdicts ""}
        $set {:pate-verdicts [migrated-test-verdict-no-tasks]}
        $pull {:tasks {:source.id {$in [(:id migrated-test-verdict-no-tasks)]}}}})

  (fact "one draft verdict with tasks"
    (migration-updates app-one-verdict-with-tasks timestamp)
    => {$unset {:verdicts ""}
        $set {:pate-verdicts [migrated-test-verdict]}
        $pull {:tasks {:source.id {$in [(:id migrated-test-verdict)]}}}})

  )
