(ns lupapalvelu.foreman-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [now]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.foreman :as f]))

(def db-name (str "test_foreman-itest_" (now)))

(testable-privates lupapalvelu.foreman
                   henkilo-invite
                   yritys-invite)

(mongo/connect!)
(mongo/with-db db-name (fixture/apply-fixture "minimal")
  (facts "Creating invites for foreman application"
         (let [test-auths             [{:invite {:user {:id "C-123"}}}
                                       {:username "testi@example.com" :id "alsdkfjalsdjflsdf"}
                                       {:username "pena" :id "777777777777777777000020"}
                                       {:username "kaino@solita.fi" :id "kainosolita"}]
                                        ; Unwrapped documents!
               non-authed-henkilo-doc {:schema-info {:subtype "hakija"}
                                       :data {:_selected "henkilo"
                                              :henkilo {:yhteystiedot {:email "heppu@example.com"}}}}
               authed-henkilo-doc     {:schema-info {:subtype "hakija"}
                                       :data {:_selected "henkilo"
                                              :henkilo {:yhteystiedot {:email "pena@example.com"}}}}
               companyid-doc          {:schema-info {:subtype "hakija"}
                                       :data {:_selected "yritys"
                                              :yritys {:companyId "C-123"}}}
               contact-person-doc     {:schema-info {:subtype "hakija"}
                                       :data {:_selected "yritys"
                                              :yritys {:yhteyshenkilo
                                                       {:yhteystiedot {:email "kaino@solita.fi"}}}}}
               bad-henkilo-doc        {:schema-info {:subtype "hakija"}
                                       :data {:_selected "henkilo"
                                              :henkilo {:yhteystiedot {:email nil}}}}
               bad-contact-doc        {:schema-info {:subtype "hakija"}
                                       :data {:_selected "yritys"
                                              :yritys {:yhteyshenkilo {:yhteystiedot {:email nil}}}}}]
           (facts "Henkilo invite"
                  (henkilo-invite nil test-auths) => (throws AssertionError)
                  (henkilo-invite {} test-auths) => (throws AssertionError)
                  (fact "wrong _selected result in assert fail"
                        (henkilo-invite companyid-doc test-auths) => (throws AssertionError)
                        (henkilo-invite contact-person-doc test-auths) => (throws AssertionError))
                  (fact "not authed henkilo is not invited"
                        (henkilo-invite non-authed-henkilo-doc test-auths) => nil)
                  (fact "authed henkilo is invited"
                        (henkilo-invite authed-henkilo-doc test-auths) => {:email "pena@example.com"
                                                                           :role "writer"})
                  (fact "Nil email is ignored"
                        (henkilo-invite bad-henkilo-doc test-auths) => nil))

           (facts "Yritys invite"
                  (yritys-invite nil test-auths) => (throws AssertionError)
                  (yritys-invite {} test-auths) => (throws AssertionError)
                  (fact "wrong _selected result in assert fail"
                        (yritys-invite authed-henkilo-doc test-auths) => (throws AssertionError))
                  (fact "Company id results in company invitation"
                        (yritys-invite companyid-doc test-auths) => {:company-id "C-123"})
                  (fact "If company-id not in auth, no invite"
                        (yritys-invite companyid-doc (drop 1 test-auths)) => nil)
                  (fact "If company id defined in auth, then invite"
                        (yritys-invite
                         companyid-doc
                         (-> (drop 1 test-auths) (conj {:id "C-123"}))) => {:company-id "C-123"})
                  (fact "Contact person in yritys results in contact person invite"
                        (yritys-invite contact-person-doc test-auths) => {:email "kaino@solita.fi"
                                                                          :role "writer"})
                  (fact "No invite for contact person if not present in auth array"
                        (yritys-invite contact-person-doc (drop-last test-auths)) => nil)
                  (fact "Nil contact email is ignored"
                        (yritys-invite bad-contact-doc test-auths) => nil)))))
