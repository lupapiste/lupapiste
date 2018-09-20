(ns lupapalvelu.migration.pate-verdict-migration-test
  (:require [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.migration.pate-verdict-migration :refer :all]
            [lupapalvelu.mongo :refer [create-id]]
            [lupapalvelu.pate.metadata :as metadata]))

(def timestamp 1503003635780)

(defn wrap [x]
  (metadata/wrap "Verdict draft Pate migration" timestamp x))

(defn tasks-for-verdict [verdict-id]
  [{:id "katselmus-id"
    :schema-info {:name "task-katselmus"}
    :taskname "Tarkastus"
    :data {:katselmuksenLaji {:value "muu tarkastus"}}
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
(def status 2)
(def code "hyväksytty")
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
                                               :status status
                                               :paatos verdict-text
                                               :paatospvm paatospvm
                                               :pykala section
                                               :paatoskoodi code}]}]})

(def test-verdict-with-signatures (assoc test-verdict :signatures signatures))

(def migrated-signatures
  [{:date    signed1
    :user-id signer-id1
    :name "First Name"}
   {:date    signed2
    :user-id signer-id2
    :name "Second Name"}])
(def migrated-test-verdict {:id verdict-id
                            :modified timestamp
                            :category "r"
                            :state (wrap "draft")
                            :data {:handler (wrap handler)
                                   :kuntalupatunnus (wrap kuntalupatunnus)
                                   :verdict-section (wrap section)
                                   :verdict-code    (wrap (str status))
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

(def migrated-test-verdict-with-signatures (assoc migrated-test-verdict :signatures migrated-signatures))

(def migrated-test-verdict-no-tasks
  (-> migrated-test-verdict
      (update :data dissoc :reviews :foremen :conditions)))

(def attachment-id (create-id))
(def attachment-id2 (create-id))
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
                                          :id attachment-id
                                          :type {:type-id    "paatos"
                                                 :type-group "paatoksenteko"}}
                                         {:latestVersion {:fileId "attachment-id"}
                                          :source {:id verdict-id

                                                   :type "verdicts"}
                                          :id attachment-id2
                                          :type {:type-id    "paatosote"
                                                 :type-group "muut"}}]
                           :documents [hakija-doc-for-tags]}))

(def app-one-verdict-no-tasks (dissoc test-application :tasks))
(def app-one-verdict-with-tasks test-application)

(def verdict-id2 (create-id))
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

(def backing-system-verdict (-> test-verdict
                                (dissoc :draft)
                                (assoc :id (create-id))))
(def backing-system-verdict2 (-> test-verdict
                                 (dissoc :draft)
                                 (assoc :id (create-id))))
(def app-with-backing-system-verdicts (assoc test-application
                                             :verdicts [backing-system-verdict
                                                        test-verdict
                                                        backing-system-verdict2]))

(def contains-published-and-archive-data?
  (contains {:published (contains {:published anto
                                   :attachment-id attachment-id
                                   :tags (contains "Sonja Sibbo")})
             :archive {:verdict-giver handler
                       :anto anto
                       :lainvoimainen lainvoimainen}
             :state (wrap "published")}))

