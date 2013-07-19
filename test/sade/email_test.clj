(ns sade.email-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [sade.email :refer :all]
            [sade.dummy-email-server :as server]))

#_(server/start)

#_(facts
  (send-mail) => (throws AssertionError)
  (send-mail :to "a@b.c" :subject "s") => (throws AssertionError)
  (send-mail :to "a@b.c" :subject "s" :text "foo") => nil
  (last (server/messages)) => (contains {:body {:html nil :plain "foo"}})
  (send-mail :to "a@b.c" :subject "s" :html "foo") => nil
  (last (server/messages)) => (contains {:body {:html "foo" :plain nil}})
  (send-mail :to "a@b.c" :subject "s" :html "foo" :text "bar") => nil
  (last (server/messages)) => (contains {:body {:html "foo" :plain "bar"}}))

#_(server/stop)
