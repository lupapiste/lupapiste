(ns lupapalvelu.reports.users-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.reports.users :refer :all]))

(testable-privates lupapalvelu.reports.users roles authorities-for-organization)

(facts "Roles are resolved correctly"
  (roles {:091-R ["reader"]} :091-R :fi) => "Lukuoikeus"
  (roles {:091-R ["reader" "authority"]
          :092-R ["archivist" "tos-editor" "approver"]} :091-R :fi) => "Lukuoikeus, Muutosoikeus"
  (roles {:091-R ["reader" "authority"]
          :092-R ["archivist" "tos-editor" "approver"]} :753-R :fi) => "")

(facts "Authorities are found for organization"
  (authorities-for-organization :091-R :fi)
    => [{:email "test.auth@hel.fi" :name "Testi Authority" :roles "Lukuoikeus"}
        {:email "test2.auth@hel.fi" :name "Testi2 Authority2" :roles "Lukuoikeus, Muutosoikeus"}]
  (provided
    (lupapalvelu.user/find-users anything anything) => [{:role "authority"
                                                        :email "test.auth@hel.fi"
                                                        :username "test.auth@hel.fi"
                                                        :firstName "Testi"
                                                        :orgAuthz {:091-R ["reader"]}
                                                        :id "123"
                                                        :lastName "Authority"
                                                        :enabled true}
                                                        {:role "authority"
                                                         :email "test2.auth@hel.fi"
                                                         :username "test2.auth@hel.fi"
                                                         :firstName "Testi2"
                                                         :orgAuthz {:091-R ["reader" "authority"]}
                                                         :id "456"
                                                         :lastName "Authority2"
                                                         :enabled true}]))



