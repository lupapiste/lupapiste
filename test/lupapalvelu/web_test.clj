(ns lupapalvelu.web-test
  (:use lupapalvelu.web
        clojure.test
        midje.sweet)
  (:require [noir.session :as session]
            [lupapalvelu.security :as security]))

#_(facts
  (against-background
    (session/get :user) => :session-user
    (security/login-with-apikey "123") => :apikey-user)
  (current-user {}) => :session-user
  (current-user {:apikey "123"}) => :apikey-user (provided (session/get :user) => nil))
