(ns lupapalvelu.integrations.state-change-json-test
  (:require [lupapalvelu.integrations.state-change :as mjson]
            [lupapalvelu.state-machine :as sm]
            [midje.sweet :refer :all]
            [sade.coordinate :as coord]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]))

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

(def hakija
  {:id "5a4b30aa88c5cbc9a3a61784"
   :schema-info {:name "hakija-r"
                 :subtype "hakija"
                 :version 1}
   :created 1505822832171
   :data {:_selected {:value "henkilo"}
          :henkilo {:userId {:value nil}
                    :henkilotiedot {:etunimi {:value "Pasi"}
                                    :sukunimi {:value "Kuikka"}
                                    :hetu {:value "010107A945R"}
                                    :turvakieltoKytkin {:value false}}
                    :osoite {:katu {:value ""}
                             :postinumero {:value ""}
                             :postitoimipaikannimi {:value ""}
                             :maa {:value "FIN"}}
                    :yhteystiedot {:puhelin {:value ""}
                                   :email {:value ""}}
                    :kytkimet {:suoramarkkinointilupa {:value false}
                               :vainsahkoinenAsiointiKytkin {:value true}}}
          :yritys {:companyId {:value ""}
                   :yritysnimi {:value "Testiyritys Oy"}
                   :liikeJaYhteisoTunnus {:value "2750335-3"}
                   :osoite {:katu {:value ""}
                            :postinumero {:value ""}
                            :postitoimipaikannimi {:value ""}
                            :maa {:value "FIN"}}
                   :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}
                                                   :sukunimi {:value ""}
                                                   :turvakieltoKytkin {:value false}}
                                   :yhteystiedot {:puhelin {:value ""}
                                                  :email {:value ""}}
                                   :kytkimet {:suoramarkkinointilupa {:value false}
                                              :vainsahkoinenAsiointiKytkin {:value true}}}}}})

(defn make-building [& kvs]
  (apply hash-map kvs))

(def buildings [(make-building :nationalId "nationalId1"
                               :localId "local1")
                (make-building :nationalId "nationalId2"
                               :localId "local2"
                               :localShortId "short2")
                (make-building :localId "local3"
                               :localShortId "short3")
                (make-building :nationalId "nationalId4"
                               :localShortId "short4")])

