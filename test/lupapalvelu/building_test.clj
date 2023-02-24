(ns lupapalvelu.building-test
  (:require [lupapalvelu.building :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.document.tools :as tools]))

(def buildings [{:description "Talo A",
                 :localShortId "101",
                 :operationId "321"
                 :buildingId "123456001M",
                 :index "1",
                 :created "2013",
                 :localId nil,
                 :usage "039 muut asuinkerrostalot",
                 :nationalId "123456001M",
                 :area "2000",
                 :propertyId "12345678912"}])

(def test-docs [{:id "123" :schema-info {:name "testi1"}}
                {:id "1234" :schema-info {:name "testi2" :op {:id "321"}}}])

(def test-operation (-> buildings first :operationId))

(against-background
  [(lupapalvelu.document.schemas/get-schema anything) => {:body [{:name "valtakunnallinenNumero"}]}]

  (facts "Building ID updates are in correct form"
    (document-buildingid-updates-for-operation {:documents test-docs} "1234M" test-operation) => {"documents.1.data.valtakunnallinenNumero.value" "1234M"}
    (document-buildingid-updates-for-operation {:documents []} "1234M" test-operation) => {}
    (document-buildingid-updates-for-operation {:documents test-docs} "1234M" {:foo "bar"}) => {}
    (document-buildingid-updates-for-operation {:documents test-docs} "1234M" nil) => {}))

(fact "No update if schema doesn't have valtakunnallinenNumero"
  (document-buildingid-updates-for-operation {:documents test-docs} "1234M" test-operation) => {}
  (provided
    (lupapalvelu.document.schemas/get-schema anything) => {:body [{:name "foobar"}]}))

