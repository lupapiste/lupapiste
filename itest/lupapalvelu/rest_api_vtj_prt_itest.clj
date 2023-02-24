(ns lupapalvelu.rest-api-vtj-prt-itest
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer [give-legacy-verdict]]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]
            [sade.coordinate :as coord]))

(apply-remote-minimal)

(def application (create-application sonja))

(def application-id (:id application))

(def uusi-rakennus-doc-id (:id (domain/get-document-by-name application "uusiRakennus")))

(fact "update apartments"
  (command sonja :update-doc :id application-id :doc uusi-rakennus-doc-id :collection "documents"
           :updates [["huoneistot.0.jakokirjain", "a"]
                     ["huoneistot.0.huoneistonumero", "153"]
                     ["huoneistot.0.porras", "b"]
                     ["huoneistot.1.jakokirjain", "c"]
                     ["huoneistot.1.huoneistonumero", "634"]
                     ["huoneistot.1.porras", "d"]
                     ["huoneistot.2.jakokirjain", "c"]
                     ["huoneistot.2.huoneistonumero", "644"]
                     ["huoneistot.2.porras", "d"]
                     ["huoneistot.3.jakokirjain", "c"]
                     ["huoneistot.3.huoneistonumero", "650"]
                     ["huoneistot.3.porras", "d"]]) => ok?)

(fact "submit and approve application"
  (command sonja :submit-application :id application-id)
  (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon")
  (command sonja :approve-application :id application-id :lang "fi") => ok?)

(def application (query-application sonja application-id))

(def document (:id (domain/get-document-by-name application "uusiRakennus")))

(def document (util/find-first (comp #{"uusiRakennus"} :name :schema-info) (:documents application)))

(def location-map (ssg/generate ssc/Location))
(def location-wgs84 (let [[x y] (coord/convert "EPSG:3067" "WGS84" 5 [(:x location-map) (:y location-map)])]
                      {:x x :y y}))

(def operation-id (get-in document [:schema-info :op :id]))

(fact "no nationalId set by default"
  (get-in document [:data :valtakunnallinenNumero :value]) => "")

(facts "sipoo backend user updates building with vtj-prt, location and pht"
  (fact "without location invalid"
    (api-update-building-data-call (:id application) {:form-params {:operationId operation-id
                                                                    :nationalBuildingId "1234567892"}
                                                      :content-type :json
                                                      :as :json
                                                      :basic-auth ["sipoo-r-backend" "sipoo"]}) => http400?)
  (fact "updated succesfully"
    (api-update-building-data-call (:id application) {:form-params {:operationId operation-id
                                                                    :nationalBuildingId "1234567892"
                                                                    :location location-map
                                                                    :apartmentsData [{:stairway            "b"
                                                                                      :flatNumber          "153"
                                                                                      :splittingLetter     "a"
                                                                                      :permanentFlatNumber "8790"}

                                                                                     {:stairway            "d"
                                                                                      :flatNumber          "634"
                                                                                      :splittingLetter     "c"
                                                                                      :permanentFlatNumber "9450"}

                                                                                     {:stairway            "d"
                                                                                      :flatNumber          "650"
                                                                                      :splittingLetter     "c"}

                                                                                     ;has no matching apartment on document
                                                                                     {:stairway            "e"
                                                                                      :flatNumber          "687"
                                                                                      :splittingLetter     "f"
                                                                                      :permanentFlatNumber "9807"}

                                                                                     ;same matching apartment but different pht => won't update the apartment
                                                                                     {:stairway            "c"
                                                                                      :flatNumber          "644"
                                                                                      :splittingLetter     "d"
                                                                                      :permanentFlatNumber "9999"}
                                                                                     {:stairway            "c"
                                                                                      :flatNumber          "644"
                                                                                      :splittingLetter     "d"
                                                                                      :permanentFlatNumber "9998"}]}
                                                      :content-type :json
                                                      :as :json
                                                      :basic-auth ["sipoo-r-backend" "sipoo"]}) => http200?)

  (let [document-data (->> (query sonja :application :id (:id application))
                           :application
                           :documents
                           (util/find-by-id (:id document))
                           :data)]
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

    (facts "building-updates have correct data"
      (let [building-updates (-> (get-by-id :applications (:id application))
                                 :body :data :building-updates)
            building (first building-updates)]
        (count building-updates) => 1
        (fact "VTJ-PRT" (:nationalBuildingId building) => "1234567892")
        (fact "location EPSG:3067" (:location building) => location-map)
        (fact "apartments-data" (:apartments building) => [{:huoneistonumero "153"
                                                            :jakokirjain "a"
                                                            :porras "b"
                                                            :pysyvaHuoneistotunnus "8790"}
                                                           {:huoneistonumero "634"
                                                            :jakokirjain "c"
                                                            :porras "d"
                                                            :pysyvaHuoneistotunnus "9450"}
                                                           {:huoneistonumero "650" :jakokirjain "c" :porras "d"}
                                                           {:huoneistonumero "687"
                                                            :jakokirjain "f"
                                                            :porras "e"
                                                            :pysyvaHuoneistotunnus "9807"}
                                                           {:huoneistonumero "644"
                                                            :jakokirjain "d"
                                                            :porras "c"
                                                            :pysyvaHuoneistotunnus "9999"}
                                                           {:huoneistonumero "644"
                                                            :jakokirjain "d"
                                                            :porras "c"
                                                            :pysyvaHuoneistotunnus "9998"}]) ))

    (facts "phts (pysyvat huoneistotunnukset) are updated for apartments"
      (fact ":0 apartment"
        (-> document-data :huoneistot :0 :pysyvaHuoneistotunnus :value) => "8790")
      (fact ":1 apartment"
        (-> document-data :huoneistot :1 :pysyvaHuoneistotunnus :value) => "9450")
      (fact ":2 apartment is not updated because it has 2 matching updates but those updates have different pht"
        (-> document-data :huoneistot :2 :pysyvaHuoneistotunnus :value) => nil))))

(fact "updating pht after verdict is given"
  (fact "Give verdict"
    (give-legacy-verdict sonja application-id) => string?)

  (fact "updated succesfully after verdict is given"
    (api-update-building-data-call (:id application) {:form-params {:operationId operation-id
                                                                    :nationalBuildingId "1234567892"
                                                                    :location location-map
                                                                    :apartmentsData [{:stairway            "d"
                                                                                      :flatNumber          "650"
                                                                                      :splittingLetter     "c"
                                                                                      :permanentFlatNumber "3950"}]}
                                                      :content-type :json
                                                      :as :json
                                                      :basic-auth ["sipoo-r-backend" "sipoo"]}) => http200?)
  (let [application (:application (query sonja :application :id (:id application)))]
    (fact "pht for building has been udpated"
      (-> application :buildings first :apartments (get 2))
      => {:huoneistonumero "650"
          :jakokirjain "c"
          :porras "d"
          :pysyvaHuoneistotunnus "3950"})

    (fact "apartment's pht on doc is also updated"
      (->> application
           :documents
           (util/find-by-id (:id document))
           :data :huoneistot :3
           :pysyvaHuoneistotunnus :value)
      => "3950")))

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
