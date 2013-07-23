(ns lupapalvelu.web-itest
  (:use [lupapalvelu.itest-util]
        [clojure.walk :only [keywordize-keys]]
        [midje.sweet]))

(facts
  (fact "ping"
    (command pena :ping) => not-ok?
    (query pena :ping) => ok?
    (raw :ping) => (contains {:status 404}))
  (fact "ping-raw"
    (command pena :ping-raw) => not-ok?
    (query pena :ping-raw) => not-ok?
    (raw :ping-raw) => (contains {:body "pong" :status 200}))
  (fact "ping!"
    (command pena :ping!) => ok?
    (query pena :ping!) => not-ok?
    (raw :ping!) => (contains {:status 404})))
