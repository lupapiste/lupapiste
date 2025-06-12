(ns lupapalvelu.asiointi.permanent-identifiers.document-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.asiointi.permanent-identifiers.document :as id-doc]
            [lupapalvelu.asiointi.permanent-identifiers.schema :as id-schema]))

(deftest path-matches-test
  (testing "happy path"
    (is (id-doc/path-matches? [] []))
    (is (id-doc/path-matches? [:foo] [:foo]))
    (is (id-doc/path-matches? [true] [:1]))
    (is (id-doc/path-matches? [:foo true] [:foo :1]))
    (is (id-doc/path-matches? [:foo true :bar] [:foo :2 :bar])))
  (testing "wrong length"
    (is (not (id-doc/path-matches? [] [:foo])))
    (is (not (id-doc/path-matches? [:foo] [:foo :bar])))
    (is (not (id-doc/path-matches? [true] [:1 :bar])))
    (is (not (id-doc/path-matches? [:foo true] [:foo :1 :bar])))
    (is (not (id-doc/path-matches? [:foo true :bar] [:foo :2 :bar :quuz]))))
  (testing "wrong content"
    (is (not (id-doc/path-matches? [:foo] [:bar])))
    (is (not (id-doc/path-matches? [:foo] [:0])))
    (is (not (id-doc/path-matches? [true] [:bar])))
    (is (not (id-doc/path-matches? [:foo true] [:foo :bar])))
    (is (not (id-doc/path-matches? [:foo true] [:0 :bar :1])))))

(deftest document-paths-test
  (let [fields+path-specs (get id-schema/document-path-specs "uusiRakennuskohde")
        ;; document with also some irrelevant data
        document          {:data {:kuvaus                {:value "foobar"}
                                  :pysyva-rakennustunnus {:value ""}
                                  :rakennuskohteen-osa   {:huoneistot {:0 {:jakokirjain           {:value "a"}
                                                                           :pysyvaHuoneistotunnus {:value ""}
                                                                           :ryhti                 {:apartmentKey {:value "B1-A1"}}}
                                                                       :1 {:jakokirjain           {:value "b"}
                                                                           :pysyvaHuoneistotunnus {:value ""}
                                                                           :ryhti                 {:apartmentKey {:value "B1-A2"}}}}}
                                  :ryhti                 {:buildingKey {:value "B1"}}}}]
    (is (=  {:apartmentKey          #{[:rakennuskohteen-osa :huoneistot :1 :ryhti :apartmentKey]
                                      [:rakennuskohteen-osa :huoneistot :0 :ryhti :apartmentKey]}
             :buildingKey           #{[:ryhti :buildingKey]}
             :pysyva-rakennustunnus #{[:pysyva-rakennustunnus]}
             :pysyvaHuoneistotunnus #{[:rakennuskohteen-osa :huoneistot :0 :pysyvaHuoneistotunnus]
                                      [:rakennuskohteen-osa :huoneistot :1 :pysyvaHuoneistotunnus]}}
            (id-doc/document-paths document fields+path-specs)))))

(deftest parse-documents-test
  (let [application {:documents [{:id          "doc-1"
                                  :schema-info {:name      "uusiRakennuskohde"
                                                :building? true}
                                  :data        {:pysyva-rakennustunnus {:value "PRT-B1"}
                                                :ryhti                 {:buildingKey {:value "B1"}}
                                                :rakennuskohteen-osa   {:huoneistot {:0 {:pysyvaHuoneistotunnus {:value ""}
                                                                                         :ryhti                 {:apartmentKey {:value "B1-A1"}}}
                                                                                     :1 {:pysyvaHuoneistotunnus {:value "PHT-B1-A2"}
                                                                                         :ryhti                 {:apartmentKey {:value "B1-A2"}}}}}}}]}]
    (is (= {"doc-1" {:buildingKey  {"B1" {::id-doc/key-path   [:ryhti :buildingKey]
                                          ::id-doc/value-path [:pysyva-rakennustunnus]
                                          ::id-doc/value      "PRT-B1"}}
                     :apartmentKey {"B1-A1" {::id-doc/key-path   [:rakennuskohteen-osa :huoneistot :0 :ryhti :apartmentKey]
                                             ::id-doc/value-path [:rakennuskohteen-osa :huoneistot :0 :pysyvaHuoneistotunnus]}
                                    "B1-A2" {::id-doc/key-path   [:rakennuskohteen-osa :huoneistot :1 :ryhti :apartmentKey]
                                             ::id-doc/value-path [:rakennuskohteen-osa :huoneistot :1 :pysyvaHuoneistotunnus]
                                             ::id-doc/value      "PHT-B1-A2"}}}}
           (id-doc/parse-documents application)))))

