(ns lupapalvelu.integrations.matti-state-change-json
  (:require [midje.sweet :refer :all]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.integrations.matti :as mjson]
            [sade.coordinate :as coord]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

(def test-doc {:schema-info {:name "rakennuksen-muuttaminen"
                             :op {:id "57603a99edf02d7047774554"}}
               :data {:buildingId {:value "100840657D"
                                   :sourceValue "100840657D"}
                      :valtakunnallinenNumero {:value "100840657D"
                                               :modified 1491470000000
                                               :source "krysp"
                                               :sourceValue "100840657D"}
                      :tunnus {:value "100840657D"}
                      :id "1321321"}})

(facts "state-changes for R app"
  (facts "existing buildings"
    (let [location [444444.0 6666666.0]
          wgs (coord/convert "EPSG:3067" "WGS84" 5 location)
          app {:id               (ssg/generate ssc/ApplicationId)
               :propertyId       "29703401070010"
               :infoRequest      false :permitType "R"
               :primaryOperation {:name "pientalo-laaj" :id (ssg/generate ssc/ObjectIdStr)}
               :address          "address"
               :municipality     "123"
               :applicant        "Tero Testaaja"
               :location         location
               :location-wgs84   wgs
               :documents        [test-doc]}
          test-fn (fn [app]
                    (when-let [new-state (sm/next-state app)]
                      (fact {:midje/description (str "data is valid for - " (name new-state))}
                        (mjson/state-change-data app new-state) => map?)
                      (recur (assoc app :state (name new-state)))))]
      (test-fn (assoc app :state (name (first (sm/application-state-seq app))))))))
