(ns lupapalvelu.integrations-api-test
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$set]]))

(testable-privates lupapalvelu.integrations-api
                   ensure-general-handler-is-set building-extinction-db-updates
                   huoneistot-updates)

(facts ensure-general-handler-is-set
  (fact "empty handlers"
    (ensure-general-handler-is-set []
                                   {:id ..user-id.. :firstName ..firstName.. :lastName ..lastName..}
                                   {:handler-roles [{:id ..role-id.. :name ..handler.. :general true}]})
    => [{:id ..handler-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName.. :roleId ..role-id..}]
    (provided (mongo/create-id) => ..handler-id..))

  (fact "nil handlers"
    (ensure-general-handler-is-set nil
                                   {:id ..user-id.. :firstName ..firstName.. :lastName ..lastName..}
                                   {:handler-roles [{:id ..role-id.. :name ..handler.. :general true}]})
    => [{:id ..handler-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName.. :roleId ..role-id..}]
    (provided (mongo/create-id) => ..handler-id..))

  (fact "existing general handler"
    (ensure-general-handler-is-set [{:id ..handler-id.. :roleId ..role-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName..}]
                                   {:id ..other-user-id.. :firstName ..other-firstName.. :lastName ..other-lastName..}
                                   {:handler-roles [{:id ..role-id.. :name ..handler.. :general true}]})
    => [{:id ..handler-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName.. :roleId ..role-id..}])

  (fact "existing non-general handler"
    (ensure-general-handler-is-set [{:id ..other-handler-id.. :roleId ..other-role-id.. :userId ..other-user-id.. :firstName ..other-firstName.. :lastName ..other-lastName..}]
                                   {:id ..user-id.. :firstName ..firstName.. :lastName ..lastName..}
                                   {:handler-roles [{:id ..other-role-id.. :name ..other-handler..}
                                                    {:id ..role-id.. :name ..handler.. :general true}]})
    => [{:id ..other-handler-id.. :roleId ..other-role-id.. :userId ..other-user-id.. :firstName ..other-firstName.. :lastName ..other-lastName..}
        {:id ..handler-id.. :roleId ..role-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName..}]
    (provided (mongo/create-id) => ..handler-id..))

  (fact "empty handlers - no general handler in organization"
    (ensure-general-handler-is-set []
                                   {:id ..user-id.. :firstName ..firstName.. :lastName ..lastName..}
                                   {:handler-roles [{:id "role-id" :name ..handler..}]})
    => []))


(facts "building-extinction-db-updates"
  (let [command {:created 123
                 :application {:primaryOperation {:id "primary-op"}
                               :secondaryOperations [{:id "secondary-op-1"}
                                                     {:id "secondary-op-2"}]}}]
    (fact "invalid operation id"
      (building-extinction-db-updates command "invalid-id" 1575158400000)
      => (partial expected-failure? "error.invalid-request"))

    (facts "primary operation"
      (fact "$set"
        (building-extinction-db-updates command "primary-op" 1575158400000) ;; 1.12.2019
        => {"$set" {:modified 123
                    :primaryOperation.extinct 1575158400000}})

      (fact "$unset"
        (building-extinction-db-updates command "primary-op" nil) ;; 1.12.2019
        => {"$set" {:modified 123}
            "$unset" {:primaryOperation.extinct true}}))

    (fact "secondary operation"
      (fact "$set"
        (building-extinction-db-updates command "secondary-op-1" 1575158400000) ;; 1.12.2019
        => {"$set" {:modified 123
                    "secondaryOperations.0.extinct" 1575158400000}})

      (fact "$unset"
        (building-extinction-db-updates command "secondary-op-2" nil) ;; 1.12.2019
        => {"$set" {:modified 123}
            "$unset" {"secondaryOperations.1.extinct" true}}))
    ))

