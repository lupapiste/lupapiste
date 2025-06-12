(ns lupapalvelu.building-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.building :as building]
            [monger.operators :refer :all]
            [sade.util :as util]))

(def buildings [{:description  "Talo A"
                 :localShortId "101"
                 :operationId  "321"
                 :buildingId   "123456001M"
                 :index        "1"
                 :created      "2013"
                 :localId      nil
                 :usage        "039 muut asuinkerrostalot"
                 :nationalId   "123456001M"
                 :area         "2000"
                 :propertyId   "12345678912"}])

(def new-doc {:id "123" :schema-info {:name "uusiRakennus"}})
(def exp-doc {:id "1234" :schema-info {:name "rakennuksen-laajentaminen" :op {:id "321"}}})
(def ryhti-new-doc {:id "456" :schema-info {:name "uusiRakennuskohde"}})
(def ryhti-exp-doc {:id "4567" :schema-info {:name "rakennuskohteenLaajentaminen" :op {:id "321"}}})
(def work-doc {:id "789" :schema-info {:name "maisematyo" :op "321"}})

(def test-docs [new-doc exp-doc ryhti-new-doc ryhti-exp-doc work-doc])

(def test-operation (-> buildings first :operationId))

(deftest document-buildingid-updates-for-operation-test
  (testing "Building ID updates are in correct form"
    (is (= {:documents.1.data.valtakunnallinenNumero.value "1234M"
            :documents.3.data.pysyva-rakennustunnus.value  "1234M"}
           (building/document-buildingid-updates-for-operation {:documents test-docs} "1234M" test-operation))))

  (testing "Multiple documents with same operation"
    (is (= {:documents.1.data.pysyva-rakennustunnus.value "1234M"
            :documents.2.data.pysyva-rakennustunnus.value "1234M"}
           (building/document-buildingid-updates-for-operation
             {:documents [(assoc-in exp-doc [:schema-info :op :id] "hello")
                          ryhti-exp-doc ryhti-exp-doc]}
             "1234M"
             test-operation))))

  (testing "Empty documents list returns nil"
    (is (nil? (building/document-buildingid-updates-for-operation {:documents []} "1234M" test-operation))))

  (testing "Invalid operation returns nil"
    (is (nil? (building/document-buildingid-updates-for-operation {:documents test-docs} "1234M" {:foo "bar"}))))

  (testing "Nil operation returns nil"
    (is (nil? (building/document-buildingid-updates-for-operation {:documents test-docs} "1234M" nil)))))

