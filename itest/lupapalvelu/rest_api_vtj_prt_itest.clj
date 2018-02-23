(ns lupapalvelu.rest-api-vtj-prt-itest
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer :all]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]
            [sade.coordinate :as coord]))

(apply-remote-minimal)

(def application (create-and-send-application sonja))

(def document (util/find-first (comp #{"uusiRakennus"} :name :schema-info) (:documents application)))

(def location-map (ssg/generate ssc/Location))
(def location-wgs84 (let [[x y] (coord/convert "EPSG:3067" "WGS84" 5 [(:x location-map) (:y location-map)])]
                      {:x x :y y}))

(def operation-id (get-in document [:schema-info :op :id]))

(fact "no nationalId set by default"
  (get-in document [:data :valtakunnallinenNumero :value]) => "")

(facts "sipoo backend user updates building with vtj-prt and location"
  (fact "without location invalid"
    (api-update-building-data-call (:id application) {:form-params {:operationId operation-id
                                                                    :nationalBuildingId "1234567892"}
                                                      :content-type :json
                                                      :as :json
                                                      :basic-auth ["sipoo-r-backend" "sipoo"]}) => http400?)
  (fact "updated succesfully"
    (api-update-building-data-call (:id application) {:form-params {:operationId operation-id
                                                                    :nationalBuildingId "1234567892"
                                                                    :location location-map}
                                                      :content-type :json
                                                      :as :json
                                                      :basic-auth ["sipoo-r-backend" "sipoo"]}) => http200?)

  (fact "vtj prt is updated"
    (->> (query sonja :application :id (:id application))
         :application
         :documents
         (util/find-by-id (:id document))
         :data
         :valtakunnallinenNumero
         :value) => "1234567892")

  (fact "integration message is added"
        (->(integration-messages (:id application))
           last
           (select-keys [:messageType :status]))
        => {:messageType "update-building-data" :status "processed"})

  #_(facts "building array has correct data"                ; TODO fix in LPK-3598
    (let [{:keys [buildings]} (query sonja :application :id (:id application))
          building (first buildings)]
     (count buildings) => 1
     (fact "VTJ-PRT" (:nationalId building) => "1234567892")
     (fact "location EPSG:3067" (:location building) => location-map)
     (fact "location WGS84" (:location-wgs84 building) => location-wgs84))))

(facts "sipoo backend user try to update vtj-prt on nonexesting document"
  (fact "Sipoo Backend can access"
    (api-update-building-data-call (:id application) {:form-params {:operationId (:id document)
                                                                    :nationalBuildingId "123456780S"
                                                                    :location location-map}
                                                      :content-type :json
                                                      :as :json
                                                      :basic-auth ["sipoo-r-backend" "sipoo"]}) => http404?)

  (fact "vtj prt is unchanged"
    (->> (query sonja :application :id (:id application))
         :application
         :documents
         (util/find-by-id (:id document))
         :data
         :valtakunnallinenNumero
         :value) => "1234567892"))

(fact "invalid vtj-prt"
  (fact "request fails with invalid parameter"
    (api-update-building-data-call (:id application) {:form-params {:operationId operation-id
                                                                    :nationalBuildingId "1234567891"
                                                                    :location location-map}
                                                      :content-type :json
                                                      :as :json
                                                      :basic-auth ["sipoo-r-backend" "sipoo"]})
    => http400?)

  (fact "vtj prt is unchanged"
    (->> (query sonja :application :id (:id application))
         :application
         :documents
         (util/find-by-id (:id document))
         :data
         :valtakunnallinenNumero
         :value) => "1234567892"))

(facts "application is not visible for jarvenpaa user"
  (fact "request fails for jarvenpaa"
    (api-update-building-data-call (:id application) {:form-params {:operationId operation-id
                                                                    :nationalBuildingId "123456780S"
                                                                    :location location-map}
                                                      :content-type :json
                                                      :as :json
                                                      :basic-auth ["jarvenpaa-backend" "jarvenpaa"]})
    => http404?)

  (fact "vtj prt is unchanged"
    (->> (query sonja :application :id (:id application))
         :application
         :documents
         (util/find-by-id (:id document))
         :data
         :valtakunnallinenNumero
         :value) => "1234567892"))
