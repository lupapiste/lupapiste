(ns lupapalvelu.batchrun.fetch-verdict-consumer-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.batchrun.fetch-verdict :as fetch-verdict]
            [lupapalvelu.batchrun.fetch-verdict-consumer :refer [handle-fetch-verdict-message]]
            [taoensso.nippy :as nippy]))

(def test-handler (handle-fetch-verdict-message (constantly :commit) (constantly :rollback)))

(facts "handle-check-verdict-message"
       (fact "commits on invalid message"
             (test-handler (nippy/freeze "invalid message")) => :commit)
       (fact "commits nonexistent application"
             (test-handler (nippy/freeze {:id "nonexistent application id"})) => :commit
             (provided
              (lupapalvelu.mongo/select-one :applications anything) => nil))
       (fact "rolls back if fetch fails"
             (test-handler (nippy/freeze {:id "ID"})) => :rollback
             (provided
              (lupapalvelu.mongo/select-one :applications {:_id "ID" :state "sent"}) => {:id "ID" :organization "organization"}
              (lupapalvelu.user/batchrun-user anything) => ...user...
              (fetch-verdict/fetch-verdict anything anything anything) => false)))
