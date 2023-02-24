(ns lupapalvelu.logging-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.logging :as logging]))

(facts "sanitize"
  (fact "truncate"
    (logging/sanitize 5 "12345") => "12345"
    (logging/sanitize 2 "12345") => "12... (truncated)")

  (fact "new lines"
    (logging/sanitize 100 "1\r2\n3\r\n4") => "1\\n2\\n3\\n4"))