(facts huoneistot-updates
  (fact "No apartments"
    (huoneistot-updates {} {} {}) => nil)
  (let [command                {:application {} :created 12345}
        doc-info               {:schema-body (:body (schemas/get-schema {:name "rakennuksen-laajentaminen"}))}
        doc-info-no-apartments {:schema-body
                                (:body (schemas/get-schema
                                         {:name "kaupunkikuvatoimenpide-ei-tunnusta"}))}]
    (fact "Empty apartments"
      (huoneistot-updates command doc-info
                          {:huoneistot {}})
      => {:mongo-updates {$set {:documents.$.data.huoneistot {}}}})
    (fact "One empty apartments"
      (huoneistot-updates command doc-info
                          {:huoneistot {:1 {}}})
      => {:mongo-updates {$set {:documents.$.data.huoneistot {:1 {}}}}})
    (fact "One apartment"
      (huoneistot-updates command doc-info
                          {:huoneistot {:0 {:WCKytkin     true
                                            :huoneistoala "10"}}})
      => {:mongo-updates {$set {:documents.$.data.huoneistot
                                {:0 {:WCKytkin     {:modified    12345
                                                    :source      "krysp"
                                                    :sourceValue true
                                                    :value       true}
                                     :huoneistoala {:modified    12345
                                                    :source      "krysp"
                                                    :sourceValue "10"
                                                    :value       "10"}}}}}})
    (fact "Two apartments, non-schema fields are ignored"
      (huoneistot-updates command doc-info
                          {:huoneistot {:0 {:WCKytkin     true
                                            :huoneistoala "10"
                                            :unsupported  "field"}
                                        :2 {:jakokirjain "j"
                                            :porras      "C"
                                            :foo         "bar"}}})
      => {:mongo-updates {$set {:documents.$.data.huoneistot
                                {:0 {:WCKytkin     {:modified    12345
                                                    :source      "krysp"
                                                    :sourceValue true
                                                    :value       true}
                                     :huoneistoala {:modified    12345
                                                    :source      "krysp"
                                                    :sourceValue "10"
                                                    :value       "10"}}
                                 :2 {:jakokirjain {:modified    12345
                                                   :source      "krysp"
                                                   :sourceValue "j"
                                                   :value       "j"}
                                     :porras      {:modified    12345
                                                   :source      "krysp"
                                                   :sourceValue "C"
                                                   :value       "C"}}}}}})
    (fact "Fail! on validation error, but only if muutostapa is defined and not poisto.
Note, that the building data from the backing system does not have muutostapa so in
practise we can read garbage. But, this should not matter, since untouched data is not
sent back. And if it is edited, it should also be corrected."
      (huoneistot-updates command doc-info
                          {:huoneistot {:0 {:WCKytkin     "hello"
                                            :huoneistoala "10"}}})
      => {:mongo-updates {"$set" {:documents.$.data.huoneistot {:0 {:WCKytkin     {:modified    12345
                                                                                   :source      "krysp"
                                                                                   :sourceValue "hello"
                                                                                   :value       "hello"}
                                                                    :huoneistoala {:modified    12345
                                                                                   :source      "krysp"
                                                                                   :sourceValue "10"
                                                                                   :value       "10"}}}}}}
      (huoneistot-updates command doc-info
                          {:huoneistot {:0 {:muutostapa   "lisäys"
                                            :WCKytkin     "hello"
                                            :huoneistoala "10"}}})
      => (throws Exception (fn [e]
                             (= (select-keys (ex-data e) [:ok :results])
                                {:ok      false
                                 :results '((({:document nil
                                               :element  {:i18nkey "huoneistot.WCKytkin"
                                                          :label   false
                                                          :locKey  "huoneistot.WCKytkin"
                                                          :name    "WCKytkin"
                                                          :type    :checkbox}
                                               :path     [:huoneistot :0 :WCKytkin]
                                               :result   [:err "illegal-value:not-a-boolean"]})))}))))

    (fact "No apartments in schema"
      (huoneistot-updates command doc-info-no-apartments
                          {:huoneistot {:0 {:WCKytkin     true
                                            :huoneistoala "10"}}})
      => nil)))
