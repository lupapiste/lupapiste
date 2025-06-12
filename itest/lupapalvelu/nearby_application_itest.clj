(ns lupapalvelu.nearby-application-itest
  (:require [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (with-local-actions
    (let [r-app   (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                 :propertyId sipoo-property-id
                                                 :x 406898.625 :y 6684125.375
                                                 :address "Hitantine 108")
          r-app-2 (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                 :propertyId sipoo-property-id
                                                 :x 406998.726 :y 6684225.476
                                                 :address "Joensuun raitti 111")
          r-app-3 (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                 :propertyId sipoo-property-id
                                                 :x 406798.625 :y 6683125.375
                                                 :address "Hitantine 205")
          ;; application in different municipality
          _       (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                 :propertyId tampere-property-id
                                                 :x 334798.624 :y 6823125.375
                                                 :address "Satulakatu 108")
          ;; application in draft state
          _       (create-application mikko :operation "kerrostalo-rivitalo"
                                      :propertyId sipoo-property-id
                                      :x 406798.625 :y 6684125.375
                                      :address "Hitantine 107")
          ;; application in the same municipality but the distance is over 2000m
          _       (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                 :propertyId sipoo-property-id
                                                 :x 406798.625 :y 6681025.375
                                                 :address "Lemminkäisen temppeli")
          app-id  (:id r-app)]

      (fact "Nearby applications not returned when feature flag is not enabled"
        (->> (query sonja :nearby-application-markers :id app-id)
             :nearbyApplications
             (map :address)
             set) =>
        (partial = #{}))

      (fact "enable flag"
        (mongo/update-by-id :organizations "753-R" {$set {:nearby-application-markers-enabled true}}) => nil)

      (fact "Nearby applications returned and not the application itself"
        (->> (query sonja :nearby-application-markers :id app-id)
             :nearbyApplications
             (map :address)
             set) =>
        (partial = #{(:address r-app-2) (:address r-app-3)})))))
