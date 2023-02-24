(ns lupapalvelu.change-email-test
  (:require [lupapalvelu.change-email :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.token :as token]
            [lupapalvelu.user :as usr]
            [lupapalvelu.vetuma :as vetuma]
            [midje.sweet :refer :all]
            [midje.util]
            [monger.operators :refer :all]
            [slingshot.slingshot :refer [try+]]))

(facts change-email
  (fact "normal user"
    (change-email ..token-id.. ..stamp.. ..created..) => {:ok true}

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..})
    (provided (vetuma/get-user ..stamp..) => {:userid ..hetu..})
    (provided (usr/verified-person-id? {:id       ..user-id..
                                        :username ..username..
                                        :email    ..old-email..
                                        :personId ..hetu..}) => true)
    (provided (usr/get-user-by-email ..new-email..) => {:id ..dummy-id..
                                                        :role "dummy"})

    (provided (usr/update-user-by-email ..old-email.. {:personId ..hetu..} {$set {:username ..new-email.. :email ..new-email..}}) => 1)
    (provided (vetuma/consume-user ..stamp..) => 1)
    (provided (token/get-usable-token ..token-id.. :consume true) => 1)
    (provided (#'lupapalvelu.change-email/remove-dummy-auths-where-user-already-has-auth ..user-id.. ..new-email.. ) => 1)
    (provided (#'lupapalvelu.change-email/change-auths-dummy-id-to-user-id {:id ..user-id.. :username ..username.. :email ..old-email.. :personId ..hetu..} ..dummy-id.. ) => 1)
    (provided (usr/remove-dummy-user ..dummy-id..) => 1)
    (provided (#'lupapalvelu.change-email/update-email-in-application-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-invite-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-application-forms! ..user-id.. ..old-email.. ..new-email.. ..created..) => 0)
    (provided (notifications/notify! :email-changed {:user {:id ..user-id.. :username ..username.. :email ..old-email.. :personId ..hetu..}, :data {:new-email ..new-email..}}) => 1))

  (fact "no dummy user created"
    (change-email ..token-id.. ..stamp.. ..created..) => {:ok true}

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..})
    (provided (vetuma/get-user ..stamp..) => {:userid ..hetu..})
    (provided (usr/verified-person-id? {:id       ..user-id..
                                        :username ..username..
                                        :email    ..old-email..
                                        :personId ..hetu..}) => true)
    (provided (usr/get-user-by-email ..new-email..) => nil)

    (provided (usr/update-user-by-email ..old-email.. {:personId ..hetu..} {$set {:username ..new-email.. :email ..new-email..}}) => 1)
    (provided (vetuma/consume-user ..stamp..) => 1)
    (provided (token/get-usable-token ..token-id.. :consume true) => 1)
    (provided (#'lupapalvelu.change-email/update-email-in-application-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-invite-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-application-forms! ..user-id.. ..old-email.. ..new-email.. ..created..) => 0)
    (provided (notifications/notify! :email-changed {:user {:id ..user-id.. :username ..username.. :email ..old-email.. :personId ..hetu..}, :data {:new-email ..new-email..}}) => 1))

  (fact "company user"
    (change-email ..token-id.. nil ..created..) => {:ok true}

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
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
    (provided (token/get-usable-token ..token-id.. :consume true) => 1)
    (provided (#'lupapalvelu.change-email/remove-dummy-auths-where-user-already-has-auth ..user-id.. ..new-email.. ) => 1)
    (provided (#'lupapalvelu.change-email/change-auths-dummy-id-to-user-id {:id ..user-id.. :username ..username.. :email ..old-email.. :company {:role "user"}} ..dummy-id.. ) => 1)
    (provided (usr/remove-dummy-user ..dummy-id..) => 1)
    (provided (#'lupapalvelu.change-email/update-email-in-application-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-invite-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-application-forms! ..user-id.. ..old-email.. ..new-email.. ..created..) => 0)
    (provided (notifications/notify! :email-changed {:user {:id ..user-id.. :username ..username.. :email ..old-email.. :company {:role "user"}}, :data {:new-email ..new-email..}}) => 1))

  (fact "company admin - first change"
    (change-email ..token-id.. ..stamp.. ..created..) => {:ok true}

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :company {:role "admin"}})
    (provided (vetuma/get-user ..stamp..) => {:userid ..hetu..})
    (provided (usr/get-user-by-email ..new-email..) => {:id ..dummy-id..
                                                        :role "dummy"})

    (provided (usr/update-user-by-email ..old-email.. {:personId nil} {$set {:username ..new-email.. :email ..new-email.. :personId ..hetu.. :personIdSource :identification-service}}) => 1)
    (provided (token/get-usable-token ..token-id.. :consume true) => 1)
    (provided (#'lupapalvelu.change-email/remove-dummy-auths-where-user-already-has-auth ..user-id.. ..new-email.. ) => 1)
    (provided (#'lupapalvelu.change-email/change-auths-dummy-id-to-user-id {:id ..user-id.. :username ..username.. :email ..old-email.. :company {:role "admin"}} ..dummy-id.. ) => 1)
    (provided (usr/remove-dummy-user ..dummy-id..) => 1)
    (provided (#'lupapalvelu.change-email/update-email-in-application-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-invite-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-application-forms! ..user-id.. ..old-email.. ..new-email.. ..created..) => 0)
    (provided (notifications/notify! :email-changed {:user {:id ..user-id.. :username ..username.. :email ..old-email.. :company {:role "admin"}}, :data {:new-email ..new-email..}}) => 1))

  (fact "company admin - later change"
    (change-email ..token-id.. ..stamp.. ..created..) => {:ok true}

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..
                                                    :company {:role "admin"}})
    (provided (vetuma/get-user ..stamp..) => {:userid ..hetu..})
    (provided (usr/verified-person-id? {:id       ..user-id..
                                        :username ..username..
                                        :email    ..old-email..
                                        :personId ..hetu..
                                        :company {:role "admin"}}) => true)
    (provided (usr/get-user-by-email ..new-email..) => {:id ..dummy-id..
                                                        :role "dummy"})

    (provided (usr/update-user-by-email ..old-email.. {:personId ..hetu..} {$set {:username ..new-email.. :email ..new-email..}}) => 1)
    (provided (vetuma/consume-user ..stamp..) => 1)
    (provided (token/get-usable-token ..token-id.. :consume true) => 1)
    (provided (#'lupapalvelu.change-email/remove-dummy-auths-where-user-already-has-auth ..user-id.. ..new-email.. ) => 1)
    (provided (#'lupapalvelu.change-email/change-auths-dummy-id-to-user-id {:id ..user-id.. :username ..username.. :email ..old-email.. :personId ..hetu.. :company {:role "admin"}} ..dummy-id.. ) => 1)
    (provided (usr/remove-dummy-user ..dummy-id..) => 1)
    (provided (#'lupapalvelu.change-email/update-email-in-application-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-invite-auth! ..user-id.. ..old-email.. ..new-email..) => 0)
    (provided (#'lupapalvelu.change-email/update-email-in-application-forms! ..user-id.. ..old-email.. ..new-email.. ..created..) => 0)
    (provided (notifications/notify! :email-changed {:user {:id ..user-id.. :username ..username.. :email ..old-email.. :personId ..hetu.. :company {:role "admin"}}, :data {:new-email ..new-email..}}) => 1))

  (fact "normal user - vetumadata does not match"
    (try+
     (change-email ..token-id.. ..stamp.. ..created..)
     (catch [:sade.core/type :sade.core/fail] e (:text e))) => "error.personid-mismatch"

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..})
    (provided (vetuma/get-user ..stamp..) => {:userid ..another-hetu..})
    (provided (usr/verified-person-id? {:id       ..user-id..
                                        :username ..username..
                                        :email    ..old-email..
                                        :personId ..hetu..}) => true))

  (fact "company admin - later chaange - vetumadata does not match"
    (try+
     (change-email ..token-id.. ..stamp.. ..created..)
     (catch [:sade.core/type :sade.core/fail] e (:text e))) => "error.personid-mismatch"

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..
                                                    :company {:role "admin"}})
    (provided (vetuma/get-user ..stamp..) => {:userid ..another-hetu..})
    (provided (usr/verified-person-id? {:id       ..user-id..
                                        :username ..username..
                                        :email    ..old-email..
                                        :personId ..hetu..
                                        :company {:role "admin"}}) => true))

  (fact "no person id nor company role"
    (try+
     (change-email ..token-id.. ..stamp.. ..created..)
     (catch [:sade.core/type :sade.core/fail] e (:text e))) => "error.missing-person-id"

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
                                                 :token-type :change-email
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId nil
                                                    :role "applicant"}))

  (fact "invalid token"
    (try+
     (change-email ..token-id.. ..stamp.. ..created..)
     (catch [:sade.core/type :sade.core/fail] e (:text e))) => "error.token-not-found"

    (provided (token/get-usable-token ..token-id..) => {:id ..token-id..
                                                 :token-type :invalid-type
                                                 :user-id ..user-id..
                                                 :data {:new-email ..new-email..}})
    (provided (usr/get-user-by-id! ..user-id..) => {:id       ..user-id..
                                                    :username ..username..
                                                    :email    ..old-email..
                                                    :personId ..hetu..})
    (provided (vetuma/get-user ..stamp..) => {:userid ..hetu..})
    (provided (usr/verified-person-id? {:id       ..user-id..
                                        :username ..username..
                                        :email    ..old-email..
                                        :personId ..hetu..}) => true)))

(facts update-email-in-application-forms!
  (fact "applications with multiple applicable and non-applicable documents"
    (let [right-info  {:yhteystiedot {:email "teppo@example.com"}}
          wrong-info  {:yhteystiedot {:email "rauno@example.com"}}
          mock-doc    {:created     1000
                       :id          "doc-id-0"
                       :schema-info {:name    "hakija-r"
                                     :subtype "hakija"
                                     :type    "party"
                                     :version 1}
                       :data        {:_selected "henkilo"
                                     :henkilo   right-info}}
          mock-notice {:customer {:email "teppo@example.com"}
                       :history  [{:state "open" :timestamp 2003
                                   :user {:username "teppo@example.com"}}
                                  {:state "rejected" :timestamp 2002
                                   :user {:username "rauno@example.com"}}
                                  {:state "ok" :timestamp 2001}]}
          design-doc  (-> (assoc-in mock-doc [:schema-info :name] "suunnittelija")
                          (assoc :created 2600))
          mock-app    {:id           "LP-753-1900-1"
                       :state        "verdictGiven"
                       :history      [{:state :submitted :ts 2000}
                                      {:state :verdictGiven :ts 2500}
                                      {:state :sent :ts 2900}]
                       :notice-forms [mock-notice
                                      (update mock-notice :history conj {:timestamp 2005 :state "ok"})]
                       :documents    (mapv #(update % :data tools/wrapped)
                                           [;; Document 0 - changed email
                                            mock-doc
                                            ;; Document 1 - unchanged email
                                            (assoc-in mock-doc [:data :henkilo] wrong-info)
                                            ;; Document 2 - building owners
                                            (-> (dissoc mock-doc :data)
                                                (assoc-in [:data :rakennuksenOmistajat]
                                                          {:0 {:_selected "henkilo"
                                                               :henkilo   wrong-info}
                                                           :1 {:_selected "yritys"
                                                               :henkilo   right-info}
                                                           :2 {:_selected "yritys"
                                                               :henkilo   wrong-info}}))
                                            ;; Document 3 - unchanged email (alternate path)
                                            (-> (dissoc mock-doc :data)
                                                (assoc :data wrong-info))
                                            ;; Document 4 - changed email (alternate path)
                                            (-> (dissoc mock-doc :data)
                                                (assoc :data right-info))
                                            ;; Document 5 - changed email, disabled document
                                            (assoc mock-doc :disabled true)
                                            ;; Document 6 - changed email, created after verdict
                                            design-doc
                                            ;; Document 7 - changed email, created after verdict yet approved
                                            (assoc-in design-doc [:meta :_approved :value] "approved")
                                            ;; Document 8 - company contact
                                            (-> (dissoc mock-doc :data)
                                                (assoc-in [:data :yritys :yhteyshenkilo] right-info))])}]
      (try+
        (update-email-in-application-forms! ..user-id.. "teppo@example.com" ..new-email.. 3000) => nil?
        (provided (mongo/select :applications anything anything)
                  => [mock-app
                      (assoc mock-app :state "draft" :id "LP-753-1900-2")])
        (provided (mongo/update-by-id
                    :applications
                    "LP-753-1900-1"
                    {$push {:_sheriff-notes {:created 3000
                                             :note    "Changed email from teppo@example.com to ..new-email.."}}
                     $set  {:modified                                             3000
                            :documents.6.data.henkilo.yhteystiedot.email.value    ..new-email..
                            :documents.6.data.henkilo.yhteystiedot.email.modified 3000
                            :notice-forms.0.customer.email                        ..new-email..
                            :notice-forms.0.history.0.user.username               ..new-email..}})
                  => 1)
        (provided (mongo/update-by-id
                    :applications
                    "LP-753-1900-2"
                    {$push {:_sheriff-notes {:created 3000
                                             :note    "Changed email from teppo@example.com to ..new-email.."}}
                     $set  {:modified                                                                    3000
                              :documents.0.data.henkilo.yhteystiedot.email.value                           ..new-email..
                              :documents.0.data.henkilo.yhteystiedot.email.modified                        3000
                              :documents.2.data.rakennuksenOmistajat.1.henkilo.yhteystiedot.email.value    ..new-email..
                              :documents.2.data.rakennuksenOmistajat.1.henkilo.yhteystiedot.email.modified 3000
                              :documents.4.data.yhteystiedot.email.value                                   ..new-email..
                              :documents.4.data.yhteystiedot.email.modified                                3000
                              :documents.6.data.henkilo.yhteystiedot.email.value                           ..new-email..
                              :documents.6.data.henkilo.yhteystiedot.email.modified                        3000
                              :documents.7.data.henkilo.yhteystiedot.email.value                           ..new-email..
                              :documents.7.data.henkilo.yhteystiedot.email.modified                        3000
                              :documents.8.data.yritys.yhteyshenkilo.yhteystiedot.email.value              ..new-email..
                              :documents.8.data.yritys.yhteyshenkilo.yhteystiedot.email.modified           3000
                              :notice-forms.0.customer.email                                               ..new-email..
                              :notice-forms.0.history.0.user.username                                      ..new-email..}})
                  => 1)))))
