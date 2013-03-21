(ns sade.email-test
  (:use sade.email
        midje.sweet))

(fact "me@example.com does not give error"
      (send-mail? "me@example.com" "test" "test") => true)

(fact "me@example.org does not give error"
      (send-mail? "me@example.org" "test" "test") => true)

(fact "no domain at all does not give error"
      (send-mail? "root" "test" "test") => true)
