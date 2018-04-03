(ns lupapalvelu.authorization-api-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.authorization-api :refer :all]))

(facts no-company-users-authorized-when-company-denies-invitations
  (fact "company user of company that denies invitation is authorized to application - fail removing company"
    (no-company-users-in-auths-when-company-denies-invitations {:auth-entry  {:id "com1" :type "company"}
                                                                :application {:auth [{:id "com1"}
                                                                                     {:id "usr1"
                                                                                      :firstName "Paa"
                                                                                      :lastName "Kayttaja"}]}})
    => {:ok false
        :text "error.company-users-have-to-be-removed-before-company"
        :users [{:firstName "Paa" :lastName "Kayttaja"}]}

    (provided (lupapalvelu.company/find-company-by-id "com1") => {:invitationDenied true}
              (lupapalvelu.mongo/select :users {:company.id "com1"} [:_id]) => [{:id "usr1"}]))

  (fact "no company users authorized"
    (no-company-users-in-auths-when-company-denies-invitations {:auth-entry  {:id "com1" :type "company"}
                                                                :application {:auth [{:id "com1"}
                                                                                     {:id "usr1"
                                                                                      :firstName "Paa"
                                                                                      :lastName "Kayttaja"}]}})
    => nil

    (provided (lupapalvelu.company/find-company-by-id "com1") => {:invitationDenied true}
              (lupapalvelu.mongo/select :users {:company.id "com1"} [:_id]) => [{:id "usr2"}]))

  (fact "non-company entry"
    (no-company-users-in-auths-when-company-denies-invitations {:auth-entry  {:id "usr1"}
                                                                :application {:auth [{:id "com1"}
                                                                                     {:id "usr1"
                                                                                      :firstName "Paa"
                                                                                      :lastName "Kayttaja"}]}})
    => nil)

  (fact "company do not deny invitations"
    (no-company-users-in-auths-when-company-denies-invitations {:auth-entry  {:id "com1" :type "company"}
                                                                :application {:auth [{:id "com1"}
                                                                                     {:id "usr1"
                                                                                      :firstName "Paa"
                                                                                      :lastName "Kayttaja"}]}})
    => nil

    (provided (lupapalvelu.company/find-company-by-id "com1") => {:invitationDenied false})))
