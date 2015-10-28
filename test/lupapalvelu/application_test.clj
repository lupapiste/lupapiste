(ns lupapalvelu.application-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$set $push]]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.application :refer :all]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]))


(fact "update-document"
  (update-application {:application ..application.. :data {:id ..id..}} ..changes..) => nil
  (provided
    ..application.. =contains=> {:id ..id..}
    (mongo/update-by-query :applications {:_id ..id..} ..changes..) => 1))

(testable-privates lupapalvelu.application-api validate-x validate-y add-operation-allowed?)
(testable-privates lupapalvelu.application is-link-permit-required)


(facts "coordinate validation"
  (validate-x {:data {:x nil}}) => nil
  (validate-y {:data {:y nil}}) => nil
  (validate-x {:data {:x ""}}) => {:ok false :text "error.illegal-coordinates"}
  (validate-x {:data {:x "0"}}) => {:ok false :text "error.illegal-coordinates"}
  (validate-x {:data {:x "1000"}}) => {:ok false :text "error.illegal-coordinates"}
  (validate-x {:data {:x "10001"}}) => nil
  (validate-x {:data {:x "799999"}}) => nil
  (validate-x {:data {:x "800000"}}) => {:ok false :text "error.illegal-coordinates"}
  (validate-y {:data {:y ""}}) => {:ok false :text "error.illegal-coordinates"}
  (validate-y {:data {:y "0"}}) => {:ok false :text "error.illegal-coordinates"}
  (validate-y {:data {:y "6609999"}}) => {:ok false :text "error.illegal-coordinates"}
  (validate-y {:data {:y "6610000"}}) => nil
  (validate-y {:data {:y "7780000"}}) => {:ok false :text "error.illegal-coordinates"}
  (validate-y {:data {:y "7779999"}}) => nil)

(facts "mark-indicators-seen-updates"
  (let [timestamp 123
        expected-seen-bys {"_comments-seen-by.pena" timestamp, "_statements-seen-by.pena" timestamp, "_verdicts-seen-by.pena" timestamp}
        expected-attachment (assoc expected-seen-bys :_attachment_indicator_reset timestamp)
        expected-docs (assoc expected-attachment "documents.0.meta._indicator_reset.timestamp" timestamp)]
    (mark-indicators-seen-updates {} {:id "pena"} timestamp) => expected-seen-bys
    (mark-indicators-seen-updates {:documents []} {:id "pena", :role "authority"} timestamp) => expected-attachment
    (mark-indicators-seen-updates {:documents [{}]} {:id "pena", :role "authority"} timestamp) => expected-docs))

(defn find-by-schema? [docs schema-name]
  (domain/get-document-by-name {:documents docs} schema-name))

(defn has-schema? [schema] (fn [docs] (find-by-schema? docs schema)))

(facts filter-repeating-party-docs
  (filter-repeating-party-docs 1 ["a" "b" "c"]) => (just "a")
  (provided (schemas/get-schemas 1) => {"a" {:info {:type :party
                                                    :repeating true}}
                                        "b" {:info {:type :party
                                                    :repeating false}}
                                        "c" {:info {:type :foo
                                                    :repeating true}}
                                        "d" {:info {:type :party
                                                    :repeating true}}}))

(facts "is-link-permit-required works correctly"
  (fact "Muutoslupa requires" (is-link-permit-required {:permitSubtype "muutoslupa"}) => truthy)
  (fact "Aloitusilmoitus requires" (is-link-permit-required {:primaryOperation {:name "aloitusoikeus"}}) => truthy)
  (fact "Poikkeamis not requires" (is-link-permit-required {:primaryOperation {:name "poikkeamis"}}) => falsey)
  (fact "Poikkeamis not requires" (is-link-permit-required {:secondaryOperations [{:name "poikkeamis"}]}) => falsey)
  (fact "ya-jatkoaika requires" (is-link-permit-required {:primaryOperation {:name "ya-jatkoaika"}}) => truthy)
  (fact "ya-jatkoaika requires" (is-link-permit-required {:secondaryOperations [{:name "ya-jatkoaika"}]}) => truthy))

(facts "Add operation allowed"
       (let [not-allowed-for #{:raktyo-aloit-loppuunsaat :jatkoaika :aloitusoikeus :suunnittelijan-nimeaminen :tyonjohtajan-nimeaminen :tyonjohtajan-nimeaminen-v2 :aiemmalla-luvalla-hakeminen :rajankaynti}
        error {:ok false :text "error.add-operation-not-allowed"}]

    (doseq [operation lupapalvelu.operations/operations]
      (let [[op {permit-type :permit-type}] operation
            application {:primaryOperation {:name (name op)} :permitSubtype nil}
            operation-allowed (add-operation-allowed? nil application)]
        (fact {:midje/description (name op)}
              (if (or (not (contains? #{"R" "KT"} permit-type)) (not-allowed-for op))
            (fact "Add operation not allowed" operation-allowed => error)
            (fact "Add operation allowed" operation-allowed => nil?)))))

    (fact "Add operation not allowed for :muutoslupa"
      (add-operation-allowed? nil {:primaryOperation {:name "kerrostalo-rivitalo"} :permitSubtype :muutoslupa}) => error)))

(fact "validate-has-subtypes"
  (validate-has-subtypes nil {:permitType "P"}) => nil
  (validate-has-subtypes nil {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}) => nil
  (validate-has-subtypes nil {:permitType "R"}) => {:ok false :text "error.permit-has-no-subtypes"}
  (validate-has-subtypes nil nil) => {:ok false :text "error.permit-has-no-subtypes"})

(fact "State transitions"
  (let [pena {:username "pena", :firstName "Pena" :lastName "Panaani"}]
    (state-transition-update :open 1 pena) => {$set {:state :open, :opened 1}, $push {:history {:state :open, :ts 1, :user pena}}}
    (state-transition-update :submitted 2 pena) => {$set {:state :submitted, :submitted 2}, $push {:history {:state :submitted, :ts 2, :user pena}}}
    (state-transition-update :verdictGiven 3 pena) => {$set {:state :verdictGiven}, $push {:history {:state :verdictGiven, :ts 3, :user pena}}}))
