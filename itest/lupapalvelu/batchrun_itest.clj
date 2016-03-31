(ns lupapalvelu.batchrun-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.batchrun :as bat]))

(apply-remote-minimal)

(facts "batch run tests"
       (fact "check-for-verdicts doesn't crash"
             (bat/check-for-verdicts) => nil)
       (fact "check-for-verdicts logs :error on exception"
             (bat/check-for-verdicts) => nil
              (provided
              (mongo/select :applications anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
              (mongo/select :organizations anything anything) => [{:foo 42}]
              (clojure.string/blank? anything) => false
              (lupapalvelu.logging/log-event :error anything) => nil
              ;;(lupapalvelu.verdict-api/do-check-for-verdict anything) => nil
              ))
       (fact "check-for-verdicts logs failure details"
             (bat/check-for-verdicts) => nil
              (provided
              (mongo/select :applications anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
              (mongo/select :organizations anything anything) => [{:foo 42}]
              (clojure.string/blank? anything) => false
              (lupapalvelu.logging/log-event :error anything) => nil
              ;;(lupapalvelu.verdict-api/do-check-for-verdict anything) => nil
              ))
       )
