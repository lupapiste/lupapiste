(ns lupapalvelu.application-execution-permit-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.application-api]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer [give-legacy-verdict]]
            [lupapalvelu.prev-permit-api :refer :all]))

(use-fixtures :once (fn [test-fn]
                      (with-test-system (-> {:fixture "minimal"}
                                            test-system/with-remote-components
                                            test-system/with-mock-laundry)
                        (test-fn))))

(defn planning-permit-create-submit-update-and-give-verdict [updates]
  (let [app           (create-and-submit-application pena :propertyId sipoo-property-id
                                                     :operation "sijoittamislupa-v2")
        {app-id :id}  app
        planning-doc  (domain/get-document-by-name app "sijoittamislupa-v2")
        update-resp   (command sonja :update-doc :id app-id :doc (:id planning-doc)
                               :updates updates)
        _             (give-legacy-verdict sonja app-id :R true)]
    (is (true? (:ok update-resp)))
    app-id))

(deftest create-execution-application-from-sijoittamislupa-building
  (testing "Successfully creates execution application from application operation sijoittaminen, type building"
    (let [app-id        (planning-permit-create-submit-update-and-give-verdict
                         [["hankkeen-tyyppi"                                                    "uusiRakennus"]
                          ["uusiRakennus.mitat.tilavuus"                                        "42"]
                          ["uusiRakennus.puhtaan-siirtyman-lupa.puhtaan-siirtyman-lupa-enabled" true]
                          ["uusiRakennus.puhtaan-siirtyman-lupa.tyyppi"                         "teollisuuden-hanke"]])
          response      (command sonja :add-execution-permit :id app-id :operation "vapaa-ajan-asuinrakennus")
          execution-app (query sonja :application :id (:id response))
          ex-docs       (domain/get-document-by-name (:application execution-app) "uusi-rakennus-ei-huoneistoa")]
      (is (true? (:ok response)))
      (is (= (-> ex-docs :data :mitat :tilavuus :value)
             "42"))
      (is (= (-> ex-docs :data :puhtaan-siirtyman-lupa :tyyppi :value)
             "teollisuuden-hanke"))
      (is (= (-> ex-docs :data :puhtaan-siirtyman-lupa :puhtaan-siirtyman-lupa-enabled :value)
             true)))))

(deftest create-execution-application-from-sijoittamislupa-construction
  (testing "Successfully creates execution application from application operation sijoittaminen, type construction"
    (let [app-id        (planning-permit-create-submit-update-and-give-verdict
                         [["hankkeen-tyyppi"            "rakennelma"]
                          ["rakennelma.kokonaisala"     "42"]
                          ["rakennelma.kuvaus"          "Big Antenna"]
                          ["rakennelma.kayttotarkoitus" "Antenni"]])
          response      (command sonja :add-execution-permit :id app-id :operation "masto-tms")
          execution-app (query sonja :application :id (:id response))
          ex-docs       (domain/get-document-by-name (:application execution-app) "kaupunkikuvatoimenpide")]
      (is (true? (:ok response)))
      (is (= (-> ex-docs :data :kokonaisala :value)
             "42"))
      (is (= (-> ex-docs :data :kuvaus :value)
             "Big Antenna"))
      (is (= (-> ex-docs :data :kayttotarkoitus :value)
             "Antenni"))
      (is (= (-> ex-docs :data :puhtaan-siirtyman-lupa :puhtaan-siirtyman-lupa-enabled :value)
             false)))))

(deftest create-execution-application-from-sijoittamislupa-area
  (testing "Successfully creates execution application from application operation sijoittaminen, type area"
    (let [app-id         (planning-permit-create-submit-update-and-give-verdict
                          [["hankkeen-tyyppi"                                       "erityista-toimintaa-varten-rakennettava-alue"]
                           ["erityista-toimintaa-varten-rakennettava-alue.tyyppi"   "kaatopaikka"]
                           ["erityista-toimintaa-varten-rakennettava-alue.pintaAla" "42"]
                           ["erityista-toimintaa-varten-rakennettava-alue.kuvaus"   "Big Dump"]])
          response      (command sonja :add-execution-permit :id app-id :operation "erityista-toimintaa-alue")
          execution-app (query sonja :application :id (:id response))
          ex-docs       (domain/get-document-by-name (:application execution-app) "erityista-toimintaa-varten-rakennettava-alue")]
      (is (true? (:ok response)))
      (is (= (-> ex-docs :data :pintaAla :value)
             "42"))
      (is (= (-> ex-docs :data :kuvaus :value)
             "Big Dump"))
      (is (= (-> ex-docs :data :tyyppi :value)
             "kaatopaikka")))))

(deftest create-execution-application-from-sijoittamislupa-construction
  (testing "Execution application can only be created from application operation sijoittaminen"
    (let [{app-id :id} (create-and-submit-application pena :propertyId sipoo-property-id
                                                      :operation "lannan-varastointi-v2")
          _            (give-legacy-verdict sonja app-id :YM false) ; YM -> no kuulutus
          response     (command sonja :add-execution-permit :id app-id
                                :operation "vapaa-ajan-asuinrakennus")]
      (is (false? (:ok response))))))
