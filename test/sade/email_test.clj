(ns sade.email-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [sade.email :refer :all]
            [sade.dummy-email-server :as server]))

(server/start)

(facts
  (send-mail) => (throws AssertionError)
  (send-mail :to "a@b.c" :subject "s") => (throws AssertionError)
  (send-mail :to "a@b.c" :subject "s" :text "foo") => nil
  )