(against-background
  [(lupapalvelu.document.schemas/get-schema anything) => {:body [{:name "valtakunnallinenNumero"}]}]

  (facts "Building and document updates together"
    (fact "buildings array and document update OK"
      (building-updates {:documents test-docs} 123  buildings) => {$set {:buildings buildings, "documents.1.data.valtakunnallinenNumero.value" "123456001M"}})
    (let [first-tag-unknown (map #(assoc % :operationId "foobar") buildings)]
      (fact "no document updates if unknown operation in buildings"
        (building-updates {:documents test-docs} 123 first-tag-unknown) => {$set {:buildings first-tag-unknown}}))))

(defn make-task [schema-name building-key building-index & [national-id]]
  (cond-> (assoc-in {:schema-info {:name schema-name}}
                    [:data :rakennus (util/make-kw building-key) :rakennus
                     :jarjestysnumero :value] (str building-index))
    national-id (assoc-in [:data :rakennus (util/make-kw building-key) :rakennus
                           :valtakunnallinenNumero :value] national-id)))

(defn make-building [index]
  {:index       (str index)
   :operationId (str "op" index)})

(facts "review-buildings-national-id-updates"
  (let [application {:buildings (concat buildings
                                        (map make-building (range 2 5)))
                     :tasks     [(make-task "foobar" 1 2)
                                 (make-task "task-katselmus" 2 2 "new-vtj-prt")
                                 (make-task "task-katselmus" 4 3)
                                 (make-task "task-katselmus" 5 2)]}]
    (fact "No such operation"
      (review-buildings-national-id-updates application "bad-operation" "vtj-prt" 1234)
      => nil)
    (fact "The same building in multiple tasks"
      (review-buildings-national-id-updates application "op2" "vtj-prt" 1234)
      => {:tasks.1.data.rakennus.2.rakennus.valtakunnallinenNumero {:value    "vtj-prt"
                                                                    :modified 1234}
          :tasks.3.data.rakennus.5.rakennus.valtakunnallinenNumero {:value    "vtj-prt"
                                                                    :modified 1234}})
    (fact "No unnecessary updates"
      (review-buildings-national-id-updates application "op2" "new-vtj-prt" 4321)
      => {:tasks.3.data.rakennus.5.rakennus.valtakunnallinenNumero {:value    "new-vtj-prt"
                                                                    :modified 4321}})))

(def operation-id
  "890")

(def test-documents
  [{:id "123",
    :created 1581571565542,
    :schema-info {:id "bar"
                  :name "kerrostalo-rivitalo"},
    :data {:foo "foo"}}
   {:id "456",
    :created 1581571565542,
    :schema-info {:name "uusiRakennus",
                  :version 1,
                  :op {:id "890",
                       :name "kerrostalo-rivitalo",
                       :description nil,
                       :created 1581571565542}},
    :data {:huoneistot {:0 {:jakokirjain {:value "b", :modified 1581571852493},
                            :huoneistonumero {:value "154", :modified 1581571840154}
                            :porras {:value "B", :modified 1581571840154}}

                        :1 {:jakokirjain {:value "c", :modified 1581571852493}
                            :huoneistonumero {:value "154", :modified 1581571840154}
                            :porras {:value "B", :modified 1581571840154}}

                        :2 {:jakokirjain {:value nil, :modified 1581571852493}
                            :huoneistonumero {:value "154", :modified 1581571840154}
                            :porras {:value nil, :modified 1581571840154}}

                        :3 {:jakokirjain {:value "b", :modified 1581571852493}
                            :huoneistonumero {:value "155", :modified 1581571840154}
                            :porras {:value "B", :modified 1581571840154}}

                        ;;Not updated because apartment-pht-updates has no match
                        :4 {:jakokirjain {:value nil, :modified 1581571852493}
                            :huoneistonumero {:value "156", :modified 1581571840154}
                            :porras {:value nil, :modified 1581571840154}}

                        ;;Not updated because has 2 matches in apartment-pht-updates with different pht
                        :5 {:jakokirjain {:value "c", :modified 1581571852493}
                            :huoneistonumero {:value "157", :modified 1581571840154}
                            :porras {:value "b", :modified 1581571840154}}

                        ;;Not updated because the value has not changed
                        :6 {:pysyvaHuoneistotunnus "000"
                            :jakokirjain {:value "c", :modified 1581571852493}
                            :huoneistonumero {:value "158", :modified 1581571840154}
                            :porras {:value "b", :modified 1581571840154}}}}}])

(def apartment-pht-updates
  [{:porras "b", :huoneistonumero "154", :jakokirjain "b", :pysyvaHuoneistotunnus "567"}
   {:porras "b", :huoneistonumero "155", :jakokirjain "b", :pysyvaHuoneistotunnus "678"}
   {:porras "b", :huoneistonumero "154", :jakokirjain "c", :pysyvaHuoneistotunnus "789"}
   {:huoneistonumero "154", :pysyvaHuoneistotunnus "890"}
   {:porras "b", :huoneistonumero "157", :jakokirjain "c", :pysyvaHuoneistotunnus "345"}
   {:porras "b", :huoneistonumero "157", :jakokirjain "c", :pysyvaHuoneistotunnus "346"}
   {:porras "b", :huoneistonumero "158", :jakokirjain "c", :pysyvaHuoneistotunnus "000"}])

(fact "pht-updates for docs are correct"
  (apartment-pht-updates-for-document test-documents apartment-pht-updates 123 operation-id)
  => {:documents.1.data.huoneistot.0.pysyvaHuoneistotunnus {:modified 123 :value "567"}
      :documents.1.data.huoneistot.1.pysyvaHuoneistotunnus {:modified 123 :value "789"}
      :documents.1.data.huoneistot.2.pysyvaHuoneistotunnus {:modified 123 :value "890"}
      :documents.1.data.huoneistot.3.pysyvaHuoneistotunnus {:modified 123 :value "678"}})

(def test-buildings
  [{:description "Talo A, Toinen selite"
    :buildingId "123456001M"
    :apartments [{:jakokirjain "b"
                  :huoneistonumero "154"
                  :porras "b"}

                 {:jakokirjain "c"
                  :huoneistonumero "154"
                  :porras "b"}

                 {:jakokirjain nil
                  :huoneistonumero "154"
                  :porras nil}

                 {:jakokirjain "b"
                  :huoneistonumero "155"
                  :porras "b"}

                 ;;Not updated because apartment-pht-updates has no match
                 {:jakokirjain nil
                  :huoneistonumero "156"
                  :porras nil}

                 ;;Not updated because apartment-pht-updates has 2 matches with different pht
                 {:jakokirjain "c"
                  :huoneistonumero "157"
                  :porras "b"}]
    :usage "039 muut asuinkerrostalot"
    :nationalId "123456001M"
    :area "2000"
    :propertyId "18601234567890"
    :operationId "890"}
   {:apartments []
    :operationId nil}
   {:description "3"
    :localShortId "103"
    :building-type nil
    :buildingId "103"
    :apartments []
    :operationId nil}])

(fact "pht-updates for buildings are correct"
  (apartment-pht-updates-for-building test-buildings apartment-pht-updates operation-id)
  => {:buildings.0.apartments.0.pysyvaHuoneistotunnus "567"
      :buildings.0.apartments.1.pysyvaHuoneistotunnus "789"
      :buildings.0.apartments.2.pysyvaHuoneistotunnus "890"
      :buildings.0.apartments.3.pysyvaHuoneistotunnus "678"})

