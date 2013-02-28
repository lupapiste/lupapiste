(ns lupapalvelu.csrf-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [clj-http.client :as c]))

(fact "Valid apikey bypasses CSRF check"
      (success (query mikko :allowed-actions)) => true)

(fact "CSRF check must not be bypassed if there is no apikey"
      (invalid-csrf-token (query nil :allowed-actions)) => true)

(fact "Calling non-protected resources returns a csrf token"
      (let [resp (c/get (str (server-address) "/"))
            cookie (get-in resp [:cookies "lupapiste-token"])]
        (:status resp) => 200
        cookie => truthy
        (:path cookie) => "/"))
