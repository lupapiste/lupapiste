(ns lupapalvelu.copy-application-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.test-util :refer [walk-dissoc-keys]]
            [lupapalvelu.application :as app]
            [sade.property :as prop]))

(apply-remote-minimal)

(facts "copying application"
  (let [pena-user (find-user-from-minimal-by-apikey pena)
        sonja-user (find-user-from-minimal-by-apikey sonja)
        {app-id :id} (create-and-submit-application pena)
        app (query-application sonja app-id)
        _ (Thread/sleep 1000)
        x 444445.0 y 6666665.0
        property-id "75312312341234"
        {copy-app-id :id} (command sonja :copy-application
                                   :x x :y y
                                   :address "Testitie 1"
                                   :auth-invites [pena-id]
                                   :propertyId property-id
                                   :source-application-id app-id) => ok?
        copy-app (query-application sonja copy-app-id)]

    (fact "primaryOperation is copied, but id is new"
      (dissoc (:primaryOperation copy-app) :id)
      => (dissoc (:primaryOperation app) :id)
      (:id (:primaryOperation copy-app))
      =not=> (:id (:primaryOperation app)))

    (fact "documents are copied, apart from ids"
      (walk-dissoc-keys (:documents copy-app) :id :created :allowedActions)
      => (just (walk-dissoc-keys (:documents app) :id :created  :allowedActions) :in-any-order))

    (fact "the copied app has the provided location and property id"
      (:location copy-app) => {:x x :y y}
      (:propertyId copy-app) => property-id
      (:municipality copy-app) => (prop/municipality-id-by-property-id property-id))

    (fact "Sonja is new owner, Pena (previous owner) is invited as writer"
      (-> copy-app :auth (first) ((juxt :id :role))) => [(:id sonja-user) "owner"]
      (-> copy-app :auth (second) ((juxt :id (comp :role :invite)))) => [(:id pena-user) "writer"])

    (fact "Only auths with ids in auth-invites are copied from old app"
          (let [{copy-app-id :id} (command sonja :copy-application
                                           :x x :y y
                                           :address "Testitie 1"
                                           :auth-invites []
                                           :propertyId property-id
                                           :source-application-id app-id) => ok?
                copy-app (query-application sonja copy-app-id)]
            (-> copy-app :auth (first) ((juxt :id :role))) => [(:id sonja-user) "owner"]
            (-> copy-app :auth count) => 1))))