(deftest building-updates-test
  (testing "buildings array and document update OK"
    (is (= {$set {:buildings                                     buildings
                  :documents.1.data.valtakunnallinenNumero.value "123456001M"
                  :documents.3.data.pysyva-rakennustunnus.value  "123456001M"}}
           (building/building-updates {:documents test-docs} 123 buildings))))

  (testing "no document updates if unknown operation in buildings"
    (let [first-tag-unknown (map #(assoc % :operationId "foobar") buildings)]
      (is (= {$set {:buildings first-tag-unknown}}
             (building/building-updates {:documents test-docs} 123 first-tag-unknown)))))

  (testing "nil buildings returns nil"
    (is (nil? (building/building-updates {:documents test-docs} 123 nil))))

  (testing "empty buildings returns nil"
    (is (nil? (building/building-updates {:documents test-docs} 123 [])))))

(defn make-task [schema-name building-key building-index & [national-id]]
  (cond-> (assoc-in {:schema-info {:name schema-name}}
                    [:data :rakennus (util/make-kw building-key) :rakennus
                     :jarjestysnumero :value] (str building-index))
    national-id (assoc-in [:data :rakennus (util/make-kw building-key) :rakennus
                           :valtakunnallinenNumero :value] national-id)))

(defn make-building [index]
  {:index       (str index)
   :operationId (str "op" index)})

(deftest review-buildings-national-id-updates-test
  (let [application {:buildings (concat buildings
                                        (map make-building (range 2 5)))
                     :tasks     [(make-task "foobar" 1 2)
                                 (make-task "task-katselmus" 2 2 "new-vtj-prt")
                                 (make-task "task-katselmus" 4 3)
                                 (make-task "task-katselmus" 5 2)]}]
    (testing "No such operation"
      (is (nil? (building/review-buildings-national-id-updates application "bad-operation" "vtj-prt" 1234))))

    (testing "The same building in multiple tasks"
      (is (= {:tasks.1.data.rakennus.2.rakennus.valtakunnallinenNumero {:value    "vtj-prt"
                                                                        :modified 1234}
              :tasks.3.data.rakennus.5.rakennus.valtakunnallinenNumero {:value    "vtj-prt"
                                                                        :modified 1234}}
             (building/review-buildings-national-id-updates application "op2" "vtj-prt" 1234))))

    (testing "No unnecessary updates"
      (is (= {:tasks.3.data.rakennus.5.rakennus.valtakunnallinenNumero {:value    "new-vtj-prt"
                                                                        :modified 4321}}
             (building/review-buildings-national-id-updates application "op2" "new-vtj-prt" 4321))))))

(def operation-id "890")

(def test-apartments {:0 {:jakokirjain     {:value "b" :modified 1581571852493}
                          :huoneistonumero {:value "154" :modified 1581571840154}
                          :porras          {:value "B" :modified 1581571840154}}

                      :1 {:jakokirjain     {:value "c" :modified 1581571852493}
                          :huoneistonumero {:value "154" :modified 1581571840154}
                          :porras          {:value "B" :modified 1581571840154}}

                      :2 {:jakokirjain     {:value nil :modified 1581571852493}
                          :huoneistonumero {:value "154" :modified 1581571840154}
                          :porras          {:value nil :modified 1581571840154}}

                      :3 {:jakokirjain     {:value "b" :modified 1581571852493}
                          :huoneistonumero {:value "155" :modified 1581571840154}
                          :porras          {:value "B" :modified 1581571840154}}

                      ;; Not updated because apartment-pht-updates has no match
                      :4 {:jakokirjain     {:value nil :modified 1581571852493}
                          :huoneistonumero {:value "156" :modified 1581571840154}
                          :porras          {:value nil :modified 1581571840154}}

                      ;; Not updated because has 2 matches in apartment-pht-updates with different pht
                      :5 {:jakokirjain     {:value "c" :modified 1581571852493}
                          :huoneistonumero {:value "157" :modified 1581571840154}
                          :porras          {:value "b" :modified 1581571840154}}

                      ;; Not updated because the value has not changed
                      :6 {:pysyvaHuoneistotunnus "000"
                          :jakokirjain           {:value "c" :modified 1581571852493}
                          :huoneistonumero       {:value "158" :modified 1581571840154}
                          :porras                {:value "b" :modified 1581571840154}}})

(def foo-doc {:id          "123"
              :created     1581571565542
              :schema-info {:id   "bar"
                            :name "kerrostalo-rivitalo"}
              :data        {:foo "foo"}})

(def new-apts-doc {:id          "456"
                   :created     1581571565542
                   :schema-info {:name    "uusiRakennus"
                                 :version 1
                                 :op      {:id          "890"
                                           :name        "kerrostalo-rivitalo"
                                           :description nil
                                           :created     1581571565542}}
                   :data        {:huoneistot test-apartments}})

(def ryhti-new-apts-doc {:id          "789"
                         :created     1581571565542
                         :schema-info {:name    "uusiRakennuskohde"
                                       :version 1
                                       :op      {:id          "900"
                                                 :name        "kerrostalo-rivitalo"
                                                 :description nil
                                                 :created     1581571565542}}
                         :data        {:rakennuskohteen-osa {:huoneistot test-apartments}}})

(def test-documents [foo-doc new-apts-doc ryhti-new-apts-doc])

(def apartment-pht-updates
  [{:porras "b" :huoneistonumero "154" :jakokirjain "b" :pysyvaHuoneistotunnus "567"}
   {:porras "b" :huoneistonumero "155" :jakokirjain "b" :pysyvaHuoneistotunnus "678"}
   {:porras "b" :huoneistonumero "154" :jakokirjain "c" :pysyvaHuoneistotunnus "789"}
   {:huoneistonumero "154" :pysyvaHuoneistotunnus "890"}
   {:porras "b" :huoneistonumero "157" :jakokirjain "c" :pysyvaHuoneistotunnus "345"}
   {:porras "b" :huoneistonumero "157" :jakokirjain "c" :pysyvaHuoneistotunnus "346"}
   {:porras "b" :huoneistonumero "158" :jakokirjain "c" :pysyvaHuoneistotunnus "000"}])

(deftest apartment-pht-updates-for-document-test
  (testing "pht-updates for standard document"
    (is (= {:documents.1.data.huoneistot.0.pysyvaHuoneistotunnus {:modified 123 :value "567"}
            :documents.1.data.huoneistot.1.pysyvaHuoneistotunnus {:modified 123 :value "789"}
            :documents.1.data.huoneistot.2.pysyvaHuoneistotunnus {:modified 123 :value "890"}
            :documents.1.data.huoneistot.3.pysyvaHuoneistotunnus {:modified 123 :value "678"}}
           (building/apartment-pht-updates-for-document test-documents apartment-pht-updates 123 operation-id))))

  (testing "pht-updates for Ryhti document"
    (is (= {:documents.2.data.rakennuskohteen-osa.huoneistot.0.pysyvaHuoneistotunnus {:modified 123 :value "567"}
            :documents.2.data.rakennuskohteen-osa.huoneistot.1.pysyvaHuoneistotunnus {:modified 123 :value "789"}
            :documents.2.data.rakennuskohteen-osa.huoneistot.2.pysyvaHuoneistotunnus {:modified 123 :value "890"}
            :documents.2.data.rakennuskohteen-osa.huoneistot.3.pysyvaHuoneistotunnus {:modified 123 :value "678"}}
           (building/apartment-pht-updates-for-document test-documents apartment-pht-updates 123 "900"))))

  (testing "no matching document returns nil"
    (is (nil? (building/apartment-pht-updates-for-document test-documents apartment-pht-updates 123 "nonexistent"))))

  (testing "empty apartment updates returns empty map"
    (is (= {} (building/apartment-pht-updates-for-document test-documents [] 123 operation-id)))))

(def test-buildings
  [{:description "Talo A Toinen selite"
    :buildingId  "123456001M"
    :apartments  [{:jakokirjain     "b"
                   :huoneistonumero "154"
                   :porras          "b"}

                  {:jakokirjain     "c"
                   :huoneistonumero "154"
                   :porras          "b"}

                  {:jakokirjain     nil
                   :huoneistonumero "154"
                   :porras          nil}

                  {:jakokirjain     "b"
                   :huoneistonumero "155"
                   :porras          "b"}

                  ;; Not updated because apartment-pht-updates has no match
                  {:jakokirjain     nil
                   :huoneistonumero "156"
                   :porras          nil}

                  ;; Not updated because apartment-pht-updates has 2 matches with different pht
                  {:jakokirjain     "c"
                   :huoneistonumero "157"
                   :porras          "b"}]
    :usage       "039 muut asuinkerrostalot"
    :nationalId  "123456001M"
    :area        "2000"
    :propertyId  "18601234567890"
    :operationId "890"}
   {:apartments  []
    :operationId nil}
   {:description   "3"
    :localShortId  "103"
    :building-type nil
    :buildingId    "103"
    :apartments    []
    :operationId   nil}])

(deftest apartment-pht-updates-for-building-test
  (testing "pht-updates for buildings are correct"
    (is (= {:buildings.0.apartments.0.pysyvaHuoneistotunnus "567"
            :buildings.0.apartments.1.pysyvaHuoneistotunnus "789"
            :buildings.0.apartments.2.pysyvaHuoneistotunnus "890"
            :buildings.0.apartments.3.pysyvaHuoneistotunnus "678"}
           (building/apartment-pht-updates-for-building test-buildings apartment-pht-updates operation-id))))

  (testing "no matching building returns nil"
    (is (nil? (building/apartment-pht-updates-for-building test-buildings apartment-pht-updates "nonexistent"))))

  (testing "empty buildings returns nil"
    (is (nil? (building/apartment-pht-updates-for-building [] apartment-pht-updates operation-id)))))

(deftest find-correct-pht-value-from-updates-test
  (testing "finds exact match"
    (is (= "567"
           (building/find-correct-pht-value-from-updates
             {:porras "b" :huoneistonumero "154" :jakokirjain "b"}
             apartment-pht-updates))))

  (testing "nil for blank huoneistonumero"
    (is (nil? (building/find-correct-pht-value-from-updates
                {:porras "b" :huoneistonumero "" :jakokirjain "b"}
                apartment-pht-updates))))

  (testing "nil for nil huoneistonumero"
    (is (nil? (building/find-correct-pht-value-from-updates
                {:porras "b" :huoneistonumero nil :jakokirjain "b"}
                apartment-pht-updates))))

  (testing "nil when multiple conflicting pht values"
    (is (nil? (building/find-correct-pht-value-from-updates
                {:porras "b" :huoneistonumero "157" :jakokirjain "c"}
                apartment-pht-updates))))

  (testing "case insensitive matching"
    (is (= "567"
           (building/find-correct-pht-value-from-updates
             {:porras "B" :huoneistonumero "154" :jakokirjain "B"}
             apartment-pht-updates)))))
