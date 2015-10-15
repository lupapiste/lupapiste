(ns lupapalvelu.foreman-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.foreman :as f]
            [lupapalvelu.mongo :as mongo]))

(testable-privates lupapalvelu.foreman
                   validate-notice-or-application
                   validate-notice-submittable
                   henkilo-invite
                   yritys-invite)

(def foreman-app {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype "tyonjohtaja-ilmoitus"
                  :documents [{:schema-info {:name "tyonjohtaja-v2"}
                               :data {}}]
                  :linkPermitData [{:type "lupapistetunnus"
                                    :id "LP-123"}]})

(facts "Foreman application validation"
  (validate-notice-or-application foreman-app) => nil
  (validate-notice-or-application
    (assoc foreman-app :permitSubtype "")) => (partial expected-failure? :error.foreman.type-not-selected)
  (validate-notice-or-application
    (assoc foreman-app :permitSubtype nil)) => (partial expected-failure? :error.foreman.type-not-selected)
  (validate-notice-or-application
    (assoc foreman-app :permitSubtype "tyonjohtaja-hakemus")) => nil)

(facts "Notice? tests"
  (f/notice? nil) => false
  (f/notice? {}) => false
  (f/notice? foreman-app) => true
  (f/notice? (assoc-in foreman-app [:primaryOperation :name] "other-op")) => false
  (f/notice? (assoc foreman-app :permitSubtype "tyonjohtaja-hakemus")) => false)

(facts "validate if notice is submittable"
  (validate-notice-submittable nil) => nil
  (validate-notice-submittable {}) => nil
  (fact "Only foreman notice apps are validated"
    (validate-notice-submittable (assoc foreman-app :permitSubtype "hakemus")) => nil)
  (fact "Link permit must exist for validate to run"
    (validate-notice-submittable (dissoc foreman-app :linkPermitData)) => nil)
  (fact "Validator returns nil if state is post-verdict state"
    (validate-notice-submittable foreman-app) => nil
    (provided
      (mongo/select-one :applications {:_id "LP-123"} {:state 1}) => {:state "verdictGiven"}))
  (fact "Validator returns fail! when state is post-verdict state"
    (validate-notice-submittable foreman-app) => (partial expected-failure? :error.foreman.notice-not-submittable)
    (provided
      (mongo/select-one :applications {:_id "LP-123"} {:state 1}) => {:state "sent"})))

(facts "Creating invites for foreman application"
  (let [test-auths [{:invite {:user {:id "C-123"}}}
                    {:username "testi@example.com"}
                    {:username "pena@example.com"}
                    {:username "contact@person.com"}]
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
                                       :yritys {:yhteyshenkilo {:yhteystiedot {:email "contact@person.com"}}}}}]
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
                                                           :role "writer"}))

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
        (yritys-invite contact-person-doc test-auths) => {:email "contact@person.com"
                                                          :role "writer"})
      (fact "No invite for contact person if not present in auth array"
        (yritys-invite contact-person-doc (drop-last test-auths)) => nil))))

