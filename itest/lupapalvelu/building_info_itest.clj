(ns lupapalvelu.building-info-itest
  (:require [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [clojure.java.io :as io]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [sade.core :as sade]
            [sade.xml :as xml]
            [sade.common-reader :as cr]
            [mount.core :as mount])
  (:import [com.mongodb DuplicateKeyException]))


(def local-db-name (str "test_building_info_itest_" (sade/now)))

(mount/start #'mongo/connection)
(mongo/with-db local-db-name (fixture/apply-fixture "minimal"))

(facts "Building info cache"
       (against-background [(cr/get-xml anything anything anything anything)
                            => (-> "krysp/dev/building.xml"
                                   io/resource
                                   io/input-stream
                                   xml/parse)])
       (mongo/with-db local-db-name
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
                 (mongo/select-one :buildingCache {:propertId bad-property-id}) => nil?))))
