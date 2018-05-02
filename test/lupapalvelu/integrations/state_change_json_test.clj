(ns lupapalvelu.integrations.state-change-json-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.integrations.state-change :as mjson]
            [sade.coordinate :as coord]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

(def old-building-test-doc
  {:schema-info {:name "rakennuksen-muuttaminen"
                 :op {:id "57603a99edf02d7047774554"}}
   :data {:buildingId {:value "100840657D"
                       :sourceValue "100840657D"}
          :valtakunnallinenNumero {:value "100840657D"
                                   :modified 1491470000000
                                   :source "krysp"
                                   :sourceValue "100840657D"}
          :tunnus {:value "100840657D"}
          :id "1321321"}
   :id "foo-old"})

(def old-building-manuaalinen-rakennusnumero                ; VTJ-PRT is not known and user has inpute buildingId manually
  {:schema-info {:name "rakennuksen-muuttaminen"
                 :op {:id "57603a99edf02d7047774554"}}
   :data {:buildingId {:value "other"
                       :sourceValue "100840657D"}
          :manuaalinen_rakennusnro {:value "123"}
          :valtakunnallinenNumero {:value "100840657D"
                                   :modified 1491470000000
                                   :source "krysp"
                                   :sourceValue "100840657D"}
          :tunnus {:value "100840657D"}
          :id "1321321"}
   :id "foo-manual"})

(def new-building-data
  {:id "foo-new"
   :schema-info {:op {:id "57603a99edf02d7047774554"
                      :name "kerrostalo-rivitalo",
                      :description nil,
                      :created 1505822832171}
                 :name "uusiRakennus"}
   :data {:rakennuksenOmistajat {:0 {}}
          :varusteet {}
          :verkostoliittymat {}
          :huoneistot {:0 {}}
          :tunnus {:value "Tunnus"}
          :mitat {}
          :valtakunnallinenNumero {:value "123"}}})

(def new-structure-data
  {:id "foo-structure"
   :schema-info {:op {:id "57603a99edf02d7047774554"
                      :name "Aita"
                      :description nil
                      :created 1505822832171}
                 :name "kaupunkikuvatoimenpide-ei-tunnusta"}
   :data {:kokonaisala {:value "4"}
          :kayttotarkoitus {:value "Aallonmurtaja"}
          :kuvaus {:value "text"}}})

(defn app-with-docs [documents]
  (let [location [444444.0 6666666.0]
        wgs (coord/convert "EPSG:3067" "WGS84" 5 location)]
    {:id               (ssg/generate ssc/ApplicationId)
     :propertyId       "29703401070010"
     :infoRequest      false
     :permitType "R"
     :primaryOperation {:name "pientalo-laaj" :id "57603a99edf02d7047774554"}
     :address          "address"
     :municipality     "123"
     :applicant        "Tero Testaaja"
     :location         location
     :location-wgs84   wgs
     :documents        documents}))

(def initial-state (-> (app-with-docs [new-building-data])
                       (sm/application-state-seq)
                       (first)
                       (name)))

(facts "state-changes for R app"
  (letfn
    [(valid-fn
       [data]
       (fact {:midje/description (str "data is valid for - " (:state data))}
         data => map?
         (:messageType data) => "state-change"))
     (existing-building-test
       [data]
       (fact "has buildingId and new:false"
         (-> data :operations first :building) => (just [[:new false]
                                                         [:buildingId
                                                          (get-in old-building-test-doc
                                                                  [:data :valtakunnallinenNumero :value])]])))
     (new-building-test
       [data]
       (fact "building has new:true"
         (-> data :operations first :building) => (just [[:new true]])))
     (manual-building-id-test
       [data]
       (fact "manual building-id as id"
         (-> data :operations first :building) => (just [[:new false]
                                                         [:buildingId
                                                          (get-in old-building-manuaalinen-rakennusnumero
                                                                  [:data :manuaalinen_rakennusnro :value])]])))
     (building-not-present [data]
       (fact "building key not present"
         (not-any? #(contains? (set (keys %)) :building) (:operations data)) => true))

     (run-test
       [app test-fn]
       (when-let [new-state (sm/next-state app)]
         (let [state-change-data (mjson/state-change-data app new-state)]
           (valid-fn state-change-data)
           (test-fn state-change-data))
         (recur (assoc app :state (name new-state)) test-fn)))]

    (facts "existing buildings"
      (let [app (app-with-docs [old-building-test-doc])]
        (run-test (assoc app :state initial-state) existing-building-test)))
    (facts "new buildings"
      (let [app (app-with-docs [new-building-data])]
        (run-test (assoc app :state initial-state) new-building-test )))
    (fact "manuaal building id"
      (let [app (app-with-docs [old-building-manuaalinen-rakennusnumero])]
        (run-test (assoc app :state initial-state) manual-building-id-test)))
    (fact "permitType P"
      (let [app (app-with-docs [new-building-data])]
        (run-test (assoc app :state initial-state :permitType "P") building-not-present)))))

(fact "permitSubtype"
  (fact "if no key, nil"
    (-> (assoc (app-with-docs [new-building-data]) :state "open")
        (mjson/state-change-data "submitted")) => (contains {:permitSubtype nil}))
  (fact "blank as nil"
    (-> (assoc (app-with-docs [new-building-data]) :state "open" :permitSubtype "")
        (mjson/state-change-data "submitted")) => (contains {:permitSubtype nil}))
  (fact "ok"
    (-> (assoc (app-with-docs [new-building-data]) :state "open" :permitSubtype "testi")
        (mjson/state-change-data "submitted")) => (contains {:permitSubtype "testi"})))

(fact "presence of :building and :structure keys"
  (let [state-change-data (assoc (app-with-docs [new-structure-data]) :state "open" :primaryOperation {:name "aita" :id "57603a99edf02d7047774554"})
        change-report (mjson/state-change-data state-change-data "submitted")
        operation-keys (-> change-report :operations first keys set)]
    (fact "message for structure does not contain :building flag"
      (contains? operation-keys :building) => false)
    (fact "message for structure does contain :structure flag"
      (contains? operation-keys :structure) => true)))

(fact "multiple operations in single application"
  (let [app (app-with-docs [new-building-data])
        app-with-ops (assoc app :state "open"
                                :primaryOperation {:name "pientalo" :id "57603a99edf02d7047774554"}
                                :secondaryOperations [{:name "mainoslaite" :id "57603a99edf02d7047774554"}
                                                      {:name "tyonjohtajan-nimeaminen-v2" :id "57603a99edf02d7047774554"}])
        change-report (mjson/state-change-data app-with-ops "submitted")
        operations (:operations change-report)]
    (facts "primary operation (pientalo)"
      (let [pientalo (first operations)]
        (fact "contains the flag :building"
          (contains? pientalo :building) => true)
        (fact "does not contain the flag :structure"
          (contains? pientalo :structure) => false)))
    (facts "mainoslaite"
      (let [mainoslaite (second operations)]
        (fact "does not contain the flag :building"
          (contains? mainoslaite :building) => false)
        (fact "contains the flag :structure"
          (contains? mainoslaite :structure) => true)))
    (facts "työnjohtajan nimeäminen"
      (let [tyonjohtaja (last operations)]
        (fact "does not contain :building"
          (contains? tyonjohtaja :building) => false)
        (fact "does not contain :structure"
          (contains? tyonjohtaja :structure) => false)))))
