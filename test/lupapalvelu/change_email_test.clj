(ns lupapalvelu.change-email-test
  (:require [midje.sweet :refer :all]
            [midje.util]
            [lupapalvelu.change-email :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.token :as token]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.vetuma :as vetuma]
            [slingshot.slingshot :refer [try+]]
            [monger.operators :refer :all]))

(facts change-email
  (fact "normal user"
    (change-email ..token-id.. ..stamp..) => {:ok true}

    (provided (token/get-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..})
    (provided (vetuma/get-user ..stamp..) => {:userid ..hetu..})
    (provided (usr/get-user-by-email ..new-email..) => {:id ..dummy-id..
                                                        :role "dummy"})

    (provided (usr/update-user-by-email ..old-email.. {:personId ..hetu..} {$set {:username ..new-email.. :email ..new-email..}}) => 1)
    (provided (vetuma/consume-user ..stamp..) => 1)
    (provided (token/get-token ..token-id.. :consume true) => 1)
    (provided (#'lupapalvelu.change-email/remove-dummy-auths-where-user-already-has-auth ..user-id.. ..new-email.. ) => 1)
    (provided (#'lupapalvelu.change-email/change-auths-dummy-id-to-user-id {:id ..user-id.. :username ..username.. :email ..old-email.. :personId ..hetu..} ..dummy-id.. ) => 1)
    (provided (usr/remove-dummy-user ..dummy-id..) => 1)
    (provided (#'lupapalvelu.change-email/update-email-in-application-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-invite-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (notifications/notify! :email-changed {:user {:id ..user-id.. :username ..username.. :email ..old-email.. :personId ..hetu..}, :data {:new-email ..new-email..}}) => 1))

  (fact "no dummy user created"
    (change-email ..token-id.. ..stamp..) => {:ok true}

    (provided (token/get-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..})
    (provided (vetuma/get-user ..stamp..) => {:userid ..hetu..})
    (provided (usr/get-user-by-email ..new-email..) => nil)

    (provided (usr/update-user-by-email ..old-email.. {:personId ..hetu..} {$set {:username ..new-email.. :email ..new-email..}}) => 1)
    (provided (vetuma/consume-user ..stamp..) => 1)
    (provided (token/get-token ..token-id.. :consume true) => 1)
    (provided (#'lupapalvelu.change-email/update-email-in-application-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-invite-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (notifications/notify! :email-changed {:user {:id ..user-id.. :username ..username.. :email ..old-email.. :personId ..hetu..}, :data {:new-email ..new-email..}}) => 1))

  (fact "company user"
    (change-email ..token-id.. nil) => 2

    (provided (token/get-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :company  {:role "user"}})
    (provided (usr/get-user-by-email ..new-email..) => {:id ..dummy-id..
                                                        :role "dummy"})

    (provided (usr/update-user-by-email ..old-email.. {:personId nil} {$set {:username ..new-email.. :email ..new-email..}}) => 1)
    (provided (token/get-token ..token-id.. :consume true) => 1)
    (provided (#'lupapalvelu.change-email/remove-dummy-auths-where-user-already-has-auth ..user-id.. ..new-email.. ) => 1)
    (provided (#'lupapalvelu.change-email/change-auths-dummy-id-to-user-id {:id ..user-id.. :username ..username.. :email ..old-email.. :company {:role "user"}} ..dummy-id.. ) => 1)
    (provided (usr/remove-dummy-user ..dummy-id..) => 1)
    (provided (#'lupapalvelu.change-email/update-email-in-application-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-invite-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (notifications/notify! :email-changed {:user {:id ..user-id.. :username ..username.. :email ..old-email.. :company {:role "user"}}, :data {:new-email ..new-email..}}) => 1))

  (fact "normal user - vetumadata does not match"
    (try+
     (change-email ..token-id.. ..stamp..)
     (catch [:sade.core/type :sade.core/fail] e (:text e))) => "error.personid-mismatch"

    (provided (token/get-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..})
    (provided (vetuma/get-user ..stamp..) => {:userid ..another-hetu..}))

  (fact "no person id nor company role"
    (try+
     (change-email ..token-id.. ..stamp..)
     (catch [:sade.core/type :sade.core/fail] e (:text e))) => "error.missing-person-id"

    (provided (token/get-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId nil}))

  (fact "invalid token"
    (try+
     (change-email ..token-id.. ..stamp..)
     (catch [:sade.core/type :sade.core/fail] e (:text e))) => "error.token-not-found"

    (provided (token/get-token ..token-id..) => {:id ..token-id..
                                                 :token-type :invalid-type
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..})
    (provided (vetuma/get-user ..stamp..) => {:userid ..hetu..})))
