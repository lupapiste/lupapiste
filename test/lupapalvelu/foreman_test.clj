(ns lupapalvelu.foreman-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.foreman :as f]
            [lupapalvelu.mongo :as mongo]))

(testable-privates lupapalvelu.foreman validate-notice-or-application validate-notice-submittable)

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
