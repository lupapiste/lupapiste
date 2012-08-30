(ns lupapalvelu.web-test
  (:use lupapalvelu.web
        clojure.test
        midje.sweet)
  (:require [noir.session :as session]
            [lupapalvelu.security :as security]))

#_(facts
  (against-background
    (session/get :party) => :session-party
    (security/login-with-apikey "123") => :apikey-party)
  (current-party {}) => :session-party
  (current-party {:apikey "123"}) => :apikey-party (provided (session/get :party) => nil))
