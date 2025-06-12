(ns lupapalvelu.asiointi.permanent-identifiers.schema-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.asiointi.permanent-identifiers.schema :as id-schema]))

(deftest document-path-specs-test
  ;; NOTE: this test serves mostly as documentation to describe which documents & fields
  ;; AsiointiAPI considers when looking for PRT & PHT values.
  (is (= {"rakennuskohteenTietojenKorjaus"             {:apartmentKey          [:rakennuskohteen-osa :huoneistot true :ryhti :apartmentKey]
                                                        :buildingKey           [:ryhti :buildingKey]
                                                        :pysyva-rakennustunnus [:pysyva-rakennustunnus]
                                                        :pysyvaHuoneistotunnus [:rakennuskohteen-osa :huoneistot true :pysyvaHuoneistotunnus]}
          "rakennuskohteenPurkaminen"                  {:apartmentKey          [:rakennuskohteen-osa :huoneistot true :ryhti :apartmentKey]
                                                        :buildingKey           [:ryhti :buildingKey]
                                                        :pysyva-rakennustunnus [:pysyva-rakennustunnus]
                                                        :pysyvaHuoneistotunnus [:rakennuskohteen-osa :huoneistot true :pysyvaHuoneistotunnus]}
          "rakennuskohteenLaajentaminen"               {:apartmentKey          [:rakennuskohteen-osa :huoneistot true :ryhti :apartmentKey]
                                                        :buildingKey           [:ryhti :buildingKey]
                                                        :pysyva-rakennustunnus [:pysyva-rakennustunnus]
                                                        :pysyvaHuoneistotunnus [:rakennuskohteen-osa :huoneistot true :pysyvaHuoneistotunnus]}
          "rakennuskohteenMuuttaminen"                 {:apartmentKey          [:rakennuskohteen-osa :huoneistot true :ryhti :apartmentKey]
                                                        :buildingKey           [:ryhti :buildingKey]
                                                        :pysyva-rakennustunnus [:pysyva-rakennustunnus]
                                                        :pysyvaHuoneistotunnus [:rakennuskohteen-osa :huoneistot true :pysyvaHuoneistotunnus]}
          "uusiRakennuskohde"                          {:apartmentKey          [:rakennuskohteen-osa :huoneistot true :ryhti :apartmentKey]
                                                        :buildingKey           [:ryhti :buildingKey]
                                                        :pysyva-rakennustunnus [:pysyva-rakennustunnus]
                                                        :pysyvaHuoneistotunnus [:rakennuskohteen-osa :huoneistot true :pysyvaHuoneistotunnus]}
          "rakennuskohteenPurkaminen-purkamisilmoitus" {:apartmentKey          [:rakennuskohteen-osa :huoneistot true :ryhti :apartmentKey]
                                                        :buildingKey           [:ryhti :buildingKey]
                                                        :pysyva-rakennustunnus [:pysyva-rakennustunnus]
                                                        :pysyvaHuoneistotunnus [:rakennuskohteen-osa :huoneistot true :pysyvaHuoneistotunnus]}}
         id-schema/document-path-specs)))
