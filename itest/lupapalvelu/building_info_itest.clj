(ns lupapalvelu.building-info-itest
  (:require [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.common-reader :as cr]
            [sade.xml :as xml])
  (:import [com.mongodb DuplicateKeyException]))

(with-test-system {:fixture "minimal"}
  (facts "Building info cache"
    (against-background [(cr/get-xml anything anything anything anything)
                         => (-> "krysp/dev/building.xml"
                                io/resource
                                io/input-stream
                                xml/parse)])
    (let [{app-id :id} (create-local-app pena
                                         :propertyId sipoo-property-id
                                         :operation "pientalo")]
      (fact "Fetch buildings via WFS"
        (let [{data :data} (local-query pena :get-building-info-from-wfs :id app-id)]
          (first data) => (contains {:created      "1962"
                                     :localShortId "001"
                                     :buildingId   "122334455R"})))
      (fact "Cache is filled"
        (mongo/select-one :buildingCache {:propertyId sipoo-property-id})
        => truthy)
      (fact "Make sure that the data comes from cache"
        (mongo/update :buildingCache
                      {:propertyId sipoo-property-id}
                      {$set {:buildings.0.created "1988"}})
        (-> (local-query pena :get-building-info-from-wfs :id app-id)
            :data first :created)=> "1988")
      (fact "Property ids must be unique"
        (mongo/insert :buildingCache {:propertyId sipoo-property-id} {:hii "hoo"})
        => (throws DuplicateKeyException)))
    (fact "Failed queries do not update cache"
      (let [bad-property-id "bad-property-id"
            {app-id :id}    (create-local-app pena
                                              :propertyId bad-property-id
                                              :operation "pientalo")]
        (local-query pena :get-building-info-from-wfs :id app-id)
        => (just {:ok true})
        (mongo/select-one :buildingCache {:propertyId bad-property-id}) => nil?))))
