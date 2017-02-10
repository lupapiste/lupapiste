(ns lupapalvelu.integrations-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.integrations-api]
            [lupapalvelu.mongo :as mongo]))

(testable-privates lupapalvelu.integrations-api ensure-general-handler-is-set)

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
