(ns lupapalvelu.foreman-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer [expected-failure?]]
            [lupapalvelu.foreman :as f]
            [lupapalvelu.mongo :as mongo]))

(def foreman-app {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :documents [{:schema-info {:name "tyonjohtaja-v2"}
                               :data {:ilmoitusHakemusValitsin {:value "ilmoitus"}}}]
                  :linkPermitData [{:type "lupapistetunnus"
                                    :id "LP-123"}]})

(facts "Notice? tests"
  (f/notice? foreman-app) => true
  (f/notice? (assoc-in foreman-app [:primaryOperation :name] "other-op")) => false
  (f/notice? (assoc-in foreman-app [:documents 0 :schema-info :name] "other-doc")) => false
  (f/notice? (assoc-in foreman-app [:documents 0 :data :ilmoitusHakemusValitsin :value] "hakemus")) => false
  (f/notice? nil) => false)

(facts "validate if notice is submittable"
  (f/validate-notice-submittable nil nil) => nil
  (f/validate-notice-submittable nil false) => nil
  (f/validate-notice-submittable {} false) => nil
  (fact "If confirm is true, nil is returned (everything ok)"
    (f/validate-notice-submittable foreman-app true) => nil)
  (fact "Only foreman notice apps are validated"
    (f/validate-notice-submittable (assoc-in foreman-app [:documents 0 :data :ilmoitusHakemusValitsin :value] "hakemus") false) => nil)
  (fact "Link permit must exist for validate to run"
    (f/validate-notice-submittable (dissoc foreman-app :linkPermitData) false) => nil)
  (fact "Validator returns nil if state is post-verdict state"
    (f/validate-notice-submittable foreman-app false) => nil
    (provided
      (mongo/select-one :applications {:_id "LP-123"} {:state 1}) => {:state "verdictGiven"}))
  (fact "Validator returns fail! when state is post-verdict state"
    (f/validate-notice-submittable foreman-app false) => (partial expected-failure? :error.foreman.notice-not-submittable)
    (provided
      (mongo/select-one :applications {:_id "LP-123"} {:state 1}) => {:state "sent"})))
