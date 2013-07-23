(ns lupapalvelu.web-itest
  (:use [lupapalvelu.itest-util]
        [clojure.walk :only [keywordize-keys]]
        [midje.sweet]))

(defn raw-error? [{:keys [status]}]
  (= status 404))

(facts
  (fact "ping"
    (query pena :ping) => ok?
    (raw :ping) => raw-error?)
  (fact "ping-raw"
    (query pena :ping-raw) => not-ok?
    (raw :ping-raw) => (contains {:body "pong" :status 200})))