(defn app-with-docs [documents]
  (let [location [444444.0 6666666.0]
        wgs      (coord/convert "EPSG:3067" "WGS84" 5 location)]
    {:id               (ssg/generate ssc/ApplicationId)
     :buildings        buildings
     :propertyId       "29703401070010"
     :infoRequest      false
     :permitType       "R"
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

(defn make-building-app
  ([selected-value vtj-prt]
   (-> [{:schema-info {:name "rakennuksen-muuttaminen"
                       :op   {:id "57603a99edf02d7047774554"}}
         :data        {:buildingId             {:value selected-value}
                       :valtakunnallinenNumero {:value vtj-prt}
                       :tunnus                 {:value "100840657D"}
                       :id                     "1321321"}
         :id          "foo-old"}]
       app-with-docs
       (assoc :state initial-state)))
  ([selected-value] (make-building-app selected-value nil)))

(fact "get-op-data"
  (mjson/get-op-data {:id "57603a99edf02d7047774554" :name "pientalo-laaj"})
  => {:displayName (select-keys {:en "Extension of a detached house (detached house with no more than two dwellings)"
                                 :fi "Asuinpientalon laajentaminen (enint\u00e4\u00e4n kaksiasuntoinen erillispientalo)"
                                 :sv "Tillbyggande av bostadshus (h\u00f6gst ett frist\u00e5ende sm\u00e5hus f\u00f6r tv\u00e5 bost\u00e4der)"}
                                lupapalvelu.i18n/supported-langs)
      :id "57603a99edf02d7047774554"
      :name "pientalo-laaj"}
  (mjson/get-op-data {:id "57603a99edf02d7047774554" :name "pientalo-laaj" :description nil})
  => (contains {:id "57603a99edf02d7047774554" :displayName map? :name "pientalo-laaj" :description nil})
  (mjson/get-op-data {:id "57603a99edf02d7047774554" :name "varasto-tms" :description "Sauna"})
  => (contains {:id "57603a99edf02d7047774554" :displayName map? :name "varasto-tms" :description "Sauna"}))

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

     (resolve-building
       ([vtj-prt data]
        (fact {:midje/description (str "Resolved VTJ-PRT is " vtj-prt)}
          (-> data :operations first :building)
          => (cond-> {:new false}
               vtj-prt (assoc :buildingId vtj-prt))))
       ([data] (resolve-building nil data)))

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
        (run-test (assoc app :state initial-state :permitType "P") building-not-present)))
    (facts "Resolve building"
      (fact "Document data"
        (run-test (make-building-app "some-id" "doc-vtj-prt")
                  (partial resolve-building "doc-vtj-prt")))
      (fact "Building by nationalId"
        (run-test (make-building-app "nationalId1" " ")
                  (partial resolve-building "nationalId1"))
        (run-test (make-building-app "nationalId2" "")
                  (partial resolve-building "nationalId2"))
        (run-test (make-building-app "nationalId4")
                  (partial resolve-building "nationalId4")))
      (fact "Building by localId"
        (run-test (make-building-app "local1" " ")
                  (partial resolve-building "nationalId1"))
        (run-test (make-building-app "local2" "")
                  (partial resolve-building "nationalId2")))
      (fact "Building by localShortId"
        (run-test (make-building-app "short2" "")
                  (partial resolve-building "nationalId2"))
        (run-test (make-building-app "short4")
                  (partial resolve-building "nationalId4")))
      (fact "Not resolved"
        (doseq [v ["bad4" "local3" "short3"]]
          (run-test (make-building-app v) resolve-building))))))

(fact "permitSubtype"
  (fact "if no key, nil"
    (-> (assoc (app-with-docs [new-building-data]) :state "open")
        (mjson/state-change-data "submitted")) => (contains {:permitSubtype nil}))
  (fact "blank as nil"
    (-> (assoc (app-with-docs [new-building-data]) :state "open" :permitSubtype "")
        (mjson/state-change-data "submitted")) => (contains {:permitSubtype nil}))
  (fact "ok"
    (-> (assoc (app-with-docs [new-building-data]) :state "open" :permitSubtype "tyolupa")
        (mjson/state-change-data "submitted")) => (contains {:permitSubtype "tyolupa"})))

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
    (facts "tyonjohtajan nimeaminen"
      (let [tyonjohtaja (last operations)]
        (fact "does not contain :building"
          (contains? tyonjohtaja :building) => false)
        (fact "does not contain :structure"
          (contains? tyonjohtaja :structure) => false)))))

(fact "applicant data"
  (let [app (app-with-docs [hakija])
        app-with-ops (assoc app :state "open")
        change-report (mjson/state-change-data app-with-ops "submitted")
        applicant (:applicantInfo change-report)]
    (facts "applicant data"
      (fact "applicant is a person"
        (:type applicant) => "person"
        (:firstName applicant) => "Pasi"
        (:lastName applicant) => "Kuikka"
        (:personId applicant) => "010107A945R"
        (:companyId applicant) => nil
        (:companyName applicant) => nil))))

(fact "applicant data company"
  (let [app (app-with-docs [(assoc-in hakija [:data :_selected :value] "yritys")])
        app-with-ops (assoc app :state "open")
        change-report (mjson/state-change-data app-with-ops "submitted")
        applicant (:applicantInfo change-report)]
    (facts "applicant data"
      (fact "applicant is company"
        (:type applicant) => "company"
        (:companyId applicant) => "2750335-3"
        (:companyName applicant) => "Testiyritys Oy"
        (:firstName applicant) => nil
        (:lastName applicant) => nil
        (:personId applicant) => nil))))