(deftest match-buildings-test
  (let [current-state {"doc-1" {:buildingKey  {"B1" {::id-doc/key-path   [:ryhti :buildingKey]
                                                     ::id-doc/value-path [:pysyva-rakennustunnus]}}
                                :apartmentKey {"B1-A1" {::id-doc/key-path   [:rakennuskohteen-osa :huoneistot :0 :ryhti :apartmentKey]
                                                        ::id-doc/value-path [:rakennuskohteen-osa :huoneistot :0 :pysyvaHuoneistotunnus]}
                                               "B1-A2" {::id-doc/key-path   [:rakennuskohteen-osa :huoneistot :1 :ryhti :apartmentKey]
                                                        ::id-doc/value-path [:rakennuskohteen-osa :huoneistot :1 :pysyvaHuoneistotunnus]
                                                        ::id-doc/value      "PHT-B1-A2"}}}
                       "doc-2" {:buildingKey  {"B2" {::id-doc/key-path   [:ryhti :buildingKey]
                                                     ::id-doc/value-path [:pysyva-rakennustunnus]
                                                     ::id-doc/value      "PRT-B2"}}
                                :apartmentKey {"B2-A1" {::id-doc/key-path   [:rakennuskohteen-osa :huoneistot :0 :ryhti :apartmentKey]
                                                        ::id-doc/value-path [:rakennuskohteen-osa :huoneistot :0 :pysyvaHuoneistotunnus]}
                                               "B2-A2" {::id-doc/key-path   [:rakennuskohteen-osa :huoneistot :1 :ryhti :apartmentKey]
                                                        ::id-doc/value-path [:rakennuskohteen-osa :huoneistot :1 :pysyvaHuoneistotunnus]
                                                        ::id-doc/value      "PHT-B2-A2"}}}}]
    (testing "error cases"
      (testing "unknown building"
        (is (= [{:error     :unknown-field
                 :key-name  :buildingKey
                 :key-value "foobar"}]
               (id-doc/match-buildings [{:buildingKey "foobar"}]
                                       current-state)))
        (is (= [{:error     :incorrect-building
                 :key-name  :apartmentKey
                 :key-value "B1-A1"
                 :doc-id    "doc-1"}
                {:error     :unknown-field
                 :key-name  :buildingKey
                 :key-value "foobar"}]
               (id-doc/match-buildings [{:buildingKey "foobar"
                                         :apartments  [{:apartmentKey        "B1-A1"
                                                        :apartmentIdentifier "uusi-pht"}]}]
                                       current-state))))
      (testing "unknown apartment"
        (is (= [{:error            :unknown-field
                 :key-name         :apartmentKey
                 :key-value        "foobar"
                 :permanent-id-new "uusi-pht"}]
               (id-doc/match-buildings [{:buildingKey "B1"
                                         :apartments  [{:apartmentKey        "foobar"
                                                        :apartmentIdentifier "uusi-pht"}]}]
                                       current-state))))
      (testing "accidentally overwriting"
        (is (= [{:error            :overwriting
                 :doc-id           "doc-1"
                 :key-name         :apartmentKey
                 :key-value        "B1-A2"
                 :permanent-id-new "uusi-pht"
                 :permanent-id-old "PHT-B1-A2"}]
               (id-doc/match-buildings [{:buildingKey "B1"
                                         :apartments  [{:apartmentKey        "B1-A2"
                                                        :apartmentIdentifier "uusi-pht"}]}]
                                       current-state))))
      (testing "apartment under wrong building"
        (is (= [{:error     :incorrect-building
                 :key-name  :apartmentKey
                 :key-value "B2-A1"
                 :doc-id    "doc-2"}]
               (id-doc/match-buildings [{:buildingKey "B1"
                                         :apartments  [{:apartmentKey        "B2-A1"
                                                        :apartmentIdentifier "uusi-pht"}]}]
                                       current-state)))))
    (testing "nothing to do"
      (testing "minimal update with nothing to do"
        (is (= []
               (id-doc/match-buildings [{:buildingKey "B1"}]
                                       current-state))))
      (testing "no changes"
        (is (= []
               (id-doc/match-buildings [{:buildingKey "B1"
                                         :apartments  [{:apartmentKey        "B1-A2"
                                                        :apartmentIdentifier "PHT-B1-A2"}]}]
                                       current-state)))))
    (testing "happy path"
      (testing "update just PRT"
        (is (= [{:doc-id            "doc-1"
                 :key-name          :buildingKey
                 :key-value         "B1"
                 :permanent-id-new  "uusi-prt"
                 :permanent-id-path [:pysyva-rakennustunnus]}]
               (id-doc/match-buildings [{:buildingKey                 "B1"
                                         :permanentBuildingIdentifier "uusi-prt"}]
                                       current-state))))
      (testing "update just PHT"
        (is (= [{:doc-id            "doc-1"
                 :key-name          :apartmentKey
                 :key-value         "B1-A1"
                 :permanent-id-new  "uusi-pht"
                 :permanent-id-path [:rakennuskohteen-osa :huoneistot :0 :pysyvaHuoneistotunnus]}]
               (id-doc/match-buildings [{:buildingKey "B1"
                                         :apartments  [{:apartmentKey        "B1-A1"
                                                        :apartmentIdentifier "uusi-pht"}]}]
                                       current-state))))
      (testing "update PRT & PHT"
        (is (= [{:doc-id            "doc-1"
                 :key-name          :buildingKey
                 :key-value         "B1"
                 :permanent-id-new  "uusi-prt"
                 :permanent-id-path [:pysyva-rakennustunnus]}
                {:doc-id            "doc-1"
                 :key-name          :apartmentKey
                 :key-value         "B1-A1"
                 :permanent-id-new  "uusi-pht"
                 :permanent-id-path [:rakennuskohteen-osa :huoneistot :0 :pysyvaHuoneistotunnus]}]
               (id-doc/match-buildings [{:buildingKey                 "B1"
                                         :permanentBuildingIdentifier "uusi-prt"
                                         :apartments                  [{:apartmentKey        "B1-A1"
                                                                        :apartmentIdentifier "uusi-pht"}]}]
                                       current-state)))))))
