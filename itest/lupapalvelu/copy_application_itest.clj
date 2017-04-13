(ns lupapalvelu.copy-application-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.test-util :refer [walk-dissoc-keys]]
            [lupapalvelu.application :as app]
            [sade.property :as prop]))

(apply-remote-minimal)

(facts "copying application"
  (facts "successful copy"
    (let [app-id (create-app-id pena)
          app (query-application pena app-id)
          _ (Thread/sleep 1000)
          x 444445.0 y 6666665.0
          property-id "75312312341234"
          {copy-app-id :id} (command pena :copy-application
                                     :x x :y y
                                     :address "Testitie 1"
                                     :auth-invites "invites"
                                     :propertyId property-id
                                     :source-application-id app-id)
          copy-app (query-application pena copy-app-id)]

      (fact "primaryOperation is copied, but id is new"
        (dissoc (:primaryOperation copy-app) :id)
        => (dissoc (:primaryOperation app) :id)
        (:id (:primaryOperation copy-app))
        =not=> (:id (:primaryOperation app)))

      (fact "documents are copied, apart from ids"
        (walk-dissoc-keys (:documents copy-app) :id :created)
        => (just (walk-dissoc-keys (:documents app) :id :created) :in-any-order))

      (fact "the copied app has the provided location and property id"
        (:location copy-app) => {:x x :y y}
        (:propertyId copy-app) => property-id
        (:municipality copy-app) => (prop/municipality-id-by-property-id property-id)))))
