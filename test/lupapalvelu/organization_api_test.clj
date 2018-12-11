(ns lupapalvelu.organization-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.organization-api :refer :all]
            [lupapalvelu.user :as usr]))

(testable-privates lupapalvelu.organization-api validate-handler-role-in-organization validate-handler-role-not-general)

(facts validate-handler-role-in-organization
  (fact "role id found"
    (validate-handler-role-in-organization {:data {:roleId ..role-id..}
                                            :user ..user..
                                            :user-organizations [{:id ..org-id.. :handler-roles [{:id ..role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil
    (provided (usr/authority-admins-organization-id ..user..) => ..org-id..))

  (fact "role id not found in organization"
    (:ok (validate-handler-role-in-organization {:data {:roleId ..role-id..}
                                                 :user ..user..
                                                 :user-organizations [{:id ..org-id.. :handler-roles [{:id ..another-role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]})) => false
    (provided (usr/authority-admins-organization-id ..user..) => ..org-id..))

  (fact "no mathing authAdmin organization"
    (validate-handler-role-in-organization {:data {:roleId nil}
                                            :user ..user..
                                            :user-organizations [{:id ..org-id.. :handler-roles [{:id ..role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil
    (provided (usr/authority-admins-organization-id ..user..) => nil))

  (fact "no role id in data"
    (validate-handler-role-in-organization {:data {:roleId nil}
                                            :user ..user..
                                            :user-organizations [{:id ..org-id.. :handler-roles [{:id ..role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil
    (provided (usr/authority-admins-organization-id ..user..) => ..org-id..)))

(facts validate-handler-role-not-general
  (fact "non general role id found"
    (validate-handler-role-not-general {:data {:roleId ..role-id..}
                                        :user ..user..
                                        :user-organizations [{:id ..org-id.. :handler-roles [{:id ..role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil
    (provided (usr/authority-admins-organization-id ..user..) => ..org-id..))

  (fact "general role id found"
    (:ok (validate-handler-role-not-general {:data {:roleId ..role-id..}
                                             :user ..user..
                                             :user-organizations [{:id ..org-id.. :handler-roles [{:id ..role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"} :general true}]}]})) => false
    (provided (usr/authority-admins-organization-id ..user..) => ..org-id..))

  (fact "role id not found in organization"
    (validate-handler-role-not-general {:data {:roleId ..role-id..}
                                                  :user ..user..
                                                  :user-organizations [{:id ..org-id.. :handler-roles [{:id ..another-role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil
    (provided (usr/authority-admins-organization-id ..user..) => ..org-id..))

  (fact "no matching authAdmin organization"
    (validate-handler-role-not-general {:data {:roleId nil}
                                            :user ..user..
                                            :user-organizations [{:id ..org-id.. :handler-roles [{:id ..role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil
    (provided (usr/authority-admins-organization-id ..user..) => nil))

  (fact "no role id in data"
    (validate-handler-role-not-general {:data {:roleId nil}
                                            :user ..user..
                                            :user-organizations [{:id ..org-id.. :handler-roles [{:id ..role-id.. :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil
    (provided (usr/authority-admins-organization-id ..user..) => ..org-id..)))
