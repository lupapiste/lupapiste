(ns lupapalvelu.copy-application-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(facts "copying application"
  (let [app-id (create-app-id pena)]
    (command pena :copy-application :x 1 :y 1 :address "testikatu 1" :municipality "oulu" :auth-invites "invites" :propertyId () :source-application-id app-id)))
