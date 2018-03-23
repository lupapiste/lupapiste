(ns lupapalvelu.authorization-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.authorization-api :refer :all]
            [lupapalvelu.authorization :as auth]))

(facts "generate-remove-invalid-user-from-docs-updates"
  (generate-remove-invalid-user-from-docs-updates nil) => empty?
  (generate-remove-invalid-user-from-docs-updates {:auth nil, :documents nil}) => empty?

  (generate-remove-invalid-user-from-docs-updates {:auth nil
                                                   :documents [{:schema-info {:name "hakija-r" :version 1}
                                                                :data {:henkilo {:userId {:value "123"}}}}
                                                               {:schema-info {:name "hakija" :version 1}
                                                                :data {:henkilo {:userId {:value "345"}}}}]})
  => {"documents.0.data.henkilo.userId" ""
      "documents.1.data.henkilo.userId" ""}

  (generate-remove-invalid-user-from-docs-updates {:auth [{:id "123"}]
                                                   :documents [{:schema-info {:name "hakija-r" :version 1}
                                                                :data {:henkilo {:userId {:value "123"}}}}]})
  => empty?

  (generate-remove-invalid-user-from-docs-updates {:auth nil
                                                   :documents [{:schema-info {:name "uusiRakennus" :version 1}
                                                                :data {:rakennuksenOmistajat {:0 {:henkilo {:userId {:value "123"}}}
                                                                                              :1 {:henkilo {:userId {:value "345"}}}}}}]})
  => {"documents.0.data.rakennuksenOmistajat.0.henkilo.userId" ""
      "documents.0.data.rakennuksenOmistajat.1.henkilo.userId" ""}

  (against-background (auth/has-auth-via-company? anything "123") => false)
  (against-background (auth/has-auth-via-company? anything "345") => false))

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
