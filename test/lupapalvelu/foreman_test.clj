(ns lupapalvelu.foreman-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.foreman :as f]
            [lupapalvelu.mongo :as mongo]))

(testable-privates lupapalvelu.foreman validate-notice-or-application validate-notice-submittable)

(def foreman-app {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :documents [{:schema-info {:name "tyonjohtaja-v2"}
                               :data {:ilmoitusHakemusValitsin {:value "ilmoitus"}}}]
                  :linkPermitData [{:type "lupapistetunnus"
                                    :id "LP-123"}]})

(facts "Foreman application validation"
  (validate-notice-or-application foreman-app) => nil
  (validate-notice-or-application
    (assoc-in foreman-app
      [:documents 0 :data :ilmoitusHakemusValitsin :value]
      "")) => (partial expected-failure? :error.foreman.type-not-selected)
  (validate-notice-or-application
    (assoc-in foreman-app
      [:documents 0 :data :ilmoitusHakemusValitsin :value]
      nil)) => (partial expected-failure? :error.foreman.type-not-selected)
  (validate-notice-or-application
    (assoc-in foreman-app
      [:documents 0 :data :ilmoitusHakemusValitsin :value]
      "hakemus")) => nil)

(facts "Notice? tests"
  (f/notice? foreman-app) => true
  (f/notice? (assoc-in foreman-app [:primaryOperation :name] "other-op")) => false
  (f/notice? (assoc-in foreman-app [:documents 0 :schema-info :name] "other-doc")) => false
  (f/notice? (assoc-in foreman-app [:documents 0 :data :ilmoitusHakemusValitsin :value] "hakemus")) => false
  (f/notice? nil) => false)

(facts "validate if notice is submittable"
  (validate-notice-submittable nil) => nil
  (validate-notice-submittable {}) => nil
  (fact "Only foreman notice apps are validated"
    (validate-notice-submittable (assoc-in foreman-app [:documents 0 :data :ilmoitusHakemusValitsin :value] "hakemus")) => nil)
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
