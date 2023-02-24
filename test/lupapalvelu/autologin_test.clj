(ns lupapalvelu.autologin-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.autologin :refer :all]))

(testable-privates lupapalvelu.autologin parse-ts-hash valid-hash?)

(fact "documented password"
  (parse-ts-hash "1410421001438_7a29b8a99c379788ba4f94b6342b7cd7950e5e3992dc90cdf1c124c515fb0892")
      => ["1410421001438" "7a29b8a99c379788ba4f94b6342b7cd7950e5e3992dc90cdf1c124c515fb0892"])

(fact "documented hash"
  (valid-hash? "7a29b8a99c379788ba4f94b6342b7cd7950e5e3992dc90cdf1c124c515fb0892"
               "user@example.com"
               "127.0.0.1"
               "1410421001438"
               "LUPAPISTE")
      => true)

(fact "invalid password"
  (valid-hash? "7a29b8a99c379788ba4f94b6342b7cd7950e5e3992dc90cdf1c124c515fb0892"
               "user@example.com"
               "127.0.0.1"
               "1410421001438"
               "NOT-LUPAPISTE")
      => false)

(fact "invalid hash for documented content"
  (valid-hash? "0000b8a99c379788ba4f94b6342b7cd7950e5e3992dc90cdf1c124c515fb0000"
               "user@example.com"
               "127.0.0.1"
               "1410421001438"
               "LUPAPISTE")
      => false)

(fact "invalid content for documented hash"
  (valid-hash? "7a29b8a99c379788ba4f94b6342b7cd7950e5e3992dc90cdf1c124c515fb0892"
               "user2@example.com"
               "127.0.0.1"
               "1410421001438"
               "LUPAPISTE")
      => false)
