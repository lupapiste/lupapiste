(ns lupapalvelu.application-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.application :refer :all]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]))

(facts "sorting parameter parsing"
  (make-sort {:iSortCol_0 0 :sSortDir_0 "asc"})  => {:infoRequest 1}
  (make-sort {:iSortCol_0 1 :sSortDir_0 "desc"}) => {:address -1}
  (make-sort {:iSortCol_0 2 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 3 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 4 :sSortDir_0 "asc"})  => {:submitted 1}
  (make-sort {:iSortCol_0 5 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 6 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 7 :sSortDir_0 "asc"})  => {:modified 1}
  (make-sort {:iSortCol_0 8 :sSortDir_0 "asc"})  => {:state 1}
  (make-sort {:iSortCol_0 9 :sSortDir_0 "asc"})  => {:authority 1}
  (make-sort {:iSortCol_0 {:injection "attempt"}
              :sSortDir_0 "; drop database;"})   => {}
  (make-sort {})                                 => {}
  (make-sort nil)                                => {})

(fact "update-document"
  (update-application {:application ..application.. :data {:id ..id..}} ..changes..) => truthy
  (provided
    ..application.. =contains=> {:id ..id..}
    (mongo/update :applications {:_id ..id..} ..changes..) => true))

(testable-privates lupapalvelu.application validate-x validate-y)

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

(defn find-by-schema? [docs schema-name]
  (domain/get-document-by-name {:documents docs} schema-name))

(defn has-schema? [schema] (fn [docs] (find-by-schema? docs schema)))

(fact "make-query (LUPA-519) with filter-user checks both authority and auth.id"
  (make-query {} {:filter-kind  "both"
                  :filter-state "all"
                  :filter-user  "123"}) => (contains {"$or" [{"auth.id" "123"} {"authority.id" "123"}]}))

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
       (fact "Jatkolupa requires" (is-link-permit-required {:permitSubtype "muutoslupa"}) => truthy)
       (fact "Aloitusilmoitus requires" (is-link-permit-required {:operations [{:name "aloitusoikeus"}]}) => truthy)
       (fact "Poikkeamis not requires" (is-link-permit-required {:operations [{:name "poikkeamis"}]}) => nil))


(testable-privates lupapalvelu.application add-operation-allowed?)

(facts "Add operation allowed"
  (let [not-allowed-for #{:jatkoaika :aloitusoikeus :suunnittelijan-nimeaminen :tyonjohtajan-nimeaminen}
        error {:ok false :text "error.add-operation-not-allowed"}]
    (doseq [op (keys lupapalvelu.operations/operations)]
      (let [application {:operations [{:name (name op)}] :permitSubtype nil}
            operation-allowed (doc-result (add-operation-allowed? nil application) op)]
        (if (not-allowed-for op)
          (fact "Add operation not allowed" operation-allowed => (doc-check = error))
          (fact "Add operation allowed" operation-allowed => (doc-check nil?)))))
    (fact "Add operation not allowed for :muutoslupa"
          (add-operation-allowed? nil {:operations [{:name "asuinrakennus"}] :permitSubtype :muutoslupa}) => error)))
