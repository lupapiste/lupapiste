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

(def hakija-doc-for-tags
  {:id "5b868cb8e7d8a158be266087"
   :schema-info {:name "hakija-r"
                 :version 1
                 :type "party"
                 :subtype "hakija"}
   :created 1535544504326
   :data {:_selected {:value "henkilo"
                      :modified 1535544504531}
          :henkilo {:userId {:value "777777777777777777000023"
                             :modified 1535544504531}
                    :henkilotiedot {:etunimi {:value "Sonja"
                                              :modified 1535544504531}
                                    :sukunimi {:value "Sibbo"
                                               :modified 1535544504531}
                                    :hetu {:value ""
                                           :modified 1535544504531}
                                    :turvakieltoKytkin {:value false
                                                        :modified 1535544504531}}
                    :osoite {:katu {:value "Katuosoite 1 a 1"
                                    :modified 1535544504531}
                             :postinumero {:value "33456"
                                           :modified 1535544504531}
                             :postitoimipaikannimi {:value "Sipoo"
                                                    :modified 1535544504531}
                             :maa {:value "FIN"}}
                    :yhteystiedot {:puhelin {:value "03121991"
                                             :modified 1535544504531}
                                   :email {:value "sonja.sibbo@sipoo.fi"
                                           :modified 1535544504531}}
                    :kytkimet {:suoramarkkinointilupa {:value false
                                                       :modified 1535544504531}
                               :vainsahkoinenAsiointiKytkin {:value true}}}
          :yritys {:companyId {:value nil}
                   :yritysnimi {:value ""}
                   :liikeJaYhteisoTunnus {:value ""}
                   :osoite {:katu {:value ""}
                            :postinumero {:value ""}
                            :postitoimipaikannimi {:value ""}
                            :maa {:value "FIN"}}
                   :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}
                                                   :sukunimi {:value ""}
                                                   :turvakieltoKytkin {:value false}}
                                   :yhteystiedot {:puhelin {:value ""}
                                                  :email {:value ""}}
                                   :kytkimet {:suoramarkkinointilupa {:value false}
                                              :vainsahkoinenAsiointiKytkin {:value true}}}}}
   :meta {:_indicator_reset {:timestamp 1535544525806}}})

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
                            :modified timestamp
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
(def test-application (-> {:id "app-id"
                           :verdicts [test-verdict]
                           :permitType "R"
                           :organization "753-R"
                           :primaryOperation {:id "5b868cb8e7d8a158be266085"
                                              :name "kerrostalo-rivitalo"
                                              :description nil
                                              :created 1535544504326}
                           :propertyId "75341600550007"
                           :tasks (tasks-for-verdict (:id test-verdict))
                           :attachments [{:latestVersion {:fileId "attachment-id"}
                                          :target {:id verdict-id

                                                   :type "verdict"}
                                          :id "attachment1"
                                          :type {:type-id    "paatos"
                                                 :type-group "paatoksenteko"}}]
                           :documents [hakija-doc-for-tags]}
                          (select-keys migration-projection)))

(def app-one-verdict-no-tasks (dissoc test-application :tasks))
(def app-one-verdict-with-tasks test-application)

(def verdict-id2 "verdict-id2")
(def test-verdict2 (assoc test-verdict :id verdict-id2))
(def migrated-test-verdict2 (-> migrated-test-verdict
                                (assoc :id verdict-id2)
                                ;; No attachment in test application referring test verdict 2
                                (update :data dissoc :attachments)))

(def app-two-verdicts-with-tasks (assoc test-application
                                        :verdicts [test-verdict test-verdict2]
                                        :tasks (concat (tasks-for-verdict verdict-id)
                                                       (tasks-for-verdict verdict-id2))))
(def app-with-draft-and-published (assoc test-application
                                         :verdicts [(assoc test-verdict :draft false)
                                                    test-verdict2]
                                         :tasks (concat (tasks-for-verdict verdict-id)
                                                        (tasks-for-verdict verdict-id2))))

(def contains-published-and-archive-data?
  (contains {:published (contains {:published anto
                                   :attachment-id "attachment1"
                                   :tags (contains "Sonja Sibbo")})
             :archive {:verdict-giver handler
                       :lainvoimainen lainvoimainen}}))

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

  (fact "published verdict has published and archive data"
    (->pate-legacy-verdict (update test-application
                                   :verdicts
                                   #(map (fn [v] (assoc v :draft false)) %))
                           (assoc test-verdict :draft false)
                           timestamp)
    => contains-published-and-archive-data?)

  (fact "verdict can be migrated even when the application does not have applicant document"
        (->pate-legacy-verdict test-application
                               (assoc test-verdict :draft false)
                               timestamp)
        => (contains {:published (contains {:tags string?})}))

  (against-background
   (lupapalvelu.organization/get-organization-name anything anything) => "Sipoon rakennusvalvonta"))

(facts "migration-updates"
  (fact "one draft verdict, no tasks"
    (migration-updates app-one-verdict-no-tasks timestamp)
    => {$unset {:verdicts ""}
        $set {:pate-verdicts [migrated-test-verdict-no-tasks]
              :pre-pate-verdicts (:verdicts app-one-verdict-no-tasks)}
        $pull {:tasks {:source.id {$in [(:id migrated-test-verdict-no-tasks)]}}}})

  (fact "one draft verdict with tasks"
    (migration-updates app-one-verdict-with-tasks timestamp)
    => {$unset {:verdicts ""}
        $set {:pate-verdicts [migrated-test-verdict]
              :pre-pate-verdicts (:verdicts app-one-verdict-with-tasks)}
        $pull {:tasks {:source.id {$in [(:id migrated-test-verdict)]}}}})

  (fact "two draft verdicts with tasks"
    (migration-updates app-two-verdicts-with-tasks timestamp)
    => {$unset {:verdicts ""}
        $set {:pate-verdicts [migrated-test-verdict migrated-test-verdict2]
              :pre-pate-verdicts (:verdicts app-two-verdicts-with-tasks)}
        $pull {:tasks {:source.id {$in [verdict-id verdict-id2]}}}})

  (fact "two verdicts with tasks, one draft, one published"
    (migration-updates app-with-draft-and-published timestamp)
    => (contains
        {$unset {:verdicts ""}
         $set (contains {:pate-verdicts (just [contains-published-and-archive-data?
                                               migrated-test-verdict2])
                         :pre-pate-verdicts (:verdicts app-with-draft-and-published)})
         ;; Only tasks related to the unpublished verdict are pulled
         $pull {:tasks {:source.id {$in [verdict-id2]}}}}))

  (against-background
   (lupapalvelu.organization/get-organization-name anything anything) => "Sipoon rakennusvalvonta"))