(facts "->pate-legacy-verdict"
  (fact "base case"
    (->pate-legacy-verdict test-application
                           test-verdict
                           timestamp)
    =>
    migrated-test-verdict)

  (fact "old agreements are given category migration-contract"
    (->pate-legacy-verdict test-application
                           (assoc test-verdict :sopimus true)
                           timestamp)
    => (contains {:category "migration-contract"
                  :data (contains {:contract-text (wrap verdict-text)})}))

  (fact "verdicts with signatures given category migration-contract"
        (->pate-legacy-verdict test-application
                               test-verdict-with-signatures
                               timestamp)
        => (contains {:category "migration-contract"
                      :signatures migrated-signatures}))

  (fact "if verdict cannot be validated with the default category, a permissive migration-verdict category is used"
    (->pate-legacy-verdict (assoc test-application :permitType "YA")
                           test-verdict
                           timestamp)
    => (contains {:category "migration-verdict"
                  :data (contains {:verdict-text (wrap verdict-text)})}))

  (fact "YMP verdicts have textual verdict code instead of numeric"
        (->pate-legacy-verdict (assoc app-one-verdict-no-tasks :permitType "YI")
                               test-verdict
                               timestamp)
        => (contains {:category "ymp"
                      :data (contains {:verdict-code (wrap "Hyväksytty")})}))

  (fact "YMP verdicts that need to be migrated as migration-verdicts have numeric verdict-codes"
        (->pate-legacy-verdict (assoc test-application :permitType "YI")
                               test-verdict
                               timestamp)
        => (contains {:category "migration-verdict"
                      :data (contains {:verdict-code (wrap (str status))})}))

  (fact "if contract cannot be validated with the default category, a permissive migration-contract category is used"
        (->pate-legacy-verdict (assoc test-application :permitType "YA")
                               (assoc test-verdict :sopimus true)
                               timestamp)
        => (contains {:category "migration-contract"
                      :data (contains {:contract-text (wrap verdict-text)})})

        (->pate-legacy-verdict (assoc test-application
                                      :permitType "YA"
                                      :permitSubtype "sijoitussopimus")
                               test-verdict
                               timestamp)
        => (contains {:category "migration-contract"
                      :data (contains {:contract-text (wrap verdict-text)})})

        (->pate-legacy-verdict test-application
                               (assoc test-verdict :sopimus true)
                               timestamp)
        => (contains {:category "migration-contract"
                      :data (contains {:contract-text (wrap verdict-text)})}))

  (fact "published migration contracts have 'Päätös / Sopimus' in tags"
        (->pate-legacy-verdict test-application
                               (assoc test-verdict :draft false :sopimus true)
                               timestamp)
        => (contains {:category "migration-contract"
                      :published (contains {:tags (contains "Päätös / Sopimus")})}))

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

  (fact "for published verdicts, timestamp 0 is not cleaned up"
        (->pate-legacy-verdict (update test-application
                                       :verdicts
                                       #(map (fn [v] (assoc v :draft false)) %))
                               (-> test-verdict
                                   (assoc :draft false)
                                   (assoc-in [:paatokset 0 :paivamaarat :anto] 0))
                               timestamp)
        => (contains {:published (contains {:published 0
                                            :attachment-id attachment-id
                                            :tags (contains "Sonja Sibbo")})
                      :archive {:verdict-giver handler
                                :anto 0
                                :lainvoimainen lainvoimainen}
                      :state (wrap "published")}))

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
    => {$set {:pate-verdicts [migrated-test-verdict-no-tasks]
              :pre-pate-verdicts (:verdicts app-one-verdict-no-tasks)}
        $pull {:tasks {:source.id {$in [(:id migrated-test-verdict-no-tasks)]}}
               :verdicts {:id {$in [(:id migrated-test-verdict-no-tasks)]}}}}
    (migration-updates (update app-one-verdict-no-tasks
                               :verdicts
                               (fn [verdicts]
                                 (map #(assoc % :draft false)
                                      verdicts)))
                       timestamp)
    =not=> (contains {$pull (contains {:tasks anything})}))

  (fact "one draft verdict with tasks"
    (migration-updates app-one-verdict-with-tasks timestamp)
    => {$set {:pate-verdicts [migrated-test-verdict]
              :pre-pate-verdicts (:verdicts app-one-verdict-with-tasks)}
        $pull {:tasks {:source.id {$in [(:id migrated-test-verdict)]}}
               :verdicts {:id {$in [(:id migrated-test-verdict)]}}}})
  (fact "two draft verdicts with tasks"
    (migration-updates app-two-verdicts-with-tasks timestamp)
    => {$set {:pate-verdicts [migrated-test-verdict migrated-test-verdict2]
              :pre-pate-verdicts (:verdicts app-two-verdicts-with-tasks)}
        $pull {:tasks {:source.id {$in [verdict-id verdict-id2]}}
               :verdicts {:id {$in [verdict-id verdict-id2]}}}})

  (fact "two verdicts with tasks, one draft, one published"
    (migration-updates app-with-draft-and-published timestamp)
    => (contains
        {$set (contains {:pate-verdicts (just [contains-published-and-archive-data?
                                               migrated-test-verdict2])
                         :pre-pate-verdicts (:verdicts app-with-draft-and-published)})
         ;; Only tasks related to the unpublished verdict are pulled
         $pull {:tasks {:source.id {$in [verdict-id2]}}
                :verdicts {:id {$in [verdict-id verdict-id2]}}}}))

  (fact "verdicts from backing system are left intact"
    (migration-updates app-with-backing-system-verdicts timestamp)
    => (contains
        ;; Backing system verdicts are not migrated to pate-verdicts ...
        {$set (contains {:pate-verdicts [migrated-test-verdict]
                         :pre-pate-verdicts (:verdicts app-with-backing-system-verdicts)})
         ;; ... nor are they pulled from verdicts
         $pull {:tasks {:source.id {$in [verdict-id]}}
                :verdicts {:id {$in [verdict-id]}}}}))

  (against-background
   (lupapalvelu.organization/get-organization-name anything anything) => "Sipoon rakennusvalvonta"))
