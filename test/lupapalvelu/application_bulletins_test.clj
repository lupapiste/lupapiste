(ns lupapalvelu.application-bulletins-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [lupapalvelu.application-bulletins :refer :all]
            [lupapalvelu.mongo :refer [create-id]]
            [lupapalvelu.pate.metadata :as metadata]
            [sade.util :as util]))

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
                                :paivamaarat    {:anto anto
                                                 :lainvoimainen lainvoimainen}
                                :poytakirjat    [{:paatoksentekija handler
                                                  :urlHash "5b7e5772e7d8a1a88e669356"
                                                  :status status
                                                  :paatos verdict-text
                                                  :paatospvm paatospvm
                                                  :pykala section
                                                  :paatoskoodi code}]
                                :lupamaaraykset {:autopaikkojaEnintaan      0
                                                 :autopaikkojaKiinteistolla 0
                                                 :autopaikkojaRakennettu    0
                                                 :maaraykset                [{:sisalto "Muu 1"}
                                                                             {:sisalto "Muu 2"}]
                                                 :vaaditutErityissuunnitelmat []
                                                 :vaaditutKatselmukset [{:tarkastuksenTaiKatselmuksenNimi "Tarkastus"}]
                                                 :vaaditutTyonjohtajat "Supervising supervisor"}}]})

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

(fact "verdict-data-for-bulletin-snapshot"
      (verdict-data-for-bulletin-snapshot test-verdict) => (verdict-data-for-bulletin-snapshot pate-test-verdict))

(defn- ignore-some-data [verdict]
  (-> verdict
      (util/dissoc-in [:paatokset 0 :poytakirjat 0 :paatospvm]) ; No place for this in Pate, replaced by :anto
      (util/dissoc-in [:paatokset 0 :poytakirjat 0 :urlHash])   ; Not available, assume not needed
      (util/dissoc-in [:paatokset 0 :id])                       ; Not available, assume not needed, use verdict id
      (dissoc :metadata)))                                      ; Not needed

(fact "->backing-system-verdict"
  (let [backing-system-verdict (->backing-system-verdict pate-test-verdict)]
    (ignore-some-data backing-system-verdict)
    => (ignore-some-data test-verdict)

    (get-in backing-system-verdict [:paatokset 0 :poytakirjat 0 :paatospvm])
    => (-> pate-test-verdict :data :anto :_value)
    (get-in backing-system-verdict [:paatokset 0 :paivamaarat :anto])
    => (-> pate-test-verdict :data :anto :_value))

  (let [backing-system-verdict (->backing-system-verdict (assoc pate-test-verdict
                                                                :category "ymp"))]
    (get-in backing-system-verdict [:paatokset 0 :poytakirjat 0 :status])
    => nil   ;; Since for Pate legacy YMP verdicts the verdict code is interpreted as free text
    (get-in backing-system-verdict [:paatokset 0 :poytakirjat 0 :paatoskoodi])
    => "2")) ;; Since for Pate legacy YMP verdicts the verdict code is interpreted as free text


(def example-app-with-verdict (assoc example-app :verdicts [test-verdict]))
(def example-app-with-pate-verdict (assoc example-app :pate-verdicts [pate-test-verdict]))
(def example-app-with-both-verdicts (assoc example-app
                                           :verdicts [test-verdict]
                                           :pate-verdicts [pate-test-verdict]))

(facts "further create-bulletin-snapshot tests"
  (let [verdict-snapshot (create-bulletin-snapshot example-app-with-verdict)
        pate-verdict-snapshot (create-bulletin-snapshot example-app-with-pate-verdict)
        both-snapshot (create-bulletin-snapshot example-app-with-both-verdicts)]
    (fact "apart from :verdicts and :id, the two snapshots are the same"
          (dissoc verdict-snapshot :verdicts :id) => (dissoc pate-verdict-snapshot :verdicts :id))
    (fact "the verdicts differ only within the constraints of ->backing-system-verdict"
          (mapv ignore-some-data (:verdicts verdict-snapshot))
          => (mapv ignore-some-data (:verdicts pate-verdict-snapshot)))
    (fact "if both backing system and Pate verdicts are present, Pate takes precedence"
      ;; Feel free to disagree, just spelling out the existing implementation
      (:verdicts both-snapshot) => [(->backing-system-verdict pate-test-verdict) test-verdict]
      (:verdictData both-snapshot) => (verdict-data-for-bulletin-snapshot pate-test-verdict))))

(facts "Verdict accessors"
  (fact "for Pate verdict"
    (foremen {:data {:foremen ["vv-tj" "vastaava-tj" "iv-tj"]}})
      => "Vesi- ja viem\u00e4rity\u00f6njohtaja, Vastaava ty\u00f6njohtaja, Ilmanvaihtoty\u00f6njohtaja"
    (conditions {:data {:conditions {:5beec543e9a0396ee0e14f1a {:condition "Ehto"}}}})
      => [{:sisalto "Ehto"}]
    (reviews {:data       {:reviews ["5beec544e9a0396ee0e14f1c"]}
              :references {:reviews [{:fi "Katselmus"
                                      :sv "Katslemus"
                                      :en "Katselmus"
                                      :type "muu-katselmus"
                                      :id "5beec544e9a0396ee0e14f1c"}]}})
      => [{:tarkastuksenTaiKatselmuksenNimi "Katselmus"}])
  (fact "for legacy verdict"
    (foremen {:category "r"
              :legacy? true
              :data {:foremen {:5beeca9fe9a0396ee0e14f36 {:role "Ty\u00f6njohtaja"}
                               :5beecaa5e9a0396ee0e14f37 {:role "Vastaava ty\u00f6njohtaja"}
                               :5beecaa8e9a0396ee0e14f38 {:role "Vesi- ja viem\u00e4rity\u00f6njohtaja"}}}})
      => "Ty\u00f6njohtaja, Vastaava ty\u00f6njohtaja, Vesi- ja viem\u00e4rity\u00f6njohtaja"))
